package com.mistfox.platform.api

import android.content.Context
import com.mistfox.platform.runtime.MistFoxBackgroundManager
import com.mistfox.platform.security.MiniAppContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class BackgroundScheduleAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "background.schedule"
    override val requiredPermission: String = "background"
    override val isBackgroundSafe: Boolean = false

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val taskId = args["taskId"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'taskId' parameter")
        val delay = args["delay"]?.jsonPrimitive?.content?.toLongOrNull() ?: 60000L

        val manager = MistFoxBackgroundManager(androidContext)
        val enqueued = manager.scheduleBackgroundTask(context.packageId, taskId, delay)

        return buildJsonObject {
            put("taskId", taskId)
            put("scheduled", enqueued)
            put("delay", delay)
        }
    }
}

class BackgroundCancelAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "background.cancel"
    override val requiredPermission: String = "background"
    override val isBackgroundSafe: Boolean = false

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val taskId = args["taskId"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'taskId' parameter")

        val manager = MistFoxBackgroundManager(androidContext)
        val cancelled = manager.cancelBackgroundTask(context.packageId, taskId)

        return buildJsonObject {
            put("taskId", taskId)
            put("cancelled", cancelled)
        }
    }
}
