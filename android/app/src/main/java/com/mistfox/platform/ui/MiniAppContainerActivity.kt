package com.mistfox.platform.ui

import android.os.Bundle
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.mistfox.platform.pkg.PackageInstaller
import com.mistfox.platform.runtime.MiniAppRuntime

class MiniAppContainerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APP_ID = "extra_app_id"
    }

    private var miniAppRuntime: MiniAppRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appId = intent.getStringExtra(EXTRA_APP_ID)
        if (appId.isNullOrEmpty()) {
            Toast.makeText(this, "No Mini-App ID specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val packageInstaller = PackageInstaller(this)
        val manifest = packageInstaller.getInstalledManifest(appId)
        if (manifest == null) {
            Toast.makeText(this, "App '$appId' is not installed", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val packageDir = packageInstaller.getAppPackageDir(appId)
        val dataDir = packageInstaller.getAppDataDir(appId)

        val webView = WebView(this)

        miniAppRuntime = MiniAppRuntime(
            context = this,
            webView = webView,
            manifest = manifest,
            packageDir = packageDir,
            dataDir = dataDir,
            coroutineScope = lifecycleScope
        )

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text(manifest.name) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { webView.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                }
                                IconButton(onClick = {
                                    Toast.makeText(
                                        this@MiniAppContainerActivity,
                                        "App: ${manifest.name}\nVersion: ${manifest.version}\nID: ${manifest.id}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }) {
                                    Icon(Icons.Default.Info, contentDescription = "App Info")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        AndroidView(
                            factory = {
                                FrameLayout(it).apply {
                                    addView(
                                        webView,
                                        FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        miniAppRuntime?.launch()
    }

    override fun onDestroy() {
        miniAppRuntime?.destroy()
        super.onDestroy()
    }
}
