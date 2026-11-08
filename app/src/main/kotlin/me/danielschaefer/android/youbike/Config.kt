package me.danielschaefer.android.youbike

object Config {
    // Taipei City stations
    const val TAIPEI_API_URL = "https://tcgbusfs.blob.core.windows.net/dotapp/youbike/v2/youbike_immediate.json"

    // New Taipei City stations (paginated with ?page=N&size=M)
    const val NEW_TAIPEI_API_URL =
        "https://data.ntpc.gov.tw/api/datasets/010e5b15-3823-4b20-b401-b1cf000550c5/json"

    val FAVORITE_STATION_IDS = listOf(
        // 捷運古亭站(3號出口)
        "500101232",
        // 和平金山路口
        "500101105",
        // 松山高中
        "500112054"
    )

    const val MAX_NEAREST_STATIONS = 25

    // Stations further away than this are not shown as "nearest"
    const val MAX_NEAREST_DISTANCE_METERS = 3000

    // WorkManager enforces a minimum of 15 minutes for periodic work
    const val UPDATE_INTERVAL_MINUTES = 15L
}
