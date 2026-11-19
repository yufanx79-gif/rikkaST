package me.rerere.rikkahub.data.st.runtime

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [今晚 P0] `ConnectionManagerRequestService` 契约守卫。
 *
 * ## 为什么需要这个测试
 * 它是**空壳回归守卫**。此前 runtime.js:2322 是：
 *
 * ```js
 * ConnectionManagerRequestService: { handleDropdown: () => {}, sendRequest: async () => ({}) },
 * ```
 *
 * 9 个真实插件在调它（22 处 sendRequest），插件不崩，但拿到的是 `{}` —— 结果永远为空。
 * 这类「不报错但什么也没做」的问题不会出现在加载率里，只能靠断言锁住。
 *
 * ## 为什么用源码结构断言而不是行为断言
 * 真正跑 JS 需要 WebView / Chaquopy，JVM 单测里起不来。
 * 行为断言放在 `dev-scripts/_cmrs_contract.mjs`（53 项，真加载 runtime.js）。
 * 这里只锁「代码结构不许退化回去」。
 *
 * ## 踩过的坑（务必保持）
 * 「必须不存在」的断言**必须行锚定**（`RegexOption.MULTILINE`）。
 * 上一轮 `StEventBusContractTest` 用 `src.contains("const ES = ...")`
 * 匹配到了注释里引用的旧代码，导致假失败。
 */
class StConnectionManagerContractTest {

