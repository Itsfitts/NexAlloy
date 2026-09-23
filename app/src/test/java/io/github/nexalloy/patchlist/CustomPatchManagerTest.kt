package io.github.nexalloy.patchlist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomPatchManagerTest {

    @Test
    fun testParseMorphePatchesListJson() {
        val json = """
            {
              "version": "1.43.0",
              "patches": [
                {
                  "name": "Hide ads",
                  "description": "Removes ads in YouTube",
                  "use": true,
                  "compatiblePackages": [
                    { "packageName": "com.google.android.youtube", "name": "YouTube" }
                  ]
                },
                {
                  "name": "SponsorBlock",
                  "description": "Skip sponsored segments",
                  "default": false,
                  "compatiblePackages": ["com.google.android.youtube"]
                }
              ]
            }
        """.trimIndent()

        val parsed = PatchListParser.parse(json, "morphe-patches.json")

        assertEquals("1.43.0", parsed.version)
        assertEquals(2, parsed.patches.size)

        val patch1 = parsed.patches[0]
        assertEquals("Hide ads", patch1.name)
        assertEquals("Removes ads in YouTube", patch1.description)
        assertTrue(patch1.use)
        assertEquals(listOf("com.google.android.youtube"), patch1.compatiblePackages)

        val patch2 = parsed.patches[1]
        assertEquals("SponsorBlock", patch2.name)
        assertFalse(patch2.use)
        assertEquals(listOf("com.google.android.youtube"), patch2.compatiblePackages)
    }

    @Test
    fun testParseRootArrayJson() {
        val json = """
            [
              {
                "name": "Custom Patch 1",
                "description": "Description 1",
                "enabled": true,
                "packageName": "com.reddit.frontpage"
              },
              {
                "name": "Custom Patch 2",
                "enabled": false,
                "packages": ["com.strava"]
              }
            ]
        """.trimIndent()

        val parsed = PatchListParser.parse(json, "array-patches.json")

        assertEquals(2, parsed.patches.size)

        val patch1 = parsed.patches[0]
        assertEquals("Custom Patch 1", patch1.name)
        assertTrue(patch1.use)
        assertEquals(listOf("com.reddit.frontpage"), patch1.compatiblePackages)

        val patch2 = parsed.patches[1]
        assertEquals("Custom Patch 2", patch2.name)
        assertFalse(patch2.use)
        assertEquals(listOf("com.strava"), patch2.compatiblePackages)
    }

    @Test
    fun testParseInvalidJsonThrows() {
        val invalidJson = "{ name: 'broken' "
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PatchListParser.parse(invalidJson)
        }
        assertTrue(exception.message!!.contains("Invalid JSON format"))
    }

    @Test
    fun testParseEmptyContentThrows() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PatchListParser.parse("   ")
        }
        assertTrue(exception.message!!.contains("empty"))
    }

    @Test
    fun testParseNoValidPatchesThrows() {
        val json = """{ "version": "1.0", "patches": [] }"""
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PatchListParser.parse(json)
        }
        assertTrue(exception.message!!.contains("No valid patch entries"))
    }

    @Test
    fun testDeduplicationAndPackageFiltering() {
        val json = """
            [
              {
                "name": "Hide ads",
                "description": "Version 1",
                "use": true,
                "packageName": "com.google.android.youtube"
              },
              {
                "name": "Hide ads",
                "description": "Version 2",
                "use": false,
                "packageName": "com.google.android.youtube"
              }
            ]
        """.trimIndent()

        val parsed = PatchListParser.parse(json)
        assertEquals(1, parsed.patches.size)
        assertEquals("Hide ads", parsed.patches[0].name)
    }

    @Test
    fun testCustomPatchListSummary() {
        val summary = CustomPatchListSummary(
            fileName = "test.json",
            totalPatches = 5,
            packageCount = 2,
            version = "1.0.0"
        )
        assertEquals("test.json", summary.fileName)
        assertEquals(5, summary.totalPatches)
        assertEquals(2, summary.packageCount)
        assertEquals("1.0.0", summary.version)
    }
}
