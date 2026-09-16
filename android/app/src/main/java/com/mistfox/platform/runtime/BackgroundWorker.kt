package com.mistfox.platform.runtime

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mistfox.platform.api.*
import com.mistfox.platform.pkg.PackageInstaller
import com.mistfox.platform.security.MiniAppContext
import com.mistfox.platform.security.PermissionManager
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

class BackgroundWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val appId = inputData.getString("appId") ?: return Result.failure()
        val eventType = inputData.getString("eventType") ?: "schedule"
        val taskId = inputData.getString("taskId") ?: "task_default"

        val packageInstaller = PackageInstaller(applicationContext)
        val manifest = packageInstaller.getInstalledManifest(appId) ?: return Result.failure()

        val bgConfig = manifest.background
        if (bgConfig == null || !bgConfig.enabled) {
            return Result.failure()
        }

        val packageDir = packageInstaller.getAppPackageDir(appId)
        val dataDir = packageInstaller.getAppDataDir(appId)

        val bgScriptFile = File(packageDir, bgConfig.entry)
        if (!bgScriptFile.exists()) {
            return Result.failure()
        }

        val permissionManager = PermissionManager(applicationContext)
        val apiRegistry = APIRegistry(permissionManager)

        // Register background-safe APIs
        apiRegistry.register(StorageGetAPI(applicationContext))
        apiRegistry.register(StorageSetAPI(applicationContext))
        apiRegistry.register(StorageRemoveAPI(applicationContext))
        apiRegistry.register(StorageClearAPI(applicationContext))
        apiRegistry.register(StorageHasAPI(applicationContext))
        apiRegistry.register(StorageKeysAPI(applicationContext))
        apiRegistry.register(NetworkFetchAPI())
        apiRegistry.register(NotificationsSendAPI(applicationContext))

        val appContext = MiniAppContext.fromManifest(manifest, packageDir, dataDir, isTrustedOrigin = true)

        return try {
            val rhinoRuntime = RhinoBackgroundRuntime(appContext, apiRegistry)
            val scriptContent = bgScriptFile.readText(Charsets.UTF_8)
            val payload = buildJsonObject { put("taskId", JsonPrimitive(taskId)) }

            rhinoRuntime.executeBackgroundEvent(scriptContent, eventType, payload)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