    private val stRuntimeDir: File by lazy {
        // Gradle 单测的 working dir 可能是模块根，也可能是仓库根，两条都试。
        val candidates = listOf(
            File("src/main/assets/st-runtime"),
            File("app/src/main/assets/st-runtime"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("找不到 st-runtime 目录，尝试过: ${candidates.map { it.absolutePath }}")
    }

    private val runtimeJs: String by lazy {
        File(stRuntimeDir, "runtime.js").readText()
    }

    @Test
    fun `ConnectionManagerRequestService 不再是空壳对象`() {
        // 行锚定：只匹配真正的代码行，不匹配注释里引用的旧代码。
        val stubPattern = Regex(
            """^\s*ConnectionManagerRequestService:\s*\{""",
            RegexOption.MULTILINE,
        )
        assertTrue(
            "runtime.js 里仍有 `ConnectionManagerRequestService: { ... }` 空壳对象字面量。\n" +
                "必须改成 `ConnectionManagerRequestService: ConnectionManagerRequestService,`（引用上面的 class）。",
            !stubPattern.containsMatchIn(runtimeJs),
        )

        // 必须引用了真实的 class
        assertTrue(
            "runtime.js 里找不到 `ConnectionManagerRequestService: ConnectionManagerRequestService`，stCtx 没有接上真实 class。",
            runtimeJs.contains("ConnectionManagerRequestService: ConnectionManagerRequestService"),
        )
    }

    @Test
    fun `ConnectionManagerRequestService 暴露上游全部 static 方法`() {
        // 对齐上游 public/scripts/extensions/shared.js:392
        val required = listOf(
            "getAllowedTypes",
            "getSupportedProfiles",
            "getProfile",
            "getProfileIcon",
            "isProfileSupported",
            "validateProfile",
            "constructPrompt",
            "sendRequest",
            "handleDropdown",
        )
        val missing = required.filter { method ->
            !Regex("""static\s+(async\s+)?$method\s*\(""").containsMatchIn(runtimeJs)
        }
        assertTrue(
            "ConnectionManagerRequestService 缺少上游 static 方法: $missing. " +
                "上游定义见 SillyTavern 1.19.0 public/scripts/extensions/shared.js:392-700。",
            missing.isEmpty(),
        )
    }

    @Test
    fun `getProfile 找不到时必须 throw 而不是静默返回`() {
        // 上游 shared.js:552 是 `if (!profile) throw new Error(...)`。
        // 静默返回 undefined 会让插件在很后面才崩，定位成本极高。
        assertTrue(
            "getProfile 找不到 profile 时必须 throw（对齐上游 shared.js:552）。",
            Regex("""throw new Error\(`Profile not found""").containsMatchIn(runtimeJs),
        )
        assertTrue(
            "validateProfile 未知 api 时必须 throw `Unknown API type ...`（对齐上游 shared.js:612）。",
            Regex("""throw new Error\(`Unknown API type""").containsMatchIn(runtimeJs),
        )
    }

    @Test
    fun `sendRequest 走真实的宿主导出通道而不是返回空对象`() {
        // DIVERGENCE 说明：我们复用 A1 的 chat-completions fetch patch
        // （runtime.js 顶部 installChatCompletionsFetchPatch），
        // 经 callBridge('chatCompletionStart') 交给 Kotlin startChatCompletionStream。
        assertTrue(
            "sendRequest 必须走 /api/backends/chat-completions/generate（A1 通道）。",
            runtimeJs.contains("/api/backends/chat-completions/generate"),
        )
        assertTrue(
            "runtime.js 里必须存在 chatCompletionStart 桥调用（宿主生成通道）。",
            runtimeJs.contains("chatCompletionStart"),
        )
        // 流式必须明确 throw，不能静默假装成功
        assertTrue(
            "sendRequest 在 stream:true 时必须明确 throw（不允许静默假成功）。",
            Regex("""stream:true 暂不支持""").containsMatchIn(runtimeJs),
        )
    }

    @Test
    fun `extensionSettings 保证 connectionManager 形状`() {
        // 上游 extensions.js:172
        //   connectionManager: { selectedProfile: '', profiles: [] }
        // getSupportedProfiles() 第一行就读 .profiles，缺键直接 TypeError。
        assertTrue(
            "ensureStStateShapes 必须保证 extensionSettings.connectionManager 存在（上游 extensions.js:172）。",
            Regex("""es\.connectionManager\s*=\s*\{\s*selectedProfile:\s*''\s*,\s*profiles:\s*\[\]\s*\}""")
                .containsMatchIn(runtimeJs),
        )
        assertTrue(
            "必须保证 connectionManager.profiles 是数组。",
            Regex("""if\s*\(!Array\.isArray\(es\.connectionManager\.profiles\)\)""").containsMatchIn(runtimeJs),
        )
    }

    @Test
    fun `Kotlin 侧有 connectionProfiles 的注入点与桥`() {
        // 与 stRuntimeDir 同样的双路径策略（Gradle 单测工作目录不固定）。
        val manager = listOf(
            File("src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt"),
            File("app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt"),
        ).firstOrNull { it.isFile }
        assertTrue(
            "找不到 TavernRuntimeManager.kt，尝试过 src/main/... 与 app/src/main/...",
            manager != null,
        )
        val src = manager!!.readText()

        assertTrue(
            "TavernRuntimeManager 必须有 connectionProfilesSupplier 注入点。",
            src.contains("connectionProfilesSupplier"),
        )
        assertTrue(
            "TavernRuntimeManager 必须暴露 getConnectionProfilesJson() 给 JS。",
            src.contains("fun getConnectionProfilesJson()"),
        )
        // 安全红线：绝不把密钥下发到 JS。
        // 注意 secret-id 的拼装发生在 ChatPage.kt（真正读 providers 的地方），
        // 不在 TavernRuntimeManager.kt（只做桥转发），所以要单独找那个文件。
        val chatPage = listOf(
            File("src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt"),
            File("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt"),
        ).firstOrNull { it.isFile }
        assertTrue("找不到 ChatPage.kt（connectionProfilesSupplier 的注入点）", chatPage != null)
        val chatSrc = chatPage!!.readText()
        assertTrue(
            "ChatPage.kt 里必须有 connectionProfilesSupplier 注入。",
            chatSrc.contains("connectionProfilesSupplier"),
        )
        assertTrue(
            "connectionProfiles 的 secret-id 必须恒为空串（安全红线：不下发 API Key）。",
            Regex("""put\("secret-id",\s*JsonPrimitive\(""\)\)""").containsMatchIn(chatSrc),
        )
        assertTrue(
            "ChatPage.kt 里不允许把 provider 的 apiKey 写进 profile JSON（安全红线）。",
            !Regex("""put\("[^"]*",\s*JsonPrimitive\([^)]*apiKey""").containsMatchIn(chatSrc),
        )
    }
}
