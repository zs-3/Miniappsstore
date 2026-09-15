package com.mistfox.platform.pkg

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PackageInstaller(private val context: Context) {

    companion object {
        const val MAX_PACKAGE_SIZE_BYTES = 50L * 1024 * 1024 // 50MB
        const val MAX_EXTRACTED_SIZE_BYTES = 150L * 1024 * 1024 // 150MB
        const val MAX_FILE_COUNT = 2000
    }

    class SizeBoundedInputStream(private val delegate: InputStream, private val maxBytes: Long) : InputStream() {
        private var totalRead = 0L

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) {
                totalRead++
                checkLimit()
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = delegate.read(b, off, len)
            if (bytesRead > 0) {
                totalRead += bytesRead
                checkLimit()
            }
            return bytesRead
        }

        private fun checkLimit() {
            if (totalRead > maxBytes) {
                throw PackageException.ResourceLimitExceeded("Package compressed size exceeds maximum allowed limit of $maxBytes bytes.")
            }
        }

        override fun close() {
            delegate.close()
        }
    }

    data class InstalledPackage(
        val manifest: Manifest,
        val packageDir: File,
        val dataDir: File,
        val isDevUnsigned: Boolean
    )

    fun getAppsDir(): File {
        val appsDir = File(context.filesDir, "apps")
        if (!appsDir.exists()) {
            appsDir.mkdirs()
        }
        return appsDir
    }

    fun getAppPackageDir(appId: String): File {
        return File(getAppsDir(), "$appId/package")
    }

    fun getAppDataDir(appId: String): File {
        return File(getAppsDir(), "$appId/data")
    }

    fun installPackage(pkgInputStream: InputStream): InstalledPackage {
        val tempDir = File(context.cacheDir, "pkg_temp_${System.currentTimeMillis()}")
        var targetPackageDir: File? = null
        try {
            if (!tempDir.exists()) tempDir.mkdirs()

            val boundedStream = SizeBoundedInputStream(pkgInputStream, MAX_PACKAGE_SIZE_BYTES)
            extractAndValidateZip(boundedStream, tempDir)

            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw PackageException.InvalidManifest("manifest.json missing from package.")
            }

            val manifestContent = manifestFile.readText(Charsets.UTF_8)
            val manifest = Manifest.parseAndValidate(manifestContent)

            val entryFile = File(tempDir, manifest.entry)
            if (!entryFile.exists()) {
                throw PackageException.InvalidManifest("Specified entry file '${manifest.entry}' does not exist in package.")
            }

            PackageVerifier.verifyPackageSignature(tempDir, context = context)

            targetPackageDir = getAppPackageDir(manifest.id)
            val targetDataDir = getAppDataDir(manifest.id)

            if (targetPackageDir.exists()) {
                targetPackageDir.deleteRecursively()
            }
            targetPackageDir.mkdirs()

            if (!targetDataDir.exists()) {
                targetDataDir.mkdirs()
            }

            tempDir.copyRecursively(targetPackageDir, overwrite = true)

            return InstalledPackage(
                manifest = manifest,
                packageDir = targetPackageDir,
                dataDir = targetDataDir,
                isDevUnsigned = !File(targetPackageDir, "signature/signature").exists()
            )
        } catch (e: Exception) {
            targetPackageDir?.deleteRecursively()
            throw e
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun installPackageFile(pkgFile: File): InstalledPackage {
        if (pkgFile.length() > MAX_PACKAGE_SIZE_BYTES) {
            throw PackageException.ResourceLimitExceeded("Package file size (${pkgFile.length()} bytes) exceeds maximum limit of $MAX_PACKAGE_SIZE_BYTES bytes.")
        }
        return FileInputStream(pkgFile).use { installPackage(it) }
    }

    private fun extractAndValidateZip(inputStream: InputStream, targetDir: File) {
        val canonicalTargetDirPath = targetDir.canonicalPath
        var totalBytesExtracted = 0L
        var totalFileCount = 0

        ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                totalFileCount++
                if (totalFileCount > MAX_FILE_COUNT) {
                    throw PackageException.ResourceLimitExceeded("Package file count exceeds maximum allowed limit of $MAX_FILE_COUNT.")
                }

                val entryName = entry.name
                if (entryName.contains("..")) {
                    throw PackageException.SecurityViolation("Path traversal attempt detected in ZIP entry: '$entryName'")
                }

                val destFile = File(targetDir, entryName)
                val canonicalDestPath = destFile.canonicalPath

                val targetPathWithSep = if (canonicalTargetDirPath.endsWith(File.separator)) canonicalTargetDirPath else canonicalTargetDirPath + File.separator
                if (!canonicalDestPath.startsWith(targetPathWithSep) && canonicalDestPath != canonicalTargetDirPath) {
                    throw PackageException.SecurityViolation("Zip slip vulnerability detected for entry: '$entryName'")
                }

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { out ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            totalBytesExtracted += bytesRead
                            if (totalBytesExtracted > MAX_EXTRACTED_SIZE_BYTES) {
                                throw PackageException.ResourceLimitExceeded("Extracted size exceeds limit of $MAX_EXTRACTED_SIZE_BYTES bytes (Zip bomb protection).")
                            }
                            out.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    fun uninstallPackage(appId: String): Boolean {
        val appBaseDir = File(getAppsDir(), appId)
        return if (appBaseDir.exists()) {
            appBaseDir.deleteRecursively()
        } else false
    }

    fun clearPackageData(appId: String): Boolean {
        val dataDir = getAppDataDir(appId)
        return if (dataDir.exists()) {
            dataDir.deleteRecursively()
            dataDir.mkdirs()
            true
        } else false
    }

    fun getInstalledPackages(): List<Manifest> {
        val appsDir = getAppsDir()
        val installedList = mutableListOf<Manifest>()
        val appDirs = appsDir.listFiles() ?: return emptyList()

        for (dir in appDirs) {
            if (dir.isDirectory) {
                val manifestFile = File(dir, "package/manifest.json")
                if (manifestFile.exists()) {
                    try {
                        val manifest = Manifest.parseAndValidate(manifestFile.readText(Charsets.UTF_8))
                        installedList.add(manifest)
                    } catch (_: Exception) {}
                }
            }
        }
        return installedList
    }

    fun getInstalledManifest(appId: String): Manifest? {
        val manifestFile = File(getAppPackageDir(appId), "manifest.json")
        if (!manifestFile.exists()) return null
        return try {
            Manifest.parseAndValidate(manifestFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
