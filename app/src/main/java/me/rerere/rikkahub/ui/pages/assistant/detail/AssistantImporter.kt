package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ToolPolicy
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.st.expressions.SpriteRepository
import me.rerere.rikkahub.data.model.TavernEmbeddedBook
import me.rerere.rikkahub.data.model.TavernBookEntry
import me.rerere.rikkahub.data.st.regex.parseCardRegexScripts
import me.rerere.rikkahub.data.model.TavernAsset
import me.rerere.rikkahub.data.model.SelectiveLogic
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/**
 * 酒馆导入结果
 */
data class TavernImportResult(
    val assistant: Assistant,
    val newLorebooks: List<Lorebook> = emptyList(),  // 从内嵌世界书创建的新Lorebook
)

@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    onImport: (TavernImportResult) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SillyTavernImporter(onImport = onImport)
    }
}

@Composable
private fun SillyTavernImporter(
    onImport: (TavernImportResult) -> Unit
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var isLoading by remember { mutableStateOf(false) }

    val pngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFile(context, uri, onImport, filesManager, toaster, scope) { isLoading = it } }
    }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFile(context, uri, onImport, filesManager, toaster, scope) { isLoading = it } }
    }

    val unifiedCardPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFile(context, uri, onImport, filesManager, toaster, scope) { isLoading = it } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { unifiedCardPickerLauncher.launch(arrayOf("*/*")) },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(if (isLoading) stringResource(R.string.assistant_importer_importing)
                 else "一键导入角色卡 (.png / .json / .charx / .byaf)")
        }
        OutlinedButton(
            onClick = { pngPickerLauncher.launch(arrayOf("image/png")) },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(if (isLoading) stringResource(R.string.assistant_importer_importing)
                 else stringResource(R.string.assistant_importer_import_tavern_png))
        }
        OutlinedButton(
            onClick = { jsonPickerLauncher.launch(arrayOf("application/json")) },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(if (isLoading) stringResource(R.string.assistant_importer_importing)
                 else stringResource(R.string.assistant_importer_import_tavern_json))
        }
    }
}

private fun importFile(
    context: Context, uri: Uri,
    onImport: (TavernImportResult) -> Unit,
    filesManager: FilesManager, toaster: ToasterState,
    scope: kotlinx.coroutines.CoroutineScope,
    setLoading: (Boolean) -> Unit
) {
    setLoading(true)
    scope.launch {
        try {
            runCatching {
                importFromUri(context, uri, filesManager, onImport, toaster)
            }.onFailure { e ->
                e.printStackTrace()
                toaster.show(e.message ?: context.getString(R.string.assistant_importer_import_failed))
            }
        } finally { setLoading(false) }
    }
}

private suspend fun importFromUri(
    context: Context, uri: Uri, filesManager: FilesManager,
    onImport: (TavernImportResult) -> Unit, toaster: ToasterState
) {
    val (assistant, lorebooks) = parseCardFromUri(context, uri, filesManager)
    // [v240 W3] V3 角色卡 assets 里的内嵌立绘 → filesDir/sprites/<assistantId>/<label>.*
    // best-effort：失败不影响角色卡导入本身
    runCatching {
        SpriteRepository.syncFromCardAssets(
            context = context,
            assistantId = assistant.id,
            assets = assistant.tavernData?.assets.orEmpty(),
        )
    }
    toaster.show(context.getString(R.string.app_name, assistant.name))
    onImport(
        TavernImportResult(
            assistant = assistant,
            newLorebooks = lorebooks,
        )
    )
}

/**
 * 解析角色卡文件（PNG 内嵌元数据 / JSON）为 (Assistant, 内嵌世界书列表)。
 * 供单卡导入与「从 SillyTavern 备份全量导入」共用；mime 探测失败时按扩展名回退
 * （归档导入走 file:// URI，ContentResolver 无法解析其类型）。
 */
internal suspend fun parseCardFromUri(
    context: Context, uri: Uri, filesManager: FilesManager
): Pair<Assistant, List<Lorebook>> {
    val mime = withContext(Dispatchers.IO) { filesManager.getFileMimeType(uri) }
        ?: when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "png" -> "image/png"
            "json" -> "application/json"
            "charx" -> "application/x-charx"
            "byaf" -> "application/x-byaf"
            else -> null
        }
    return withContext(Dispatchers.IO) {
        when (mime) {
            "image/png" -> {
                val result = ImageUtils.getTavernCharacterMeta(context, uri)
                val (jsonString, backgroundStr, avatarUri) = result.map { base64Data ->
                    val json = String(Base64.decode(base64Data, Base64.DEFAULT))
                    // PNG本身既是背景源也是头像
                    val savedUris = filesManager.createChatFilesByContents(listOf(uri))
                    val bg = savedUris.first().toString()
                    val avatar = savedUris.first().toString()
                    Triple(json, bg, avatar)
                }.getOrElse { throw it }
                val json = Json.parseToJsonElement(jsonString).jsonObject
                parseCardJson(json, backgroundStr, avatarUri, context)
            }
            "application/json" -> {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()
                    .use { it?.readText() }
                    ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                val json = Json.parseToJsonElement(jsonString).jsonObject
                parseCardJson(json, null, null, context)
            }
            "application/x-charx", "application/zip" -> {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                parseCharxBytes(bytes, context)
            }
            "application/x-byaf" -> {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                parseByafBytes(bytes, context)
            }
            else -> error(context.getString(R.string.assistant_importer_unsupported_file_type, mime ?: "unknown"))
        }
    }
}

/** 本地化错误文案：无 Context（JVM 单测/纯解析路径）时回退中文默认串。 */
private fun importError(context: Context?, resId: Int, fallback: String, vararg args: Any): Nothing =
    error(context?.getString(resId, *args) ?: fallback)

/**
 * [v241] 角色卡 JSON → (Assistant, 内嵌世界书列表)：**纯解析入口**，不依赖 Android 资源。
 *
 * 与 [parseCardFromUri] 的关系：后者负责 PNG/JSON 读取（ContentResolver/Bitmap），
 * 解析本身收敛到这里，JVM 单测可以直接跑「真实卡 JSON → 导入结果」的全链路。
 * [context] 仅用于错误文案本地化，传 null 时使用中文默认文案。
 */
internal fun parseCardJson(
    json: JsonObject,
    background: String? = null,
    avatarUri: String? = null,
    context: Context? = null,
): Pair<Assistant, List<Lorebook>> {
    val spec = json["spec"]?.jsonPrimitiveOrNull?.contentOrNull
    return when (spec) {
        "chara_card_v2" -> parseV2Card(context, json, background, avatarUri)
        "chara_card_v3" -> parseV3Card(context, json, background, avatarUri)
        null -> {
            // 兼容旧版 TavernAI / V1 角色卡 JSON（无顶层 spec 字段，但顶层或 data 对象包含 name 与角色卡核心字段）
            val dataObj = json["data"]?.jsonObjectOrNull ?: json
            val hasName = !dataObj["name"]?.jsonPrimitiveOrNull?.contentOrNull.isNullOrBlank()
            val hasV1Fields = dataObj.containsKey("description") ||
                dataObj.containsKey("first_mes") ||
                dataObj.containsKey("personality") ||
                dataObj.containsKey("scenario") ||
                dataObj.containsKey("mes_example")
            if (hasName && hasV1Fields) {
                val upgraded = if (json["data"]?.jsonObjectOrNull != null) {
                    JsonObject(json + ("spec" to kotlinx.serialization.json.JsonPrimitive("chara_card_v2")))
                } else {
                    val baseMap = mutableMapOf<String, JsonElement>(
                        "spec" to kotlinx.serialization.json.JsonPrimitive("chara_card_v2"),
                        "spec_version" to kotlinx.serialization.json.JsonPrimitive("2.0"),
                        "data" to dataObj,
                    )
                    json["talkativeness"]?.let { baseMap["talkativeness"] = it }
                    json["fav"]?.let { baseMap["fav"] = it }
                    JsonObject(baseMap)
                }
                parseV2Card(context, upgraded, background, avatarUri)
            } else {
                importError(context, R.string.assistant_importer_missing_spec_field, "角色卡缺少 spec 字段")
            }
        }
        else -> importError(context, R.string.assistant_importer_unsupported_spec, "不支持的角色卡 spec：$spec", spec)
    }
}

