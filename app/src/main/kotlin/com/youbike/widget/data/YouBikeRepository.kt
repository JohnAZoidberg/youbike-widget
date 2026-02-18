package com.youbike.widget.data

import android.location.Location
import com.youbike.widget.Config
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.math.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class YouBikeRepository {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    suspend fun fetchStations(): List<Station> {
        var lastException: Exception? = null
        repeat(2) { attempt ->
            try {
                return client.get(Config.API_URL).body()
            } catch (e: Exception) {
                lastException = e
                if (attempt == 0) {
                    delay(1000)
                }
            }
        }
        throw lastException!!
    }

    fun getFavoriteStations(allStations: List<Station>): List<StationWithDistance> {
        return Config.FAVORITE_STATION_IDS.mapNotNull { id ->
            allStations.find { it.sno == id && it.isActive }
        }.map { station ->
            StationWithDistance(station, -1)
        }
    }

    fun getNearestStations(
        allStations: List<Station>,
        location: Location,
        count: Int = Config.MAX_NEAREST_STATIONS,
        excludeIds: Set<String> = emptySet()
    ): List<StationWithDistance> {
        return allStations
            .filter { it.isActive && it.sno !in excludeIds }
            .map { station ->
                val distance = calculateDistance(
                    location.latitude,
                    location.longitude,
                    station.latitude,
                    station.longitude
                )
                val bearing = calculateBearing(
                    location.latitude,
                    location.longitude,
                    station.latitude,
                    station.longitude
                )
                StationWithDistance(station, distance, bearing)
            }
            .sortedBy { it.distanceMeters }
            .take(count)
    }

    fun addDistanceToFavorites(favorites: List<StationWithDistance>, location: Location): List<StationWithDistance> {
        return favorites.map { fav ->
            val distance = calculateDistance(
                location.latitude,
                location.longitude,
                fav.station.latitude,
                fav.station.longitude
            )
            val bearing = calculateBearing(
                location.latitude,
                location.longitude,
                fav.station.latitude,
                fav.station.longitude
            )
            fav.copy(distanceMeters = distance, bearingDegrees = bearing)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0 // Earth's radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2).pow(2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return (r * c).toInt()
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val x = cos(phi2) * sin(deltaLambda)
        val y = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)

        val bearing = Math.toDegrees(atan2(x, y))
        return bearing.toFloat()
    }
}
