package me.rerere.rikkahub.ui.pages.imggen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageGenSize
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.io.File
import java.net.SocketTimeoutException
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class GeneratedImage(
    val id: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String
)

private fun GenMediaEntity.toGeneratedImage(filesManager: FilesManager): GeneratedImage {
    val imagesDir = filesManager.getImagesDir()
    val name = this.path.removePrefix("images/")
    val file = File(imagesDir, name)
    val fullPath = if (file.exists() || '/' !in modelId) {
        file.absolutePath
    } else {
        File(imagesDir, "${createAt}_${modelId.substringBeforeLast('/')}/$name").absolutePath
    }

    return GeneratedImage(
        id = this.id,
        prompt = this.prompt,
        filePath = fullPath,
        timestamp = this.createAt,
        model = this.modelId
    )
}

private fun String.toFileNamePart(): String = replace(Regex("""[\\/:*?"<>|]"""), "_")

/**
 * 归一化生图尺寸 / 画幅参数（支持 `auto`、`portrait`、`landscape`、`square`、`1:1`、`2:3`、`3:2`、`9:16`、`16:9` 以及 `WxH` 像素格式）。
 */
internal fun normalizeImageGenSize(rawSize: String?): String {
    val s = rawSize?.trim()?.lowercase().orEmpty()
    if (s.isEmpty() || s == "auto") return ImageGenSize.AUTO.value
    return when (s) {
        "square", "1:1", "1024x1024" -> "1024x1024"
        "portrait", "2:3", "3:4", "9:16", "1024x1536", "1024x1792", "832x1216" -> "1024x1536"
        "landscape", "3:2", "4:3", "16:9", "1536x1024", "1792x1024", "1216x832" -> "1536x1024"
        else -> if (Regex("""^\d{2,4}x\d{2,4}$""").matches(s)) s else ImageGenSize.AUTO.value
    }
}

/**
 * 将生图异常分类为可读、脱敏的诊断提示（覆盖缺失模型/提供商、未配置密钥、401/403、429 限流、超时、响应格式错误）。
 */
