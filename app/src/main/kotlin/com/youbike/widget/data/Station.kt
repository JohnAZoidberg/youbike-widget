package com.youbike.widget.data

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

data class StationWithDistance(
    val station: Station,
    val distanceMeters: Int
) {
    val formattedDistance: String
        get() = when {
            distanceMeters < 1000 -> "$distanceMeters m"
            else -> String.format("%.1f km", distanceMeters / 1000.0)
        }
}
