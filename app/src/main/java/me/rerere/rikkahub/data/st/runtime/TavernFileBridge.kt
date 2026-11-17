package me.rerere.rikkahub.data.st.runtime

import android.util.Log
import java.io.File

/**
 * 酒馆文件读写桥（JSR `getTavernFile` / `uploadTavernFile` / `deleteTavernFile` 语义）。
 *
 * 上游实现（`src/function/file.ts`）走 ST 后端的 HTTP 接口：
 *   - `getTavernFile(path)`     → `fetch(path)`
 *   - `uploadTavernFile(name, base64)` → `POST /api/files/upload`
 *   - `deleteTavernFile(path)`  → `POST /api/files/delete`
 * 我们没有 ST 后端，改为直接读写沙箱目录。
 *
 * **安全约束（必须遵守）**：
 *  1. 所有路径限制在 `filesDir/st-user-files/` 内，拒绝 `..` 穿越与绝对路径
 *  2. 单文件上限 [MAX_FILE_BYTES]（32 MB），防止卡片脚本写爆存储
 *  3. 只允许白名单扩展名（文本/JSON/图片/音频），拒绝可执行文件
 *
 * 失败一律返回结构化错误，不抛异常给 JS（否则卡片脚本整段崩）。
 */
object TavernFileBridge {

    private const val TAG = "TavernFileBridge"
    private const val MAX_FILE_BYTES = 32L * 1024 * 1024

    /** 允许的扩展名白名单。 */
    private val ALLOWED_EXTENSIONS = setOf(
        "txt", "json", "md", "csv", "log", "yaml", "yml", "xml", "html", "css", "js",
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg",
        "mp3", "ogg", "wav", "m4a", "opus", "flac",
    )

    private var rootDir: File? = null

    fun init(filesDir: File) {
        val dir = File(filesDir, "st-user-files")
        if (!dir.exists()) dir.mkdirs()
        rootDir = dir
    }

    /**
     * [v248] 沙箱根目录（画廊页面用）。
     *
     * 之前只有 [list] 这种返回 JSON 的方法，Compose 侧拿不到真实 File，
     * 结果是「桥通了但 UI 无法展示」。这里补一个只读访问器，
     * 不改变任何既有路径校验逻辑（写操作仍只走 [resolveSafe]）。
     *
     * @return `filesDir/st-user-files`；未初始化返回 null
     */
    fun rootDirectory(): File? = rootDir

    /**
     * 把用户传入的路径规范化到沙箱内。
     *
     * @return 规范化后的 File；越界/非法/扩展名不允许时返回 null
     */
    private fun resolveSafe(rawPath: String): File? {
        val root = rootDir ?: return null
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return null
        // (1) 拒绝协议前缀（file:// / http:// / content:// 等）
        if (trimmed.contains("://")) return null
        // (2) 拒绝绝对路径（Windows 盘符与 Unix 根路径都算）
        if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.matches(Regex("^[A-Za-z]:.*"))) {
            return null
        }
        // (3) 拒绝路径穿越
        if (trimmed.split('/', '\\').any { it == ".." }) return null
        val file = File(root, trimmed)
        // (4) 规范化后必须仍在 root 下（防符号链接逃逸）
        val canonicalRoot = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val canonicalFile = runCatching { file.canonicalPath }.getOrNull() ?: return null
        if (!canonicalFile.startsWith(canonicalRoot + File.separator) && canonicalFile != canonicalRoot) {
            return null
        }
        // (5) 扩展名必须在白名单内；**无扩展名一律拒绝**（此前放行是漏洞）
        val ext = file.extension.lowercase()
        if (ext.isEmpty() || ext !in ALLOWED_EXTENSIONS) return null
        return file
    }

    /**
     * 读文件。返回 JSON：`{ok, data}`（data 是 base64）或 `{ok:false, error}`。
     * 用 base64 而不是文本，因为图片/音频是二进制。
     */
    fun read(path: String): String {
        return runCatching {
            val file = resolveSafe(path) ?: return err("invalid path: $path")
            if (!file.exists()) return err("file not found: $path")
            if (!file.isFile) return err("not a file: $path")
            if (file.length() > MAX_FILE_BYTES) return err("file too large (max 32MB): ${file.length()}")
            val bytes = file.readBytes()
            val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
            """{"ok":true,"data":"$b64","size":${bytes.size}}"""
        }.getOrElse {
            Log.w(TAG, "read($path) failed", it)
            err(it.message ?: "read failed")
        }
    }

    /**
     * 写文件。`base64` 为文件内容（base64 编码）。
     * 返回 JSON：`{ok, path, size}` 或 `{ok:false, error}`。
     */
    fun write(path: String, base64: String): String {
        return runCatching {
            val file = resolveSafe(path) ?: return err("invalid path: $path")
            val bytes = java.util.Base64.getDecoder().decode(base64)
            if (bytes.size > MAX_FILE_BYTES) return err("file too large (max 32MB): ${bytes.size}")
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            """{"ok":true,"path":${quote(path)},"size":${bytes.size}}"""
        }.getOrElse {
            Log.w(TAG, "write($path) failed", it)
            err(it.message ?: "write failed")
        }
    }

    /** 删除文件。返回 JSON：`{ok}` 或 `{ok:false, error}`。 */
    fun delete(path: String): String {
        return runCatching {
            val file = resolveSafe(path) ?: return err("invalid path: $path")
            if (!file.exists()) return err("file not found: $path")
            if (file.delete()) ok() else err("delete failed: $path")
        }.getOrElse {
            Log.w(TAG, "delete($path) failed", it)
            err(it.message ?: "delete failed")
        }
    }

    /** 列目录。返回 JSON：`{ok, files:[{name,size,dir}]}`。 */
    fun list(path: String = ""): String {
        return runCatching {
            val root = rootDir ?: return err("file bridge not initialized")
            val dir = if (path.isBlank()) root else (resolveSafe(path) ?: return err("invalid path: $path"))
            if (!dir.exists() || !dir.isDirectory) return err("not a directory: $path")
            val entries = dir.listFiles().orEmpty().map { f ->
                """{"name":${quote(f.name)},"size":${if (f.isFile) f.length() else 0},"dir":${f.isDirectory}}"""
            }
            """{"ok":true,"files":[${entries.joinToString(",")}]}"""
        }.getOrElse {
            Log.w(TAG, "list($path) failed", it)
            err(it.message ?: "list failed")
        }
    }

    private fun ok(): String = """{"ok":true}"""

    private fun err(msg: String): String = """{"ok":false,"error":${quote(msg)}}"""

    /** JSON 字符串转义（避免引入 JSONObject 依赖，保持零分配开销）。 */
    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
