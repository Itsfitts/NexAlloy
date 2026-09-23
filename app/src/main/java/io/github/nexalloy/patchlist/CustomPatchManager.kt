package io.github.nexalloy.patchlist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import app.morphe.extension.shared.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

object CustomPatchManager {

    private const val PREFS_NAME = "custom_patch_list_prefs"
    private const val KEY_ENABLED = "custom_patch_list_enabled"
    private const val KEY_URI = "custom_patch_list_uri"
    private const val KEY_FILE_NAME = "custom_patch_list_filename"
    private const val KEY_JSON_CACHE = "custom_patch_list_json"
    private const val KEY_PATCH_COUNT = "custom_patch_list_patch_count"
    private const val KEY_VERSION = "custom_patch_list_version"

    // In-memory cache for fast access
    private var memoryCacheList: CustomPatchList? = null
    private var isMemoryCacheValid = false

    private fun resolveContext(provided: Context?): Context? {
        if (provided != null) return provided
        return runCatching {
            app.morphe.extension.shared.Utils.getContext()
        }.getOrNull() ?: runCatching {
            io.github.nexalloy.activity.SettingApplication.instance
        }.getOrNull()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context? = null): Boolean {
        val ctx = resolveContext(context) ?: return isMemoryCacheValid && memoryCacheList != null
        return getPrefs(ctx).getBoolean(KEY_ENABLED, false)
    }

    fun getPersistedUri(context: Context): String? {
        return getPrefs(context).getString(KEY_URI, null)
    }

    fun getFileName(context: Context): String? {
        return getPrefs(context).getString(KEY_FILE_NAME, null)
    }

    fun getSummary(context: Context? = null): CustomPatchListSummary? {
        val ctx = resolveContext(context) ?: return null
        if (!isEnabled(ctx)) return null
        val fileName = getFileName(ctx) ?: "custom_patch_list.json"
        val patchCount = getPrefs(ctx).getInt(KEY_PATCH_COUNT, 0)
        val version = getPrefs(ctx).getString(KEY_VERSION, null)

        val patchList = getCustomPatchList(ctx)
        val packageCount = patchList?.patches
            ?.flatMap { it.compatiblePackages }
            ?.distinct()
            ?.size ?: 0

        return CustomPatchListSummary(
            fileName = fileName,
            totalPatches = patchCount,
            packageCount = packageCount,
            version = version
        )
    }

    fun loadFromUri(context: Context, uri: Uri): Result<CustomPatchListSummary> {
        return try {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Logger.printInfo { "Could not take persistable URI permission: ${e.message}" }
            }

            val fileName = resolveFileName(context, uri)
            val content = readUriContent(context, uri)

            val parsedList = PatchListParser.parse(content, fileName, uri.toString())

            getPrefs(context).edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_URI, uri.toString())
                .putString(KEY_FILE_NAME, fileName)
                .putString(KEY_JSON_CACHE, content)
                .putInt(KEY_PATCH_COUNT, parsedList.patches.size)
                .putString(KEY_VERSION, parsedList.version)
                .apply()

            memoryCacheList = parsedList
            isMemoryCacheValid = true

            val packageCount = parsedList.patches
                .flatMap { it.compatiblePackages }
                .distinct()
                .size

            val summary = CustomPatchListSummary(
                fileName = fileName,
                totalPatches = parsedList.patches.size,
                packageCount = packageCount,
                version = parsedList.version
            )

            Result.success(summary)
        } catch (e: Exception) {
            Logger.printException({ "Failed to load custom patch list from URI: $uri" }, e)
            Result.failure(e)
        }
    }

    fun reloadCurrent(context: Context): Result<CustomPatchListSummary> {
        val uriStr = getPersistedUri(context)
            ?: return Result.failure(IllegalStateException("No custom patch list URI saved"))

        val uri = Uri.parse(uriStr)
        return try {
            val content = readUriContent(context, uri)
            val fileName = getFileName(context) ?: resolveFileName(context, uri)
            val parsedList = PatchListParser.parse(content, fileName, uri.toString())

            getPrefs(context).edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_JSON_CACHE, content)
                .putInt(KEY_PATCH_COUNT, parsedList.patches.size)
                .putString(KEY_VERSION, parsedList.version)
                .apply()

            memoryCacheList = parsedList
            isMemoryCacheValid = true

            val packageCount = parsedList.patches
                .flatMap { it.compatiblePackages }
                .distinct()
                .size

            val summary = CustomPatchListSummary(
                fileName = fileName,
                totalPatches = parsedList.patches.size,
                packageCount = packageCount,
                version = parsedList.version
            )

            Result.success(summary)
        } catch (e: Exception) {
            Logger.printInfo { "Failed to reload from URI ($uri), attempting cached JSON fallback..." }
            val cachedJson = getPrefs(context).getString(KEY_JSON_CACHE, null)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val fileName = getFileName(context) ?: "cached_patch_list.json"
                    val parsedList = PatchListParser.parse(cachedJson, fileName, uriStr)

                    memoryCacheList = parsedList
                    isMemoryCacheValid = true

                    val summary = CustomPatchListSummary(
                        fileName = fileName,
                        totalPatches = parsedList.patches.size,
                        packageCount = parsedList.patches.flatMap { it.compatiblePackages }.distinct().size,
                        version = parsedList.version
                    )
                    return Result.success(summary)
                } catch (fallbackErr: Exception) {
                    Logger.printException({ "Cached JSON fallback failed" }, fallbackErr)
                }
            }
            Result.failure(e)
        }
    }

    fun clear(context: Context) {
        val uriStr = getPersistedUri(context)
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
            } catch (e: Exception) {
                Logger.printInfo { "Could not release persistable URI permission: ${e.message}" }
            }
        }

        getPrefs(context).edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_URI)
            .remove(KEY_FILE_NAME)
            .remove(KEY_JSON_CACHE)
            .remove(KEY_PATCH_COUNT)
            .remove(KEY_VERSION)
            .apply()

        memoryCacheList = null
        isMemoryCacheValid = false
    }

    fun getCustomPatchList(context: Context? = null): CustomPatchList? {
        if (isMemoryCacheValid && memoryCacheList != null) {
            return memoryCacheList
        }

        val ctx = resolveContext(context) ?: return null

        if (!isEnabled(ctx)) return null

        val cachedJson = getPrefs(ctx).getString(KEY_JSON_CACHE, null)
        val fileName = getFileName(ctx)
        val uriStr = getPersistedUri(ctx)

        if (!cachedJson.isNullOrBlank()) {
            return try {
                val parsed = PatchListParser.parse(cachedJson, fileName, uriStr)
                memoryCacheList = parsed
                isMemoryCacheValid = true
                parsed
            } catch (e: Exception) {
                Logger.printException({ "Failed to parse cached custom patch list" }, e)
                getPrefs(ctx).edit().putBoolean(KEY_ENABLED, false).apply()
                null
            }
        }

        return null
    }

    fun invalidateCache() {
        memoryCacheList = null
        isMemoryCacheValid = false
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.printInfo { "Error querying display name for URI: ${e.message}" }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment
        }
        return if (!name.isNullOrBlank()) name!! else "custom_patch_list.json"
    }

    private fun readUriContent(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                return reader.readText()
            }
        } ?: throw java.io.IOException("Unable to open input stream for URI: $uri")
    }
}
