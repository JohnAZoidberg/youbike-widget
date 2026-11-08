package me.danielschaefer.android.youbike

object Config {
    const val API_URL = "https://tcgbusfs.blob.core.windows.net/dotapp/youbike/v2/youbike_immediate.json"

    val FAVORITE_STATION_IDS = listOf(
        // 捷運古亭站(3號出口)
        "500101232",
        // 和平金山路口
        "500101105",
        // 松山高中
        "500112054"
    )

    const val MAX_NEAREST_STATIONS = 25

    // WorkManager enforces a minimum of 15 minutes for periodic work
    const val UPDATE_INTERVAL_MINUTES = 15L
}
