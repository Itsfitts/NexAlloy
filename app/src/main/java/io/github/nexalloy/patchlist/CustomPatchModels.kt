package io.github.nexalloy.patchlist

data class ParsedPatchInfo(
    val name: String,
    val description: String = "",
    val use: Boolean = true,
    val compatiblePackages: List<String> = emptyList()
)

data class CustomPatchList(
    val version: String? = null,
    val uri: String? = null,
    val fileName: String? = null,
    val patches: List<ParsedPatchInfo> = emptyList()
)

data class CustomPatchListSummary(
    val fileName: String,
    val totalPatches: Int,
    val packageCount: Int,
    val version: String? = null
)
