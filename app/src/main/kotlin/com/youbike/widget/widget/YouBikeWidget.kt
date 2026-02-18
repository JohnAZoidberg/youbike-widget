package com.youbike.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.color.ColorProvider
import com.youbike.widget.MainActivity
import com.youbike.widget.R
import com.youbike.widget.data.StationWithDistance
import java.util.Locale

data class WidgetData(
    val nearestStations: List<StationWithDistance>,
    val favoriteStations: List<StationWithDistance>,
    val lastUpdated: String,
    val hasLocation: Boolean,
    val error: String? = null,
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

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color.White, Color(0xFF1E1E1E)))
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                val hasStationData = data != null &&
                    (data.nearestStations.isNotEmpty() || data.favoriteStations.isNotEmpty())

                if (data == null) {
                    LoadingContent(context)
                } else if (!hasStationData && data.error != null) {
                    // Only show full error if we have no cached data
                    ErrorContent(data.error)
                } else if (hasStationData) {
                    // Show station data (even if there's an error, use cached data)
                    HeaderRow(context)
                    Spacer(GlanceModifier.height(4.dp))

                    LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                        if (data.hasLocation && data.nearestStations.isNotEmpty()) {
                            items(data.nearestStations) { station ->
                                StationRow(station, isNearest = true, locale = locale)
                            }
                            item {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(ColorProvider(Color(0xFFE0E0E0), Color(0xFF404040)))
                                ) {}
                            }
                        }

                        items(data.favoriteStations) { station ->
                            StationRow(station, isNearest = false, locale = locale)
                        }
                    }

                    Spacer(GlanceModifier.height(4.dp))
                    FooterRow(context, data.lastUpdated, data.error)
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
    private fun StationRow(station: StationWithDistance, isNearest: Boolean, locale: Locale) {
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
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = (if (isNearest) "📍 " else "⭐ ") + station.station.getDisplayName(locale),
                modifier = GlanceModifier.defaultWeight(),
                style = cellStyle(),
                maxLines = 1
            )
            Text(
                text = if (station.distanceMeters >= 0) station.formattedDistance else "-",
                modifier = GlanceModifier.width(56.dp),
                style = cellStyle()
            )
            Text(
                text = station.station.availableReturnBikes.toString(),
                modifier = GlanceModifier.width(40.dp),
                style = TextStyle(
                    color = spotsColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = station.station.availableRentBikes.toString(),
                modifier = GlanceModifier.width(40.dp),
                style = TextStyle(
                    color = bikesColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    @Composable
    private fun FooterRow(context: Context, timestamp: String, error: String?) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
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
}

class YouBikeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YouBikeWidget()
}
