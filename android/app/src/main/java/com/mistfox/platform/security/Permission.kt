package com.mistfox.platform.security

import android.Manifest as AndroidManifest

enum class ProtectionLevel {
    SAFE,
    SENSITIVE,
    DANGEROUS,
    RESTRICTED
}

enum class MistFoxPermission(
    val key: String,
    val protectionLevel: ProtectionLevel,
    val description: String,
    val androidPermissions: List<String> = emptyList()
) {
    STORAGE("storage", ProtectionLevel.SAFE, "Isolated app storage"),
    FILES_READ("files.read", ProtectionLevel.SENSITIVE, "Read selected local files"),
    FILES_WRITE("files.write", ProtectionLevel.SENSITIVE, "Save files to device"),
    CAMERA("camera", ProtectionLevel.DANGEROUS, "Access device camera", listOf(AndroidManifest.permission.CAMERA)),
    MICROPHONE("microphone", ProtectionLevel.DANGEROUS, "Record audio with microphone", listOf(AndroidManifest.permission.RECORD_AUDIO)),
    LOCATION("location", ProtectionLevel.SENSITIVE, "Access approximate device location", listOf(AndroidManifest.permission.ACCESS_COARSE_LOCATION)),
    LOCATION_PRECISE("location.precise", ProtectionLevel.DANGEROUS, "Access precise GPS location", listOf(AndroidManifest.permission.ACCESS_FINE_LOCATION, AndroidManifest.permission.ACCESS_COARSE_LOCATION)),
    NOTIFICATIONS("notifications", ProtectionLevel.SENSITIVE, "Display system notifications", listOf("android.permission.POST_NOTIFICATIONS")),
    CLIPBOARD_READ("clipboard.read", ProtectionLevel.SENSITIVE, "Read text from clipboard"),
    CLIPBOARD_WRITE("clipboard.write", ProtectionLevel.SAFE, "Copy text to clipboard"),
    CONTACTS_READ("contacts.read", ProtectionLevel.DANGEROUS, "Read contact list", listOf(AndroidManifest.permission.READ_CONTACTS)),
    CONTACTS_WRITE("contacts.write", ProtectionLevel.DANGEROUS, "Modify contacts", listOf(AndroidManifest.permission.WRITE_CONTACTS)),
    CALENDAR_READ("calendar.read", ProtectionLevel.SENSITIVE, "Read calendar events", listOf(AndroidManifest.permission.READ_CALENDAR)),
    CALENDAR_WRITE("calendar.write", ProtectionLevel.SENSITIVE, "Modify calendar events", listOf(AndroidManifest.permission.WRITE_CALENDAR)),
    PHONE("phone", ProtectionLevel.SENSITIVE, "Open phone dialer"),
    SMS("sms", ProtectionLevel.SENSITIVE, "Open SMS composer"),
    BLUETOOTH("bluetooth", ProtectionLevel.SENSITIVE, "Access Bluetooth features", listOf(AndroidManifest.permission.BLUETOOTH, AndroidManifest.permission.BLUETOOTH_ADMIN)),
    BLUETOOTH_SCAN("bluetooth.scan", ProtectionLevel.DANGEROUS, "Scan for Bluetooth devices", listOf(AndroidManifest.permission.BLUETOOTH_SCAN, AndroidManifest.permission.ACCESS_FINE_LOCATION)),
    BLUETOOTH_CONNECT("bluetooth.connect", ProtectionLevel.DANGEROUS, "Connect to Bluetooth devices", listOf(AndroidManifest.permission.BLUETOOTH_CONNECT)),
    NFC("nfc", ProtectionLevel.SENSITIVE, "Read and write NFC tags", listOf(AndroidManifest.permission.NFC)),
    SENSORS("sensors", ProtectionLevel.SAFE, "Access device motion and light sensors"),
    VIBRATE("vibrate", ProtectionLevel.SAFE, "Control vibration motor", listOf(AndroidManifest.permission.VIBRATE)),
    AUDIO("audio", ProtectionLevel.SAFE, "Play audio streams"),
    MEDIA("media", ProtectionLevel.SENSITIVE, "Select media files"),
    SHARE("share", ProtectionLevel.SAFE, "Share content via Android sharesheet"),
    BIOMETRIC("biometric", ProtectionLevel.DANGEROUS, "Authenticate using fingerprint or face unlock", listOf(AndroidManifest.permission.USE_BIOMETRIC)),
    DEVICE_INFO("device.info", ProtectionLevel.SAFE, "Read hardware and OS information"),
    NETWORK("network", ProtectionLevel.SAFE, "Fetch data from external network APIs"),
    BACKGROUND("background", ProtectionLevel.RESTRICTED, "Perform background tasks via WorkManager"),
    ALARMS("alarms", ProtectionLevel.SENSITIVE, "Schedule user alarms", listOf(AndroidManifest.permission.SCHEDULE_EXACT_ALARM)),
    FOREGROUND_SERVICE("foreground_service", ProtectionLevel.RESTRICTED, "Run foreground tasks"),
    SCREEN_WAKE("screen_wake", ProtectionLevel.SAFE, "Keep screen awake during use", listOf(AndroidManifest.permission.WAKE_LOCK)),
    ORIENTATION("orientation", ProtectionLevel.SAFE, "Control screen orientation"),
    NEARBY_DEVICES("nearby_devices", ProtectionLevel.DANGEROUS, "Discover nearby devices");

    companion object {
        fun fromKey(key: String): MistFoxPermission? {
            return entries.find { it.key == key }
        }
    }
}
