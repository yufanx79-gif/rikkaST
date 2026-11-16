package me.rerere.rikkahub.data.st.runtime

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v247] 接线审计：防止「实现了但没接线」这类静默失效。
 *
 * ## 为什么需要这个测试
 * 项目历史上反复出现同一类问题：
 *  - `getPreset()` 实现了但 runtime 没定义 → 脚本第一行就崩
 *  - 声明式按钮解析了但没人调用 `syncDeclaredScriptButtons` → 面板按钮不出现
 *  - `__rikkahub_regex_scripts__` 读的人有了、写的人没有 → 正则永远不生效
 *  - `charactersSupplier` 加了但 ChatPage 没赋值 → 还是空数组
 *
 * 这类问题的共同特征：**代码都写好了，但没有连起来**。
 * 单元测试单独测每个函数都通过，可端到端就是不工作。
 *
 * 本测试做**静态接线检查**（读源码文本），确保三端一致：
 *   1. Kotlin 声明了 supplier
 *   2. Kotlin 有对应的 @JavascriptInterface 桥方法
 *   3. JS 侧真的调用了 callBridge('那个方法名')
 *   4. UI 层（ChatPage）真的给 supplier 赋了值
 *
 * 任一环缺失 → 测试失败并指出缺哪一环。
 */
class TavernWiringAuditTest {

    private val repoRoot: File = run {
        // Gradle 单测工作目录是模块目录（app/），向上找仓库根
        val candidates = listOf(File("."), File(".."), File("../.."))
        candidates.first { File(it, "app/src/main/java").exists() }.absoluteFile
    }

    private fun read(rel: String): String {
        val f = File(repoRoot, rel)
        assertTrue("文件不存在: ${f.absolutePath}", f.exists())
        return f.readText()
    }

    private val manager by lazy {
        read("app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt")
    }
    private val chatPage by lazy {
        read("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt")
    }
    private val runtimeJs by lazy {
        read("app/src/main/assets/st-runtime/runtime.js")
    }

    /**
     * 每一条 = (supplier 名, 桥方法名, JS 侧是否必须调用)。
     *
     * 新增 supplier 时**必须**同时在这里登记，否则这个测试不会覆盖它 ——
     * 但下面的 [everySupplierIsCoveredByAuditTable] 会强制你登记。
     */
    private val wiringTable: List<Wiring> = listOf(
        Wiring("tavernPresetSupplier", "getTavernPresetJson", jsCalls = true),
        Wiring("proxyPresetNamesSupplier", "getProxyPresetNamesJson", jsCalls = true),
        Wiring("modelListSupplier", "getModelListJson", jsCalls = true),
        Wiring("charactersSupplier", "getCharactersJson", jsCalls = true),
        Wiring("personasSupplier", "getPersonasJson", jsCalls = true),
        Wiring("regexScriptsSupplier", "getRegexScriptsJson", jsCalls = true),
        Wiring("tavernCharacterSupplier", "getCharacterJson", jsCalls = true),
        Wiring("tavernLorebookSupplier", "getLorebookJson", jsCalls = true),
        Wiring("worldNamesSupplier", "getWorldNamesJson", jsCalls = true),
        Wiring("tavernScriptsSupplier", "getTavernScriptsJson", jsCalls = true),
        Wiring("chatJsonSupplier", "getChatJson", jsCalls = true),
        Wiring("thirdPartyExtensionsSupplier", "getThirdPartyExtensionsJson", jsCalls = true),
        Wiring("thirdPartySettingsSupplier", "getThirdPartySettingsJson", jsCalls = true),
        // 这些 supplier 由非 ChatPage 的地方接线（ChatService / 设置页），只校验声明与桥
        Wiring("generationModelIdsSupplier", "getModelListJson", jsCalls = true, requireChatPageWiring = false),
        Wiring("currentModelIdSupplier", null, jsCalls = false, requireChatPageWiring = false),
        // 这两个不走 callBridge，而是 Kotlin 内部直接读（activeAssistant 供宏替换、图像状态供轮询）
        Wiring("activeAssistantSupplier", null, jsCalls = false),
        Wiring("imageGenerationStatusSupplier", "getImageGenerationStatusJson", jsCalls = true, requireChatPageWiring = false),
        Wiring("imageCaptioner", "captionImage", jsCalls = false, requireChatPageWiring = true),
        Wiring("conversationBrancher", "branchConversation", jsCalls = false, requireChatPageWiring = true),
        Wiring("conversationOpener", "openConversation", jsCalls = false, requireChatPageWiring = true),
    )

    data class Wiring(
        val supplier: String,
        val bridge: String?,
        val jsCalls: Boolean,
        val requireChatPageWiring: Boolean = true,
    )

