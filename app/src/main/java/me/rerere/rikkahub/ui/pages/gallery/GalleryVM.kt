package me.rerere.rikkahub.ui.pages.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.st.runtime.TavernFileBridge
import java.io.File

/**
 * 画廊（对齐 SillyTavern `public/scripts/extensions/gallery/index.js`）。
 *
 * ## 为什么需要这个页面
 * `st-compat/extensions/gallery/index.js` 的 `getGalleryImages()` 已经能通过
 * `RikkaBridge.galleryListJson()` 拿到图片列表 —— 但**没有任何界面展示它**。
 * 也就是说：桥是通的，数据是真的，功能是「不存在」的。这正是「做了但没起效」。
 *
 * ## 数据来源
 * ST 从 `user/images/<folder>/` 列文件；我们对应沙箱 `filesDir/st-user-files/images/`。
 * 与文件桥共用同一个根目录，所以扩展上传的图片、用户手动放的图片都能看到。
 */
class GalleryVM : ViewModel() {

    /** 一个画廊条目。 */
    data class Item(
        val file: File,
        val name: String,
        val folder: String,
        val sizeBytes: Long,
        val lastModified: Long,
    )

    data class UiState(
        val loading: Boolean = true,
        /** 可选的子目录名（空串代表 images 根目录）。 */
        val folders: List<String> = emptyList(),
        val selectedFolder: String = "",
        val items: List<Item> = emptyList(),
        /** 初始化失败原因（文件桥未就绪等）。 */
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val imagesRoot: File?
        get() = TavernFileBridge.rootDirectory()?.let { File(it, "images") }

    /** 重新扫描目录（切目录 / 下拉刷新 / 删除后调用）。 */
    fun refresh(folder: String? = null) {
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) { scan(folder ?: _state.value.selectedFolder) }
            _state.value = next
        }
    }

    private fun scan(folder: String): UiState {
        val root = imagesRoot
            ?: return UiState(loading = false, error = "file bridge not ready")

        if (!root.exists()) {
            // 目录不存在是正常状态（还没人传过图），不是错误。
            return UiState(loading = false, folders = emptyList(), selectedFolder = "", items = emptyList())
        }

        val folders = root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

        val dir = if (folder.isBlank()) root else File(root, folder)
        if (!dir.exists() || !dir.isDirectory) {
            return UiState(loading = false, folders = folders, selectedFolder = "", items = emptyList())
        }

        val items = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                Item(
                    file = f,
                    name = f.name,
                    folder = folder,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified(),
                )
            }
            .orEmpty()

        return UiState(
            loading = false,
            folders = folders,
            selectedFolder = if (dir == root) "" else folder,
            items = items,
        )
    }

    /** 删除一张图。返回是否真的删掉了（UI 用它决定是否弹提示）。 */
    fun delete(item: Item, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { item.file.exists() && item.file.delete() }.getOrDefault(false)
            }
            if (ok) refresh()
            onDone(ok)
        }
    }

    companion object {
        /**
         * 与 ST 的 `MEDIA_REQUEST_TYPE.IMAGE` 对齐：只列图片，不列视频。
         * ST 原版还会列视频并生成缩略图；我们当前只做图片（视频缩略图需要
         * MediaMetadataRetriever，属于独立功能，不在本轮范围）。
         */
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif", "jfif")
    }
}
