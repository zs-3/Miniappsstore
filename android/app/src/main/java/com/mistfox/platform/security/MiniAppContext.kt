package com.mistfox.platform.security

import com.mistfox.platform.pkg.Manifest
import java.io.File

data class MiniAppContext(
    val packageId: String,
    val name: String,
    val version: String,
    val packageDir: File,
    val dataDir: File,
    val declaredPermissions: Set<String>,
    val runtimeVersion: String = "1.0.0",
    val isTrustedOrigin: Boolean = true
) {
    fun hasDeclaredPermission(permissionKey: String): Boolean {
        if (!isTrustedOrigin) return false
        return declaredPermissions.contains(permissionKey)
    }

    companion object {
        fun fromManifest(
            manifest: Manifest,
            packageDir: File,
            dataDir: File,
            isTrustedOrigin: Boolean = true
        ): MiniAppContext {
            return MiniAppContext(
                packageId = manifest.id,
                name = manifest.name,
                version = manifest.version,
                packageDir = packageDir,
                dataDir = dataDir,
                declaredPermissions = manifest.permissions.toSet(),
                isTrustedOrigin = isTrustedOrigin
            )
        }

        fun untrusted(originUrl: String): MiniAppContext {
            return MiniAppContext(
                packageId = "untrusted.origin",
                name = "External Page",
                version = "0.0.0",
                packageDir = File("/dev/null"),
                dataDir = File("/dev/null"),
                declaredPermissions = emptySet(),
                isTrustedOrigin = false
            )
        }
    }
}
