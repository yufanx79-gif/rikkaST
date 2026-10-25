package me.rerere.rikkahub.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import okhttp3.OkHttpClient
import okhttp3.Request
import me.rerere.common.http.await
import java.io.File
import java.io.FileOutputStream
import kotlin.uuid.Uuid

private const val TAG = "UpdateChecker"

// 更新源已从官方服务器切到本仓库：发新版时同步更新仓库根目录 update.json 并发布 GitHub Release
private const val API_URL = "https://raw.githubusercontent.com/yufanx79-gif/rikkaST/main/update.json"
private val SHA256_HEX_RE = Regex("^[a-fA-F0-9]{64}$")
private val SEMVER_TAG_RE = Regex("""(?<!\d)(\d+\.\d+\.\d+)(?!\d)""")
private val EMBEDDED_SEMVER_RE = Regex("\\b(\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?)\\b")
private val ACTIVE_DOWNLOAD_STATUSES = setOf("", "ready", "available", "active", "released", "published")

class UpdateChecker(
    private val client: OkHttpClient,
) {
    fun checkUpdate(): Flow<UiState<UpdateInfo>> = flow {
        emit(UiState.Loading)
        val request = Request.Builder()
            .url(API_URL)
            .build()
        val response = client.newCall(request).await()
        if (response.isSuccessful) {
            val body = response.body.string()
            val updateInfo = JsonInstant.decodeFromString<UpdateInfo>(body)
            emit(UiState.Success(updateInfo))
        } else {
            error("Failed to check update: ${response.code}")
        }
    }.catch {
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)

    fun downloadUpdate(
        context: Context,
        download: UpdateDownload,
        info: UpdateInfo? = null,
    ) {
        validateBeforeDownload(
            info = info,
            download = download,
            expectedPackageName = context.packageName,
            currentVersionCode = BuildConfig.VERSION_CODE.toInt(),
            currentVersionName = BuildConfig.VERSION_NAME,
        ).onFailure { err ->
            Toast.makeText(context, err.message ?: "更新包校验失败", Toast.LENGTH_LONG).show()
            return
        }

        // Start download in browser
        context.openUrl(download.url)
    }

    companion object {
        /**
         * Validates that an [UpdateDownload] entry is safe and eligible to be downloaded/installed
         * for the given [UpdateInfo] and host application package/version.
         */
        fun validateBeforeDownload(
            info: UpdateInfo?,
            download: UpdateDownload,
            expectedPackageName: String = "me.rerere.rikkahub.st",
            currentVersionCode: Int = 0,
            currentVersionName: String = "",
        ): Result<Unit> = runCatching {
            if (info != null) {
                require(info.isArtifactAvailable() && !info.isSourceOnlyOrUnavailable) {
                    "当前版本 (${info.version}) 尚未发布可下载的 APK 安装包（状态：${info.artifactStatus}）"
                }
                if (info.packageName.isNotBlank()) {
                    require(info.packageName.trim() == expectedPackageName.trim()) {
                        "更新包名不匹配：期望 $expectedPackageName，实际 ${info.packageName}"
                    }
                }
                if (info.versionCode > 0 && currentVersionCode > 0) {
                    require(info.versionCode > currentVersionCode) {
                        "更新包 versionCode (${info.versionCode}) 不高于当前已安装版本 ($currentVersionCode)"
                    }
                }
                if (info.versionCode > 0 && download.versionCode > 0) {
                    require(download.versionCode == info.versionCode) {
                        "下载项 versionCode (${download.versionCode}) 与发布版本 (${info.versionCode}) 不一致"
                    }
                }
                if (info.version.isNotBlank() && currentVersionName.isNotBlank()) {
                    require(Version.compare(info.version.trim(), currentVersionName.trim()) > 0) {
                        "更新包版本 (${info.version}) 不高于当前已安装版本 ($currentVersionName)"
                    }
                }
                if (info.version.isNotBlank()) {
                    require(!download.containsConflictingVersion(info.version)) {
                        "下载项文件或链接版本 (${download.name}) 与目标发布版本 (${info.version}) 不匹配"
                    }
                }
            }
            require(!download.isHistoricalOrUnavailable && download.isDownloadable(info?.version)) {
                "更新下载项不可用或与目标版本 (${info?.version ?: "unknown"}) 不匹配：${download.name}"
            }
            val cleanUrl = download.url.trim()
            require(cleanUrl.startsWith("https://", ignoreCase = true)) {
                "拒绝非 HTTPS 更新下载链接：$cleanUrl"
            }
            require(cleanUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)) {
                "更新下载链接必须指向 .apk 安装包：$cleanUrl"
            }
            require(download.name.trim().endsWith(".apk", ignoreCase = true)) {
                "更新文件名必须以 .apk 结尾：${download.name}"
            }
            require(download.sha256.isNotBlank() && SHA256_HEX_RE.matches(download.sha256.trim())) {
                "更新包 SHA-256 摘要缺失或格式非法：${download.sha256}"
            }
        }
    }
}

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String,
    val abi: String = "arm64-v8a",
    val sha256: String = "",
    val status: String = "ready",
    val targetReleaseName: String = "",
    val version: String = "",
    val versionCode: Int = 0,
) {
    val isHistoricalOrUnavailable: Boolean
        get() {
            val s = status.trim().lowercase()
            if (s.isEmpty()) return false
            if (s !in ACTIVE_DOWNLOAD_STATUSES) return true
            return s.contains("historical") ||
                s.contains("unavailable") ||
                s.contains("source-only") ||
                s.contains("source_only") ||
                s.contains("pending")
        }

    fun containsConflictingVersion(expectedVersion: String): Boolean {
        val expectedCore = SEMVER_TAG_RE.find(expectedVersion.trim())?.groupValues?.getOrNull(1)
            ?: expectedVersion.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (expectedCore.isBlank()) return false
        if (version.isNotBlank()) {
            val explicitCore = SEMVER_TAG_RE.find(version.trim())?.groupValues?.getOrNull(1)
                ?: version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            if (explicitCore.isNotBlank() && explicitCore != expectedCore) return true
        }
        val foundInName = SEMVER_TAG_RE.findAll(name).map { it.groupValues[1] }.toList()
        if (foundInName.any { it != expectedCore }) return true
        val foundInUrl = SEMVER_TAG_RE.findAll(url.substringBefore('?')).map { it.groupValues[1] }.toList()
        if (foundInUrl.any { it != expectedCore }) return true
        return false
    }

    fun isDownloadable(expectedVersion: String? = null): Boolean {
        if (isHistoricalOrUnavailable) return false
        val cleanUrl = url.trim()
        val cleanName = name.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) return false
        if (!cleanUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)) return false
        if (!cleanName.endsWith(".apk", ignoreCase = true)) return false
        if (sha256.isBlank() || !SHA256_HEX_RE.matches(sha256.trim())) return false

        val expVer = expectedVersion?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        if (expVer.isNotEmpty()) {
            if (containsConflictingVersion(expVer)) return false
            if (version.isNotBlank() && version.trim().removePrefix("v").removePrefix("V") != expVer) {
                return false
            }
            val nameVersions = EMBEDDED_SEMVER_RE.findAll(cleanName).map { it.groupValues[1] }.toList()
            if (nameVersions.isNotEmpty() && nameVersions.any { it != expVer }) {
                return false
            }
            val urlVersions = EMBEDDED_SEMVER_RE.findAll(cleanUrl.substringBefore('?')).map { it.groupValues[1] }.toList()
            if (urlVersions.isNotEmpty() && urlVersions.any { it != expVer }) {
                return false
            }
        }
        return true
    }
}

