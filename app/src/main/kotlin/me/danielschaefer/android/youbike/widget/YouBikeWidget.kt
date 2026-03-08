package me.danielschaefer.android.youbike.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import java.util.Locale
import me.danielschaefer.android.youbike.R
import me.danielschaefer.android.youbike.ThemePreference
import me.danielschaefer.android.youbike.data.StationWithDistance
import me.danielschaefer.android.youbike.worker.WidgetUpdateWorker

data class WidgetData(
    val nearestStations: List<StationWithDistance>,
    val favoriteStations: List<StationWithDistance>,
    val lastUpdated: String,
    val apiUpdateTime: String? = null,
    val hasLocation: Boolean,
    val error: String? = null
)

private fun colorProvider(color: Color) = ColorProvider(color, color)

class YouBikeWidget : GlanceAppWidget() {

    // Use Exact mode - renders for exact sizes provided by launcher
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = YouBikeWidgetDataStore.getData(context)
        val isDark = ThemePreference.isDark(context)

        provideContent {
            WidgetContent(data, isDark)
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData?, isDark: Boolean) {
        val context = LocalContext.current
        val locale = context.resources.configuration.locales[0]
        val size = LocalSize.current
        val heightDp = size.height.value
        val widthDp = size.width.value
        val isCompact = heightDp < 100

        Log.d("YouBikeWidget", "Widget size: ${widthDp}x${heightDp}dp")

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0xFF1E1E1E) else Color.White)
                    .padding(if (isCompact) 8.dp else 12.dp)
            ) {
                val hasStationData = data != null &&
                    (data.nearestStations.isNotEmpty() || data.favoriteStations.isNotEmpty())

                if (data == null) {
                    LoadingContent(context, isDark)
                } else if (!hasStationData && data.error != null) {
                    ErrorContent(data.error, isDark)
                } else if (hasStationData) {
                    // Always show header
                    HeaderRow(context, isDark)

                    // Combine all stations and sort by distance
                    val favoriteIds = data.favoriteStations.map { it.station.sno }.toSet()
                    val allStations = (data.favoriteStations + data.nearestStations)
                        .distinctBy { it.station.sno }
                        .sortedBy { it.distanceMeters.let { d -> if (d < 0) Int.MAX_VALUE else d } }

                    // Use LazyColumn to fill available space
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(allStations) { station ->
                            val isFavorite = station.station.sno in favoriteIds
                            StationRow(
                                station,
                                isFavorite = isFavorite,
                                locale = locale,
                                compact = false,
                                isDark = isDark
                            )
                        }
                    }
                } else {
                    LoadingContent(context, isDark)
                }
            }
        }
    }

    @Composable
    private fun LoadingContent(context: Context, isDark: Boolean) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.loading),
                style = TextStyle(
                    color = colorProvider(if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)),
                    fontSize = 14.sp
                )
            )
        }
    }

    @Composable
    private fun ErrorContent(error: String, isDark: Boolean) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = error,
                style = TextStyle(
                    color = colorProvider(if (isDark) Color(0xFFFF6666) else Color(0xFFCC0000)),
                    fontSize = 12.sp
                )
            )
        }
    }

    @Composable
    private fun HeaderRow(context: Context, isDark: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = context.getString(R.string.header_station),
                modifier = GlanceModifier.defaultWeight(),
                style = headerStyle(isDark)
            )
            // Direction arrow column (no header text needed)
            Text(
                text = "",
                modifier = GlanceModifier.width(20.dp),
                style = headerStyle(isDark)
            )
            Text(
                text = context.getString(R.string.header_distance),
                modifier = GlanceModifier.width(56.dp),
                style = headerStyle(isDark)
            )
            Text(
                text = context.getString(R.string.header_spots),
                modifier = GlanceModifier.width(40.dp),
                style = headerStyle(isDark)
            )
            Text(
                text = context.getString(R.string.header_bikes),
                modifier = GlanceModifier.width(40.dp),
                style = headerStyle(isDark)
            )
        }
    }

    @Composable
    private fun StationRow(
        station: StationWithDistance,
        isFavorite: Boolean,
        locale: Locale,
        compact: Boolean = false,
        isDark: Boolean = false
    ) {
        fun statusColor(count: Int) = when {
            count == 0 -> colorProvider(
                if (isDark) Color(0xFFFF6666) else Color(0xFFCC0000)
            )
            count <= 3 -> colorProvider(
                if (isDark) Color(0xFFFFAA44) else Color(0xFFFF8800)
            )
            else -> colorProvider(
                if (isDark) Color(0xFF66CC66) else Color(0xFF008800)
            )
        }
        val spotsColor = statusColor(station.station.availableReturnBikes)
        val bikesColor = statusColor(station.station.availableRentBikes)

        val lat = station.station.latitude
        val lon = station.station.longitude
        val label = station.station.getDisplayName(locale)
        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 1.dp else 2.dp)
                .clickable(actionStartActivity(mapIntent)),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = (if (isFavorite) "⭐ " else "") + station.station.getDisplayName(locale),
                modifier = GlanceModifier.defaultWeight(),
                style = if (compact) compactCellStyle(isDark) else cellStyle(isDark),
                maxLines = 1
            )
            Text(
                text = station.directionArrow,
                modifier = GlanceModifier.width(20.dp),
                style = if (compact) compactCellStyle(isDark) else cellStyle(isDark)
            )
            Text(
                text = if (station.distanceMeters >= 0) station.formattedDistance else "-",
                modifier = GlanceModifier.width(if (compact) 48.dp else 56.dp),
                style = if (compact) compactCellStyle(isDark) else cellStyle(isDark)
            )
            Text(
                text = station.station.availableReturnBikes.toString(),
                modifier = GlanceModifier.width(if (compact) 32.dp else 40.dp),
                style = TextStyle(
                    color = spotsColor,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = station.station.availableRentBikes.toString(),
                modifier = GlanceModifier.width(if (compact) 32.dp else 40.dp),
                style = TextStyle(
                    color = bikesColor,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    @Composable
    private fun FooterRow(context: Context, timestamp: String, error: String?, isDark: Boolean) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionRunCallback<RefreshAction>()),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "🔄 ",
                style = TextStyle(
                    color = colorProvider(if (isDark) Color(0xFF666666) else Color(0xFF999999)),
                    fontSize = 10.sp
                )
            )
            if (error != null) {
                Text(
                    text = "⚠ ",
                    style = TextStyle(
                        color = colorProvider(if (isDark) Color(0xFFFF6666) else Color(0xFFCC0000)),
                        fontSize = 10.sp
                    )
                )
            }
            Text(
                text = context.getString(R.string.update_time, timestamp),
                style = TextStyle(
                    color = if (error != null) {
                        colorProvider(if (isDark) Color(0xFFFF6666) else Color(0xFFCC0000))
                    } else {
                        colorProvider(if (isDark) Color(0xFF666666) else Color(0xFF999999))
                    },
                    fontSize = 10.sp
                )
            )
        }
    }

    private fun headerStyle(isDark: Boolean) = TextStyle(
        color = colorProvider(if (isDark) Color(0xFFCCCCCC) else Color(0xFF333333)),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )

    private fun cellStyle(isDark: Boolean) = TextStyle(
        color = colorProvider(if (isDark) Color(0xFFBBBBBB) else Color(0xFF444444)),
        fontSize = 12.sp
    )

    private fun compactCellStyle(isDark: Boolean) = TextStyle(
        color = colorProvider(if (isDark) Color(0xFFBBBBBB) else Color(0xFF444444)),
        fontSize = 11.sp
    )
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdateWorker.runOnce(context)
    }
}

class YouBikeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YouBikeWidget()
}
