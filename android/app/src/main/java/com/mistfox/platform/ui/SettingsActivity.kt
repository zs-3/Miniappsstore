package com.mistfox.platform.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mistfox.platform.pkg.Manifest
import com.mistfox.platform.pkg.PackageInstaller
import com.mistfox.platform.pkg.PackageVerifier
import com.mistfox.platform.security.PermissionManager

class SettingsActivity : ComponentActivity() {

    private lateinit var packageInstaller: PackageInstaller
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageInstaller = PackageInstaller(this)
        permissionManager = PermissionManager(this)

        setContent {
            MaterialTheme {
                SettingsScreen(
                    onBack = { finish() },
                    isDevMode = PackageVerifier.isDevelopmentMode(this),
                    onToggleDevMode = { enabled ->
                        PackageVerifier.setDevelopmentMode(this, enabled)
                        Toast.makeText(this, "Development Mode: $enabled", Toast.LENGTH_SHORT).show()
                    },
                    getInstalledApps = { packageInstaller.getInstalledPackages() },
                    onUninstallApp = { appId ->
                        val success = packageInstaller.uninstallPackage(appId)
                        if (success) {
                            Toast.makeText(this, "Uninstalled $appId", Toast.LENGTH_SHORT).show()
                        }
                        success
                    },
                    onClearData = { appId ->
                        val success = packageInstaller.clearPackageData(appId)
                        if (success) {
                            Toast.makeText(this, "Cleared data for $appId", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    isDevMode: Boolean,
    onToggleDevMode: (Boolean) -> Unit,
    getInstalledApps: () -> List<Manifest>,
    onUninstallApp: (String) -> Boolean,
    onClearData: (String) -> Unit
) {
    var devModeState by remember { mutableStateOf(isDevMode) }
    var apps by remember { mutableStateOf(emptyList<Manifest>()) }

    LaunchedEffect(Unit) {
        apps = getInstalledApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Developer Options", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Development Mode", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Allow installation of unsigned .pkg development packages.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Switch(
                                checked = devModeState,
                                onCheckedChange = { checked ->
                                    devModeState = checked
                                    onToggleDevMode(checked)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text("Installed Mini-Apps (${apps.size})", style = MaterialTheme.typography.titleMedium)
            }

            items(apps) { app ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(app.name, style = MaterialTheme.typography.titleSmall)
                        Text("ID: ${app.id}", style = MaterialTheme.typography.bodySmall)
                        Text("Version: ${app.version}", style = MaterialTheme.typography.bodySmall)
                        if (app.permissions.isNotEmpty()) {
                            Text(
                                "Permissions: ${app.permissions.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onClearData(app.id) }) {
                                Text("Clear Data")
                            }
                            Button(
                                onClick = {
                                    if (onUninstallApp(app.id)) {
                                        apps = getInstalledApps()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Uninstall")
                            }
                        }
                    }
                }
            }
        }
    }
}
