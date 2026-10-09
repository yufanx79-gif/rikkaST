package me.rerere.rikkahub.data.st.expressions

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.model.TavernAsset
import java.io.File
import java.util.Base64
import kotlin.uuid.Uuid

/**
 * 立绘文件仓库：`filesDir/sprites/<assistantId>/<label>.(png|jpg|webp|gif)`。
 *
 * 对齐 SillyTavern Character Expressions 的「按角色的立绘文件夹 + 文件名 = 标签」约定
 * （官方 `validateExpressionSpriteName`：`^label(?:[-.].*?)?$`），
 * 每个助手一套立绘，标签用 [ExpressionLabels.ALL] 的 28 个 GoEmotions 标签匹配。
 *
 * 来源：
 * - A：V3 角色卡 `data.assets` 里的内嵌图片（data URI）→ [syncFromCardAssets]；
 * - B：设置页手动多选图片 → [importFromUri]（按文件名推断标签）。
 *
 * 存储即文件：不做数据库迁移、不写进 Settings，导入/导出角色卡不受影响。
 */
object SpriteRepository {

    private const val SPRITE_ROOT = "sprites"

    /** 支持的文件扩展名（png/jpg/webp/gif；官方立绘常见格式） */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf("png", "jpg", "jpeg", "webp", "gif")

    /** 该助手的立绘目录（不存在时创建） */
    fun spriteDir(context: Context, assistantId: Uuid): File =
        File(File(context.filesDir, SPRITE_ROOT), assistantId.toString()).apply { mkdirs() }

    /** 仅查询目录（不创建） */
    private fun existingDir(context: Context, assistantId: Uuid): File =
        File(File(context.filesDir, SPRITE_ROOT), assistantId.toString())

    /** 该助手已拥有的立绘标签（按官方 28 标签顺序排序） */
    fun listLabels(context: Context, assistantId: Uuid): List<String> {
        val dir = existingDir(context, assistantId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            .mapNotNull { ExpressionLabels.labelFromFileName(it.name) }
            .distinct()
            .sortedBy { ExpressionLabels.ALL.indexOf(it) }
            .toList()
    }

    /** 标签 → 立绘文件（按扩展名优先级 png → jpg/jpeg → webp → gif；不存在返回 null） */
    fun resolveFile(context: Context, assistantId: Uuid, label: String): File? {
        val dir = existingDir(context, assistantId)
        if (!dir.isDirectory) return null
        val normalized = label.trim().lowercase()
        return listOf("png", "jpg", "jpeg", "webp", "gif")
            .map { File(dir, "$normalized.$it") }
            .firstOrNull { it.isFile }
    }

    /**
     * 手动导入单张图片：按 [labelHint]（显式标签）或文件名推断标签。
     * @return 导入后的标签；文件名无法匹配 28 标签时返回 null（调用方提示用户）
     */
    suspend fun importFromUri(
        context: Context,
        assistantId: Uuid,
        uri: Uri,
        labelHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
        val label = labelHint?.let { ExpressionLabels.matchLabel(it) }
            ?: ExpressionLabels.labelFromFileName(displayName)
            ?: return@withContext null
        val ext = resolveExtension(displayName, context.contentResolver.getType(uri)) ?: "png"
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        writeSprite(context, assistantId, label, bytes, ext)
        label
    }

    /** 删除某标签的全部立绘文件；返回是否有文件被删除 */
    suspend fun deleteLabel(context: Context, assistantId: Uuid, label: String): Boolean =
        withContext(Dispatchers.IO) {
            val dir = existingDir(context, assistantId)
            if (!dir.isDirectory) return@withContext false
            val normalized = label.trim().lowercase()
            val removed = dir.listFiles().orEmpty()
                .filter { ExpressionLabels.labelFromFileName(it.name) == normalized }
                .count { it.delete() }
            removed > 0
        }

    /**
     * 来源 A：V3 角色卡 assets 同步（仅处理 `data:image/...;base64,` 内嵌资源）。
     * 外链（http/ccdefault/asset://）暂不落盘 —— 不引入网络下载依赖（本批范围）。
     * @return 成功落盘的标签列表
     */
    suspend fun syncFromCardAssets(
        context: Context,
        assistantId: Uuid,
        assets: List<TavernAsset>,
    ): List<String> = withContext(Dispatchers.IO) {
        if (assets.isEmpty()) return@withContext emptyList()
        val imported = linkedSetOf<String>()
        for (asset in assets) {
            val label = ExpressionLabels.labelFromFileName(asset.name) ?: continue
            val decoded = decodeDataUri(asset.uri) ?: continue
            val ext = decoded.second
                ?: asset.ext.lowercase().takeIf { it in SUPPORTED_EXTENSIONS }
                ?: "png"
            writeSprite(context, assistantId, label, decoded.first, ext)
            imported += label
        }
        imported.toList()
    }

    // ==================== 内部实现 ====================

    private fun writeSprite(
        context: Context,
        assistantId: Uuid,
        label: String,
        bytes: ByteArray,
        ext: String,
    ) {
        val dir = spriteDir(context, assistantId)
        val safeExt = ext.lowercase().takeIf { it in SUPPORTED_EXTENSIONS } ?: "png"
        // 一个标签只保留一份文件：先清掉同标签其它扩展名，避免 resolveFile 命中旧图
        dir.listFiles().orEmpty()
            .filter { ExpressionLabels.labelFromFileName(it.name) == label && it.extension.lowercase() != safeExt }
            .forEach { it.delete() }
        File(dir, "$label.$safeExt").writeBytes(bytes)
    }

    private fun resolveExtension(displayName: String, mime: String?): String? {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName in SUPPORTED_EXTENSIONS) return fromName
        return when (mime?.lowercase()) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    /**
     * `data:image/png;base64,xxxx` → (bytes, ext)；非 data URI / 非 base64 返回 null。
     * 与 JS 侧 `atob` 等价（java.util.Base64，minSdk 26 可用，且为纯 JVM 可单测实现）。
     */
    fun decodeDataUri(uri: String): Pair<ByteArray, String?>? {
        if (!uri.startsWith("data:", ignoreCase = true)) return null
        val comma = uri.indexOf(',')
        if (comma <= 0) return null
        val meta = uri.substring(5, comma)
        if (!meta.contains("base64", ignoreCase = true)) return null
        val mime = meta.substringBefore(';').trim().lowercase()
        val ext = when (mime) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
        val payload = uri.substring(comma + 1).filterNot { it.isWhitespace() }
        val bytes = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        return bytes to ext
    }
}
