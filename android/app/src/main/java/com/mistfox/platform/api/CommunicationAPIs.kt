package com.mistfox.platform.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mistfox.platform.security.MiniAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class DeviceInfoAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "device.info"
    override val requiredPermission: String = "device.info"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val displayMetrics = androidContext.resources.displayMetrics
        return buildJsonObject {
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("screenWidth", displayMetrics.widthPixels)
            put("screenHeight", displayMetrics.heightPixels)
            put("density", displayMetrics.density.toDouble())
            put("locale", java.util.Locale.getDefault().toString())
            put("timezone", java.util.TimeZone.getDefault().id)
        }
    }
}

class NetworkFetchAPI : MistFoxAPI {
    override val name: String = "network.fetch"
    override val requiredPermission: String = "network"

    companion object {
        const val MAX_RESPONSE_SIZE_BYTES = 10L * 1024 * 1024 // 10MB
        const val MAX_REQUEST_BODY_SIZE_BYTES = 2L * 1024 * 1024 // 2MB
    }

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val urlString = args["url"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'url' parameter")
        val method = args["method"]?.jsonPrimitive?.content?.uppercase() ?: "GET"
        val requestBody = args["body"]?.jsonPrimitive?.content

        if (method !in listOf("GET", "POST", "PUT", "DELETE", "HEAD")) {
            throw APIException.InvalidArgument("Unsupported HTTP method '$method'.")
        }

        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            throw APIException.InvalidArgument("Invalid URL string: '$urlString'.")
        }

        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw APIException.SecurityBlocked("Only HTTPS connections are permitted in MistFox Mini Apps.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val host = url.host
                if (host.equals("localhost", ignoreCase = true) || host.contains("127.0.0.1") || host == "::1") {
                    throw APIException.SecurityBlocked("Access to local loopback hosts ($host) is prohibited (SSRF prevention).")
                }

                val addresses = try {
                    InetAddress.getAllByName(host)
                } catch (e: Exception) {
                    throw APIException.InvalidArgument("Failed to resolve host '$host': ${e.message}")
                }

                for (address in addresses) {
                    if (address.isLoopbackAddress ||
                        address.isSiteLocalAddress ||
                        address.isAnyLocalAddress ||
                        address.isLinkLocalAddress ||
                        address.isMulticastAddress) {
                        throw APIException.SecurityBlocked("Access to internal/private network destination ($host / ${address.hostAddress}) is prohibited (SSRF prevention).")
                    }

                    val rawBytes = address.address
                    if (rawBytes.size == 16) {
                        val isIpv4Mapped = (0..9).all { rawBytes[it] == 0.toByte() } &&
                                (rawBytes[10] == (-1).toByte() || rawBytes[10] == 255.toByte()) &&
                                (rawBytes[11] == (-1).toByte() || rawBytes[11] == 255.toByte())
                        if (isIpv4Mapped) {
                            val b0 = rawBytes[12].toInt() and 0xFF
                            val b1 = rawBytes[13].toInt() and 0xFF
                            if (b0 == 127 || b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168) || b0 == 0) {
                                throw APIException.SecurityBlocked("Access to internal/private IPv4-mapped IPv6 destination ($host) is prohibited (SSRF prevention).")
                            }
                        }
                    }
                }

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = false // Prevent redirect-based SSRF bypass

                if (requestBody != null && (method == "POST" || method == "PUT")) {
                    val bodyBytes = requestBody.toByteArray(Charsets.UTF_8)
                    if (bodyBytes.size > MAX_REQUEST_BODY_SIZE_BYTES) {
                        throw APIException.InvalidArgument("Request body exceeds maximum allowed limit of $MAX_REQUEST_BODY_SIZE_BYTES bytes.")
                    }
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { out ->
                        out.write(bodyBytes)
                    }
                }

                val statusCode = conn.responseCode
                val stream: InputStream? = if (statusCode in 200..299) conn.inputStream else conn.errorStream

                val responseText = if (stream != null) {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    val out = StringBuilder()
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        totalRead += bytesRead
                        if (totalRead > MAX_RESPONSE_SIZE_BYTES) {
                            throw APIException.SecurityBlocked("Network response size exceeded maximum allowed limit of $MAX_RESPONSE_SIZE_BYTES bytes.")
                        }
                        out.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                    }
                    out.toString()
                } else ""

                buildJsonObject {
                    put("status", statusCode)
                    put("data", responseText)
                    put("ok", statusCode in 200..299)
                }
            } catch (e: APIException) {
                throw e
            } catch (e: Exception) {
                throw APIException.InternalError("Network request failed: ${e.message}")
            }
        }
    }
}

class ClipboardReadAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "clipboard.read"
    override val requiredPermission: String = "clipboard.read"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val clipboard = androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        val text = if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).text?.toString() ?: ""
        } else ""

        return buildJsonObject {
            put("text", text)
        }
    }
}

class ClipboardWriteAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "clipboard.write"
    override val requiredPermission: String = "clipboard.write"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val text = args["text"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'text' parameter")

        val clipboard = androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MistFox Clipboard", text)
        clipboard.setPrimaryClip(clip)

        return buildJsonObject { put("copied", true) }
    }
}

class ShareTextAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "share.text"
    override val requiredPermission: String = "share"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val text = args["text"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'text' parameter")
        val title = args["title"]?.jsonPrimitive?.content ?: "Share"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        androidContext.startActivity(shareIntent)

        return buildJsonObject { put("shared", true) }
    }
}

class BrowserOpenAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "browser.open"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val url = args["url"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'url' parameter")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        androidContext.startActivity(intent)

        return buildJsonObject { put("opened", true) }
    }
}

class PhoneOpenDialerAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "phone.openDialer"
    override val requiredPermission: String = "phone"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val number = args["number"]?.jsonPrimitive?.content ?: ""
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        androidContext.startActivity(intent)
        return buildJsonObject { put("opened", true) }
    }
}

class SmsOpenComposerAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "sms.openComposer"
    override val requiredPermission: String = "sms"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val number = args["number"]?.jsonPrimitive?.content ?: ""
        val body = args["body"]?.jsonPrimitive?.content ?: ""
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        androidContext.startActivity(intent)
        return buildJsonObject { put("opened", true) }
    }
}

class NotificationsSendAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "notifications.send"
    override val requiredPermission: String = "notifications"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val title = args["title"]?.jsonPrimitive?.content ?: context.name
        val body = args["body"]?.jsonPrimitive?.content ?: ""

        val notificationManager = androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mistfox_miniapps_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MistFox Mini Apps",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(androidContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notificationId, notification)

        return buildJsonObject {
            put("sent", true)
            put("notificationId", notificationId)
        }
    }
}

class BluetoothIsAvailableAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "bluetooth.isAvailable"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        @Suppress("DEPRECATION")
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        return buildJsonObject {
            put("available", adapter != null)
            put("enabled", adapter?.isEnabled == true)
        }
    }
}

class NfcIsAvailableAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "nfc.isAvailable"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val manager = androidContext.getSystemService(Context.NFC_SERVICE) as? android.nfc.NfcManager
        val adapter = manager?.defaultAdapter
        return buildJsonObject {
            put("available", adapter != null)
            put("enabled", adapter?.isEnabled == true)
        }
    }
}
