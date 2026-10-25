package me.rerere.rikkahub.utils

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sourceOnlyRelease_hasNoAvailableDownloads_andRejectsHistoricalApkDownload() {
        val raw = """
            {
              "version": "2.5.9",
              "versionCode": 241,
              "packageName": "me.rerere.rikkahub.st",
              "minAndroidSdk": 26,
              "publishedAt": "2026-10-22T00:00:00Z",
              "artifactStatus": "SOURCE_ONLY_PENDING_SDK_BUILD",
              "targetArtifactName": "rikkaST-2.5.9-universal-release.apk",
              "changelog": "Source only",
              "downloads": [],
              "historicalArtifacts": [
                {
                  "name": "rikkaST-2.5.7-universal-release.apk",
                  "version": "2.5.7",
                  "versionCode": 239,
                  "status": "historical-reference-2.5.7-apk-only-no-2.5.9-binary-built-in-offline-sandbox",
                  "url": "https://github.com/yufanx79-gif/rikkaST/releases/download/v2.5.7/rikkaST-2.5.7-universal-release.apk",
                  "size": "58.7 MB",
                  "abi": "arm64-v8a",
                  "sha256": "8620c3e2bf744732ed7fcdd6f9b9de8175814242315fe70a73124972a9f3441e"
                }
              ]
            }
        """.trimIndent()

        val info = json.decodeFromString<UpdateInfo>(raw)
        assertFalse(info.isArtifactAvailable())
        assertTrue(info.availableDownloads().isEmpty())
        assertEquals(1, info.historicalArtifacts.size)

        val historical = info.historicalArtifacts.first()
        assertFalse(historical.isDownloadable("2.5.9"))
        assertTrue(
            UpdateChecker.validateBeforeDownload(
                info = info,
                download = historical,
                expectedPackageName = "me.rerere.rikkahub.st",
                currentVersionCode = 238,
                currentVersionName = "2.5.6",
            ).isFailure
        )
    }

    @Test
    fun readyRelease_blocksVersionMismatchedApk_andAcceptsMatchingVerifiedApk() {
        val staleDownload = UpdateDownload(
            name = "rikkaST-2.5.7-universal-release.apk",
            url = "https://github.com/yufanx79-gif/rikkaST/releases/download/v2.5.7/rikkaST-2.5.7-universal-release.apk",
            size = "58.7 MB",
            sha256 = "8620c3e2bf744732ed7fcdd6f9b9de8175814242315fe70a73124972a9f3441e",
            status = "ready",
        )
        val validDownload = UpdateDownload(
            name = "rikkaST-2.5.9-universal-release.apk",
            url = "https://github.com/yufanx79-gif/rikkaST/releases/download/v2.5.9/rikkaST-2.5.9-universal-release.apk",
            size = "59.0 MB",
            sha256 = "8620c3e2bf744732ed7fcdd6f9b9de8175814242315fe70a73124972a9f3441e",
            status = "ready",
            version = "2.5.9",
            versionCode = 241,
        )
        val info = UpdateInfo(
            version = "2.5.9",
            versionCode = 241,
            packageName = "me.rerere.rikkahub.st",
            publishedAt = "2026-10-22T00:00:00Z",
            artifactStatus = "READY",
            changelog = "Release 2.5.9",
            downloads = listOf(staleDownload, validDownload),
        )

        assertTrue(info.isArtifactAvailable())
        val available = info.availableDownloads()
        assertEquals(1, available.size)
        assertEquals("rikkaST-2.5.9-universal-release.apk", available.single().name)

        // Mismatched 2.5.7 APK must be rejected even if status="ready"
        assertTrue(
            UpdateChecker.validateBeforeDownload(
                info = info,
                download = staleDownload,
                expectedPackageName = "me.rerere.rikkahub.st",
                currentVersionCode = 238,
                currentVersionName = "2.5.6",
            ).isFailure
        )

        // Matching 2.5.9 APK with valid hash and higher versionCode succeeds
        assertTrue(
            UpdateChecker.validateBeforeDownload(
                info = info,
                download = validDownload,
                expectedPackageName = "me.rerere.rikkahub.st",
                currentVersionCode = 238,
                currentVersionName = "2.5.6",
            ).isSuccess
        )

        // Downgrade / equal versionCode must be rejected
        assertTrue(
            UpdateChecker.validateBeforeDownload(
                info = info,
                download = validDownload,
                expectedPackageName = "me.rerere.rikkahub.st",
                currentVersionCode = 241,
                currentVersionName = "2.5.9",
            ).isFailure
        )
    }
}
