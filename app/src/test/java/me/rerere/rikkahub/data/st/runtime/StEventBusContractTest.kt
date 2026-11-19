package me.rerere.rikkahub.data.st.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 事件总线契约守卫（`eventSource` / `event_types` 的**身份与语义**）。
 *
 * ## 背景（真实插件静默失效）
 * 上游 `public/script.js:278` 是：
 *   `import { event_types, eventSource } from './scripts/events.js';`
 * 我们此前写成 `const ES = st().eventSource || host().eventSource;` 再导出。
 *
 * 根因：`runtime.js:29` 静态 `import * as stCompatScript from './st-compat/script.js'`，
 * 而 `runtime.js:2472` 才 `window.SillyTavern = SillyTavern`。ES 模块求值顺序是
 * 被 import 的模块先跑完模块体，所以 `script.js` 求值时 `window.SillyTavern` 仍是
 * undefined → `st()` 返回 `{}` → `ES` 为 undefined → **手写 fallback 被永久固化**。
 *
 * 实测后果：
 *   - `script.js` 的 `eventSource` 是死总线（`emit`/`on` 全空函数，`events` 为 undefined）
 *   - `script.js` 的 `event_types` 是 **空对象，0 个键**（真值 104）
 *   - 43 个插件文件从 script.js 导入这两个符号 → 事件订阅全部静默失效
 *   - Roadway 的 `makeFirst(CHARACTER_MESSAGE_RENDERED)` 装在死总线上，自动触发永不响应
 *
 * 这个测试防止再退回 `st()` 取值 / 手写 fallback。
 */
class StEventBusContractTest {

    /**
     * 定位 assets 目录。Gradle 单测的工作目录不固定（可能是 app/ 也可能是仓库根），
     * 所以两种相对路径都试，避免把测试绑死在某个工作目录上。
     */
    private val stRuntimeDir: File by lazy {
        val candidates = listOf(
            File("src/main/assets/st-runtime"),           // cwd = app/
            File("app/src/main/assets/st-runtime"),       // cwd = 仓库根
        )
        candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError(
                "找不到 st-runtime 目录，cwd=${File("").absolutePath}，" +
                    "尝试过: ${candidates.joinToString { it.absolutePath }}",
            )
    }

    private fun scriptJs(): String {
        val f = File(stRuntimeDir, "st-compat/script.js")
        assertTrue("缺少 st-compat/script.js: ${f.absolutePath}", f.isFile)
        return f.readText()
    }

    private fun eventEmitterJs(): String {
        val f = File(stRuntimeDir, "vendor/st/eventemitter.js")
        assertTrue("缺少 vendor/st/eventemitter.js: ${f.absolutePath}", f.isFile)
        return f.readText()
    }

    @Test
    fun `script js forwards the real shared eventSource singleton`() {
        val src = scriptJs()
        assertTrue(
            "script.js 必须直接转发 vendor/st/events.js 的真实单例（对齐上游 script.js:278）",
            src.contains("export { eventSource, event_types } from '../vendor/st/events.js';"),
        )
        // 注意：不能用 src.contains(...) 直接字面匹配 —— 修复说明的**注释里**引用了旧代码，
        // 会把注释当成代码从而误报。用行首锚定只匹配真正的可执行语句。
        assertFalse(
            "不允许再用 st() 取值固化 eventSource（模块求值时 window.SillyTavern 还不存在）",
            Regex("""^const ES = st\(\)\.eventSource""", RegexOption.MULTILINE).containsMatchIn(src),
        )
        assertFalse(
            "不允许再手写 eventSource fallback 空函数体",
            Regex("""^export const eventSource = ES \|\| \{""", RegexOption.MULTILINE).containsMatchIn(src),
        )
        assertFalse(
            "不允许再把 event_types 固化成可能为空的字面量",
            Regex("""^export const event_types = st\(\)\.eventTypes""", RegexOption.MULTILINE).containsMatchIn(src),
        )
    }

    @Test
    fun `event emitter keeps the full upstream surface`() {
        val src = eventEmitterJs()
        // 上游 public/lib/eventemitter.js 的全部原型方法
        for (method in listOf("on", "makeLast", "makeFirst", "removeListener", "emit", "emitAndWait", "once")) {
            assertTrue(
                "EventEmitter.prototype.$method 必须存在（插件依赖，尤其 makeFirst/makeLast 的排序语义）",
                src.contains("EventEmitter.prototype.$method ="),
            )
        }
        // 插件会直读这两个字段（Roadway / 扩展管理器）
        assertTrue("必须保留 this.events 监听表", src.contains("this.events = {}"))
        assertTrue("必须保留 this.autoFireAfterEmit", src.contains("this.autoFireAfterEmit = new Set("))
        assertTrue("必须保留 this.autoFireLastArgs", src.contains("this.autoFireLastArgs = new Map("))
    }

    @Test
    fun `events module shares one instance across module paths`() {
        val f = File(stRuntimeDir, "vendor/st/events.js")
        assertTrue("缺少 vendor/st/events.js", f.isFile)
        val src = f.readText()
        assertTrue(
            "必须经 globalThis.__rikkaSharedEventSource 保证多路径同一实例" +
                "（否则插件从 /scripts/events.js 订阅、宿主从 vendor 路径 emit，永远收不到）",
            src.contains("__rikkaSharedEventSource"),
        )
        assertTrue(
            "event_types 必须非空（实测真值 104 个键）",
            src.contains("MESSAGE_SENT: 'message_sent'") && src.contains("APP_READY: 'app_ready'"),
        )
    }
}
