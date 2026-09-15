package com.mistfox.platform.pkg

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.regex.Pattern

@Serializable
data class Manifest(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int = 1,
    val description: String = "",
    val entry: String = "index.html",
    val icon: String = "icon.png",
    val permissions: List<String> = emptyList(),
    val minMistFoxVersion: String = "1.0.0",
    val category: String? = null
) {
    companion object {
        private val PACKAGE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun parseAndValidate(jsonString: String): Manifest {
            if (jsonString.length > 512 * 1024) {
                throw PackageException.InvalidManifest("manifest.json size exceeds maximum limit of 512KB.")
            }

            val manifest = try {
                jsonParser.decodeFromString<Manifest>(jsonString)
            } catch (e: Exception) {
                throw PackageException.InvalidManifest("Failed to parse manifest.json: ${e.message}")
            }

            manifest.validate()
            return manifest
        }
    }

    fun validate() {
        if (id.isBlank()) {
            throw PackageException.InvalidManifest("Package ID cannot be empty.")
        }
        if (id.length > 128) {
            throw PackageException.InvalidManifest("Package ID exceeds maximum length of 128 characters.")
        }
        if (!PACKAGE_ID_PATTERN.matcher(id).matches()) {
            throw PackageException.InvalidManifest("Invalid Package ID format: '$id'. Must be reverse domain format (e.g., com.example.app).")
        }
        if (name.isBlank()) {
            throw PackageException.InvalidManifest("Package name cannot be empty.")
        }
        if (name.length > 64) {
            throw PackageException.InvalidManifest("Package name exceeds maximum length of 64 characters.")
        }
        if (version.isBlank()) {
            throw PackageException.InvalidManifest("Package version cannot be empty.")
        }
        if (entry.isBlank()) {
            throw PackageException.InvalidManifest("Entry file cannot be empty.")
        }
        if (entry.contains("..") || entry.startsWith("/") || entry.contains("\\")) {
            throw PackageException.InvalidManifest("Invalid entry point path: '$entry'. Path traversal is prohibited.")
        }
        if (icon.contains("..") || icon.startsWith("/") || icon.contains("\\")) {
            throw PackageException.InvalidManifest("Invalid icon path: '$icon'. Path traversal is prohibited.")
        }
    }
}

sealed class PackageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidManifest(msg: String) : PackageException(msg)
    class SecurityViolation(msg: String) : PackageException(msg)
    class ResourceLimitExceeded(msg: String) : PackageException(msg)
    class SignatureInvalid(msg: String) : PackageException(msg)
    class InstallationFailed(msg: String, cause: Throwable? = null) : PackageException(msg, cause)
}
