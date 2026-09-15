package com.mistfox.platform.api

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.mistfox.platform.security.MiniAppContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CameraIsAvailableAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "camera.isAvailable"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val hasCamera = androidContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        return buildJsonObject {
            put("available", hasCamera)
        }
    }
}

class CameraTakePhotoAPI : MistFoxAPI {
    override val name: String = "camera.takePhoto"
    override val requiredPermission: String = "camera"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        throw APIException.ApiUnavailable("Camera interactive capture UI flow is deferred in V1.")
    }
}

class CameraPickPhotoAPI : MistFoxAPI {
    override val name: String = "camera.pickPhoto"
    override val requiredPermission: String = "media"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        throw APIException.ApiUnavailable("Photo picker activity intent flow is deferred in V1.")
    }
}

class CameraGetCamerasAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "camera.getCameras"
    override val requiredPermission: String = "camera"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val hasCamera = androidContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (!hasCamera) {
            throw APIException.ApiUnavailable("No camera hardware present on device.")
        }
        return buildJsonObject {
            put("cameras", "front,back")
        }
    }
}

class MicrophoneIsAvailableAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "microphone.isAvailable"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val hasMic = androidContext.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        return buildJsonObject {
            put("available", hasMic)
        }
    }
}

class MicrophoneRequestPermissionAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "microphone.requestPermission"
    override val requiredPermission: String = "microphone"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val status = ContextCompat.checkSelfPermission(androidContext, android.Manifest.permission.RECORD_AUDIO)
        val isGranted = (status == PackageManager.PERMISSION_GRANTED)
        if (!isGranted) {
            throw APIException.AndroidPermissionDenied("Android RECORD_AUDIO system permission has not been granted.")
        }
        return buildJsonObject {
            put("granted", true)
        }
    }
}

class SensorsAvailableAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "sensors.available"
    override val requiredPermission: String = "sensors"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val sensorManager = androidContext.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val hasAccelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) != null
        val hasGyroscope = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) != null
        val hasLight = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) != null

        return buildJsonObject {
            put("accelerometer", hasAccelerometer)
            put("gyroscope", hasGyroscope)
            put("light", hasLight)
        }
    }
}

class LocationGetCurrentAPI : MistFoxAPI {
    override val name: String = "location.getCurrent"
    override val requiredPermission: String = "location"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        throw APIException.ApiUnavailable("Real-time GPS location listener requires active device hardware connection.")
    }
}

class BiometricIsAvailableAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "biometric.isAvailable"
    override val requiredPermission: String? = null

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val biometricManager = androidx.biometric.BiometricManager.from(androidContext)
        val canAuthenticate = biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return buildJsonObject {
            put("available", canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS)
            put("status", canAuthenticate)
        }
    }
}

class BiometricAuthenticateAPI : MistFoxAPI {
    override val name: String = "biometric.authenticate"
    override val requiredPermission: String = "biometric"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        throw APIException.ApiUnavailable("Biometric prompt dialog UI presentation is deferred in V1.")
    }
}

class HapticsVibrateAPI(private val androidContext: Context) : MistFoxAPI {
    override val name: String = "haptics.vibrate"
    override val requiredPermission: String = "vibrate"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val vibrator = androidContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
            return buildJsonObject { put("vibrated", true) }
        } else {
            throw APIException.ApiUnavailable("Vibration hardware not available.")
        }
    }
}

class OrientationLockAPI : MistFoxAPI {
    override val name: String = "orientation.lock"
    override val requiredPermission: String = "orientation"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        throw APIException.ApiUnavailable("Dynamic orientation locking requiring host Activity instance is deferred in V1.")
    }
}
