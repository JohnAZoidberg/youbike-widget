package com.youbike.widget.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.youbike.widget.data.Station
import com.youbike.widget.data.StationWithDistance
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_data")

object YouBikeWidgetDataStore {
    private val NEAREST_STATIONS = stringPreferencesKey("nearest_stations")
    private val FAVORITE_STATIONS = stringPreferencesKey("favorite_stations")
    private val LAST_UPDATED = stringPreferencesKey("last_updated")
    private val HAS_LOCATION = booleanPreferencesKey("has_location")
    private val ERROR = stringPreferencesKey("error")

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveData(context: Context, data: WidgetData) {
        context.widgetDataStore.edit { prefs ->
            prefs[NEAREST_STATIONS] = json.encodeToString(data.nearestStations.map { it.toSerializable() })
            prefs[FAVORITE_STATIONS] = json.encodeToString(data.favoriteStations.map { it.toSerializable() })
            prefs[LAST_UPDATED] = data.lastUpdated
            prefs[HAS_LOCATION] = data.hasLocation
            if (data.error != null) {
                prefs[ERROR] = data.error
            } else {
                prefs.remove(ERROR)
            }
        }
    }

    suspend fun getData(context: Context): WidgetData? {
        val prefs = context.widgetDataStore.data.first()
        val nearestJson = prefs[NEAREST_STATIONS] ?: return null
        val favoriteJson = prefs[FAVORITE_STATIONS] ?: return null
        val lastUpdated = prefs[LAST_UPDATED] ?: return null

        return try {
            val nearest = json.decodeFromString<List<SerializableStationWithDistance>>(nearestJson)
                .map { it.toStationWithDistance() }
            val favorites = json.decodeFromString<List<SerializableStationWithDistance>>(favoriteJson)
                .map { it.toStationWithDistance() }

            WidgetData(
                nearestStations = nearest,
                favoriteStations = favorites,
                lastUpdated = lastUpdated,
                hasLocation = prefs[HAS_LOCATION] ?: false,
                error = prefs[ERROR]
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveError(context: Context, error: String) {
        context.widgetDataStore.edit { prefs ->
            prefs[ERROR] = error
            prefs[LAST_UPDATED] = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
        }
    }
}

@kotlinx.serialization.Serializable
private data class SerializableStationWithDistance(
    val station: Station,
    val distanceMeters: Int
) {
    fun toStationWithDistance() = StationWithDistance(station, distanceMeters)
}

private fun StationWithDistance.toSerializable() = SerializableStationWithDistance(station, distanceMeters)
