package com.mistfox.platform.runtime

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.mistfox.platform.api.APIException
import com.mistfox.platform.api.APIRegistry
import com.mistfox.platform.security.MiniAppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class MistFoxNativeBridge(
    private val webView: WebView,
    private val apiRegistry: APIRegistry,
    private val getAppContext: () -> MiniAppContext,
    private val coroutineScope: CoroutineScope
) : WebViewCompat.WebMessageListener {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        val messageJson = message.data ?: return
        if (!isMainFrame || sourceOrigin.toString() != "https://app.mistfox.local") {
            // Reject messages from iframes or external origins
            return
        }

        coroutineScope.launch(Dispatchers.Default) {
            val responseJson = handleMessage(messageJson)
            withContext(Dispatchers.Main) {
                try {
                    replyProxy.postMessage(responseJson)
                } catch (_: Exception) {
                    val escapedJson = responseJson.replace("\\", "\\\\").replace("'", "\\'")
                    webView.evaluateJavascript("window.MistFoxNativeBridge._onResponse('$escapedJson')", null)
                }
            }
        }
    }

    suspend fun handleMessage(messageJson: String): String {
        var requestId = "unknown"
        return try {
            val request = json.parseToJsonElement(messageJson).jsonObject
            requestId = request["id"]?.jsonPrimitive?.content ?: "unknown"
            val apiName = request["api"]?.jsonPrimitive?.content
                ?: throw APIException.InvalidArgument("Missing required 'api' field.")
            val args = request["args"]?.jsonObject ?: buildJsonObject {}

            val currentContext = getAppContext()

            val result = apiRegistry.dispatch(currentContext, apiName, args)

            buildJsonObject {
                put("id", requestId)
                put("success", true)
                put("result", result)
            }.toString()
        } catch (e: APIException) {
            buildJsonObject {
                put("id", requestId)
                put("success", false)
                put("error", buildJsonObject {
                    put("code", e.code)
                    put("message", e.message ?: "An error occurred")
                })
            }.toString()
        } catch (e: Exception) {
            buildJsonObject {
                put("id", requestId)
                put("success", false)
                put("error", buildJsonObject {
                    put("code", "INTERNAL_ERROR")
                    put("message", e.message ?: "An unexpected error occurred")
                })
            }.toString()
        }
    }
}
