package com.mistfox.platform.api

import android.content.Context
import com.mistfox.platform.security.MiniAppContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class StorageGetAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "storage.get"
    override val requiredPermission: String = "storage"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val key = args["key"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'key' argument")

        val prefs = androidContext.getSharedPreferences("mistfox_storage_${context.packageId}", Context.MODE_PRIVATE)
        val value = prefs.getString(key, null)

        return buildJsonObject {
            put("key", key)
            if (value != null) {
                put("value", value)
                put("exists", true)
            } else {
                put("exists", false)
            }
        }
    }
}

class StorageSetAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "storage.set"
    override val requiredPermission: String = "storage"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val key = args["key"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'key' argument")
        val value = args["value"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'value' argument")

        val prefs = androidContext.getSharedPreferences("mistfox_storage_${context.packageId}", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()

        return buildJsonObject {
            put("key", key)
            put("stored", true)
        }
    }
}

class StorageRemoveAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "storage.remove"
    override val requiredPermission: String = "storage"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val key = args["key"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'key' argument")

        val prefs = androidContext.getSharedPreferences("mistfox_storage_${context.packageId}", Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()

        return buildJsonObject { put("removed", true) }
    }
}

class StorageClearAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "storage.clear"
    override val requiredPermission: String = "storage"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val prefs = androidContext.getSharedPreferences("mistfox_storage_${context.packageId}", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        return buildJsonObject { put("cleared", true) }
    }
}

class StorageHasAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "storage.has"
    override val requiredPermission: String = "storage"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val key = args["key"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'key' argument")

        val prefs = androidContext.getSharedPreferences("mistfox_storage_${context.packageId}", Context.MODE_PRIVATE)
        return buildJsonObject {
            put("has", prefs.contains(key))
        }
    }
}

class StorageKeysAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "storage.keys"
    override val requiredPermission: String = "storage"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val prefs = androidContext.getSharedPreferences("mistfox_storage_${context.packageId}", Context.MODE_PRIVATE)
        val keysArray = buildJsonArray {
            prefs.all.keys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        }
        return buildJsonObject {
            put("keys", keysArray)
        }
    }
}
