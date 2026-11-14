package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.python.PythonBridge
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.knowledge.KnowledgeBaseService
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.java.KoinJavaComponent
import java.io.File

fun createPythonTool(context: Context, timeoutSec: Int = 30): Tool = Tool(
    name = "execute_python",
    description = "Execute Python 3.12 code. The interpreter is BUILT INTO the app and always available — " +
        "you never need to install Python, and `python3` inside workspace_shell is a DIFFERENT, unrelated environment.\n\n" +
        "Runtime: Python 3.12 with the standard library plus requests, beautifulsoup4, markdown, pypdf, " +
        "openpyxl, markdownify, tabulate, python-dateutil, pytz.\n\n" +
        "IMPORTANT — two separate environments, do not confuse them:\n" +
        "- `execute_python` (this tool): runs on the app side. Its working directory is the app's private " +
        "storage, NOT /workspace. Files you wrote into the workspace Rootfs are NOT visible here.\n" +
        "- `workspace_shell`: runs inside the Linux Rootfs. It is a minimal Ubuntu base image, so python3 " +
        "is NOT preinstalled there. Install it with `apt-get update && apt-get install -y python3` only if " +
        "you truly need to run a Python script inside a shell pipeline.\n\n" +
        "Code style — full scripts are supported:\n" +
        "- Multiple lines, imports, function/class definitions, loops and conditionals all work.\n" +
        "- Variables persist between calls in the same app session, so you can import once and reuse later.\n" +
        "- If the last statement is an expression, its value is returned as `result`.\n" +
        "- Use print() for anything you want to see in `stdout`.\n\n" +
        "When to use:\n" +
        "- Data processing and transformation (CSV, JSON, spreadsheets)\n" +
        "- API calls to external services\n" +
        "- File generation (CSV, JSON, XLSX, images)\n" +
        "- Multi-step computations that calculator cannot handle\n\n" +
        "When NOT to use:\n" +
        "- Simple math (use calculator)\n" +
        "- Shell commands inside the Rootfs (use workspace_shell)\n" +
        "- Android file operations (use the file tool)\n\n" +
        "Args:\n" +
        "- code: Python source to execute. Full multi-line scripts are supported.",
    needsApproval = { false },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("code", buildJsonObject {
                    put("type", "string")
                    put("description", "Python 3.12 source code. Multi-line scripts, imports and function definitions are supported. Last expression value is returned; use print() for output.")
                })
            },
            required = listOf("code"),
        )
    },
    execute = { args ->
        val code = args.jsonObject["code"]?.jsonPrimitive?.content
            ?: error("code parameter is required")

        // 并行调用必须串行化启动，否则并发第二个起会抛 "Python already started"
        PythonRuntime.ensureStarted(context)

        val py = Python.getInstance()
        val executor = py.getModule("executor")
        val workdir = context.filesDir.absolutePath

        // Inject Android bridge (passed as parameter to execute())
        val bridge = PythonBridge(
            context = context,
            db = KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java),
            settingsStore = KoinJavaComponent.get<SettingsStore>(SettingsStore::class.java),
            conversationRepo = KoinJavaComponent.get<ConversationRepository>(ConversationRepository::class.java),
            kbService = KoinJavaComponent.get<KnowledgeBaseService>(KnowledgeBaseService::class.java),
        )

        val rawResult = withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(timeoutSec * 1000L) {
                executor.callAttr("execute", code, workdir, bridge).toString()
            }
        }

        // Try to parse JSON result from Python
        val resultJson = try {
            kotlinx.serialization.json.Json.parseToJsonElement(rawResult).jsonObject
        } catch (e: Exception) {
            // Not JSON, return as plain text
            return@Tool listOf(UIMessagePart.Text(rawResult))
        }

        // Build structured response with files
        val parts = mutableListOf<UIMessagePart>()

        // Collect output text
        val output = buildString {
            resultJson["stdout"]?.jsonPrimitive?.content?.let {
                if (it.isNotBlank()) appendLine("Output:\n$it")
            }
            resultJson["result"]?.jsonPrimitive?.content?.let {
                if (it.isNotBlank()) appendLine("Result: $it")
            }
            resultJson["error"]?.jsonPrimitive?.content?.let {
                appendLine("Error: $it")
            }
        }
        if (output.isNotBlank()) {
            parts.add(UIMessagePart.Text(output.trimEnd()))
        }

        // Attach generated files
        val files = resultJson["files"]?.let { elem ->
            try {
                elem.jsonArray.map { it.jsonPrimitive.content }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

        for (fname in files) {
            val file = File(workdir, fname)
            if (file.exists()) {
                parts.add(UIMessagePart.Document(
                    url = "file://" + file.absolutePath,
                    fileName = fname,
                    mime = fname.mimeType(),
                ))
            }
        }

        if (parts.isEmpty()) {
            parts.add(UIMessagePart.Text(rawResult))
        }
        parts
    },
)

private fun String.mimeType(): String = when {
    endsWith(".png") -> "image/png"
    endsWith(".svg") -> "image/svg+xml"
    endsWith(".jpg") || endsWith(".jpeg") -> "image/jpeg"
    endsWith(".gif") -> "image/gif"
    endsWith(".html") -> "text/html"
    endsWith(".json") -> "application/json"
    endsWith(".csv") -> "text/csv"
    endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    endsWith(".pdf") -> "application/pdf"
    endsWith(".txt") || endsWith(".md") -> "text/plain"
    endsWith(".yaml") || endsWith(".yml") -> "application/x-yaml"
    else -> "application/octet-stream"
}