internal fun classifyImageGenerationError(error: Throwable): String {
    val raw = (error.message ?: error.javaClass.simpleName).trim()
    val redacted = raw
        .replace(Regex("""Bearer\s+[A-Za-z0-9._\-]+""", RegexOption.IGNORE_CASE), "Bearer ***")
        .replace(Regex("""sk-[A-Za-z0-9._\-]{6,}"""), "sk-***")
    return when {
        error is SocketTimeoutException || raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
            "图片生成请求超时（Timeout），请检查网络或代理连接后重试"
        raw.contains("No model selected", ignoreCase = true) || raw.contains("未选择", ignoreCase = true) ->
            "尚未选择生图模型：请先在设置或生图页选择可用的生图模型"
        raw.contains("Provider not found", ignoreCase = true) || raw.contains("提供商不存在", ignoreCase = true) ->
            "所选生图模型对应的 API 提供商不存在或已被禁用，请重新选择提供商"
        raw.contains("Missing API key", ignoreCase = true) || raw.contains("未填写密钥", ignoreCase = true) ->
            "当前生图提供商尚未填写 API 密钥，请先在提供商设置中配置密钥"
        Regex("""\b(401|unauthorized)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw) ->
            "生图鉴权失败（HTTP 401）：API 密钥无效或已过期（$redacted）"
        Regex("""\b(403|forbidden)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw) ->
            "生图请求被拒绝（HTTP 403）：当前账号无权限或需完成组织验证（$redacted）"
        Regex("""\b(429|rate\s*limit|too\s*many\s*requests|quota)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw) ->
            "生图请求触发限流或额度不足（HTTP 429）：请稍后重试或检查账户额度（$redacted）"
        raw.contains("malformed", ignoreCase = true) || raw.contains("empty image", ignoreCase = true) ||
            raw.contains("IllegalArgumentException", ignoreCase = true) || raw.contains("JsonDecodingException", ignoreCase = true) ->
            "生图接口返回的数据格式无效或未包含有效图片数据（$redacted）"
        else -> redacted.ifBlank { "Unknown error occurred" }
    }
}

/**
 * 校验并提取 base64 图片载荷（支持 `data:image/...;base64,...` 与纯 base64，校验非空及最小合法图片头）。
 */
internal fun extractValidImageBase64(rawPayload: String): String {
    val trimmed = rawPayload.trim()
    require(trimmed.isNotEmpty()) { "Malformed image payload: empty image data" }
    val cleanB64 = if (trimmed.startsWith("data:", ignoreCase = true)) {
        val commaIdx = trimmed.indexOf(',')
        require(commaIdx > 0 && commaIdx < trimmed.lastIndex) { "Malformed data URI image payload" }
        trimmed.substring(commaIdx + 1).trim()
    } else {
        trimmed
    }
    val decoded = runCatching { Base64.getMimeDecoder().decode(cleanB64) }.getOrNull()
        ?: throw IllegalArgumentException("Malformed image payload: invalid base64 encoding")
    require(decoded.size >= 8) { "Malformed image payload: decoded bytes too short (${decoded.size})" }
    return cleanB64
}

class ImgGenVM(
    context: Application,
    val settingsStore: SettingsStore,
    val providerManager: ProviderManager,
    val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
) : AndroidViewModel(context) {
    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _numberOfImages = MutableStateFlow(1)
    val numberOfImages: StateFlow<Int> = _numberOfImages

    private val _size = MutableStateFlow(ImageGenSize.AUTO.value)
    val size: StateFlow<String> = _size

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    private var cancelJob: Job? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentGeneratedImages = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val currentGeneratedImages: StateFlow<List<GeneratedImage>> = _currentGeneratedImages

    private val _referenceImages = MutableStateFlow<List<String>>(emptyList())
    val referenceImages: StateFlow<List<String>> = _referenceImages

    val pager = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { genMediaRepository.getAllMedia() }
    )
    val generatedImages: Flow<PagingData<GeneratedImage>> = pager.flow
        .map { pagingData ->
            pagingData.map { entity -> entity.toGeneratedImage(filesManager) }
        }
        .cachedIn(viewModelScope)

    fun updatePrompt(prompt: String) {
        _prompt.value = prompt
    }

    fun updateNumberOfImages(count: Int) {
        _numberOfImages.value = count.coerceIn(1, 4)
    }

    fun updateSize(size: String) {
        _size.value = normalizeImageGenSize(size)
    }

    fun addReferenceImages(paths: List<String>) {
        _referenceImages.value = (_referenceImages.value + paths).distinct().take(MAX_REFERENCE_IMAGES)
    }

    fun removeReferenceImage(path: String) {
        _referenceImages.value = _referenceImages.value.filterNot { it == path }
        deleteReferenceFiles(listOf(path))
    }

    fun clearReferenceImages() {
        deleteReferenceFiles(_referenceImages.value)
        _referenceImages.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewSession() {
        cancelGeneration()
        clearReferenceImages()
        _prompt.value = ""
        _currentGeneratedImages.value = emptyList()
        _error.value = null
    }

    fun generateImage() {
        if (prompt.value.isBlank()) return
        cancelJob?.cancel()
        cancelJob = viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()

                val settings = settingsStore.awaitLoaded()
                val model = settings.findModelById(settings.imageGenerationModelId)
                    ?: settings.providers.filter { it.enabled }.flatMap { it.models }
                        .firstOrNull { it.type == me.rerere.ai.provider.ModelType.IMAGE }
                    ?: throw IllegalStateException("No model selected")

                val provider = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found")

                if (provider is ProviderSetting.ApiKey && provider.apiKey.isBlank()) {
                    throw IllegalStateException("Missing API key for provider ${provider.name}")
                }

                val requestPrompt = _prompt.value
                val normalizedSize = normalizeImageGenSize(_size.value)
                val params = ImageGenerationParams(
                    model = model,
                    prompt = requestPrompt,
                    numOfImages = _numberOfImages.value,
                    size = normalizedSize,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies
                )

                val images = providerManager.getProviderByType(provider)
                    .generateImage(provider, params)

                collectImageGeneration(
                    images = images,
                    prompt = requestPrompt,
                    modelName = model.displayName.ifBlank { model.modelId },
                )
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                val msg = classifyImageGenerationError(e)
                Log.e(TAG, "Failed to generate image: $msg")
                _error.value = msg
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun editImage() {
        if (prompt.value.isBlank() || referenceImages.value.isEmpty()) return
        cancelJob?.cancel()
        cancelJob = viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()

                val settings = settingsStore.awaitLoaded()
                val model = settings.findModelById(settings.imageGenerationModelId)
                    ?: settings.providers.filter { it.enabled }.flatMap { it.models }
                        .firstOrNull { it.type == me.rerere.ai.provider.ModelType.IMAGE }
                    ?: throw IllegalStateException("No model selected")

                val provider = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found")

                if (provider is ProviderSetting.ApiKey && provider.apiKey.isBlank()) {
                    throw IllegalStateException("Missing API key for provider ${provider.name}")
                }

                val requestPrompt = _prompt.value
                val sourceImages = _referenceImages.value
                val normalizedSize = normalizeImageGenSize(_size.value)
                val params = ImageEditParams(
                    model = model,
                    prompt = requestPrompt,
                    images = sourceImages,
                    numOfImages = _numberOfImages.value,
                    size = normalizedSize,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies
                )

                val images = providerManager.getProviderByType(provider)
                    .editImage(provider, params)

                collectImageGeneration(
                    images = images,
                    prompt = requestPrompt,
                    modelName = model.displayName.ifBlank { model.modelId },
                    type = GenMediaEntity.TYPE_IMAGE_EDIT,
                    sourcePaths = sourceImages.joinToString("\n"),
                )
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                val msg = classifyImageGenerationError(e)
                Log.e(TAG, "Failed to edit image: $msg")
                _error.value = msg
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun cancelGeneration() {
        val job = cancelJob
        cancelJob = null
        job?.cancel()
        _isGenerating.value = false
    }

    private suspend fun collectImageGeneration(
        images: Flow<ImageGenerationItem>,
        prompt: String,
        modelName: String,
        type: String = GenMediaEntity.TYPE_IMAGE_GENERATION,
        sourcePaths: String? = null,
    ) {
        val finalImages = mutableListOf<GeneratedImage>()
        var previewFile: File? = null
        var finalIndex = 0

        try {
            images.collect { item ->
                if (item.partial) {
                    previewFile?.delete()
                    val imageFile = saveImagePreview(
                        item = item,
                        modelName = modelName,
                        index = item.partialImageIndex ?: finalIndex,
                    )
                    previewFile = imageFile
                    _currentGeneratedImages.value = finalImages + GeneratedImage(
                        id = 0,
                        prompt = prompt,
                        filePath = imageFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        model = modelName
                    )
                } else {
                    previewFile?.delete()
                    previewFile = null
                    val imageFile = saveImageToStorage(
                        item = item,
                        prompt = prompt,
                        modelName = modelName,
                        index = finalIndex,
                        type = type,
                        sourcePaths = sourcePaths,
                    )
                    finalImages.add(
                        GeneratedImage(
                            id = 0,
                            prompt = prompt,
                            filePath = imageFile.absolutePath,
                            timestamp = System.currentTimeMillis(),
                            model = modelName
                        )
                    )
                    finalIndex++
                    _currentGeneratedImages.value = finalImages.toList()
                }
            }
        } finally {
            previewFile?.delete()
        }
    }

    private fun saveImagePreview(
        item: ImageGenerationItem,
        modelName: String,
        index: Int,
    ): File {
        val cleanB64 = extractValidImageBase64(item.data)
        val timestamp = System.currentTimeMillis()
        val imageFile = File(
            getApplication<Application>().appTempFolder,
            "imggen_${timestamp}_${modelName.toFileNamePart()}_$index.png"
        )
        return filesManager.createImageFileFromBase64(cleanB64, imageFile.absolutePath)
    }

    private suspend fun saveImageToStorage(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
        type: String = GenMediaEntity.TYPE_IMAGE_GENERATION,
        sourcePaths: String? = null,
    ): File {
        val cleanB64 = extractValidImageBase64(item.data)
        val imagesDir = filesManager.getImagesDir()

        val timestamp = System.currentTimeMillis()
        val filename = "${timestamp}_${modelName.toFileNamePart()}_$index.png"
        val imageFile = File(imagesDir, filename)

        val createdFile = filesManager.createImageFileFromBase64(cleanB64, imageFile.absolutePath)

        val relativePath = "images/${imageFile.name}"
        val entity = GenMediaEntity(
            path = relativePath,
            modelId = modelName,
            prompt = prompt,
            createAt = timestamp,
            type = type,
            sourcePaths = sourcePaths,
        )
        genMediaRepository.insertMedia(entity)

        return createdFile
    }

    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            try {
                genMediaRepository.deleteMedia(image.id)
                val file = File(image.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image", e)
                _error.value = "Failed to delete image"
            }
        }
    }

    suspend fun deleteImages(images: List<GeneratedImage>): List<GeneratedImage> =
        withContext(Dispatchers.IO) {
            images.filter { image ->
                try {
                    val file = File(image.filePath)
                    check(!file.exists() || file.delete()) { "Failed to delete image file" }
                    genMediaRepository.deleteMedia(image.id)
                    false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete image ${image.id}", e)
                    true
                }
            }
        }

    private fun deleteReferenceFiles(paths: List<String>) {
        viewModelScope.launch {
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        private const val TAG = "ImgGenVM"
        private const val MAX_REFERENCE_IMAGES = 16
    }
}
