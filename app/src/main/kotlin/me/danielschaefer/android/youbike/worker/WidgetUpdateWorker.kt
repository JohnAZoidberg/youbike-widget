package me.danielschaefer.android.youbike.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.*
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import me.danielschaefer.android.youbike.Config
import me.danielschaefer.android.youbike.R
import me.danielschaefer.android.youbike.data.YouBikeRepository
import me.danielschaefer.android.youbike.widget.WidgetData
import me.danielschaefer.android.youbike.widget.YouBikeWidget
import me.danielschaefer.android.youbike.widget.YouBikeWidgetDataStore

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = YouBikeRepository()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Update started (attempt $runAttemptCount, tags=$tags)")
        return try {
            val stations = repository.fetchStations()
            val locationStart = SystemClock.elapsedRealtime()
            val location = getLocation()
            val locationMillis = SystemClock.elapsedRealtime() - locationStart
            Log.d(TAG, "Location ${if (location != null) "found" else "unavailable"} after ${locationMillis}ms")

            val favorites = repository.getFavoriteStations(stations)
            val nearest = if (location != null) {
                repository.getNearestStations(
                    stations,
                    location,
                    excludeIds = Config.FAVORITE_STATION_IDS.toSet()
                )
            } else {
                emptyList()
            }

            val favoritesWithDistance = if (location != null) {
                repository.addDistanceToFavorites(favorites, location)
            } else {
                favorites
            }

            val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            // Same "yyyy-MM-dd HH:mm:ss" format for all stations, so string max is the newest
            val apiUpdateTime = stations.maxOfOrNull { it.updateTime }

            val widgetData = WidgetData(
                nearestStations = nearest,
                favoriteStations = favoritesWithDistance,
                lastUpdated = timestamp,
                apiUpdateTime = apiUpdateTime,
                hasLocation = location != null
            )

            YouBikeWidgetDataStore.saveData(context, widgetData)
            updateWidgets()

            Log.d(TAG, "Update finished, api time $apiUpdateTime")
            Result.success()
        } catch (e: CancellationException) {
            // Work was stopped by WorkManager; don't record that as an error
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update failed", e)
            val errorMsg = context.getString(R.string.update_failed, e.message ?: "Unknown")
            YouBikeWidgetDataStore.saveError(context, errorMsg)
            updateWidgets()
            // Don't return Result.retry(): for periodic work WorkManager then schedules the next
            // run with exponential backoff instead of the period, so a few failures in a row
            // push the next update out by up to 5 hours. The next periodic run retries anyway.
            Result.success()
        }
    }

    private suspend fun getLocation(): Location? {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            return null
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(TimeUnit.MINUTES.toMillis(2))
            .setDurationMillis(LOCATION_TIMEOUT_MS)
            .build()
        val cancellationToken = CancellationTokenSource()
        val current = try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS + 5_000) {
                fusedLocationClient.getCurrentLocation(request, cancellationToken.token).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed", e)
            null
        } finally {
            cancellationToken.cancel()
        }
        if (current != null) return current

        // getCurrentLocation() usually returns null rather than throwing when no fix is
        // available (common in the background), so fall back to the last known location.
        val last = try {
            fusedLocationClient.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val ageMillis = last?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
        Log.d(TAG, "Current location unavailable, last known location age: ${ageMillis?.let { "${it / 1000}s" }}")
        return last?.takeIf { ageMillis != null && ageMillis < MAX_LAST_LOCATION_AGE_MS }
    }

    private suspend fun updateWidgets() {
        // Widgets observe the data store, but update() is still needed to start a
        // session when none is running.
        val manager = GlanceAppWidgetManager(context)
        val widget = YouBikeWidget()
        manager.getGlanceIds(YouBikeWidget::class.java).forEach { glanceId ->
            widget.update(context, glanceId)
        }
    }

    companion object {
        private const val TAG = "YouBikeWorker"
        private const val WORK_NAME = "youbike_widget_update"
        private const val REFRESH_WORK_NAME = "youbike_widget_refresh"
        private const val LOCATION_TIMEOUT_MS = 15_000L
        private val MAX_LAST_LOCATION_AGE_MS = TimeUnit.MINUTES.toMillis(30)

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                Config.UPDATE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            // UPDATE (rather than KEEP) applies changes to the request to already-installed
            // apps while keeping the existing schedule.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Expedited so a manual refresh runs right away instead of waiting for job quota
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            // Unique so repeated taps don't start several workers racing each other
            WorkManager.getInstance(context).enqueueUniqueWork(
                REFRESH_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
