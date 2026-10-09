package me.rerere.rikkahub.data.st.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * [v240 W5] Kotlin 真 tokenizer 单测。
 *
 * 金标准：SillyTavern 1.18 侧的真 tiktoken BPE（`st-compat/tokenizers.js`）与
 * OpenAI 官方 tiktoken 的等价实现；本测试的期望值由独立 Python 复刻（官方 pat_str +
 * byte_pair_merge + 本地 .tiktoken 词表）离线算出，并经官方 Rust CoreBPE 逐 token 对标。
 *
 * 词表直接从源码树加载（`app/src/main/assets/st-runtime/vendor/data/` 下的 .tiktoken）：
 * JVM 单测读不到 Android assets，但与打包进 APK 的是同一份文件。
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TikTokenTest {

    companion object {
        private val openerCalls = AtomicInteger()

        private fun assetOpener(path: String): InputStream? {
            openerCalls.incrementAndGet()
            val candidates = listOf(
                File("src/main/assets/$path"),
                File("app/src/main/assets/$path"),
            )
            val file = candidates.firstOrNull { it.isFile } ?: return null
            return file.inputStream()
        }

        @JvmStatic
        @BeforeClass
        fun attach() {
            // 清掉本进程内其它用例可能缓存的「未挂载 loader」失败，再挂真实词表
            TikToken.resetForTest()
            TikToken.attach(::assetOpener)
        }
    }

    private fun cl100k(text: String) = TikToken.count(text, TikToken.Encoding.CL100K_BASE)
    private fun o200k(text: String) = TikToken.count(text, TikToken.Encoding.O200K_BASE)

    private fun assertBoth(text: String, cl: Int, o: Int) {
        assertEquals(
            "cl100k: ${text.take(40)} (loadFailure=${TikToken.lastFailure(TikToken.Encoding.CL100K_BASE)?.message})",
            cl, cl100k(text),
        )
        assertEquals(
            "o200k: ${text.take(40)} (loadFailure=${TikToken.lastFailure(TikToken.Encoding.O200K_BASE)?.message})",
            o, o200k(text),
        )
    }

    // ==================== ground truth（Python 复刻 + 官方 Rust 对标） ====================

    @Test
    fun `matches tiktoken ground truth for 12 samples`() {
        assertBoth("", 0, 0)
        assertBoth("Hello, world!", 4, 4)
        assertBoth("The quick brown fox jumps over the lazy dog.", 10, 10)
        assertBoth("你好，世界！今天天气不错。", 15, 9)
        assertBoth("混合 mixed 文本 with English 空格 和 标点符号。", 19, 15)
        assertBoth("RikkaHub 是一个 Kotlin + Jetpack Compose 原生 Android LLM 客户端。", 24, 19)
        assertBoth("def fibonacci(n):\n    return n if n < 2 else fibonacci(n-1) + fibonacci(n-2)", 24, 24)
        assertBoth("emoji 😀🎉 and symbols →★", 9, 8)
        assertBoth("  \t\n  multiple   spaces\t\t", 6, 7)
        assertBoth("世界, hello 12345 67890 abcDEF", 13, 11)
        assertBoth("酒馆世界书条目：<world_info>魔法少女是不会败北恶堕的吧！</world_info>", 38, 28)
        assertBoth("'s't're've'm'll'd contractions: I'm, you've, we'll, they're, don't, he's", 27, 20)
    }

    @Test
    fun `matches tiktoken ground truth for mixed extra samples`() {
        assertBoth("今天 review 了 3 个 PR：tokenizer 精度 99.9%，LGTM！", 24, 21)
        assertBoth("fun main() {\n    println(\"你好, Kotlin!\")\n}", 13, 12)
        assertBoth("模型 A100 80GB × 4 张卡，batch_size=32, lr=1e-5。", 27, 24)
        assertBoth("错误日志: java.lang.NullPointerException at MainActivity.kt:42\n", 15, 15)
        assertBoth("《魔法少女小圆》TV 版 2011 年播出，评分 9.0/10。", 31, 25)
        assertBoth("微信 unread 消息 12 条，emoji 提醒 🔔！", 19, 14)
    }

    @Test
    fun `special tokens are not in the vocabulary and count as plain text`() {
        // .tiktoken 只有 mergeable ranks（不含 <|endoftext|> 等 special token）
        assertBoth("<|endoftext|>", 7, 7)
    }

    // ==================== 缓存 / 单次加载 ====================

    @Test
    fun `vocabulary is loaded once and reused`() {
        // 词表可能已被其它用例加载；先确保加载，再记录 opener 调用次数
        assertNotNull(cl100k("warm up"))
        assertEquals(100_256, TikToken.vocabSize(TikToken.Encoding.CL100K_BASE))
        assertTrue(TikToken.isLoaded(TikToken.Encoding.CL100K_BASE))
        val callsAfterLoad = openerCalls.get()
        repeat(20) { cl100k("repeat $it") }
        assertEquals("词表命中缓存后不得再次读取资源", callsAfterLoad, openerCalls.get())
        // o200k 独立缓存（199998 行）
        assertNotNull(o200k("warm up"))
        assertEquals(199_998, TikToken.vocabSize(TikToken.Encoding.O200K_BASE))
    }

    @Test
    fun `chunk cache returns stable results`() {
        val first = cl100k("中国 China 混排 mixed")
        repeat(10) { assertEquals(first, cl100k("中国 China 混排 mixed")) }
    }

    // ==================== 降级路径 ====================

    @Test
    fun `missing vocabulary falls back to heuristic`() {
        TikToken.resetForTest()
        try {
            TikToken.attach { null } // 词表缺失
            assertNull(cl100k("hello"))
            assertNull(o200k("hello"))
            // 统一入口必须回退启发式，绝不抛错/阻断
            assertEquals(estimateTokensHeuristic("你好 world"), countTokens("你好 world", TokenizerMode.CL100K))
            assertEquals(estimateTokensHeuristic("你好 world"), countTokens("你好 world", TokenizerMode.O200K))
            // OFF 模式直接用启发式
            assertEquals(estimateTokensHeuristic("你好 world"), countTokens("你好 world", TokenizerMode.OFF))
        } finally {
            TikToken.resetForTest()
            TikToken.attach(::assetOpener)
            // 恢复后必须能重新加载（否则会污染后续用例）
            assertNotNull("词表恢复失败：${TikToken.lastFailure(TikToken.Encoding.CL100K_BASE)?.message}", cl100k("restore"))
        }
    }

    @Test
    fun `mode parsing is tolerant`() {
        assertEquals(TokenizerMode.CL100K, TokenizerMode.parse(null))
        assertEquals(TokenizerMode.CL100K, TokenizerMode.parse("garbage"))
        assertEquals(TokenizerMode.CL100K, TokenizerMode.parse("cl100k"))
        assertEquals(TokenizerMode.O200K, TokenizerMode.parse("o200k_base"))
        assertEquals(TokenizerMode.OFF, TokenizerMode.parse("off"))
        assertEquals(TokenizerMode.OFF, TokenizerMode.parse("NONE"))
    }

    @Test
    fun `heuristic keeps previous semantics`() {
        assertEquals(0, estimateTokensHeuristic(""))
        assertEquals(4, estimateTokensHeuristic("你好世界")) // 4 CJK
        assertEquals(1, estimateTokensHeuristic("abcd")) // 4 ASCII → 1
        assertEquals(2, estimateTokensHeuristic("abcdef")) // 6 ASCII → 2（向上取整）
    }
}
