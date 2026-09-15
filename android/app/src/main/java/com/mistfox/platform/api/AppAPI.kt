package com.mistfox.platform.api

import com.mistfox.platform.security.MiniAppContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AppInfoAPI : MistFoxAPI {
    override val name: String = "app.info"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        return buildJsonObject {
            put("id", context.packageId)
            put("name", context.name)
            put("version", context.version)
            put("runtimeVersion", context.runtimeVersion)
            put("packageDir", context.packageDir.absolutePath)
        }
    }
}

class AppIdAPI : MistFoxAPI {
    override val name: String = "app.id"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        return buildJsonObject {
            put("id", context.packageId)
        }
    }
}

class AppVersionAPI : MistFoxAPI {
    override val name: String = "app.version"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        return buildJsonObject {
            put("version", context.version)
            put("runtimeVersion", context.runtimeVersion)
        }
    }
}

class AppCloseAPI(private val onClose: () -> Unit) : MistFoxAPI {
    override val name: String = "app.close"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        onClose()
        return buildJsonObject { put("closed", true) }
    }
}

class AppGetLaunchInfoAPI(private val launchParams: JsonObject) : MistFoxAPI {
    override val name: String = "app.getLaunchInfo"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        return launchParams
    }
}
