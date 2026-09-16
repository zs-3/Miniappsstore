package com.mistfox.platform.runtime

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MistFoxBackgroundManager(private val context: Context) {

    fun scheduleBackgroundTask(
        appId: String,
        taskId: String,
        delayMs: Long,
        eventType: String = "schedule"
    ): Boolean {
        val workData = Data.Builder()
            .putString("appId", appId)
            .putString("taskId", taskId)
            .putString("eventType", eventType)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<BackgroundWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workData)
            .addTag("mistfox_${appId}_${taskId}")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return true
    }

    fun cancelBackgroundTask(appId: String, taskId: String): Boolean {
        WorkManager.getInstance(context).cancelAllWorkByTag("mistfox_${appId}_${taskId}")
        return true
    }
}
