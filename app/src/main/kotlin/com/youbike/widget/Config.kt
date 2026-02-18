package com.youbike.widget

object Config {
    const val API_URL = "https://tcgbusfs.blob.core.windows.net/dotapp/youbike/v2/youbike_immediate.json"

    val FAVORITE_STATION_IDS = listOf(
        "500101232", // 捷運古亭站(3號出口)
        "500101105", // 和平金山路口
        "500112054", // 松山高中
    )

    const val NEAREST_STATIONS_COUNT = 2
    const val UPDATE_INTERVAL_MINUTES = 10L
}