// ==================== V2 Parser ====================

internal fun parseV2Card(context: Context?, json: JsonObject, background: String?, avatarUri: String?): Pair<Assistant, List<Lorebook>> {
    val data = json["data"]?.jsonObject
        ?: importError(context, R.string.assistant_importer_missing_data_field, "角色卡缺少 data 字段")
    val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: importError(context, R.string.assistant_importer_missing_name_field, "角色卡缺少 name 字段")

    // 官方兼容：V2 顶层 talkativeness/fav 归入 data.extensions（官方导入同样处理），
    // 原始 extensions 结构保持无损
    val extensionsObj = data["extensions"]?.jsonObjectOrNull?.let { JsonObject(it.toMap()) }
        ?: JsonObject(emptyMap())
    val mergedExtensions = extensionsObj.toMutableMap()
    (json["talkativeness"]?.jsonPrimitiveOrNull)?.let { mergedExtensions.putIfAbsent("talkativeness", it) }
    (json["fav"]?.jsonPrimitiveOrNull)?.let { mergedExtensions.putIfAbsent("fav", it) }
    val mergedExtensionsRaw = if (mergedExtensions.isEmpty()) "" else JsonObject(mergedExtensions).toString()

    val tavData = TavernCharacterData(
        spec = "chara_card_v2",
        specVersion = json["spec_version"]?.jsonPrimitive?.contentOrNull ?: "",
        name = name,
        description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        alternateGreetings = parseStringArray(data["alternate_greetings"]),
        mesExample = data["mes_example"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        systemPrompt = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        creator = data["creator"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        creatorNotes = data["creator_notes"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        characterVersion = data["character_version"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        tags = parseStringArray(data["tags"]),
        postHistoryInstructions = data["post_history_instructions"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        extensions = parseExtensions(if (mergedExtensions.isEmpty()) null else JsonObject(mergedExtensions)),
        extensionsRaw = mergedExtensionsRaw,
        depthPrompt = parseDepthPromptText(data["extensions"]?.jsonObject),
        depthPromptDepth = parseDepthPromptDepth(data["extensions"]?.jsonObject),
        depthPromptRole = parseDepthPromptRole(data["extensions"]?.jsonObject),
        embeddedBook = parseEmbeddedBook(data["character_book"]?.jsonObject),
        embeddedRegexScripts = parseCardRegexScripts(data["extensions"]?.jsonObject),
    )

    val systemPrompt = buildTavernSystemPrompt(tavData)
    val presetMessages = buildPresetMessages(tavData)
    val lorebooks = buildEmbeddedLorebooks(tavData)

    val assistant = Assistant(
        name = name,
        avatar = if (avatarUri != null) Avatar.Image(avatarUri) else Avatar.Dummy,
        useAssistantAvatar = avatarUri != null,
        systemPrompt = systemPrompt,
        presetMessages = presetMessages,
        background = background,
        tavernData = tavData,
        enableTools = false,
        toolPolicy = ToolPolicy.NONE,
        localTools = emptyList(),
        totalStepsLimit = 12,
    )
    ensureAssistantAvatarAlias(context, assistant.id, avatarUri)

    return materializeCardAssetsAndRewrites(context, assistant, lorebooks)
}

/**
 * 将 V3 角色卡 `data.assets` 中的内嵌图片（`data:image/...`）同步落盘到 `charx_assets/<id>/` 与 `sprites/<id>/`，
 * 并将角色卡各字段、世界书与内嵌正则中的 `embeded://` / `embedded://` / `ccdefault:` 路径重写为可拦截的本地 URL。
 */
internal fun materializeCardAssetsAndRewrites(
    context: Context?,
    assistant: Assistant,
    lorebooks: List<Lorebook>,
): Pair<Assistant, List<Lorebook>> {
    val tav = assistant.tavernData ?: return assistant to lorebooks
    if (tav.assets.isEmpty()) return assistant to lorebooks
    val uriRewriteMap = LinkedHashMap<String, String>()
    val charxBaseDir = context?.filesDir?.let { java.io.File(it, "charx_assets/${assistant.id}").apply { mkdirs() } }
    val spriteBaseDir = context?.filesDir?.let { java.io.File(it, "sprites/${assistant.id}").apply { mkdirs() } }

    for (asset in tav.assets) {
        val rawName = asset.name.trim()
        val safeStem = me.rerere.rikkahub.data.st.expressions.SpriteRepository.sanitizeAssetStem(
            rawName.substringAfterLast('/').substringBeforeLast('.').ifBlank { rawName }
        )
        val decoded = me.rerere.rikkahub.data.st.expressions.SpriteRepository.decodeDataUri(asset.uri)
        val ext = (decoded?.second ?: asset.ext.lowercase().takeIf { it.isNotBlank() } ?: "png").lowercase()
        val fileName = if (safeStem.isNotBlank()) "$safeStem.$ext" else continue
        if (decoded == null && !asset.uri.startsWith("data:", ignoreCase = true)) {
            val existingFile = charxBaseDir?.let { java.io.File(it, fileName) }
            if (existingFile == null || !existingFile.isFile) {
                continue
            }
        }
        if (decoded != null) {
            if (charxBaseDir != null) {
                runCatching { java.io.File(charxBaseDir, fileName).writeBytes(decoded.first) }
                if (asset.type.equals("icon", ignoreCase = true) || rawName.equals("icon", ignoreCase = true) || rawName.equals("main", ignoreCase = true)) {
                    runCatching { java.io.File(charxBaseDir, "${assistant.id}.png").writeBytes(decoded.first) }
                }
            }
            if (spriteBaseDir != null) {
                runCatching { java.io.File(spriteBaseDir, fileName).writeBytes(decoded.first) }
            }
        }
        val targetUrl = if (charxBaseDir != null) {
            "https://appassets.androidplatform.net/charx_assets/${assistant.id}/$fileName"
        } else {
            asset.uri
        }
        if (rawName.isNotBlank()) {
            uriRewriteMap["embeded://$rawName"] = targetUrl
            uriRewriteMap["embedded://$rawName"] = targetUrl
            uriRewriteMap["ccdefault:$rawName"] = targetUrl
            uriRewriteMap["embeded://$rawName.$ext"] = targetUrl
            uriRewriteMap["embedded://$rawName.$ext"] = targetUrl
            uriRewriteMap["ccdefault:$rawName.$ext"] = targetUrl
        }
    }
    if (uriRewriteMap.isEmpty()) return assistant to lorebooks
    fun rewriteText(text: String): String {
        if (!text.contains("embed", ignoreCase = true) && !text.contains("ccdefault:", ignoreCase = true)) return text
        var out = text
        for ((k, v) in uriRewriteMap.entries.sortedByDescending { it.key.length }) {
            out = out.replace(k, v, ignoreCase = true)
        }
        return out
    }
    val updatedTav = tav.copy(
        firstMessage = rewriteText(tav.firstMessage),
        alternateGreetings = tav.alternateGreetings.map(::rewriteText),
        groupOnlyGreetings = tav.groupOnlyGreetings.map(::rewriteText),
        description = rewriteText(tav.description),
        systemPrompt = rewriteText(tav.systemPrompt),
        mesExample = rewriteText(tav.mesExample),
        postHistoryInstructions = rewriteText(tav.postHistoryInstructions),
        creatorNotes = rewriteText(tav.creatorNotes),
        depthPrompt = rewriteText(tav.depthPrompt),
        embeddedBook = tav.embeddedBook?.let { eb ->
            eb.copy(entries = eb.entries.map { it.copy(content = rewriteText(it.content)) })
        },
        embeddedRegexScripts = tav.embeddedRegexScripts.map { rx ->
            rx.copy(replaceString = rewriteText(rx.replaceString))
        },
    )
    val updatedPresetMessages = assistant.presetMessages.map { msg ->
        msg.copy(
            parts = msg.parts.map { part ->
                if (part is me.rerere.ai.ui.UIMessagePart.Text) part.copy(text = rewriteText(part.text)) else part
            }
        )
    }
    val updatedLorebooks = lorebooks.map { lb ->
        lb.copy(entries = lb.entries.map { it.copy(content = rewriteText(it.content)) })
    }
    return assistant.copy(
        systemPrompt = rewriteText(assistant.systemPrompt),
        presetMessages = updatedPresetMessages,
        tavernData = updatedTav,
    ) to updatedLorebooks
}

// ==================== V3 Parser ====================

internal fun parseV3Card(context: Context?, json: JsonObject, background: String?, avatarUri: String?): Pair<Assistant, List<Lorebook>> {
    val data = json["data"]?.jsonObject
        ?: importError(context, R.string.assistant_importer_missing_data_field, "角色卡缺少 data 字段")
    val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: importError(context, R.string.assistant_importer_missing_name_field, "角色卡缺少 name 字段")

    val tavData = TavernCharacterData(
        spec = "chara_card_v3",
        specVersion = json["spec_version"]?.jsonPrimitive?.contentOrNull ?: "",
        name = name,
        description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        alternateGreetings = parseStringArray(data["alternate_greetings"]),
        mesExample = data["mes_example"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        systemPrompt = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        creator = data["creator"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        creatorNotes = data["creator_notes"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        characterVersion = data["character_version"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        tags = parseStringArray(data["tags"]),
        postHistoryInstructions = data["post_history_instructions"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        extensions = parseExtensions(data["extensions"]?.jsonObject),
        extensionsRaw = data["extensions"]?.toString() ?: "",
        assets = parseAssets(data["assets"]?.jsonArray),
        groupOnlyGreetings = parseStringArray(data["group_only_greetings"]),
        nickname = data["nickname"]?.jsonPrimitiveOrNull?.contentOrNull ?: "",
        creatorNotesMultilingual = data["creator_notes_multilingual"]?.toString() ?: "",
        source = parseStringArray(data["source"]),
        creationDate = data["creation_date"]?.toString() ?: "",
        modificationDate = data["modification_date"]?.toString() ?: "",
        depthPrompt = parseDepthPromptText(data["extensions"]?.jsonObject),
        depthPromptDepth = parseDepthPromptDepth(data["extensions"]?.jsonObject),
        depthPromptRole = parseDepthPromptRole(data["extensions"]?.jsonObject),
        embeddedBook = parseEmbeddedBook(data["character_book"]?.jsonObject),
        embeddedRegexScripts = parseCardRegexScripts(data["extensions"]?.jsonObject),
    )

    val systemPrompt = buildTavernSystemPrompt(tavData)
    val presetMessages = buildPresetMessages(tavData)
    val lorebooks = buildEmbeddedLorebooks(tavData)

    val assistant = Assistant(
        name = name,
        avatar = if (avatarUri != null) Avatar.Image(avatarUri) else Avatar.Dummy,
        useAssistantAvatar = avatarUri != null,
        systemPrompt = systemPrompt,
        presetMessages = presetMessages,
        background = background,
        tavernData = tavData,
        enableTools = false,
        toolPolicy = ToolPolicy.NONE,
        localTools = emptyList(),
        totalStepsLimit = 12,
    )
    ensureAssistantAvatarAlias(context, assistant.id, avatarUri)

    return materializeCardAssetsAndRewrites(context, assistant, lorebooks)
}

// ==================== Helpers ====================

private fun parseStringArray(element: kotlinx.serialization.json.JsonElement?): List<String> {
    if (element == null) return emptyList()
    return try {
        element.jsonArray.map { it.jsonPrimitive.contentOrNull ?: "" }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }
}

private fun parseExtensions(obj: JsonObject?): Map<String, String> {
    if (obj == null) return emptyMap()
    return obj.entries.associate { (k, v) -> k to (v.jsonPrimitiveOrNull?.contentOrNull ?: v.toString()) }
}

/** 官方深度提示（extensions.depth_prompt）解析 */
private fun parseDepthPrompt(obj: JsonObject?): JsonObject? {
    if (obj == null) return null
    return try {
        (obj["depth_prompt"] as? JsonObject) ?: runCatching {
            (obj["depth_prompt"]?.jsonPrimitiveOrNull?.contentOrNull?.let {
                kotlinx.serialization.json.Json.parseToJsonElement(it)
            } as? JsonObject)
        }.getOrNull()
    } catch (_: Exception) { null }
}

private fun parseDepthPromptText(obj: JsonObject?): String =
    parseDepthPrompt(obj)?.get("prompt")?.jsonPrimitiveOrNull?.contentOrNull ?: ""

private fun parseDepthPromptDepth(obj: JsonObject?): Int =
    parseDepthPrompt(obj)?.get("depth")?.jsonPrimitiveOrNull?.contentOrNull?.toIntOrNull() ?: 4

private fun parseDepthPromptRole(obj: JsonObject?): String =
    parseDepthPrompt(obj)?.get("role")?.jsonPrimitiveOrNull?.contentOrNull?.lowercase() ?: "system"

private fun parseAssets(arr: kotlinx.serialization.json.JsonArray?): List<TavernAsset> {
    if (arr == null) return emptyList()
    return arr.mapNotNull { el ->
        try {
            val obj = el.jsonObject
            TavernAsset(
                type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "",
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: "",
                ext = obj["ext"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        } catch (_: Exception) { null }
    }
}

internal fun parseEmbeddedBook(obj: JsonObject?): TavernEmbeddedBook? {
    if (obj == null) return null
    val entries = try {
        val entriesJson = obj["entries"]
        when {
            entriesJson == null -> emptyList()
            entriesJson is kotlinx.serialization.json.JsonArray -> parseEntriesArray(entriesJson)
            else -> parseEntriesMap(entriesJson.jsonObject)
        }
    } catch (_: Exception) { emptyList() }

    if (entries.isEmpty()) return null

    return TavernEmbeddedBook(
        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
        description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
        extensions = parseExtensions(obj["extensions"]?.jsonObject),
        extensionsRaw = obj["extensions"]?.toString() ?: "",
        entries = entries,
    )
}

private fun parseEntriesArray(arr: kotlinx.serialization.json.JsonArray): List<TavernBookEntry> {
    return arr.mapNotNull { el ->
        try {
            val e = el.jsonObject
            applyEntryExtensions(TavernBookEntry(
                id = e["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                keys = parseStringArray(e["keys"]) + parseStringArray(e["key"]),
                secondaryKeys = parseStringArray(e["secondary_keys"]),
                comment = e["comment"]?.jsonPrimitive?.contentOrNull ?: "",
                content = e["content"]?.jsonPrimitive?.contentOrNull ?: "",
                constant = e["constant"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                selective = e["selective"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                selectiveLogic = e["selectiveLogic"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                group = e["group"]?.jsonPrimitive?.contentOrNull ?: "",
                position = parseEntryPosition(e),
                priority = e["order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: e["insertion_order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: e["priority"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100,
                // [v241] ST 1.18 角色卡 character_book 的规范字段是 enabled：官方写侧
                // （src/endpoints/characters.js convertWorldInfoToCharacterBook）写 enabled = !disable；
                // 读侧（world-info.js convertCharacterBook）读 enabled → disable = !enabled。
                // 旧导出/本应用 v240 之前写的是 disable —— 两者都兼容：enabled 优先，其次 disable，
                // 都没有则默认启用（V2 spec 的 disable 默认值）。
                disable = e["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { !it }
                    ?: e["disable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: false,
                caseSensitive = e["caseSensitive"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                useRegex = e["useRegex"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: e["use_regex"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                probability = e["probability"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100,
                sticky = parseStickyInt(e["sticky"]),
                cooldown = e["cooldown"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                depth = e["depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 4,
                // 官方语义：条目未写 scan_depth 时为 null → 注入时用全局默认（world_info_depth=2）
                scanDepth = e["scan_depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                role = parseEntryRole(e),
                groupWeight = e["group_weight"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100,
                groupOverride = e["group_override"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                delay = e["delay"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                useProbability = e["useProbability"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: e["use_probability"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                extensionsRaw = e["extensions"]?.toString() ?: "",
            ), e)
        } catch (_: Exception) { null }
    }
}

private fun parseEntriesMap(obj: JsonObject): List<TavernBookEntry> {
    return obj.entries.mapNotNull { (idStr, el) ->
        try {
            val e = el.jsonObject
            applyEntryExtensions(TavernBookEntry(
                id = idStr.toIntOrNull() ?: 0,
                keys = parseStringArray(e["keys"]) + parseStringArray(e["key"]),
                secondaryKeys = parseStringArray(e["secondary_keys"]),
                comment = e["comment"]?.jsonPrimitive?.contentOrNull ?: "",
                content = e["content"]?.jsonPrimitive?.contentOrNull ?: "",
                constant = e["constant"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                selective = e["selective"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                selectiveLogic = e["selectiveLogic"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                group = e["group"]?.jsonPrimitive?.contentOrNull ?: "",
                position = parseEntryPosition(e),
                priority = e["order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: e["insertion_order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: e["priority"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100,
                // [v241] ST 1.18 角色卡 character_book 的规范字段是 enabled：官方写侧
                // （src/endpoints/characters.js convertWorldInfoToCharacterBook）写 enabled = !disable；
                // 读侧（world-info.js convertCharacterBook）读 enabled → disable = !enabled。
                // 旧导出/本应用 v240 之前写的是 disable —— 两者都兼容：enabled 优先，其次 disable，
                // 都没有则默认启用（V2 spec 的 disable 默认值）。
                disable = e["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()?.let { !it }
                    ?: e["disable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: false,
                caseSensitive = e["caseSensitive"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                useRegex = e["useRegex"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: e["use_regex"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                probability = e["probability"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100,
                sticky = parseStickyInt(e["sticky"]),
                cooldown = e["cooldown"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                depth = e["depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 4,
                // 官方语义：条目未写 scan_depth 时为 null → 注入时用全局默认（world_info_depth=2）
                scanDepth = e["scan_depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                role = parseEntryRole(e),
                groupWeight = e["group_weight"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 100,
                groupOverride = e["group_override"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                delay = e["delay"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                useProbability = e["useProbability"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: e["use_probability"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                extensionsRaw = e["extensions"]?.toString() ?: "",
            ), e)
        } catch (_: Exception) { null }
    }
}

/**
 * 应用酒馆条目 extensions 里的新字段（整词匹配/递归控制/概率/权重等）。
 * 旧版顶层字段优先保留，只有 extensions 显式提供时才覆盖。
 */
private fun applyEntryExtensions(entry: TavernBookEntry, e: JsonObject?): TavernBookEntry {
    if (e == null) return entry
    val extensions = e["extensions"] as? JsonObject
    if (extensions == null) return entry
    return try {
        entry.copy(
            matchWholeWords = extensions["match_whole_words"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: entry.matchWholeWords,
            preventRecursion = extBool(extensions["prevent_recursion"]) || entry.preventRecursion,
            // 官方 delay_until_recursion 可为 true 或数字层级（1/2/3…），extensions 优先，顶层兜底
            delayUntilRecursion = parseDelayUntilRecursionInt(extensions["delay_until_recursion"])
                ?: parseDelayUntilRecursionInt(e["delayUntilRecursion"])
                ?: entry.delayUntilRecursion,
            excludeRecursion = extensions["exclude_recursion"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: entry.excludeRecursion,
            caseSensitive = extensions["case_sensitive"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: entry.caseSensitive,
            selectiveLogic = extensions["selectiveLogic"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: entry.selectiveLogic,
            probability = extensions["probability"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.probability,
            useProbability = extensions["useProbability"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: extensions["use_probability"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                // 旧卡只写 probability 没写开关时，按“有概率值即启用”兜底
                ?: (extensions["probability"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != null || entry.useProbability),
            sticky = extensions["sticky"]?.let { parseStickyInt(it) }?.takeIf { it > 0 } ?: entry.sticky,
            cooldown = extensions["cooldown"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.cooldown,
            delay = extensions["delay"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.delay,
            scanDepth = extensions["scan_depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.scanDepth,
            priority = extensions["order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.priority,
            position = extensions["position"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.position,
            depth = extensions["depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.depth,
            group = extensions["group"]?.jsonPrimitive?.contentOrNull ?: entry.group,
            groupWeight = extensions["group_weight"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.groupWeight,
            groupOverride = extensions["group_priority"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: entry.groupOverride,
            inclusionGroup = (extensions?.let { parseInclusionGroup(it["inclusion_group"]) } ?: "")
                .ifBlank { parseInclusionGroup(e["inclusion_group"]) },
            useGroupScoring = extensions?.get("use_group_scoring")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["use_group_scoring"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.useGroupScoring,
            groupPriority = extensions?.get("group_priority")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["group_priority"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.groupPriority,
            automationId = extensions?.get("automation_id")?.jsonPrimitive?.contentOrNull
                ?: e["automation_id"]?.jsonPrimitive?.contentOrNull ?: entry.automationId,
            displayIndex = extensions?.get("display_index")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: e["display_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.displayIndex,
            displayPosition = extensions?.get("display_position")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: e["display_position"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: entry.displayPosition,
            triggers = (extensions?.let { parseStringArray(it["triggers"]) }.orEmpty() +
                parseStringArray(e["triggers"])).distinct(),
            matchPersonaDescription = extensions?.get("match_persona_description")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["match_persona_description"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.matchPersonaDescription,
            matchCharacterDescription = extensions?.get("match_character_description")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["match_character_description"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.matchCharacterDescription,
            matchCharacterPersonality = extensions?.get("match_character_personality")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["match_character_personality"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.matchCharacterPersonality,
            matchCharacterDepthPrompt = extensions?.get("match_character_depth_prompt")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["match_character_depth_prompt"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.matchCharacterDepthPrompt,
            matchScenario = extensions?.get("match_scenario")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["match_scenario"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.matchScenario,
            matchCreatorNotes = extensions?.get("match_creator_notes")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["match_creator_notes"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.matchCreatorNotes,
            ignoreBudget = extensions?.get("ignore_budget")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["ignore_budget"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: entry.ignoreBudget,
            // 官方 extensions.outlet_name：outlet 条目出口名
            outletName = extensions["outlet_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: entry.outletName,
            characterFilterNames = parseCharacterFilterField(extensions["character_filter"] ?: e["characterFilter"], "names")
                .ifEmpty { entry.characterFilterNames },
            characterFilterTags = parseCharacterFilterField(extensions["character_filter"] ?: e["characterFilter"], "tags")
                .ifEmpty { entry.characterFilterTags },
            characterFilterIsExclude = parseCharacterFilterExclude(extensions["character_filter"] ?: e["characterFilter"])
                ?: entry.characterFilterIsExclude,
            vectorized = extensions["vectorized"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["vectorized"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: entry.vectorized,
            addMemo = extensions["add_memo"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: e["addMemo"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: entry.addMemo,
        )
    } catch (_: Exception) { entry }
}

/** inclusion_group 可为逗号分隔字符串或数组，统一转逗号分隔字符串 */
private fun parseInclusionGroup(element: JsonElement?): String = when {
    element == null -> ""
    element is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }.joinToString(",")
    else -> element.jsonPrimitive?.contentOrNull ?: ""
}

/** extensions 里的递归控制可为布尔、数字（延迟层级）或 uid 数组，统一转布尔 */
private fun extBool(element: JsonElement?): Boolean = when {
    element == null -> false
    element is JsonArray -> element.isNotEmpty()
    else -> try {
        element.jsonPrimitive.contentOrNull?.let { str ->
            str.toBooleanStrictOrNull() ?: (str.toIntOrNull()?.let { it > 0 } ?: false)
        } ?: false
    } catch (_: Exception) { false }
}

/**
 * 官方 delay_until_recursion：true=层级1，数字=层级 N，字符串同理解析。
 * 返回 null 表示字段不存在/无法解析。
 */
internal fun parseDelayUntilRecursionInt(element: JsonElement?): Int? {
    if (element == null) return null
    val content = try {
        element.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        return null
    } ?: return null
    content.toBooleanStrictOrNull()?.let { return if (it) 1 else 0 }
    return content.toIntOrNull()
}

/**
 * 解析 role 字段：酒馆 JSON 可能用 0/1/2 数字或 "system"/"user"/"assistant" 字符串
 */
private fun parseRoleString(element: kotlinx.serialization.json.JsonElement?): String {
    if (element == null) return "system"
    return try {
        element.jsonPrimitive.contentOrNull?.let { str ->
            when (str.lowercase()) {
                "system" -> "system"
                "user" -> "user"
                "assistant" -> "assistant"
                "0" -> "system"
                "1" -> "user"
                "2" -> "assistant"
                else -> "system"
            }
        } ?: when (element.jsonPrimitive.content.toIntOrNull()) {
            0 -> "system"
            1 -> "user"
            2 -> "assistant"
            else -> "system"
        }
    } catch (_: Exception) { "system" }
}

/**
 * 解析世界书条目 position（官方 V2 spec 字符串 + 新版 extensions.position 数字优先）：
 * 0=before_char 1=after_char 2=ANTop 3=ANBottom 4=atDepth 5=EMTop 6=EMBottom 7=outlet
 */
private fun parseEntryPosition(e: JsonObject): Int {
    val base = e["position"]?.jsonPrimitiveOrNull?.contentOrNull?.let { raw ->
        when (raw.trim().lowercase()) {
            "before_char", "before" -> 0
            "after_char", "after" -> 1
            "antop" -> 2
            "anbottom" -> 3
            "at_depth", "atdepth" -> 4
            "emtop" -> 5
            "embottom" -> 6
            "outlet" -> 7
            else -> raw.trim().toIntOrNull()
        }
    } ?: 1
    // 官方新版 UI：扩展位置数字存 extensions.position，优先于旧字段
    return e["extensions"]?.jsonObjectOrNull
        ?.get("position")?.jsonPrimitiveOrNull?.contentOrNull?.trim()?.toIntOrNull()
        ?: base
}

/** 解析世界书条目 role（官方 extensions.role 数字/字符串优先） */
private fun parseEntryRole(e: JsonObject): String {
    val fromExtensions = e["extensions"]?.jsonObjectOrNull?.get("role")
    return parseRoleString(fromExtensions ?: e["role"])
}

/** 解析 sticky：兼容数字和布尔值（酒馆旧格式） */
private fun parseStickyInt(element: kotlinx.serialization.json.JsonElement?): Int {
    if (element == null) return 0
    return try {
        element.jsonPrimitive.contentOrNull?.let { str ->
            str.toIntOrNull() ?: if (str.toBooleanStrictOrNull() == true) 1 else 0
        } ?: 0
    } catch (_: Exception) { 0 }
}

/**
 * 将内嵌世界书条目转为Rikkahub的RegexInjection
 */
private fun tavernEntryToInjection(entry: TavernBookEntry): PromptInjection.RegexInjection {
    return PromptInjection.RegexInjection(
        id = Uuid.random(),
        name = entry.comment.ifEmpty { entry.keys.firstOrNull() ?: "Entry ${entry.id}" },
        enabled = !entry.disable,
        priority = entry.priority,
        position = mapTavernPosition(entry.position),
        injectDepth = entry.depth,
        content = entry.content,
        role = mapTavernRole(entry.role),
        keywords = entry.keys,
        secondaryKeys = entry.secondaryKeys,
        useRegex = entry.useRegex,
        caseSensitive = entry.caseSensitive,
        matchWholeWords = entry.matchWholeWords,
        excludeRecursion = entry.excludeRecursion,
        preventRecursion = entry.preventRecursion,
        delayUntilRecursion = entry.delayUntilRecursion,
        scanDepth = entry.scanDepth,
        constantActive = entry.constant,
        selective = entry.selective,
        selectiveLogic = mapSelectiveLogic(entry.selectiveLogic),
        group = entry.group,
        probability = entry.probability,
        sticky = entry.sticky,
        cooldown = entry.cooldown,
        delay = entry.delay,
        groupWeight = entry.groupWeight,
        groupOverride = entry.groupOverride,
        useProbability = entry.useProbability,
        inclusionGroup = entry.inclusionGroup,
        useGroupScoring = entry.useGroupScoring,
        groupPriority = entry.groupPriority,
        automationId = entry.automationId,
        displayIndex = entry.displayIndex,
        displayPosition = entry.displayPosition,
        triggers = entry.triggers,
        matchPersonaDescription = entry.matchPersonaDescription,
        matchCharacterDescription = entry.matchCharacterDescription,
        matchCharacterPersonality = entry.matchCharacterPersonality,
        matchCharacterDepthPrompt = entry.matchCharacterDepthPrompt,
        matchScenario = entry.matchScenario,
        matchCreatorNotes = entry.matchCreatorNotes,
        ignoreBudget = entry.ignoreBudget,
        outletName = entry.outletName,
        characterFilterNames = entry.characterFilterNames,
        characterFilterTags = entry.characterFilterTags,
        characterFilterIsExclude = entry.characterFilterIsExclude,
        vectorized = entry.vectorized,
        addMemo = entry.addMemo,
    )
}

internal fun mapTavernPosition(pos: Int): InjectionPosition = when (pos) {
    0 -> InjectionPosition.BEFORE_CHARACTER    // 官方 before_char：主提示之后、角色卡之前
    1 -> InjectionPosition.AFTER_CHARACTER     // 官方 after_char：角色卡之后
    2 -> InjectionPosition.BEFORE_AUTHOR_NOTE // 官方 ANTop（作者备注上方）
    3 -> InjectionPosition.AFTER_AUTHOR_NOTE  // 官方 ANBottom（作者备注下方）
    4 -> InjectionPosition.AT_DEPTH
    5 -> InjectionPosition.EM_TOP             // 官方 EMTop：示例消息之前
    6 -> InjectionPosition.EM_BOTTOM          // 官方 EMBottom：示例消息之后
    7 -> InjectionPosition.OUTLET             // 官方 outlet：不直接注入，经 {{outlet::name}} 宏展开
    else -> InjectionPosition.AFTER_CHARACTER
}

internal fun mapTavernRole(role: String): me.rerere.ai.core.MessageRole = when (role.lowercase()) {
    "user", "1" -> me.rerere.ai.core.MessageRole.USER
    "assistant", "2" -> me.rerere.ai.core.MessageRole.ASSISTANT
    else -> me.rerere.ai.core.MessageRole.SYSTEM   // 官方世界书/深度提示默认 system
}

/**
 * 外置世界书 → 内嵌世界书同步：
 * 编辑外置世界书后，把绑定该世界书的角色卡内嵌世界书条目一并更新（最后修改生效）
 */
internal fun syncExternalToEmbedded(
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    lorebooks: List<me.rerere.rikkahub.data.model.Lorebook>,
): List<me.rerere.rikkahub.data.model.Assistant> {
    return assistants.map { assistant ->
        val tav = assistant.tavernData ?: return@map assistant
        val book = tav.embeddedBook ?: return@map assistant
        val boundBook = lorebooks.firstOrNull { lb -> lb.id in assistant.lorebookIds }
            ?: return@map assistant
        val newEntries = boundBook.entries.map { injection ->
            // 按内容+触发词匹配已有内嵌条目作为模板，避免条目增删/排序后按位置错位；
            // 匹配不到（新增条目）用全新模板，保留各自独立 id
            val template = book.entries.firstOrNull { e ->
                e.content == injection.content && e.keys == injection.keywords
            } ?: TavernBookEntry()
            injectionToTavernEntry(injection, template)
        }
        assistant.copy(
            tavernData = tav.copy(
                embeddedBook = book.copy(
                    entries = newEntries,
                )
            )
        )
    }
}

/** 映射酒馆 selectiveLogic Int 到 SelectiveLogic 枚举（官方 world_info_logic：0=AND_ANY 1=NOT_ALL 2=NOT_ANY 3=AND_ALL） */
internal fun mapSelectiveLogic(logic: Int): SelectiveLogic = when (logic) {
    0 -> SelectiveLogic.AND_ANY
    1 -> SelectiveLogic.NOT_ALL
    2 -> SelectiveLogic.NOT_ANY
    3 -> SelectiveLogic.AND_ALL
    else -> SelectiveLogic.AND_ANY
}

/** 反向转换：RegexInjection → TavernBookEntry（用于外置世界书→内嵌同步） */
internal fun injectionToTavernEntry(
    injection: PromptInjection.RegexInjection,
    template: TavernBookEntry,
): TavernBookEntry {
    return template.copy(
        keys = injection.keywords,
        secondaryKeys = injection.secondaryKeys,
        content = injection.content,
        comment = injection.name,
        constant = injection.constantActive,
        selective = injection.selective,
        selectiveLogic = when (injection.selectiveLogic) {
            SelectiveLogic.AND_ANY -> 0
            SelectiveLogic.NOT_ALL -> 1
            SelectiveLogic.NOT_ANY -> 2
            SelectiveLogic.AND_ALL -> 3
            // 官方无 OR_ANY；本地遗留条目导出时按最接近的 AND_ANY 处理
            SelectiveLogic.OR_ANY -> 0
        },
        group = injection.group,
        position = mapInjectionToPosition(injection.position),
        priority = injection.priority,
        disable = !injection.enabled,
        caseSensitive = injection.caseSensitive,
        matchWholeWords = injection.matchWholeWords,
        excludeRecursion = injection.excludeRecursion,
        preventRecursion = injection.preventRecursion,
        delayUntilRecursion = injection.delayUntilRecursion,
        useRegex = injection.useRegex,
        probability = injection.probability,
        sticky = injection.sticky,
        cooldown = injection.cooldown,
        delay = injection.delay,
        depth = injection.injectDepth,
        scanDepth = injection.scanDepth,
        role = when (injection.role) {
            me.rerere.ai.core.MessageRole.USER -> "user"
            me.rerere.ai.core.MessageRole.ASSISTANT -> "assistant"
            else -> "system"
        },
        groupWeight = injection.groupWeight,
        groupOverride = injection.groupOverride,
        useProbability = injection.useProbability,
        inclusionGroup = injection.inclusionGroup,
        useGroupScoring = injection.useGroupScoring,
        groupPriority = injection.groupPriority,
        automationId = injection.automationId,
        displayIndex = injection.displayIndex,
        displayPosition = injection.displayPosition,
        triggers = injection.triggers,
        matchPersonaDescription = injection.matchPersonaDescription,
        matchCharacterDescription = injection.matchCharacterDescription,
        matchCharacterPersonality = injection.matchCharacterPersonality,
        matchCharacterDepthPrompt = injection.matchCharacterDepthPrompt,
        matchScenario = injection.matchScenario,
        matchCreatorNotes = injection.matchCreatorNotes,
        ignoreBudget = injection.ignoreBudget,
        outletName = injection.outletName,
    )
}

/** 反向映射 InjectionPosition → 酒馆 position 数字 */
private fun mapInjectionToPosition(pos: InjectionPosition): Int = when (pos) {
    InjectionPosition.BEFORE_CHARACTER -> 0
    InjectionPosition.AFTER_CHARACTER -> 1
    InjectionPosition.BEFORE_SYSTEM_PROMPT -> 0
    InjectionPosition.AFTER_SYSTEM_PROMPT -> 1
    InjectionPosition.TOP_OF_CHAT -> 2
    InjectionPosition.BOTTOM_OF_CHAT -> 3
    InjectionPosition.AT_DEPTH -> 4
    InjectionPosition.AUTHOR_NOTE, InjectionPosition.BEFORE_AUTHOR_NOTE -> 2
    InjectionPosition.AFTER_AUTHOR_NOTE -> 3
    // 本地扩展位置写入官方枚举时落到最接近的出口（outlet=7），避免产生官方不存在的 8
    InjectionPosition.ANTAGONIZE, InjectionPosition.AFTER_DIALOG -> 7
    InjectionPosition.EM_TOP -> 5
    InjectionPosition.EM_BOTTOM -> 6
    InjectionPosition.OUTLET -> 7
}

/**
 * 从内嵌世界书 + PHI + creator_notes 构建 Lorebook 列表
 */
private fun buildEmbeddedLorebooks(tavData: TavernCharacterData): List<Lorebook> {
    val entries = mutableListOf<PromptInjection.RegexInjection>()

    // 内嵌世界书条目
    tavData.embeddedBook?.let { book ->
        entries.addAll(book.entries.map { tavernEntryToInjection(it) })
    }

    // PHI（post_history_instructions）→ 官方行为：聊天历史末尾之后追加（user 消息）
    if (tavData.postHistoryInstructions.isNotBlank()) {
        entries.add(PromptInjection.RegexInjection(
            id = Uuid.random(),
            name = "历史后续指令",
            enabled = true,
            priority = 0,
            position = InjectionPosition.AFTER_DIALOG,
            content = tavData.postHistoryInstructions,
            constantActive = true,
        ))
    }

    // 官方深度提示（extensions.depth_prompt）→ 按深度/角色注入对话（默认深度4、system）
    if (tavData.depthPrompt.isNotBlank()) {
        entries.add(PromptInjection.RegexInjection(
            id = Uuid.random(),
            name = "深度提示",
            enabled = true,
            priority = 0,
            position = InjectionPosition.AT_DEPTH,
            injectDepth = tavData.depthPromptDepth,
            content = tavData.depthPrompt,
            constantActive = true,
            role = mapTavernRole(tavData.depthPromptRole),
        ))
    }

    if (entries.isEmpty()) return emptyList()

    return listOf(
        Lorebook(
            id = Uuid.random(),
            name = tavData.embeddedBook?.name?.ifEmpty { "${tavData.name}的世界书" } ?: "${tavData.name}的世界书",
            description = tavData.embeddedBook?.description ?: "",
            isCharacterBook = true,
            enabled = true,
            entries = entries,
        )
    )
}

/**
 * 构建 system prompt — 只使用卡片原始的 system_prompt 字段，不拍平其他字段
 * 其他字段（description/personality/scenario/mes_example）由上下文模板展开
 * phi/creator_notes 由独立的注入系统处理
 */
private fun buildTavernSystemPrompt(d: TavernCharacterData): String {
    return d.systemPrompt.ifBlank { "" }
}

/**
 * 构建 presetMessages — 只使用 first_mes，alternate_greetings 由角色卡 UI 选择
 * 用户在角色卡页面点「使用此开场白」时替换 presetMessages
 */
private fun buildPresetMessages(d: TavernCharacterData): List<UIMessage> {
    val messages = mutableListOf<UIMessage>()
    if (d.firstMessage.isNotBlank()) {
        messages.add(UIMessage.assistant(d.firstMessage))
    }
    return messages
}


private fun parseCharacterFilterField(el: JsonElement?, key: String): List<String> {
    val obj = el as? JsonObject ?: return emptyList()
    return parseStringArray(obj[key])
}

private fun parseCharacterFilterExclude(el: JsonElement?): Boolean? {
    val obj = el as? JsonObject ?: return null
    return (obj["isExclude"] ?: obj["is_exclude"])?.jsonPrimitiveOrNull?.contentOrNull?.toBooleanStrictOrNull()
}

/**
 * 解析 CCv3 `.charx` ZIP 角色卡（对齐官方 `src/charx.js`）：
 * - 提取归档根目录的 `card.json`；
 * - 提取 `assets/` 下的内嵌资源（如图标、表情立绘、背景等），将 `embeded://` 或相对路径映射为 `TavernAsset`。
 */
internal fun parseCharxBytes(
    bytes: ByteArray,
    context: Context? = null,
): Pair<Assistant, List<Lorebook>> {
    var cardJsonStr: String? = null
    val zipAssets = mutableMapOf<String, ByteArray>()
    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val normName = entry.name.replace('\\', '/').trimStart('/')
                val entryBytes = zis.readBytes()
                if (normName.equals("card.json", ignoreCase = true) || normName.endsWith("/card.json", ignoreCase = true)) {
                    cardJsonStr = entryBytes.toString(Charsets.UTF_8)
                } else {
                    zipAssets[normName] = entryBytes
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    val rawCard = cardJsonStr ?: importError(context, R.string.assistant_importer_read_json_failed, ".charx 归档中未找到 card.json")
    val jsonObj = Json.parseToJsonElement(rawCard).jsonObject
    val (assistant, lorebooks) = parseCardJson(jsonObj, null, null, context)
    if (zipAssets.isEmpty()) return assistant to lorebooks
    val existingAssets = assistant.tavernData?.assets.orEmpty()
    val enrichedAssets = if (existingAssets.isNotEmpty()) {
        existingAssets.map { asset ->
            val cleanUri = asset.uri.removePrefix("embeded://").removePrefix("embedded://").removePrefix("ccdefault:").trimStart('/')
            val decodedUri = runCatching { android.net.Uri.decode(cleanUri) }.getOrDefault(cleanUri)
            val cleanDirAndStem = cleanUri.substringBeforeLast('.', cleanUri)
            val matchedEntry = zipAssets.entries.firstOrNull { (k, _) ->
                k.equals(cleanUri, ignoreCase = true) || k.equals(decodedUri, ignoreCase = true)
            } ?: zipAssets.entries.firstOrNull { (k, _) ->
                cleanDirAndStem.isNotBlank() && k.substringBeforeLast('.', k).equals(cleanDirAndStem, ignoreCase = true)
            } ?: zipAssets.entries.firstOrNull { (k, _) ->
                asset.name.isNotBlank() && k.substringAfterLast('/').substringBeforeLast('.').equals(asset.name.substringBeforeLast('.'), ignoreCase = true)
            } ?: zipAssets.entries.firstOrNull { (k, _) ->
                cleanUri.isNotBlank() && k.substringAfterLast('/').equals(cleanUri.substringAfterLast('/'), ignoreCase = true)
            }
            val matchedBytes = matchedEntry?.value
            if (matchedBytes != null) {
                val b64 = java.util.Base64.getEncoder().encodeToString(matchedBytes)
                val ext = asset.ext.ifBlank { cleanUri.substringAfterLast('.', "png") }
                asset.copy(uri = "data:image/$ext;base64,$b64")
            } else {
                asset
            }
        }
    } else {
        zipAssets.entries.map { (path, data) ->
            val ext = path.substringAfterLast('.', "png").lowercase()
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            val type = when {
                path.contains("emotion", ignoreCase = true) || path.contains("sprite", ignoreCase = true) -> "emotion"
                path.contains("icon", ignoreCase = true) -> "icon"
                else -> "image"
            }
            val b64 = java.util.Base64.getEncoder().encodeToString(data)
            TavernAsset(type = type, name = name, uri = "data:image/$ext;base64,$b64", ext = ext)
        }
    }
    val uriRewriteMap = LinkedHashMap<String, String>()
    val charxBaseDir = context?.filesDir?.let { java.io.File(it, "charx_assets/${assistant.id}").apply { mkdirs() } }
    val spriteBaseDir = context?.filesDir?.let { java.io.File(it, "sprites/${assistant.id}").apply { mkdirs() } }
    zipAssets.forEach { (rawPath, assetBytes) ->
        val cleanRel = rawPath.replace('\\', '/').trimStart('/')
        val ext = cleanRel.substringAfterLast('.', "png").lowercase()
        val baseName = cleanRel.substringAfterLast('/')
        val targetUrl = if (charxBaseDir != null) {
            val outFile = java.io.File(charxBaseDir, cleanRel)
            val canonicalBase = charxBaseDir.canonicalPath
            val canonicalOut = outFile.canonicalFile
            if (canonicalOut.path.startsWith(canonicalBase)) {
                canonicalOut.parentFile?.mkdirs()
                runCatching { canonicalOut.writeBytes(assetBytes) }
            }
            val isUniqueBaseName = zipAssets.keys.count { it.substringAfterLast('/').equals(baseName, ignoreCase = true) } == 1
            if (baseName.isNotBlank() && baseName != cleanRel && isUniqueBaseName) {
                runCatching { java.io.File(charxBaseDir, baseName).writeBytes(assetBytes) }
            }
            if (spriteBaseDir != null && baseName.isNotBlank()) {
                runCatching { java.io.File(spriteBaseDir, baseName).writeBytes(assetBytes) }
            }
            "https://appassets.androidplatform.net/charx_assets/${assistant.id}/$cleanRel"
        } else {
            val b64 = java.util.Base64.getEncoder().encodeToString(assetBytes)
            "data:image/$ext;base64,$b64"
        }
        uriRewriteMap["embeded://$cleanRel"] = targetUrl
        uriRewriteMap["embedded://$cleanRel"] = targetUrl
        uriRewriteMap["ccdefault:$cleanRel"] = targetUrl
        if (baseName.isNotBlank()) {
            uriRewriteMap["embeded://$baseName"] = targetUrl
            uriRewriteMap["embedded://$baseName"] = targetUrl
            uriRewriteMap["ccdefault:$baseName"] = targetUrl
        }
    }
    // 同时按 card.json data.assets 的 name 建立别名落盘与重写（解决正则按 asset.name 如 `织乃_zcw_2` 引用但 zip 内路径为 assets/other/image/1.png 的问题）
    existingAssets.forEach { asset ->
        val cleanUri = asset.uri.removePrefix("embeded://").removePrefix("embedded://").removePrefix("ccdefault:").trimStart('/')
        val decodedUri = runCatching { android.net.Uri.decode(cleanUri) }.getOrDefault(cleanUri)
        val cleanDirAndStem = cleanUri.substringBeforeLast('.', cleanUri)
        val matchedEntry = zipAssets.entries.firstOrNull { (k, _) ->
            k.equals(cleanUri, ignoreCase = true) || k.equals(decodedUri, ignoreCase = true)
        } ?: zipAssets.entries.firstOrNull { (k, _) ->
            cleanDirAndStem.isNotBlank() && k.substringBeforeLast('.', k).equals(cleanDirAndStem, ignoreCase = true)
        } ?: zipAssets.entries.firstOrNull { (k, _) ->
            asset.name.isNotBlank() && k.substringAfterLast('/').substringBeforeLast('.').equals(asset.name.substringBeforeLast('.'), ignoreCase = true)
        }
        val matchedBytes = matchedEntry?.value
        val rawName = asset.name.trim()
        val safeStem = me.rerere.rikkahub.data.st.expressions.SpriteRepository.sanitizeAssetStem(
            rawName.substringAfterLast('/').substringBeforeLast('.').ifBlank { rawName }
        )
        val ext = (matchedEntry?.key?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: asset.ext.ifBlank { cleanUri.substringAfterLast('.', "png") }).lowercase()
        if (matchedBytes != null && safeStem.isNotBlank()) {
            val aliasFileName = "$safeStem.$ext"
            if (charxBaseDir != null) {
                runCatching { java.io.File(charxBaseDir, aliasFileName).writeBytes(matchedBytes) }
            }
            if (spriteBaseDir != null) {
                runCatching { java.io.File(spriteBaseDir, aliasFileName).writeBytes(matchedBytes) }
            }
            val aliasUrl = if (charxBaseDir != null) {
                "https://appassets.androidplatform.net/charx_assets/${assistant.id}/$aliasFileName"
            } else {
                val b64 = java.util.Base64.getEncoder().encodeToString(matchedBytes)
                "data:image/$ext;base64,$b64"
            }
            uriRewriteMap["embeded://$rawName"] = aliasUrl
            uriRewriteMap["embedded://$rawName"] = aliasUrl
            uriRewriteMap["ccdefault:$rawName"] = aliasUrl
            uriRewriteMap["embeded://$rawName.$ext"] = aliasUrl
            uriRewriteMap["embedded://$rawName.$ext"] = aliasUrl
            uriRewriteMap["ccdefault:$rawName.$ext"] = aliasUrl
        }
    }
    fun rewriteCharxText(text: String): String {
        if (uriRewriteMap.isEmpty() || (!text.contains("embed", ignoreCase = true) && !text.contains("ccdefault:", ignoreCase = true))) {
            return text
        }
        var out = text
        for ((k, v) in uriRewriteMap.entries.sortedByDescending { it.key.length }) {
            out = out.replace(k, v, ignoreCase = true)
        }
        return out
    }
    val oldTav = assistant.tavernData
    val updatedTav = oldTav?.copy(
        firstMessage = rewriteCharxText(oldTav.firstMessage),
        alternateGreetings = oldTav.alternateGreetings.map(::rewriteCharxText),
        groupOnlyGreetings = oldTav.groupOnlyGreetings.map(::rewriteCharxText),
        description = rewriteCharxText(oldTav.description),
        systemPrompt = rewriteCharxText(oldTav.systemPrompt),
        assets = enrichedAssets,
        embeddedBook = oldTav.embeddedBook?.let { eb ->
            eb.copy(entries = eb.entries.map { it.copy(content = rewriteCharxText(it.content)) })
        },
        embeddedRegexScripts = oldTav.embeddedRegexScripts.map { rx ->
            rx.copy(replaceString = rewriteCharxText(rx.replaceString))
        },
    )
    val updatedPresetMessages = assistant.presetMessages.map { msg ->
        msg.copy(
            parts = msg.parts.map { part ->
                if (part is me.rerere.ai.ui.UIMessagePart.Text) part.copy(text = rewriteCharxText(part.text)) else part
            }
        )
    }
    val updatedLorebooks = lorebooks.map { lb ->
        lb.copy(entries = lb.entries.map { it.copy(content = rewriteCharxText(it.content)) })
    }
    val iconEntryBytes = zipAssets.entries.firstOrNull { (k, _) ->
        k.contains("icon", ignoreCase = true) || k.substringAfterLast('/').startsWith("main.", ignoreCase = true)
    }?.value ?: zipAssets.entries.firstOrNull { (k, _) ->
        val ext = k.substringAfterLast('.', "").lowercase()
        ext in setOf("png", "jpg", "jpeg", "webp", "gif")
    }?.value
    var charxAvatarUri: String? = (assistant.avatar as? Avatar.Image)?.url
    if (iconEntryBytes != null) {
        if (charxBaseDir != null) {
            val aliasFile = java.io.File(charxBaseDir, "${assistant.id}.png")
            runCatching { aliasFile.writeBytes(iconEntryBytes) }
            if (charxAvatarUri == null) {
                charxAvatarUri = android.net.Uri.fromFile(aliasFile).toString()
            }
        } else if (charxAvatarUri == null) {
            val b64 = java.util.Base64.getEncoder().encodeToString(iconEntryBytes)
            charxAvatarUri = "data:image/png;base64,$b64"
        }
    }
    val updatedAssistant = assistant.copy(
        avatar = if (charxAvatarUri != null) Avatar.Image(charxAvatarUri) else assistant.avatar,
        useAssistantAvatar = charxAvatarUri != null || assistant.useAssistantAvatar,
        systemPrompt = rewriteCharxText(assistant.systemPrompt),
        presetMessages = updatedPresetMessages,
        tavernData = updatedTav,
    )
    return updatedAssistant to updatedLorebooks
}

/**
 * 解析 Backyard AI `.byaf` 归档格式（对齐官方 `src/byaf.js`）：
 * - 读取归档中的角色 JSON（`characters/` 目录下的 `.json` 或 `character.json`），将 Backyard 字段转换为 CCv2 结构并导入。
 */
internal fun parseByafBytes(
    bytes: ByteArray,
    context: Context? = null,
): Pair<Assistant, List<Lorebook>> {
    var charJsonObj: JsonObject? = null
    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val normName = entry.name.replace('\\', '/').trimStart('/')
                if (normName.endsWith(".json", ignoreCase = true) && !normName.endsWith("manifest.json", ignoreCase = true)) {
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                    if (obj != null && (obj.containsKey("aiName") || obj.containsKey("name") || obj.containsKey("aiPersona"))) {
                        charJsonObj = obj
                        break
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    val src = charJsonObj ?: importError(context, R.string.assistant_importer_read_json_failed, ".byaf 归档中未找到角色定义 JSON")
    val name = src["aiName"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: src["name"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: "Backyard Character"
    val desc = src["aiPersona"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: src["description"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val scenario = src["scenario"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val firstMes = src["firstMessage"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: src["greeting"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val sysPrompt = src["systemPrompt"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: src["basePrompt"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val mesEx = src["exampleMessages"]?.jsonPrimitiveOrNull?.contentOrNull
        ?: src["mes_example"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
    val v2Json = kotlinx.serialization.json.buildJsonObject {
        put("spec", kotlinx.serialization.json.JsonPrimitive("chara_card_v2"))
        put("spec_version", kotlinx.serialization.json.JsonPrimitive("2.0"))
        put("data", kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(name))
            put("description", kotlinx.serialization.json.JsonPrimitive(desc))
            put("personality", kotlinx.serialization.json.JsonPrimitive(""))
            put("scenario", kotlinx.serialization.json.JsonPrimitive(scenario))
            put("first_mes", kotlinx.serialization.json.JsonPrimitive(firstMes))
            put("mes_example", kotlinx.serialization.json.JsonPrimitive(mesEx))
            put("system_prompt", kotlinx.serialization.json.JsonPrimitive(sysPrompt))
            val loreItems = src["loreItems"] as? JsonArray
            if (loreItems != null && loreItems.isNotEmpty()) {
                put("character_book", kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("$name Lore"))
                    put("entries", kotlinx.serialization.json.buildJsonArray {
                        loreItems.forEachIndexed { idx, el ->
                            val item = el as? JsonObject ?: return@forEachIndexed
                            val keyStr = item["key"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
                            val valStr = item["value"]?.jsonPrimitiveOrNull?.contentOrNull
                                ?: item["content"]?.jsonPrimitiveOrNull?.contentOrNull.orEmpty()
                            add(kotlinx.serialization.json.buildJsonObject {
                                put("id", kotlinx.serialization.json.JsonPrimitive(idx))
                                put("keys", kotlinx.serialization.json.buildJsonArray {
                                    keyStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                                        add(kotlinx.serialization.json.JsonPrimitive(it))
                                    }
                                })
                                put("content", kotlinx.serialization.json.JsonPrimitive(valStr))
                                put("enabled", kotlinx.serialization.json.JsonPrimitive(true))
                            })
                        }
                    })
                })
            }
        })
    }
    return parseCardJson(v2Json, null, null, context)
}


internal fun ensureAssistantAvatarAlias(
    context: Context?,
    assistantId: Uuid,
    avatarUri: String?,
) {
    if (context == null || avatarUri.isNullOrBlank()) return
    runCatching {
        val charxDir = java.io.File(context.filesDir, "charx_assets/$assistantId").apply { mkdirs() }
        val targetFile = java.io.File(charxDir, "$assistantId.png")
        val bytes = when {
            avatarUri.startsWith("data:", ignoreCase = true) ->
                me.rerere.rikkahub.data.st.expressions.SpriteRepository.decodeDataUri(avatarUri)?.first
            avatarUri.startsWith("file:", ignoreCase = true) -> {
                val path = Uri.parse(avatarUri).path
                if (!path.isNullOrBlank()) java.io.File(path).takeIf { it.isFile }?.readBytes() else null
            }
            else -> {
                val parsed = Uri.parse(avatarUri)
                val localFile = parsed.path?.let { java.io.File(it) }?.takeIf { it.isFile }
                localFile?.readBytes() ?: context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
            }
        }
        if (bytes != null && bytes.isNotEmpty()) {
            targetFile.writeBytes(bytes)
        }
    }
}
