package com.mistfox.platform.runtime

import com.mistfox.platform.api.APIException
import com.mistfox.platform.api.APIRegistry
import com.mistfox.platform.security.MiniAppContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

class RhinoBackgroundRuntime(
    private val appContext: MiniAppContext,
    private val apiRegistry: APIRegistry
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun executeBackgroundEvent(
        scriptContent: String,
        eventType: String,
        eventPayload: JsonObject = buildJsonObject {}
    ): String {
        val rhinoContext = Context.enter()
        try {
            rhinoContext.optimizationLevel = -1 // Interpretation mode for DEX
            rhinoContext.setClassShutter { _ ->
                // Strictly block reflection, System, Runtime, Context, and Java package access
                false
            }

            val scope: Scriptable = rhinoContext.initStandardObjects()

            // Remove dangerous Java package accessors
            ScriptableObject.deleteProperty(scope, "Packages")
            ScriptableObject.deleteProperty(scope, "java")
            ScriptableObject.deleteProperty(scope, "javax")
            ScriptableObject.deleteProperty(scope, "org")
            ScriptableObject.deleteProperty(scope, "com")

            val hostMist = HostMistObject(appContext, apiRegistry, json)
            ScriptableObject.putProperty(scope, "Mist", hostMist)
            ScriptableObject.putProperty(scope, "ZS", hostMist) // Compatibility alias

            // Evaluate background script
            rhinoContext.evaluateString(scope, scriptContent, "background.js", 1, null)

            val entryFunc = scope.get("onBackgroundEvent", scope)
            if (entryFunc is Function) {
                val eventJsObj = rhinoContext.newObject(scope)
                eventJsObj.put("type", eventJsObj, eventType)
                eventJsObj.put("timestamp", eventJsObj, System.currentTimeMillis())
                eventJsObj.put("appId", eventJsObj, appContext.packageId)

                val result = entryFunc.call(rhinoContext, scope, scope, arrayOf(eventJsObj))
                return Context.toString(result)
            } else {
                return "SUCCESS_NO_ENTRY_POINT"
            }
        } catch (e: Exception) {
            throw APIException.InternalError("Rhino background execution error: ${e.message}")
        } finally {
            Context.exit()
        }
    }

    class HostMistObject(
        private val appContext: MiniAppContext,
        private val apiRegistry: APIRegistry,
        private val jsonParser: Json
    ) : ScriptableObject() {
        override fun getClassName(): String = "HostMistObject"

        @org.mozilla.javascript.annotations.JSFunction
        fun call(apiName: String, argsObj: Any?): String {
            val argsJson: JsonObject = try {
                if (argsObj != null && argsObj != Context.getUndefinedValue()) {
                    if (argsObj is NativeObject) {
                        val cx = Context.getCurrentContext()
                        val jsonString = NativeJSON.stringify(cx, this, argsObj, null, null).toString()
                        jsonParser.parseToJsonElement(jsonString).jsonObject
                    } else {
                        val str = Context.toString(argsObj)
                        if (str.startsWith("{")) jsonParser.parseToJsonElement(str).jsonObject else buildJsonObject {}
                    }
                } else buildJsonObject {}
            } catch (_: Exception) {
                buildJsonObject {}
            }

            return runBlocking {
                try {
                    val result = apiRegistry.dispatch(appContext, apiName, argsJson, isBackground = true)
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("result", result)
                    }.toString()
                } catch (e: APIException) {
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", buildJsonObject {
                            put("code", JsonPrimitive(e.code))
                            put("message", JsonPrimitive(e.message ?: "Error"))
                        })
                    }.toString()
                } catch (e: Exception) {
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", buildJsonObject {
                            put("code", JsonPrimitive("INTERNAL_ERROR"))
                            put("message", JsonPrimitive(e.message ?: "Internal Error"))
                        })
                    }.toString()
                }
            }
        }
    }
}
