package com.mistfox.platform.api

import com.mistfox.platform.security.MiniAppContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

class FilesReadAPI : MistFoxAPI {
    override val name: String = "files.read"
    override val requiredPermission: String = "files.read"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val path = args["path"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'path' argument")

        val targetFile = resolveIsolatedPath(context, path)
        if (!targetFile.exists()) {
            throw APIException.InvalidArgument("File does not exist: $path")
        }

        val content = targetFile.readText(Charsets.UTF_8)
        return buildJsonObject {
            put("path", path)
            put("content", content)
        }
    }
}

class FilesWriteAPI : MistFoxAPI {
    override val name: String = "files.write"
    override val requiredPermission: String = "files.write"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val path = args["path"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'path' argument")
        val content = args["content"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'content' argument")

        val targetFile = resolveIsolatedPath(context, path)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(content, Charsets.UTF_8)

        return buildJsonObject {
            put("path", path)
            put("bytesWritten", targetFile.length())
            put("written", true)
        }
    }
}

class FilesDeleteAPI : MistFoxAPI {
    override val name: String = "files.delete"
    override val requiredPermission: String = "files.write"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val path = args["path"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'path' argument")

        val targetFile = resolveIsolatedPath(context, path)
        val deleted = targetFile.delete()

        return buildJsonObject {
            put("path", path)
            put("deleted", deleted)
        }
    }
}

class FilesExistsAPI : MistFoxAPI {
    override val name: String = "files.exists"
    override val requiredPermission: String = "files.read"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val path = args["path"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'path' argument")

        val targetFile = resolveIsolatedPath(context, path)

        return buildJsonObject {
            put("path", path)
            put("exists", targetFile.exists())
        }
    }
}

class FilesInfoAPI : MistFoxAPI {
    override val name: String = "files.info"
    override val requiredPermission: String = "files.read"

    override suspend fun execute(context: MiniAppContext, args: JsonObject): JsonElement {
        val path = args["path"]?.jsonPrimitive?.content
            ?: throw APIException.InvalidArgument("Missing required 'path' argument")

        val targetFile = resolveIsolatedPath(context, path)
        if (!targetFile.exists()) {
            throw APIException.InvalidArgument("File does not exist: $path")
        }

        return buildJsonObject {
            put("path", path)
            put("size", targetFile.length())
            put("isFile", targetFile.isFile)
            put("isDirectory", targetFile.isDirectory)
            put("lastModified", targetFile.lastModified())
        }
    }
}

private fun resolveIsolatedPath(context: MiniAppContext, path: String): File {
    if (path.contains("..")) {
        throw APIException.SecurityBlocked("Path traversal with '..' is prohibited")
    }
    val cleanPath = path.trimStart('/')
    val resolved = File(context.dataDir, cleanPath)
    val canonicalDataDir = context.dataDir.canonicalPath
    val canonicalResolved = resolved.canonicalPath

    val dataDirWithSep = if (canonicalDataDir.endsWith(File.separator)) canonicalDataDir else canonicalDataDir + File.separator

    if (!canonicalResolved.startsWith(dataDirWithSep) && canonicalResolved != canonicalDataDir) {
        throw APIException.SecurityBlocked("Access outside isolated app data directory is prohibited")
    }
    return resolved
}
