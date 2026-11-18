package me.rerere.rikkahub.data.st.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 ST 模块 URL → shim 路径的映射规则。
 *
 * 这些规则不是猜的：每一条都对应 dev-scripts/plugin_compat_lab.mjs
 * 里 38 个真实社区插件的实测失败。删掉任何一条都会让对应插件整模块加载失败。
 */
class StCompatRouteTest {

    /**
     * 真实 st-compat 目录的存在性查询。
     *
     * 层级归一化依赖「去掉 extensions/ 之后是否真有这个模块」，
     * 所以测试必须查真实文件，而不是假设。
     */
    private val compatRoot: java.io.File = run {
        val candidates = listOf(
            java.io.File("src/main/assets/st-runtime/st-compat"),
            java.io.File("app/src/main/assets/st-runtime/st-compat"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("st-compat 目录未找到（试过 ${candidates.map { it.absolutePath }}）")
    }

    private fun route(path: String): String? =
        resolveStCompatShimPathPure(path) { rel -> java.io.File(compatRoot, rel).isFile }

    // ---------------------------------------------------------------- 基础映射

    @Test
    fun rootModulesMapToCompatShims() {
        assertEquals("st-runtime/st-compat/script.js", route("/script.js"))
        assertEquals("st-runtime/st-compat/lib.js", route("/lib.js"))
    }

    @Test
    fun scriptsModulesMapToCompatTree() {
        assertEquals(
            "st-runtime/st-compat/slash-commands/SlashCommand.js",
            route("/scripts/slash-commands/SlashCommand.js"),
        )
        assertEquals(
            "st-runtime/st-compat/slash-commands/SlashCommandParser.js",
            route("/scripts/slash-commands/SlashCommandParser.js"),
        )
        assertEquals(
            "st-runtime/st-compat/extensions/regex/engine.js",
            route("/scripts/extensions/regex/engine.js"),
        )
    }

    @Test
    fun vendorStModulesHaveDedicatedFiles() {
        assertEquals("st-runtime/vendor/st/events.js", route("/scripts/events.js"))
        assertEquals(
            "st-runtime/vendor/st/eventemitter.js",
            route("/scripts/eventemitter.js"),
        )
    }

    // ---------------------------------------------------------------- 层级归一化

    /**
     * 插件入口在子目录（如 `dist/index.js`）时，`../../../script.js`
     * 归一化后是 `/scripts/script.js`，而 ST 的真实位置是 public 根的 `/script.js`。
     *
     * 实测：不做这条归一化，7 个真实插件整模块加载失败。
     */
    @Test
    fun deepEntryLevelScriptJsFallsBackToRoot() {
        assertEquals("st-runtime/st-compat/script.js", route("/scripts/script.js"))
        assertEquals("st-runtime/st-compat/lib.js", route("/scripts/lib.js"))
    }

    /** 入口更深时会多退一级，出现 scripts/extensions/ 前缀。 */
    @Test
    fun repeatedExtensionsSegmentsAreCollapsed() {
        assertEquals(
            "st-runtime/st-compat/script.js",
            route("/scripts/extensions/script.js"),
        )
        assertEquals(
            "st-runtime/st-compat/lib.js",
            route("/scripts/extensions/lib.js"),
        )
        assertEquals(
            "st-runtime/st-compat/extensions.js",
            route("/scripts/extensions/extensions.js"),
        )
    }

    // ---------------------------------------------------------------- lib 归一化

    /**
     * `public/lib/` 是 ST 的运行时库目录（yaml / zod / dompurify /
     * eventemitter / dialog-polyfill …），打包在 vendor/lib 下。
     *
     * 插件上溯层数不同会得到三种形态，都必须命中同一批文件。
     */
    @Test
    fun libModulesMapToVendorLibAtAnyDepth() {
        for (path in listOf(
            "/lib/yaml.esm.js",
            "/scripts/lib/yaml.esm.js",
            "/scripts/extensions/lib/yaml.esm.js",
            "/scripts/extensions/third-party/x/lib/yaml.esm.js",
        )) {
            assertEquals(
                "yaml.esm.js 应映射到 vendor/lib（路径=$path）",
                "st-runtime/vendor/lib/yaml.esm.js",
                route(path),
            )
        }
    }

    @Test
    fun eventemitterInLibGoesToVendorLib() {
        assertEquals(
            "st-runtime/vendor/lib/eventemitter.js",
            route("/scripts/extensions/lib/eventemitter.js"),
        )
    }

    @Test
    fun dialogPolyfillGoesToVendorLib() {
        assertEquals(
            "st-runtime/vendor/lib/dialog-polyfill.esm.js",
            route("/scripts/extensions/lib/dialog-polyfill.esm.js"),
        )
    }

    // ---------------------------------------------------------------- 安全

    @Test
    fun rejectsTraversalAndNonJsPaths() {
        assertNull(route("/scripts/../secret.js"))
        assertNull(route("/scripts/a/../../b.js"))
        assertNull(route("/scripts/foo.txt"))
        assertNull(route("/not-st/whatever.js"))
        assertNull(route("/"))
    }

    @Test
    fun rejectsPathsWithIllegalCharacters() {
        assertNull(route("/scripts/foo bar.js"))
        assertNull(route("/scripts/foo%2Fbar.js"))
        assertNull(route("/scripts/foo;rm -rf.js"))
    }

    @Test
    fun plausibilityCheckAcceptsNormalModulePaths() {
        assertTrue(isPlausibleStModulePathPure("script.js"))
        assertTrue(isPlausibleStModulePathPure("a/b.js"))
        assertTrue(isPlausibleStModulePathPure("slash-commands/SlashCommand.js"))
        assertTrue(isPlausibleStModulePathPure("lib/yaml.esm.js"))
    }

    // ---------------------------------------------------------------- 真实插件路径

    /**
     * 这些是 38 个真实插件里**实际出现**的导入路径（归一化后形态）。
     * 任何一条返回 null 都会让对应插件加载失败。
     */
    @Test
    fun realPluginImportPathsAllResolve() {
        val cases = mapOf(
            "/scripts/slash-commands/SlashCommand.js" to "st-runtime/st-compat/slash-commands/SlashCommand.js",
            "/scripts/slash-commands/SlashCommandParser.js" to "st-runtime/st-compat/slash-commands/SlashCommandParser.js",
            "/scripts/slash-commands/SlashCommandArgument.js" to "st-runtime/st-compat/slash-commands/SlashCommandArgument.js",
            "/scripts/slash-commands/SlashCommandCommonEnumsProvider.js" to "st-runtime/st-compat/slash-commands/SlashCommandCommonEnumsProvider.js",
            "/scripts/extensions/regex/engine.js" to "st-runtime/st-compat/extensions/regex/engine.js",
            "/scripts/extensions.js" to "st-runtime/st-compat/extensions.js",
            "/scripts/preset-manager.js" to "st-runtime/st-compat/preset-manager.js",
            "/scripts/utils.js" to "st-runtime/st-compat/utils.js",
            "/scripts/power-user.js" to "st-runtime/st-compat/power-user.js",
            "/script.js" to "st-runtime/st-compat/script.js",
            "/lib.js" to "st-runtime/st-compat/lib.js",
        )
        cases.forEach { (url, expected) ->
            assertEquals("真实插件路径 $url 必须解析成功", expected, route(url))
        }
    }
}
