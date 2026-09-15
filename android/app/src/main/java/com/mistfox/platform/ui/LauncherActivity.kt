package com.mistfox.platform.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mistfox.platform.pkg.Manifest
import com.mistfox.platform.pkg.PackageInstaller

class LauncherActivity : ComponentActivity() {

    private lateinit var packageInstaller: PackageInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageInstaller = PackageInstaller(this)

        setContent {
            MaterialTheme {
                LauncherScreen(
                    onLaunchApp = { manifest ->
                        val intent = Intent(this, MiniAppContainerActivity::class.java).apply {
                            putExtra(MiniAppContainerActivity.EXTRA_APP_ID, manifest.id)
                        }
                        startActivity(intent)
                    },
                    onImportApp = {
                        startActivity(Intent(this, PackageImportActivity::class.java))
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    getInstalledApps = { packageInstaller.getInstalledPackages() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    onLaunchApp: (Manifest) -> Unit,
    onImportApp: () -> Unit,
    onOpenSettings: () -> Unit,
    getInstalledApps: () -> List<Manifest>
) {
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(emptyList<Manifest>()) }

    LaunchedEffect(Unit) {
        apps = getInstalledApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MistFox Platform") },
                actions = {
                    IconButton(onClick = onImportApp) {
                        Icon(Icons.Default.Add, contentDescription = "Import .pkg")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Mini-Apps") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            val filteredApps = apps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
            }

            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No mini-apps installed. Tap '+' to import a .pkg file.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredApps) { app ->
                        MiniAppCard(app = app, onClick = { onLaunchApp(app) })
                    }
                }
            }
        }
    }
}

@Composable
fun MiniAppCard(app: Manifest, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = app.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "v${app.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
