package me.danielschaefer.android.youbike

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.danielschaefer.android.youbike.widget.WidgetData
import me.danielschaefer.android.youbike.widget.YouBikeWidgetDataStore
import me.danielschaefer.android.youbike.worker.WidgetUpdateWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var foregroundPermissionState = mutableStateOf(false)
    private var backgroundPermissionState = mutableStateOf(false)

    private val foregroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        foregroundPermissionState.value = granted
        backgroundPermissionState.value = hasBackgroundLocationPermission()
        // Always refresh after permission dialog - we might have location now
        WidgetUpdateWorker.runOnce(this)
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundPermissionState.value = granted
        WidgetUpdateWorker.runOnce(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        foregroundPermissionState.value = hasForegroundLocationPermission()
        backgroundPermissionState.value = hasBackgroundLocationPermission()

        setContent {
            MaterialTheme {
                MainScreen(
                    hasForegroundPermission = foregroundPermissionState.value,
                    hasBackgroundPermission = backgroundPermissionState.value,
                    onRequestForegroundPermission = { requestForegroundLocationPermission() },
                    onRequestBackgroundPermission = { requestBackgroundLocationPermission() },
                    onRefresh = { WidgetUpdateWorker.runOnce(this) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning to app (e.g., from settings)
        foregroundPermissionState.value = hasForegroundLocationPermission()
        backgroundPermissionState.value = hasBackgroundLocationPermission()
    }

    private fun hasForegroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Background location not needed on older Android versions
            true
        }
    }

    private fun requestForegroundLocationPermission() {
        foregroundLocationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
}

@Composable
fun MainScreen(
    hasForegroundPermission: Boolean,
    hasBackgroundPermission: Boolean,
    onRequestForegroundPermission: () -> Unit,
    onRequestBackgroundPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.add_widget_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!hasForegroundPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.location_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRequestForegroundPermission) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            } else if (!hasBackgroundPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.background_location_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRequestBackgroundPermission) {
                            Text(stringResource(R.string.grant_background_permission))
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.permission_granted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var widgetData by remember { mutableStateOf<WidgetData?>(null) }

            LaunchedEffect(Unit) {
                widgetData = YouBikeWidgetDataStore.getData(context)
            }

            OutlinedButton(onClick = {
                onRefresh()
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    widgetData = YouBikeWidgetDataStore.getData(context)
                }
            }) {
                Text(stringResource(R.string.refresh_now))
            }

            Spacer(modifier = Modifier.height(16.dp))

            widgetData?.let { data ->
                Text(
                    text = "Nearest: ${data.nearestStations.size}, Favorites: ${data.favoriteStations.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Fetched: ${data.lastUpdated}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "API data: ${data.apiUpdateTime ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Has location: ${data.hasLocation}",
                    style = MaterialTheme.typography.bodySmall
                )
            } ?: Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.favorite_stations),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Config.FAVORITE_STATION_IDS.forEach { id ->
                Text(
                    text = "• $id",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
