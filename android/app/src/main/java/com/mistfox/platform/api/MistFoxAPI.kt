package com.mistfox.platform.api

import com.mistfox.platform.security.MiniAppContext
import com.mistfox.platform.security.PermissionManager
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface MistFoxAPI {
    val name: String
    val requiredPermission: String?
    val isBackgroundSafe: Boolean get() = false

    suspend fun execute(
        context: MiniAppContext,
        args: JsonObject
    ): JsonElement
}

sealed class APIException(val code: String, message: String) : Exception(message) {
    class InvalidArgument(msg: String) : APIException("INVALID_ARGUMENT", msg)
    class PermissionDenied(msg: String) : APIException("PERMISSION_DENIED", msg)
    class PermissionNotDeclared(msg: String) : APIException("PERMISSION_NOT_DECLARED", msg)
    class AndroidPermissionDenied(msg: String) : APIException("ANDROID_PERMISSION_DENIED", msg)
    class ApiNotFound(msg: String) : APIException("API_NOT_FOUND", msg)
    class ApiUnavailable(msg: String) : APIException("API_UNAVAILABLE", msg)
    class ApiNotAllowedInBackground(msg: String) : APIException("API_NOT_ALLOWED_IN_BACKGROUND", msg)
    class SecurityBlocked(msg: String) : APIException("SECURITY_BLOCKED", msg)
    class InternalError(msg: String) : APIException("INTERNAL_ERROR", msg)
}

class APIRegistry(private val permissionManager: PermissionManager) {

    private val apis = mutableMapOf<String, MistFoxAPI>()

    fun register(api: MistFoxAPI) {
        apis[api.name] = api
    }

    fun hasApi(name: String): Boolean {
        return apis.containsKey(name)
    }

    suspend fun dispatch(
        appContext: MiniAppContext,
        apiName: String,
        args: JsonObject,
        isBackground: Boolean = false
    ): JsonElement {
        val api = apis[apiName]
            ?: throw APIException.ApiNotFound("API '$apiName' is not registered.")

        if (isBackground && !api.isBackgroundSafe) {
            throw APIException.ApiNotAllowedInBackground("API '$apiName' is prohibited during background execution.")
        }

        val permResult = permissionManager.checkPermission(appContext, api.requiredPermission, isBackground = isBackground)
        when (permResult) {
            PermissionManager.Result.GRANTED -> { /* Proceed */ }
            PermissionManager.Result.UNTRUSTED_ORIGIN ->
                throw APIException.SecurityBlocked("External untrusted content is not permitted to call MistFox APIs.")
            PermissionManager.Result.PERMISSION_NOT_DECLARED ->
                throw APIException.PermissionNotDeclared("Mini-app manifest missing required permission declaration: '${api.requiredPermission}'")
            PermissionManager.Result.USER_DENIED ->
                throw APIException.PermissionDenied("User denied permission: '${api.requiredPermission}'")
            PermissionManager.Result.ANDROID_PERMISSION_DENIED ->
                throw APIException.AndroidPermissionDenied("Android system permission for '${api.requiredPermission}' was not granted.")
            PermissionManager.Result.API_UNAVAILABLE ->
                throw APIException.ApiUnavailable("API '${api.name}' is unavailable on this device.")
        }

        return api.execute(appContext, args)
    }

    fun getRegisteredApiNames(): List<String> {
        return apis.keys.toList().sorted()
    }
}
