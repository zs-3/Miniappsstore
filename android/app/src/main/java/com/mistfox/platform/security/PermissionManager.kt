package com.mistfox.platform.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    enum class Result {
        GRANTED,
        PERMISSION_NOT_DECLARED,
        USER_DENIED,
        ANDROID_PERMISSION_DENIED,
        UNTRUSTED_ORIGIN,
        API_UNAVAILABLE
    }

    private val PREFS_NAME = "mistfox_permissions_store"

    fun isNexaPermissionGrantedByUser(appId: String, permissionKey: String): Boolean {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_$appId", Context.MODE_PRIVATE)
        val perm = MistFoxPermission.fromKey(permissionKey) ?: return false
        if (perm.protectionLevel == ProtectionLevel.SAFE) {
            return true
        }
        return prefs.getBoolean(permissionKey, false)
    }

    fun setNexaPermissionUserGrant(appId: String, permissionKey: String, granted: Boolean) {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_$appId", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(permissionKey, granted).apply()
    }

    fun grantAllDeclaredPermissions(appId: String, declaredPermissions: List<String>) {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_$appId", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (permKey in declaredPermissions) {
            editor.putBoolean(permKey, true)
        }
        editor.apply()
    }

    fun checkPermission(
        appContext: MiniAppContext,
        requiredPermissionKey: String?,
        isBackground: Boolean = false
    ): Result {
        if (!appContext.isTrustedOrigin) {
            return Result.UNTRUSTED_ORIGIN
        }

        if (requiredPermissionKey.isNullOrBlank()) {
            return Result.GRANTED
        }

        val permission = MistFoxPermission.fromKey(requiredPermissionKey)
            ?: return Result.API_UNAVAILABLE

        val declaredList = if (isBackground) appContext.backgroundPermissions else appContext.declaredPermissions
        if (!declaredList.contains(requiredPermissionKey)) {
            return Result.PERMISSION_NOT_DECLARED
        }

        if (permission.protectionLevel != ProtectionLevel.SAFE) {
            if (!isNexaPermissionGrantedByUser(appContext.packageId, requiredPermissionKey)) {
                return Result.USER_DENIED
            }
        }

        for (androidPerm in permission.androidPermissions) {
            val checkStatus = ContextCompat.checkSelfPermission(context, androidPerm)
            if (checkStatus != PackageManager.PERMISSION_GRANTED) {
                return Result.ANDROID_PERMISSION_DENIED
            }
        }

        return Result.GRANTED
    }
}
