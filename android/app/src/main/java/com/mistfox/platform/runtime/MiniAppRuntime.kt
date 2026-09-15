package com.mistfox.platform.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mistfox.platform.api.*
import com.mistfox.platform.pkg.Manifest
import com.mistfox.platform.security.MiniAppContext
import com.mistfox.platform.security.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.buildJsonObject
import java.io.File

class MiniAppRuntime(
    val context: Context,
    val webView: WebView,
    val manifest: Manifest,
    val packageDir: File,
    val dataDir: File,
    val coroutineScope: CoroutineScope
) {
    val permissionManager = PermissionManager(context)
    val apiRegistry = APIRegistry(permissionManager)

    private var appContext = MiniAppContext.fromManifest(manifest, packageDir, dataDir, isTrustedOrigin = true)

    private val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain("app.mistfox.local")
        .addPathHandler("/", WebViewAssetLoader.PathHandler { path ->
            val file = File(packageDir, path)
            val packagePathWithSep = if (packageDir.canonicalPath.endsWith(File.separator)) packageDir.canonicalPath else packageDir.canonicalPath + File.separator
            if (file.exists() && (file.canonicalPath.startsWith(packagePathWithSep) || file.canonicalPath == packageDir.canonicalPath)) {
                try {
                    val mimeType = when {
                        path.endsWith(".html") -> "text/html"
                        path.endsWith(".js") -> "text/javascript"
                        path.endsWith(".css") -> "text/css"
                        path.endsWith(".png") -> "image/png"
                        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                        path.endsWith(".json") -> "application/json"
                        path.endsWith(".wasm") -> "application/wasm"
                        else -> "application/octet-stream"
                    }
                    WebResourceResponse(mimeType, "UTF-8", file.inputStream())
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        })
        .build()

    init {
        configureWebViewSecurity()
        registerAllAPIs()
        setupNativeBridge()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViewSecurity() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false

        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.host == "app.mistfox.local") {
                    return assetLoader.shouldInterceptRequest(url)
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("https://app.mistfox.local/")) {
                    return false
                }
                appContext = MiniAppContext.untrusted(url)
                return false
            }
        }
    }

    private fun setupNativeBridge() {
        val bridge = MistFoxNativeBridge(
            webView = webView,
            apiRegistry = apiRegistry,
            getAppContext = { appContext },
            coroutineScope = coroutineScope
        )

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                "MistFoxBridgePort",
                setOf("https://app.mistfox.local"),
                bridge
            )
        }
        webView.addJavascriptInterface(bridge, "MistFoxBridgeInterface")
    }

    private fun registerAllAPIs() {
        apiRegistry.register(AppInfoAPI())
        apiRegistry.register(AppIdAPI())
        apiRegistry.register(AppVersionAPI())
        apiRegistry.register(AppGetLaunchInfoAPI(buildJsonObject {}))

        apiRegistry.register(UIToastAPI(context))
        apiRegistry.register(UIVibrateAPI(context))

        apiRegistry.register(StorageGetAPI(context))
        apiRegistry.register(StorageSetAPI(context))
        apiRegistry.register(StorageRemoveAPI(context))
        apiRegistry.register(StorageClearAPI(context))
        apiRegistry.register(StorageHasAPI(context))
        apiRegistry.register(StorageKeysAPI(context))

        apiRegistry.register(FilesReadAPI())
        apiRegistry.register(FilesWriteAPI())
        apiRegistry.register(FilesDeleteAPI())
        apiRegistry.register(FilesExistsAPI())
        apiRegistry.register(FilesInfoAPI())

        apiRegistry.register(CameraIsAvailableAPI(context))
        apiRegistry.register(CameraTakePhotoAPI())
        apiRegistry.register(CameraPickPhotoAPI())
        apiRegistry.register(CameraGetCamerasAPI(context))

        apiRegistry.register(MicrophoneIsAvailableAPI(context))
        apiRegistry.register(MicrophoneRequestPermissionAPI(context))

        apiRegistry.register(SensorsAvailableAPI(context))
        apiRegistry.register(LocationGetCurrentAPI())
        apiRegistry.register(BiometricIsAvailableAPI(context))
        apiRegistry.register(BiometricAuthenticateAPI())
        apiRegistry.register(HapticsVibrateAPI(context))
        apiRegistry.register(OrientationLockAPI())

        apiRegistry.register(DeviceInfoAPI(context))
        apiRegistry.register(NetworkFetchAPI())
        apiRegistry.register(ClipboardReadAPI(context))
        apiRegistry.register(ClipboardWriteAPI(context))
        apiRegistry.register(ShareTextAPI(context))
        apiRegistry.register(BrowserOpenAPI(context))
        apiRegistry.register(PhoneOpenDialerAPI(context))
        apiRegistry.register(SmsOpenComposerAPI(context))
        apiRegistry.register(NotificationsSendAPI(context))
        apiRegistry.register(BluetoothIsAvailableAPI(context))
        apiRegistry.register(NfcIsAvailableAPI(context))
    }

    fun launch() {
        webView.loadUrl("https://app.mistfox.local/${manifest.entry}")
    }

    fun destroy() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, "MistFoxBridgePort")
        }
        webView.removeJavascriptInterface("MistFoxBridgeInterface")
        webView.destroy()
    }
}
