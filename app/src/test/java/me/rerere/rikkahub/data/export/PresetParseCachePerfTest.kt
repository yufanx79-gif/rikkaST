package me.rerere.rikkahub.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `parseActiveStPresetConfig` 的缓存正确性与性能守卫。
 *
 * ## 背景
 *
 * 真机日志：`[transformer] slow #3 PromptInjectionTransformer 449ms`。
 * 一次生成里这个函数会被调用 8+ 次（transformer、GenerationLoop 的预设后处理、
 * ChatService 的续写分支…），每次都 `parseToJsonElement` 整份预设 JSON。
 * Meowssiah 2.0.11 = 543 KB，8 次 ≈ 449ms，与实测吻合。
 *
 * 修复：按 (预设JSON内容, 启用分组) 做进程级 LRU 缓存。
 *
 * ## 这个测试保证什么
 *
 * 1. 相同输入 → 返回**同一个实例**（命中缓存，没有重新解析）
 * 2. 不同分组 → 不同结果（缓存键包含分组）
 * 3. 预设内容变化 → 缓存失效（不会读到脏数据）
 * 4. 用真实 543KB 预设实测：第二次调用显著快于第一次
 */
class PresetParseCachePerfTest {

    private fun realPresetJson(): String? {
        val f = listOf(
            File("src/test/resources/presets/Meowssiah-2.0.11.json"),
            File("app/src/test/resources/presets/Meowssiah-2.0.11.json"),
        ).firstOrNull { it.isFile } ?: return null
        // 真实预设文件本身就是 {"Meowssiah": "<raw json>"} 形状（导出产物），直接用
        return f.readText()
    }

    @Test
    fun sameInputReturnsCachedInstance() {
        val json = """{"预设A":"{\"temperature\":0.8,\"prompts\":[]}"}"""
        val groups = setOf("预设A")

        val first = parseActiveStPresetConfig(json, groups)
        val second = parseActiveStPresetConfig(json, groups)

        // 命中缓存时必须返回同一个实例（data class 结构相等不够，要证明没重新解析）
        assertSame("相同输入必须命中缓存并返回同一实例", first, second)
    }

    @Test
    fun differentGroupsProduceDifferentCacheEntries() {
        val json = """{"A":"{\"temperature\":0.8}","B":"{\"temperature\":0.3}"}"""

        val a = parseActiveStPresetConfig(json, setOf("A"))
        val b = parseActiveStPresetConfig(json, setOf("B"))

        assertNotSame("不同分组必须是不同缓存条目", a, b)
    }

    @Test
    fun changedPresetContentInvalidatesCache() {
        val groups = setOf("P")
        val v1 = parseActiveStPresetConfig("""{"P":"{\"temperature\":0.8}"}""", groups)
        val v2 = parseActiveStPresetConfig("""{"P":"{\"temperature\":0.3}"}""", groups)

        assertNotSame("预设内容变化必须使缓存失效", v1, v2)
    }

    @Test
    fun emptyInputsShortCircuit() {
        assertEquals(StActivePresetConfig(), parseActiveStPresetConfig(null, setOf("x")))
        assertEquals(StActivePresetConfig(), parseActiveStPresetConfig("", setOf("x")))
        assertEquals(StActivePresetConfig(), parseActiveStPresetConfig("""{"x":"{}"}""", emptySet()))
    }

    /**
     * 真实 543KB 预设的实测对比。
     *
     * 第一次是冷解析，第二次应命中缓存。要求第二次至少快 5 倍，
     * 否则说明缓存没生效（例如每次 key 都变了）。
     */
    @Test
    fun realLargePresetSecondCallIsMuchFaster() {
        val json = realPresetJson() ?: run {
            println("[perf] 真实预设文件不存在，跳过")
            return
        }
        val groups = setOf("Meowssiah")
        println("[perf] 预设 JSON 大小 = ${json.length} 字符")

        val coldStart = System.nanoTime()
        val cold = parseActiveStPresetConfig(json, groups)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0

        val warmStart = System.nanoTime()
        val warm = parseActiveStPresetConfig(json, groups)
        val warmMs = (System.nanoTime() - warmStart) / 1_000_000.0

        println("[perf] cold=${"%.2f".format(coldMs)}ms warm=${"%.3f".format(warmMs)}ms")

        assertSame("第二次必须命中缓存", cold, warm)
        assertTrue(
            "缓存命中应显著快于冷解析（cold=${"%.2f".format(coldMs)}ms warm=${"%.3f".format(warmMs)}ms）",
            warmMs * 5 < coldMs || warmMs < 1.0,
        )
    }

    /**
     * 模拟一次生成里的真实调用模式：同一份配置被 8 处调用。
     * 只有第一次付解析成本，其余 7 次必须命中缓存。
     */
    @Test
    fun eightRepeatedCallsParseOnlyOnce() {
        val json = realPresetJson() ?: """
            {"Meowssiah":"{\"temperature\":1.09,\"prompts\":[{\"identifier\":\"a\",\"content\":\"x\"}]}"}
        """.trimIndent()
        val groups = setOf("Meowssiah")

        val results = (1..8).map { parseActiveStPresetConfig(json, groups) }
        val distinct = results.distinctBy { System.identityHashCode(it) }
        println("[perf] 8 次调用产生 ${distinct.size} 个不同实例")

        assertEquals("8 次相同调用必须只解析一次（同一个缓存实例）", 1, distinct.size)
    }
}
