package com.youbike.widget

import android.Manifest
import android.content.pm.PackageManager
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
import com.youbike.widget.widget.WidgetData
import com.youbike.widget.widget.YouBikeWidgetDataStore
import com.youbike.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var permissionState = mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        permissionState.value = granted
        // Always refresh after permission dialog - we might have location now
        WidgetUpdateWorker.runOnce(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionState.value = hasLocationPermission()

        setContent {
            MaterialTheme {
                MainScreen(
                    hasLocationPermission = permissionState.value,
                    onRequestPermission = { requestLocationPermission() },
                    onRefresh = { WidgetUpdateWorker.runOnce(this) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission when returning to app (e.g., from settings)
        permissionState.value = hasLocationPermission()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

@Composable
fun MainScreen(hasLocationPermission: Boolean, onRequestPermission: () -> Unit, onRefresh: () -> Unit) {
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

            if (!hasLocationPermission) {
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
                        Button(onClick = onRequestPermission) {
                            Text(stringResource(R.string.grant_permission))
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