@Serializable
data class UpdateInfo(
    val version: String,
    val versionCode: Int = 0,
    val packageName: String = "me.rerere.rikkahub.st",
    val minAndroidSdk: Int = 26,
    val publishedAt: String,
    val artifactStatus: String = "READY",
    val targetArtifactName: String = "",
    val changelog: String,
    val downloads: List<UpdateDownload> = emptyList(),
    val historicalArtifacts: List<UpdateDownload> = emptyList(),
) {
    val isSourceOnlyOrUnavailable: Boolean
        get() {
            val st = artifactStatus.trim().uppercase()
            return st.startsWith("SOURCE_ONLY") ||
                st.contains("SOURCE_ONLY") ||
                st.contains("PENDING") ||
                st.contains("UNAVAILABLE") ||
                st.contains("BLOCKED")
        }

    fun isArtifactAvailable(): Boolean {
        val norm = artifactStatus.trim().uppercase()
        if (norm.isEmpty()) return true
        if (isSourceOnlyOrUnavailable) return false
        return norm == "READY" || norm == "AVAILABLE" || norm == "RELEASED" || norm == "PUBLISHED"
    }

    fun availableDownloads(): List<UpdateDownload> {
        if (!isArtifactAvailable()) return emptyList()
        return downloads.filter { dl ->
            dl.isDownloadable(version) &&
                (versionCode <= 0 || dl.versionCode <= 0 || dl.versionCode == versionCode)
        }
    }

    val eligibleDownloads: List<UpdateDownload>
        get() = availableDownloads()
}

@JvmInline
value class Version(val value: String) : Comparable<Version> {

    private fun parse(): ParsedVersion {
        val withoutBuild = value.split("+").first()
        val hyphenIndex = withoutBuild.indexOf('-')
        val (coreStr, prereleaseStr) = if (hyphenIndex >= 0) {
            withoutBuild.substring(0, hyphenIndex) to withoutBuild.substring(hyphenIndex + 1)
        } else {
            withoutBuild to null
        }
        val core = coreStr.split(".").map { it.toIntOrNull() ?: 0 }
        val prerelease = prereleaseStr?.split(".")
        return ParsedVersion(core, prerelease)
    }

    override fun compareTo(other: Version): Int {
        val a = this.parse()
        val b = other.parse()

        val maxLen = maxOf(a.core.size, b.core.size)
        for (i in 0 until maxLen) {
            val ap = if (i < a.core.size) a.core[i] else 0
            val bp = if (i < b.core.size) b.core[i] else 0
            if (ap != bp) return ap.compareTo(bp)
        }

        return when {
            a.prerelease == null && b.prerelease == null -> 0
            a.prerelease != null && b.prerelease == null -> -1
            a.prerelease == null && b.prerelease != null -> 1
            else -> comparePrerelease(a.prerelease!!, b.prerelease!!)
        }
    }

    companion object {
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                if (i >= a.size) return -1
                if (i >= b.size) return 1

                val aNum = a[i].toIntOrNull()
                val bNum = b[i].toIntOrNull()

                val cmp = when {
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    aNum != null -> -1
                    bNum != null -> 1
                    else -> a[i].compareTo(b[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
