package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerValidationTest {

    @Test
    fun acceptsValidHttpsApkWithMatchingPackageVersionAndSha256() {
        val download = UpdateDownload(
            name = "rikkaST-2.5.9-universal-release.apk",
            url = "https://github.com/yufanx79-gif/rikkaST/releases/download/v2.5.9/rikkaST-2.5.9-universal-release.apk",
            size = "61.5 MB",
            abi = "arm64-v8a",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            version = "2.5.9",
            versionCode = 241,
        )
        val info = UpdateInfo(
            version = "2.5.9",
            versionCode = 241,
            packageName = "me.rerere.rikkahub.st",
            minAndroidSdk = 26,
            publishedAt = "2026-10-22T00:00:00Z",
            artifactStatus = "PUBLISHED",
            changelog = "P0 渲染与悬浮窗修复",
            downloads = listOf(download),
        )
        val result = UpdateChecker.validateBeforeDownload(
            info = info,
            download = download,
            expectedPackageName = "me.rerere.rikkahub.st",
            currentVersionCode = 238,
        )
        assertTrue(result.isSuccess)
        assertEquals(1, info.eligibleDownloads.size)
    }

    @Test
    fun rejectsSourceOnlyPendingBuildAndHistoricalOrMismatchedVersionApk() {
        val stale257Download = UpdateDownload(
            name = "rikkaST-2.5.7-universal-release.apk",
            url = "https://github.com/yufanx79-gif/rikkaST/releases/download/v2.5.7/rikkaST-2.5.7-universal-release.apk",
            size = "58.7 MB",
            abi = "arm64-v8a",
            sha256 = "8620c3e2bf744732ed7fcdd6f9b9de8175814242315fe70a73124972a9f3441e",
            status = "historical-reference-2.5.7-apk-only-no-2.5.9-binary-built-in-offline-sandbox",
        )
        val sourceOnlyInfo = UpdateInfo(
            version = "2.5.9",
            versionCode = 241,
            packageName = "me.rerere.rikkahub.st",
            minAndroidSdk = 26,
            publishedAt = "2026-10-22T00:00:00Z",
            artifactStatus = "SOURCE_ONLY_PENDING_SDK_BUILD",
            changelog = "源码候选包",
            downloads = listOf(stale257Download),
        )
        assertTrue(sourceOnlyInfo.isSourceOnlyOrUnavailable)
        assertTrue(sourceOnlyInfo.eligibleDownloads.isEmpty())
        assertFalse(UpdateChecker.validateBeforeDownload(sourceOnlyInfo, stale257Download).isSuccess)

        // Even if artifactStatus were PUBLISHED, a 2.5.7 APK must be rejected when info.version is 2.5.9
        val publishedWithWrongBinary = sourceOnlyInfo.copy(
            artifactStatus = "PUBLISHED",
            downloads = listOf(stale257Download.copy(status = "")),
        )
        assertTrue(publishedWithWrongBinary.eligibleDownloads.isEmpty())
        assertFalse(
            UpdateChecker.validateBeforeDownload(
                publishedWithWrongBinary,
                stale257Download.copy(status = ""),
            ).isSuccess
        )
    }

    @Test
    fun rejectsNonHttpsOrPackageMismatchOrInvalidSha256() {
        val baseDownload = UpdateDownload(
            name = "rikkaST.apk",
            url = "http://example.com/rikkaST.apk",
            size = "50 MB",
        )
        assertFalse(UpdateChecker.validateBeforeDownload(null, baseDownload).isSuccess)

        val validDownload = baseDownload.copy(url = "https://example.com/rikkaST.apk", sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        val wrongPkgInfo = UpdateInfo(
            version = "2.5.9",
            packageName = "me.rerere.rikkahub",
            publishedAt = "2026-10-22T00:00:00Z",
            changelog = "",
            downloads = listOf(validDownload),
        )
        assertFalse(UpdateChecker.validateBeforeDownload(wrongPkgInfo, validDownload, "me.rerere.rikkahub.st").isSuccess)

        val badHashDownload = validDownload.copy(sha256 = "not-a-sha256")
        assertFalse(UpdateChecker.validateBeforeDownload(null, badHashDownload).isSuccess)
    }
}
