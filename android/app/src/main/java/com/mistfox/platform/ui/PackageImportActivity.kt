package com.mistfox.platform.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mistfox.platform.pkg.Manifest
import com.mistfox.platform.pkg.PackageInstaller
import com.mistfox.platform.security.PermissionManager

class PackageImportActivity : ComponentActivity() {

    private lateinit var packageInstaller: PackageInstaller
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageInstaller = PackageInstaller(this)
        permissionManager = PermissionManager(this)

        setContent {
            MaterialTheme {
                var pendingPackage by remember { mutableStateOf<PackageInstaller.InstalledPackage?>(null) }

                if (pendingPackage != null) {
                    val pkg = pendingPackage!!
                    AlertDialog(
                        onDismissRequest = { pendingPackage = null },
                        title = { Text("Grant Permissions for ${pkg.manifest.name}") },
                        text = {
                            Column {
                                Text("This mini-app requests the following capabilities:")
                                Spacer(modifier = Modifier.height(8.dp))
                                if (pkg.manifest.permissions.isEmpty()) {
                                    Text("- No permissions requested.")
                                } else {
                                    pkg.manifest.permissions.forEach { perm ->
                                        Text("- $perm")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                permissionManager.grantAllDeclaredPermissions(pkg.manifest.id, pkg.manifest.permissions)
                                Toast.makeText(this@PackageImportActivity, "Installed and granted permissions for ${pkg.manifest.name}", Toast.LENGTH_SHORT).show()
                                finish()
                            }) {
                                Text("Approve & Finish")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = {
                                Toast.makeText(this@PackageImportActivity, "Installed ${pkg.manifest.name} (Permissions pending)", Toast.LENGTH_SHORT).show()
                                finish()
                            }) {
                                Text("Skip Grants")
                            }
                        }
                    )
                }

                PackageImportScreen(
                    onBack = { finish() },
                    onImportUri = { uri ->
                        try {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val installed = packageInstaller.installPackage(stream)
                                if (installed.manifest.permissions.isEmpty()) {
                                    Toast.makeText(this, "Successfully installed ${installed.manifest.name}", Toast.LENGTH_SHORT).show()
                                    finish()
                                } else {
                                    pendingPackage = installed
                                }
                            } ?: run {
                                Toast.makeText(this, "Failed to open package file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Import Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageImportScreen(
    onBack: () -> Unit,
    onImportUri: (Uri) -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import .pkg Package") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Select a MistFox Mini App Package (.pkg)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Packages contain HTML, CSS, JavaScript, WebAssembly, and static assets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select .pkg File from Device")
            }
        }
    }
}
