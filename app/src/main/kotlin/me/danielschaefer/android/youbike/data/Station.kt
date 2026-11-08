package me.danielschaefer.android.youbike.data

import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val sno: String,
    val sna: String,
    val snaen: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("available_rent_bikes")
    val availableRentBikes: Int,
    @SerialName("available_return_bikes")
    val availableReturnBikes: Int,
    val updateTime: String,
    val act: String
) {
    val isActive: Boolean
        get() = act == "1"

    fun getDisplayName(locale: Locale = Locale.getDefault()): String {
        val isChinese = locale.language == "zh" || locale.toLanguageTag().startsWith("zh")
        return if (isChinese) {
            sna.removePrefix("YouBike2.0_")
        } else {
            snaen.removePrefix("YouBike2.0_")
        }
    }
}

/** Station as returned by the New Taipei City open data API; all values are strings. */
@Serializable
data class NewTaipeiStation(
    val sno: String,
    val sna: String,
    val snaen: String,
    val lat: String,
    val lng: String,
    // Available bikes (YouBike 2.0 + E-bikes)
    @SerialName("sbi_quantity")
    val availableRentBikes: String,
    // Empty docks
    val bemp: String,
    // Last update, e.g. "20261108T161904"
    val mday: String,
    val act: String
) {
    /** Converts to the Taipei format used throughout the app, or null if the entry is malformed. */
    fun toStation(): Station? {
        return Station(
            sno = sno,
            sna = sna,
            snaen = snaen,
            latitude = lat.toDoubleOrNull() ?: return null,
            longitude = lng.toDoubleOrNull() ?: return null,
            availableRentBikes = availableRentBikes.toIntOrNull() ?: return null,
            availableReturnBikes = bemp.toIntOrNull() ?: return null,
            updateTime = convertTimestamp(mday),
            act = act
        )
    }

    private fun convertTimestamp(mday: String): String {
        return try {
            val date = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(mday) ?: return ""
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date)
        } catch (e: Exception) {
            ""
        }
    }
}

fun formatDistance(meters: Int): String = when {
    meters < 1000 -> "$meters m"
    else -> String.format("%.1f km", meters / 1000.0)
}

data class StationWithDistance(
    val station: Station,
    val distanceMeters: Int,
    val bearingDegrees: Float = 0f
) {
    val formattedDistance: String
        get() = formatDistance(distanceMeters)

    // Returns arrow character pointing in the direction of the station
    val directionArrow: String
        get() {
            if (distanceMeters < 0) return ""
            // Normalize bearing to 0-360
            val normalized = ((bearingDegrees % 360) + 360) % 360
            // 8 compass directions
            return when {
                normalized < 22.5 || normalized >= 337.5 -> "↑" // N
                normalized < 67.5 -> "↗" // NE
                normalized < 112.5 -> "→" // E
                normalized < 157.5 -> "↘" // SE
                normalized < 202.5 -> "↓" // S
                normalized < 247.5 -> "↙" // SW
                normalized < 292.5 -> "←" // W
                else -> "↖" // NW
            }
        }
}
