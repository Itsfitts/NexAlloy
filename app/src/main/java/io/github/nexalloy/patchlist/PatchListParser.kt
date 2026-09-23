package io.github.nexalloy.patchlist

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

object PatchListParser {

    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB limit

    fun parse(content: String, fileName: String? = null, uri: String? = null): CustomPatchList {
        if (content.isBlank()) {
            throw IllegalArgumentException("Patch list file is empty")
        }

        if (content.toByteArray(Charsets.UTF_8).size > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("File exceeds maximum allowed size (10MB)")
        }

        val jsonElement: JsonElement = try {
            JsonParser.parseString(content)
        } catch (e: JsonSyntaxException) {
            throw IllegalArgumentException("Invalid JSON format: ${e.message}", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse file content: ${e.message}", e)
        }

        var version: String? = null
        val rawPatchesList = mutableListOf<JsonObject>()

        when {
            jsonElement.isJsonObject -> {
                val rootObj = jsonElement.asJsonObject
                if (rootObj.has("version") && rootObj.get("version").isJsonPrimitive) {
                    version = rootObj.get("version").asString
                }

                if (rootObj.has("patches") && rootObj.get("patches").isJsonArray) {
                    val patchesArray = rootObj.getAsJsonArray("patches")
                    for (elem in patchesArray) {
                        if (elem.isJsonObject) {
                            rawPatchesList.add(elem.asJsonObject)
                        }
                    }
                } else if (rootObj.has("name") && rootObj.get("name").isJsonPrimitive) {
                    // Single patch object at root
                    rawPatchesList.add(rootObj)
                } else {
                    throw IllegalArgumentException("JSON object does not contain a valid 'patches' array")
                }
            }

            jsonElement.isJsonArray -> {
                val rootArray = jsonElement.asJsonArray
                for (elem in rootArray) {
                    if (elem.isJsonObject) {
                        rawPatchesList.add(elem.asJsonObject)
                    }
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported JSON format: expected JSON object or array")
            }
        }

        val parsedPatches = mutableListOf<ParsedPatchInfo>()
        val seenKeys = mutableSetOf<String>()

        for (patchObj in rawPatchesList) {
            val name = extractString(patchObj, "name", "id", "title")
            if (name.isBlank()) continue

            val description = extractString(patchObj, "description", "summary")
            val use = extractBoolean(patchObj, true, "use", "default", "enabled")
            val compatiblePackages = extractPackages(patchObj)

            val patchInfo = ParsedPatchInfo(
                name = name,
                description = description,
                use = use,
                compatiblePackages = compatiblePackages
            )

            // Deduplicate by name + packages
            val pkgKey = compatiblePackages.sorted().joinToString(",")
            val uniqueKey = "$name::$pkgKey"
            if (!seenKeys.contains(uniqueKey)) {
                seenKeys.add(uniqueKey)
                parsedPatches.add(patchInfo)
            }
        }

        if (parsedPatches.isEmpty()) {
            throw IllegalArgumentException("No valid patch entries found in file")
        }

        return CustomPatchList(
            version = version,
            uri = uri,
            fileName = fileName,
            patches = parsedPatches
        )
    }

    private fun extractString(obj: JsonObject, vararg keys: String): String {
        for (key in keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull) {
                val elem = obj.get(key)
                if (elem.isJsonPrimitive) {
                    return elem.asString.trim()
                }
            }
        }
        return ""
    }

    private fun extractBoolean(obj: JsonObject, defaultVal: Boolean, vararg keys: String): Boolean {
        for (key in keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull) {
                val elem = obj.get(key)
                if (elem.isJsonPrimitive) {
                    val primitive = elem.asJsonPrimitive
                    if (primitive.isBoolean) {
                        return primitive.asBoolean
                    } else if (primitive.isString) {
                        val str = primitive.asString.trim().lowercase()
                        if (str == "true" || str == "1") return true
                        if (str == "false" || str == "0") return false
                    }
                }
            }
        }
        return defaultVal
    }

    private fun extractPackages(obj: JsonObject): List<String> {
        val result = mutableListOf<String>()

        val pkgKeys = listOf("compatiblePackages", "packages", "packageName", "package")
        for (key in pkgKeys) {
            if (!obj.has(key) || obj.get(key).isJsonNull) continue
            val elem = obj.get(key)

            if (elem.isJsonArray) {
                val array = elem.asJsonArray
                for (item in array) {
                    when {
                        item.isJsonObject -> {
                            val pkgObj = item.asJsonObject
                            val pkgName = extractString(pkgObj, "packageName", "name", "id")
                            if (pkgName.isNotBlank()) {
                                result.add(pkgName)
                            }
                        }
                        item.isJsonPrimitive -> {
                            val pkgName = item.asString.trim()
                            if (pkgName.isNotBlank()) {
                                result.add(pkgName)
                            }
                        }
                    }
                }
            } else if (elem.isJsonPrimitive) {
                val str = elem.asString.trim()
                if (str.isNotBlank()) {
                    result.add(str)
                }
            }
        }

        return result.distinct()
    }
}
