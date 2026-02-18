package com.youbike.widget.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.youbike.widget.Config
import com.youbike.widget.data.YouBikeRepository
import com.youbike.widget.widget.WidgetData
import com.youbike.widget.widget.YouBikeWidget
import com.youbike.widget.widget.YouBikeWidgetDataStore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repository = YouBikeRepository()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        return try {
            val stations = repository.fetchStations()
            val location = getLocation()

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

            val widgetData = WidgetData(
                nearestStations = nearest,
                favoriteStations = favoritesWithDistance,
                lastUpdated = timestamp,
                hasLocation = location != null
            )

            YouBikeWidgetDataStore.saveData(context, widgetData)
            updateWidgets()

            Result.success()
        } catch (e: Exception) {
            YouBikeWidgetDataStore.saveError(context, "更新失敗: ${e.message}")
            updateWidgets()
            Result.retry()
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

        return try {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).await()
        } catch (e: Exception) {
            // Try last known location as fallback
            try {
                fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun updateWidgets() {
        val manager = GlanceAppWidgetManager(context)
        val widget = YouBikeWidget()
        manager.getGlanceIds(YouBikeWidget::class.java).forEach { glanceId ->
            widget.update(context, glanceId)
        }
    }

    companion object {
        private const val WORK_NAME = "youbike_widget_update"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                Config.UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
