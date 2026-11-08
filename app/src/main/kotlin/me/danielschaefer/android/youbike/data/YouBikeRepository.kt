package me.danielschaefer.android.youbike.data

import android.location.Location
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import me.danielschaefer.android.youbike.Config

enum class StationSource { TAIPEI, NEW_TAIPEI }

data class StationFetchResult(
    val stations: List<Station>,
    // Sources that could not be fetched; their stations are missing from [stations]
    val failedSources: List<StationSource>,
    // Newest station timestamp of the least recently updated source ("yyyy-MM-dd HH:mm:ss")
    val updateTime: String?
)

class YouBikeRepository {
    private val client get() = sharedClient

    companion object {
        // The API currently returns all ~1600 stations in one page of this size
        private const val NEW_TAIPEI_PAGE_SIZE = 2000
        private const val NEW_TAIPEI_MAX_PAGES = 5

        // Shared across worker runs; creating a client per run and never closing it leaks
        // its connection pool and dispatcher threads.
        private val sharedClient by lazy {
            HttpClient(Android) {
                engine {
                    connectTimeout = 15_000
                    socketTimeout = 30_000
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        }
                    )
                }
            }
        }
    }

    /**
     * Fetches Taipei and New Taipei stations in parallel. If one source fails, the stations of
     * the other are still returned and the failure is reported in [StationFetchResult.failedSources].
     * Throws if all sources fail.
     */
    suspend fun fetchStations(): StationFetchResult = coroutineScope {
        val requests = mapOf(
            StationSource.TAIPEI to async { fetchCatching { fetchTaipeiStations() } },
            StationSource.NEW_TAIPEI to async { fetchCatching { fetchNewTaipeiStations() } }
        )
        val results = requests.mapValues { it.value.await() }

        val succeeded = results.values.mapNotNull { it.getOrNull() }
        if (succeeded.isEmpty()) {
            throw results.values.first().exceptionOrNull()!!
        }

        StationFetchResult(
            stations = succeeded.flatten(),
            failedSources = results.filterValues { it.isFailure }.keys.toList(),
            // Report the oldest source's time so a stale source isn't hidden by a fresh one.
            // All timestamps use the same "yyyy-MM-dd HH:mm:ss" format, so strings compare correctly.
            updateTime = succeeded.mapNotNull { stations -> stations.maxOfOrNull { it.updateTime } }.minOrNull()
        )
    }

    private suspend fun fetchTaipeiStations(): List<Station> = withRetry {
        client.get(Config.TAIPEI_API_URL).body()
    }

    private suspend fun fetchNewTaipeiStations(): List<Station> {
        val stations = mutableListOf<Station>()
        for (page in 0 until NEW_TAIPEI_MAX_PAGES) {
            val batch: List<NewTaipeiStation> = withRetry {
                client.get(Config.NEW_TAIPEI_API_URL) {
                    parameter("page", page)
                    parameter("size", NEW_TAIPEI_PAGE_SIZE)
                }.body()
            }
            stations += batch.mapNotNull { it.toStation() }
            if (batch.size < NEW_TAIPEI_PAGE_SIZE) break
        }
        return stations
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            delay(1000)
            return block()
        }
    }

    private suspend fun <T> fetchCatching(block: suspend () -> T): kotlin.Result<T> {
        return try {
            kotlin.Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
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
        maxDistanceMeters: Int = Config.MAX_NEAREST_DISTANCE_METERS,
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
            .filter { it.distanceMeters <= maxDistanceMeters }
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
