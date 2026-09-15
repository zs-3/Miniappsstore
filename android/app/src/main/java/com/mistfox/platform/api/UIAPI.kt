package com.mistfox.platform.api

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import com.mistfox.platform.security.MiniAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class UIToastAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "ui.toast"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val text = args["text"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required argument 'text'")
        withContext(Dispatchers.Main) {
            Toast.makeText(androidContext, text, Toast.LENGTH_SHORT).show()
        }
        return buildJsonObject { put("success", true) }
    }
}

class UIVibrateAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "ui.vibrate"
    override val requiredPermission: String = "vibrate"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val duration = args["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 200L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = androidContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = androidContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
        return buildJsonObject { put("vibrated", true) }
    }
}
