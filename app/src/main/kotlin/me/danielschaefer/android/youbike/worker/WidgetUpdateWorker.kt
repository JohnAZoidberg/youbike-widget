package me.danielschaefer.android.youbike.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import me.danielschaefer.android.youbike.Config
import me.danielschaefer.android.youbike.R
import me.danielschaefer.android.youbike.data.StationSource
import me.danielschaefer.android.youbike.data.YouBikeRepository
import me.danielschaefer.android.youbike.widget.WidgetData
import me.danielschaefer.android.youbike.widget.YouBikeWidget
import me.danielschaefer.android.youbike.widget.YouBikeWidgetDataStore

private val StationSource.nameRes: Int
    get() = when (this) {
        StationSource.TAIPEI -> R.string.source_taipei
        StationSource.NEW_TAIPEI -> R.string.source_new_taipei
    }

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = YouBikeRepository()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Update started (attempt $runAttemptCount, tags=$tags)")

        // WorkManager sometimes runs work directly in the already running app process instead
        // of through JobScheduler, e.g. when a broadcast briefly wakes the app while the periodic
        // update is due. Such runs don't get the network access JobScheduler grants to jobs, so
        // once the app is back in the background Android blocks its network and requests fail
        // with UnknownHostException. Wait a bit (JobScheduler may start the job in parallel,
        // which unblocks the network) and otherwise retry soon instead of recording an error.
        if (!awaitNetworkUnblocked(NETWORK_UNBLOCK_TIMEOUT_MS)) {
            Log.w(TAG, "Network blocked for this app, retrying later")
            return Result.retry()
        }

        return try {
            val fetchResult = repository.fetchStations()
            val stations = fetchResult.stations
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
            val apiUpdateTime = fetchResult.updateTime
            // If one source failed, show the other's stations but flag the update as incomplete
            val partialError = fetchResult.failedSources.takeIf { it.isNotEmpty() }?.let { failed ->
                val names = failed.joinToString { context.getString(it.nameRes) }
                Log.w(TAG, "Fetching stations failed for: $failed")
                context.getString(R.string.update_failed, names)
            }

            val widgetData = WidgetData(
                nearestStations = nearest,
                favoriteStations = favoritesWithDistance,
                lastUpdated = timestamp,
                apiUpdateTime = apiUpdateTime,
                hasLocation = location != null,
                error = partialError
            )

            YouBikeWidgetDataStore.saveData(context, widgetData)
            updateWidgets()

            Log.d(TAG, "Update finished, api time $apiUpdateTime")
            Result.success()
        } catch (e: CancellationException) {
            // Work was stopped by WorkManager; don't record that as an error
            throw e
        } catch (e: Exception) {
            // The network may also get blocked while the update runs (see above)
            if (!awaitNetworkUnblocked(BLOCKED_CHECK_TIMEOUT_MS)) {
                Log.w(TAG, "Update failed while network was blocked for this app, retrying later", e)
                return Result.retry()
            }
            Log.w(TAG, "Update failed", e)
            val errorMsg = context.getString(R.string.update_failed, e.message ?: "Unknown")
            YouBikeWidgetDataStore.saveError(context, errorMsg)
            updateWidgets()
            // Don't return Result.retry(): for periodic work WorkManager then schedules the next
            // run with backoff instead of the period, so a few failures in a row (e.g. while
            // offline) would push the next update out by hours. The next periodic run retries
            // anyway. A blocked network is handled separately, where a retry is needed and short.
            Result.success()
        }
    }

    /**
     * Waits up to [timeoutMs] until Android lets this app use the default network. Returns false
     * if the network stays blocked for the app. Having no network at all is not reported as
     * blocked, as that is an ordinary failure.
     */
    private suspend fun awaitNetworkUnblocked(timeoutMs: Long): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        // null until the first callback, which reports the current state right after registering
        val blocked = MutableStateFlow<Boolean?>(null)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onBlockedStatusChanged(network: Network, isBlocked: Boolean) {
                blocked.value = isBlocked
            }

            override fun onLost(network: Network) {
                blocked.value = null
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        return try {
            withTimeoutOrNull(timeoutMs) { blocked.first { it == false } }
            blocked.value != true
        } finally {
            connectivityManager.unregisterNetworkCallback(callback)
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
        private const val NETWORK_UNBLOCK_TIMEOUT_MS = 20_000L
        private const val BLOCKED_CHECK_TIMEOUT_MS = 2_000L

        // Retries are only requested when the network is blocked; keep them short and linear
        // so the widget doesn't stay stale for long.
        private const val RETRY_BACKOFF_SECONDS = 30L
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
                .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
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
                .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
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
