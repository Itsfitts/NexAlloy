package io.github.nexalloy

import io.github.nexalloy.patchlist.PatchRepository

class AppPatchInfo(val appName: String, val packageName: String, val patches: Array<Patch>)

val appPatchConfigurations: List<AppPatchInfo>
    get() = PatchRepository.getAppPatchConfigurations()

val patchesByPackage: Map<String, Array<Patch>>
    get() = PatchRepository.getPatchesByPackage()
