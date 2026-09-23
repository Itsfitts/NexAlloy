package io.github.nexalloy.patchlist

import android.content.Context
import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.Patch
import io.github.nexalloy.hoodles.morphe.alltrails.AllTrailsPatches
import io.github.nexalloy.morphe.music.YTMusicPatches
import io.github.nexalloy.morphe.reddit.RedditPatches
import io.github.nexalloy.morphe.youtube.YouTubePatches
import io.github.nexalloy.revanced.googlephotos.GooglePhotosPatches
import io.github.nexalloy.revanced.meta.MetaPatches
import io.github.nexalloy.revanced.photomath.PhotomathPatches
import io.github.nexalloy.revanced.strava.StravaPatches

object PatchRepository {

    val defaultAppPatchConfigurations: List<AppPatchInfo> = listOf(
        AppPatchInfo("YouTube", "com.google.android.youtube", YouTubePatches),
        AppPatchInfo("YT Music", "com.google.android.apps.youtube.music", YTMusicPatches),
        AppPatchInfo("Reddit", "com.reddit.frontpage", RedditPatches),
        AppPatchInfo("Google Photos", "com.google.android.apps.photos", GooglePhotosPatches),
        AppPatchInfo("Photomath", "com.microblink.photomath", PhotomathPatches),
        AppPatchInfo("Instagram", "com.instagram.android", MetaPatches),
        AppPatchInfo("Threads", "com.instagram.barcelona", MetaPatches),
        AppPatchInfo("Strava", "com.strava", StravaPatches),
        AppPatchInfo("AllTrails", "com.alltrails.alltrails", AllTrailsPatches),
    )

    fun getAppPatchConfigurations(context: Context? = null): List<AppPatchInfo> {
        val customList = CustomPatchManager.getCustomPatchList(context)
        if (customList == null || customList.patches.isEmpty()) {
            return defaultAppPatchConfigurations
        }

        val customPatches = customList.patches
        val updatedConfigs = mutableListOf<AppPatchInfo>()

        for (defaultConfig in defaultAppPatchConfigurations) {
            val pkg = defaultConfig.packageName

            val matchingCustom = customPatches.filter {
                it.compatiblePackages.isEmpty() || it.compatiblePackages.contains(pkg)
            }

            if (matchingCustom.isEmpty()) {
                updatedConfigs.add(defaultConfig)
                continue
            }

            val customByName = matchingCustom.associateBy { it.name }

            val mergedPatches = defaultConfig.patches.map { builtinPatch ->
                val patchName = builtinPatch.name
                if (patchName.isBlank() || patchName.startsWith("<")) {
                    builtinPatch
                } else {
                    val customMatch = customByName[patchName]
                    if (customMatch != null) {
                        Patch(
                            name = builtinPatch.name,
                            description = if (customMatch.description.isNotBlank()) customMatch.description else builtinPatch.description,
                            use = customMatch.use,
                            run = builtinPatch.run
                        )
                    } else {
                        builtinPatch
                    }
                }
            }

            updatedConfigs.add(
                AppPatchInfo(
                    appName = defaultConfig.appName,
                    packageName = pkg,
                    patches = mergedPatches.toTypedArray()
                )
            )
        }

        return updatedConfigs
    }

    fun getPatchesByPackage(context: Context? = null): Map<String, Array<Patch>> {
        return getAppPatchConfigurations(context).associate { it.packageName to it.patches }
    }
}
