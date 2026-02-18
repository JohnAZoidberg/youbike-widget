package com.youbike.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import com.youbike.widget.MainActivity
import com.youbike.widget.R
import com.youbike.widget.data.StationWithDistance
import com.youbike.widget.worker.WidgetUpdateWorker
import java.util.Locale

data class WidgetData(
    val nearestStations: List<StationWithDistance>,
    val favoriteStations: List<StationWithDistance>,
    val lastUpdated: String,
    val hasLocation: Boolean,
    val error: String? = null
)

class YouBikeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = YouBikeWidgetDataStore.getData(context)

        provideContent {
            WidgetContent(data)
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData?) {
        val context = LocalContext.current
        val locale = context.resources.configuration.locales[0]
        val size = LocalSize.current

        // Calculate how many station rows can fit
        // Padding: 24dp total, Header: ~18dp, Footer: ~14dp, Each row: ~22dp
        val heightDp = size.height.value
        val isCompact = heightDp < 100
        val headerFooterHeight = if (isCompact) 0f else 36f // header + footer + spacers
        val availableHeight = heightDp - 24f - headerFooterHeight // subtract padding
        val rowHeight = 22f
        val maxRows = (availableHeight / rowHeight).toInt().coerceAtLeast(1)

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color.White, Color(0xFF1E1E1E)))
                    .padding(if (isCompact) 8.dp else 12.dp)
            ) {
                val hasStationData = data != null &&
                    (data.nearestStations.isNotEmpty() || data.favoriteStations.isNotEmpty())

                if (data == null) {
                    LoadingContent(context)
                } else if (!hasStationData && data.error != null) {
                    ErrorContent(data.error)
                } else if (hasStationData) {
                    // Main content area - opens app
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                            .clickable(actionStartActivity<MainActivity>())
                    ) {
                        if (!isCompact) {
                            HeaderRow(context)
                            Spacer(GlanceModifier.height(4.dp))
                        }

                        // Combine all stations and sort by distance
                        val favoriteIds = data.favoriteStations.map { it.station.sno }.toSet()
                        val allStations = (data.favoriteStations + data.nearestStations)
                            .distinctBy { it.station.sno }
                            .sortedBy { it.distanceMeters.let { d -> if (d < 0) Int.MAX_VALUE else d } }
                            .take(maxRows)

                        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                            items(allStations) { station ->
                                val isFavorite = station.station.sno in favoriteIds
                                StationRow(
                                    station,
                                    isFavorite = isFavorite,
                                    locale = locale,
                                    compact = isCompact
                                )
                            }
                        }
                    }

                    // Footer - triggers refresh
                    if (!isCompact) {
                        Spacer(GlanceModifier.height(4.dp))
                        FooterRow(context, data.lastUpdated, data.error)
                    }
                } else {
                    LoadingContent(context)
                }
            }
        }
    }

    @Composable
    private fun LoadingContent(context: Context) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.loading),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF666666), Color(0xFFAAAAAA)),
                    fontSize = 14.sp
                )
            )
        }
    }

    @Composable
    private fun ErrorContent(error: String) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = error,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFCC0000), Color(0xFFFF6666)),
                    fontSize = 12.sp
                )
            )
        }
    }

    @Composable
    private fun HeaderRow(context: Context) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = context.getString(R.string.header_station),
                modifier = GlanceModifier.defaultWeight(),
                style = headerStyle()
            )
            Text(
                text = context.getString(R.string.header_distance),
                modifier = GlanceModifier.width(56.dp),
                style = headerStyle()
            )
            Text(
                text = context.getString(R.string.header_spots),
                modifier = GlanceModifier.width(40.dp),
                style = headerStyle()
            )
            Text(
                text = context.getString(R.string.header_bikes),
                modifier = GlanceModifier.width(40.dp),
                style = headerStyle()
            )
        }
    }

    @Composable
    private fun StationRow(
        station: StationWithDistance,
        isFavorite: Boolean,
        locale: Locale,
        compact: Boolean = false
    ) {
        val spotsColor = when {
            station.station.availableReturnBikes == 0 -> ColorProvider(Color(0xFFCC0000), Color(0xFFFF6666))
            station.station.availableReturnBikes <= 3 -> ColorProvider(Color(0xFFFF8800), Color(0xFFFFAA44))
            else -> ColorProvider(Color(0xFF008800), Color(0xFF66CC66))
        }
        val bikesColor = when {
            station.station.availableRentBikes == 0 -> ColorProvider(Color(0xFFCC0000), Color(0xFFFF6666))
            station.station.availableRentBikes <= 3 -> ColorProvider(Color(0xFFFF8800), Color(0xFFFFAA44))
            else -> ColorProvider(Color(0xFF008800), Color(0xFF66CC66))
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = (if (isFavorite) "⭐ " else "") + station.station.getDisplayName(locale),
                modifier = GlanceModifier.defaultWeight(),
                style = if (compact) compactCellStyle() else cellStyle(),
                maxLines = 1
            )
            Text(
                text = if (station.distanceMeters >= 0) station.formattedDistance else "-",
                modifier = GlanceModifier.width(if (compact) 48.dp else 56.dp),
                style = if (compact) compactCellStyle() else cellStyle()
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
    private fun FooterRow(context: Context, timestamp: String, error: String?) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionRunCallback<RefreshAction>()),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "🔄 ",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF999999), Color(0xFF666666)),
                    fontSize = 10.sp
                )
            )
            if (error != null) {
                Text(
                    text = "⚠ ",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFCC0000), Color(0xFFFF6666)),
                        fontSize = 10.sp
                    )
                )
            }
            Text(
                text = context.getString(R.string.update_time, timestamp),
                style = TextStyle(
                    color = if (error != null) {
                        ColorProvider(Color(0xFFCC0000), Color(0xFFFF6666))
                    } else {
                        ColorProvider(Color(0xFF999999), Color(0xFF666666))
                    },
                    fontSize = 10.sp
                )
            )
        }
    }

    private fun headerStyle() = TextStyle(
        color = ColorProvider(Color(0xFF333333), Color(0xFFCCCCCC)),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )

    private fun cellStyle() = TextStyle(
        color = ColorProvider(Color(0xFF444444), Color(0xFFBBBBBB)),
        fontSize = 12.sp
    )

    private fun compactCellStyle() = TextStyle(
        color = ColorProvider(Color(0xFF444444), Color(0xFFBBBBBB)),
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
