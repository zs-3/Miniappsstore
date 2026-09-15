package com.mistfox.platform.pkg

import android.content.Context
import android.util.Base64
import com.mistfox.platform.BuildConfig
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object PackageVerifier {

    private const val DEV_MODE_PREF_KEY = "mistfox_dev_mode_enabled"
    private const val PREFS_NAME = "mistfox_system_settings"

    var isDevModeEnabled: Boolean = BuildConfig.DEBUG

    var embeddedProductionPublicKeyPem: String? = null

    fun isDevelopmentMode(context: Context): Boolean {
        // In release builds (!BuildConfig.DEBUG), Development Mode defaults to false unless explicitly enabled for testing
        val defaultMode = BuildConfig.DEBUG
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(DEV_MODE_PREF_KEY, defaultMode)
    }

    fun setDevelopmentMode(context: Context, enabled: Boolean) {
        isDevModeEnabled = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(DEV_MODE_PREF_KEY, enabled).apply()
    }

    fun computeDeterministicPackageHash(packageDir: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val allFiles = mutableListOf<File>()

        fun collectFiles(dir: File) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    if (file.name != "signature") {
                        collectFiles(file)
                    }
                } else {
                    val relativePath = file.relativeTo(packageDir).path.replace('\\', '/')
                    if (relativePath != "signature/signature") {
                        allFiles.add(file)
                    }
                }
            }
        }

        collectFiles(packageDir)
        allFiles.sortBy { it.relativeTo(packageDir).path.replace('\\', '/') }

        for (file in allFiles) {
            val relativePath = file.relativeTo(packageDir).path.replace('\\', '/')
            digest.update(relativePath.toByteArray(Charsets.UTF_8))
            digest.update(file.readBytes())
        }

        return digest.digest()
    }

    fun verifyPackageSignature(
        packageDir: File,
        publicKeyPem: String? = null,
        context: Context? = null
    ): Boolean {
        val devMode = if (context != null) isDevelopmentMode(context) else isDevModeEnabled
        val signatureFile = File(packageDir, "signature/signature")

        if (!signatureFile.exists()) {
            if (devMode) {
                return true
            } else {
                throw PackageException.SignatureInvalid("Package is unsigned and Development Mode is disabled.")
            }
        }

        val keyToUse = publicKeyPem ?: embeddedProductionPublicKeyPem

        if (keyToUse == null) {
            if (devMode) {
                return true
            }
            throw PackageException.SignatureInvalid("No public key provided to verify package signature in production mode. Failing closed.")
        }

        try {
            val manifestFile = File(packageDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw PackageException.InvalidManifest("manifest.json missing from extracted package.")
            }

            val signatureBytes = signatureFile.readBytes()
            val canonicalDataHash = computeDeterministicPackageHash(packageDir)

            val publicKey = parsePublicKey(keyToUse)
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(canonicalDataHash)

            val isValid = signature.verify(signatureBytes)
            if (!isValid && !devMode) {
                throw PackageException.SignatureInvalid("Package signature verification failed. Executable or static asset tampering detected.")
            }
            return isValid
        } catch (e: Exception) {
            if (devMode) return true
            throw PackageException.SignatureInvalid("Signature verification error: ${e.message}")
        }
    }

    private fun parsePublicKey(pem: String): PublicKey {
        val cleanPem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(cleanPem, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }
}