    @Test
    fun everySupplierIsDeclaredInManager() {
        val missing = wiringTable.filter { !manager.contains("var ${it.supplier}") }
        assertTrue(
            "以下 supplier 在 TavernRuntimeManager 里没有声明：${missing.map { it.supplier }}",
            missing.isEmpty(),
        )
    }

    @Test
    fun everyBridgeMethodExistsInManager() {
        val missing = wiringTable.filter { w ->
            w.bridge != null && !manager.contains("fun ${w.bridge}(")
        }
        assertTrue(
            "以下桥方法在 TavernRuntimeManager 里没有定义：${missing.map { it.bridge }}",
            missing.isEmpty(),
        )
    }

    /**
     * ★ 最关键的一条：桥方法必须被 JS 真的调用。
     *
     * 「Kotlin 写了桥、JS 从不调用」是本项目最常见的静默失效形态。
     */
    @Test
    fun everyBridgeIsActuallyCalledFromJs() {
        val missing = wiringTable
            .filter { it.jsCalls && it.bridge != null }
            .filter { w ->
                // 允许两种写法：callBridge('x' 或 bridge.x(
                !runtimeJs.contains("callBridge('${w.bridge}'") &&
                    !runtimeJs.contains("callBridge(\"${w.bridge}\"") &&
                    !runtimeJs.contains("bridge.${w.bridge}(")
            }
        assertTrue(
            "以下桥方法 Kotlin 有、但 runtime.js 从不调用（实现了但没生效）：${missing.map { it.bridge }}",
            missing.isEmpty(),
        )
    }

    /** ★ 第二关键：supplier 必须有人赋值，否则永远是 null。 */
    @Test
    fun everySupplierIsActuallyAssigned() {
        val missing = wiringTable
            .filter { it.requireChatPageWiring }
            .filter { !chatPage.contains("TavernRuntimeManager.${it.supplier} =") }
        assertTrue(
            "以下 supplier 在 ChatPage 里没有赋值（永远是 null，功能静默失效）：${missing.map { it.supplier }}",
            missing.isEmpty(),
        )
    }

    /**
     * 反向检查：Manager 里声明的每一个 `var xxxSupplier` 都必须在审计表里。
     *
     * 防止「新加了 supplier 但忘了登记，于是没人检查它有没有接线」。
     */
    @Test
    fun noUnregisteredSupplierExists() {
        val declared = Regex("""@Volatile\s+var\s+(\w+Supplier)\s*:""")
            .findAll(manager)
            .map { it.groupValues[1] }
            .toSet()
        val registered = wiringTable.map { it.supplier }.toSet()
        val unregistered = declared - registered
        assertTrue(
            "以下 supplier 已声明但没登记到 wiringTable（无法保证接线）：$unregistered",
            unregistered.isEmpty(),
        )
    }

    /**
     * JS 侧每一条 `callBridge('x')` 都必须在 Kotlin 侧有同名 @JavascriptInterface 方法。
     *
     * 这是「JS 调了但宿主没有」方向的检查 —— 会抛 "Method not found"。
     */
    @Test
    fun everyJsCallBridgeHasKotlinMethod() {
        val ktMethods = Regex("""@JavascriptInterface\s*\n\s*fun\s+(\w+)\s*\(""")
            .findAll(manager)
            .map { it.groupValues[1] }
            .toSet()
        val jsCalls = Regex("""callBridge\(\s*['"](\w+)['"]""")
            .findAll(runtimeJs)
            .map { it.groupValues[1] }
            .toSet()
        val orphan = jsCalls - ktMethods
        assertTrue(
            "runtime.js 调用了宿主不存在的方法（会抛 Method not found）：$orphan",
            orphan.isEmpty(),
        )
    }

    /**
     * st-compat 里每个模块都必须能被 runtime 的桥接层解析到。
     *
     * TH 产物 import 的是 `../scripts/xxx.js`，桥接层是 `scripts/xxx.js` 的 re-export。
     * 少一个文件 → TH 模块加载失败 → 整个 API 层不可用。
     */
    @Test
    fun everyTavernHelperExternalImportResolvesToBridgeFile() {
        val assets = File(repoRoot, "app/src/main/assets/st-runtime")
        val entry = File(assets, "tavern-helper/rikkast-entry.js")
        assertTrue("TH 产物不存在", entry.exists())
        val src = entry.readText()
        // 产物里的相对 import：from "../scripts/xxx.js" 或 from "../script.js"
        val imports = Regex("""from\s+["'](\.\./[^"']+)["']""")
            .findAll(src)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("TH 产物里没有任何相对 import（构建配置可能错了）", imports.isNotEmpty())
        val missing = imports.filter { rel ->
            val target = File(assets, rel.removePrefix("../")).canonicalFile
            !target.exists()
        }
        assertTrue(
            "TH 产物 import 了但桥接层缺失的文件：$missing",
            missing.isEmpty(),
        )
    }
}
