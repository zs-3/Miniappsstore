package com.mistfox.platform

import com.mistfox.platform.api.APIException
import com.mistfox.platform.api.APIRegistry
import com.mistfox.platform.api.AppInfoAPI
import com.mistfox.platform.api.NetworkFetchAPI
import com.mistfox.platform.pkg.Manifest
import com.mistfox.platform.pkg.PackageException
import com.mistfox.platform.pkg.PackageInstaller
import com.mistfox.platform.pkg.PackageVerifier
import com.mistfox.platform.security.MiniAppContext
import com.mistfox.platform.security.PermissionManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class ComprehensiveSecurityTestSuite {

    // --- MANIFEST & PACKAGE SECURITY ---

    @Test
    fun testValidManifestParsing() {
        val validJson = """
            {
                "id": "com.mistfox.testapp",
                "name": "Test App",
                "version": "1.0.0",
                "versionCode": 1,
                "entry": "index.html",
                "permissions": ["storage", "camera"]
            }
        """.trimIndent()

        val manifest = Manifest.parseAndValidate(validJson)
        assertEquals("com.mistfox.testapp", manifest.id)
        assertEquals("Test App", manifest.name)
        assertEquals("1.0.0", manifest.version)
        assertEquals(2, manifest.permissions.size)
    }

    @Test(expected = PackageException.InvalidManifest::class)
    fun testInvalidPackageIdFormat() {
        val invalidJson = """
            {
                "id": "invalid_id_without_dots",
                "name": "Test App",
                "version": "1.0.0",
                "entry": "index.html"
            }
        """.trimIndent()

        Manifest.parseAndValidate(invalidJson)
    }

    @Test(expected = PackageException.InvalidManifest::class)
    fun testPathTraversalInManifestEntry() {
        val maliciousJson = """
            {
                "id": "com.mistfox.testapp",
                "name": "Test App",
                "version": "1.0.0",
                "entry": "../../etc/passwd"
            }
        """.trimIndent()

        Manifest.parseAndValidate(maliciousJson)
    }

    @Test(expected = PackageException.InvalidManifest::class)
    fun testOversizedManifestRejected() {
        val hugeName = "A".repeat(600 * 1024)
        val json = """
            {
                "id": "com.mistfox.testapp",
                "name": "$hugeName",
                "version": "1.0.0",
                "entry": "index.html"
            }
        """.trimIndent()

        Manifest.parseAndValidate(json)
    }

    @Test(expected = PackageException.ResourceLimitExceeded::class)
    fun testInputStreamSizeBoundedEnforced() {
        val oversizedData = ByteArray(1024)
        val stream = PackageInstaller.SizeBoundedInputStream(ByteArrayInputStream(oversizedData), 512)
        val buffer = ByteArray(256)
        while (stream.read(buffer) != -1) {
            // Read until exception
        }
    }

    // --- DETERMINISTIC FULL-PACKAGE SIGNATURE & TAMPER DETECTION ---

    @Test
    fun testDeterministicPackageHashAssetTamper() {
        val tempDir = Files.createTempDirectory("pkg_test_").toFile()
        try {
            File(tempDir, "manifest.json").writeText("{\"id\":\"com.mistfox.app\",\"name\":\"App\",\"version\":\"1.0\"}")
            File(tempDir, "index.html").writeText("<h1>Hello</h1>")
            File(tempDir, "style.css").writeText("body { color: red; }")

            val hash1 = PackageVerifier.computeDeterministicPackageHash(tempDir)

            // Modify CSS asset file content
            File(tempDir, "style.css").writeText("body { color: blue; }")
            val hash2 = PackageVerifier.computeDeterministicPackageHash(tempDir)

            assertFalse("Tampering with CSS asset MUST alter package hash", hash1.contentEquals(hash2))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTamperDetectionFileAdditionAndRemoval() {
        val tempDir = Files.createTempDirectory("pkg_tamper_test_").toFile()
        try {
            File(tempDir, "manifest.json").writeText("{\"id\":\"com.mistfox.app\",\"name\":\"App\",\"version\":\"1.0\"}")
            File(tempDir, "app.js").writeText("console.log('init');")

            val hashBase = PackageVerifier.computeDeterministicPackageHash(tempDir)

            // Add new unauthorized file
            val newFile = File(tempDir, "malicious.js")
            newFile.writeText("eval('hack')")
            val hashAdded = PackageVerifier.computeDeterministicPackageHash(tempDir)
            assertFalse("Adding a new file MUST alter the package hash", hashBase.contentEquals(hashAdded))

            // Remove file
            newFile.delete()
            File(tempDir, "app.js").delete()
            val hashRemoved = PackageVerifier.computeDeterministicPackageHash(tempDir)
            assertFalse("Removing a file MUST alter the package hash", hashBase.contentEquals(hashRemoved))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test(expected = PackageException.SignatureInvalid::class)
    fun testProductionModeFailsClosedWithoutKey() {
        val tempDir = Files.createTempDirectory("prod_sig_test_").toFile()
        try {
            File(tempDir, "manifest.json").writeText("{\"id\":\"com.mistfox.app\",\"name\":\"App\",\"version\":\"1.0\"}")
            val sigDir = File(tempDir, "signature")
            sigDir.mkdirs()
            File(sigDir, "signature").writeText("dummy_signature")

            // In production mode (isDevModeEnabled = false), missing public key MUST fail closed
            PackageVerifier.isDevModeEnabled = false
            PackageVerifier.verifyPackageSignature(tempDir, publicKeyPem = null)
        } finally {
            PackageVerifier.isDevModeEnabled = true
            tempDir.deleteRecursively()
        }
    }

    // --- IDENTITY & ORIGIN SECURITY ---

    @Test
    fun testTrustedOriginPermissionsAllowed() {
        val manifest = Manifest(
            id = "com.mistfox.app",
            name = "App",
            version = "1.0",
            permissions = listOf("camera", "storage")
        )
        val context = MiniAppContext.fromManifest(manifest, File("/tmp/pkg"), File("/tmp/data"), isTrustedOrigin = true)

        assertTrue(context.isTrustedOrigin)
        assertTrue(context.hasDeclaredPermission("camera"))
        assertTrue(context.hasDeclaredPermission("storage"))
        assertFalse(context.hasDeclaredPermission("location"))
    }

    @Test
    fun testUntrustedOriginPermissionsBlocked() {
        val untrustedContext = MiniAppContext.untrusted("https://malicious-external-site.com")

        assertFalse(untrustedContext.isTrustedOrigin)
        assertFalse(untrustedContext.hasDeclaredPermission("camera"))
        assertFalse(untrustedContext.hasDeclaredPermission("storage"))
    }

    // --- API ROUTER & UNKNOWN API REJECTION ---

    @Test(expected = APIException.ApiNotFound::class)
    fun testUnknownApiInvocationFails() {
        runBlocking {
            val permissionManager = PermissionManager(DummyContext())
            val registry = APIRegistry(permissionManager)

            val manifest = Manifest(id = "com.mistfox.app", name = "App", version = "1.0")
            val context = MiniAppContext.fromManifest(manifest, File("/tmp/pkg"), File("/tmp/data"), isTrustedOrigin = true)

            registry.dispatch(context, "non.existent.api", buildJsonObject {})
        }
    }

    @Test(expected = APIException.SecurityBlocked::class)
    fun testUntrustedOriginApiDispatchBlocked() {
        runBlocking {
            val permissionManager = PermissionManager(DummyContext())
            val registry = APIRegistry(permissionManager)
            registry.register(AppInfoAPI())

            val untrustedContext = MiniAppContext.untrusted("https://example.com")
            registry.dispatch(untrustedContext, "app.info", buildJsonObject {})
        }
    }

    // --- NETWORK SECURITY & SSRF BLOCKING ---

    @Test(expected = APIException.SecurityBlocked::class)
    fun testNonHttpsNetworkFetchBlocked() {
        runBlocking {
            val fetchAPI = NetworkFetchAPI()
            val manifest = Manifest(id = "com.mistfox.app", name = "App", version = "1.0", permissions = listOf("network"))
            val context = MiniAppContext.fromManifest(manifest, File("/tmp/pkg"), File("/tmp/data"), isTrustedOrigin = true)

            val args = buildJsonObject { put("url", "http://example.com/data.json") }
            fetchAPI.execute(context, args)
        }
    }

    @Test(expected = APIException.SecurityBlocked::class)
    fun testLocalhostSsrfNetworkFetchBlocked() {
        runBlocking {
            val fetchAPI = NetworkFetchAPI()
            val manifest = Manifest(id = "com.mistfox.app", name = "App", version = "1.0", permissions = listOf("network"))
            val context = MiniAppContext.fromManifest(manifest, File("/tmp/pkg"), File("/tmp/data"), isTrustedOrigin = true)

            val args = buildJsonObject { put("url", "https://127.0.0.1/admin") }
            fetchAPI.execute(context, args)
        }
    }

    @Test(expected = APIException.SecurityBlocked::class)
    fun testIpv6LoopbackSsrfNetworkFetchBlocked() {
        runBlocking {
            val fetchAPI = NetworkFetchAPI()
            val manifest = Manifest(id = "com.mistfox.app", name = "App", version = "1.0", permissions = listOf("network"))
            val context = MiniAppContext.fromManifest(manifest, File("/tmp/pkg"), File("/tmp/data"), isTrustedOrigin = true)

            val args = buildJsonObject { put("url", "https://[::1]/secret") }
            fetchAPI.execute(context, args)
        }
    }

    private class DummyContext : android.content.ContextWrapper(null)
}
