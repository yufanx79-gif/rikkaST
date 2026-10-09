package me.rerere.rikkahub.data.st.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

/**
 * ST 宏引擎移植测试（批次 A 收尾）。
 *
 * 金标准来源：
 * - seedrandom / droll 向量：Node 运行原版 seedrandom 3.0.5 / droll 0.2.1 生成；
 * - unknown / scoped 边界输出：`refdeps/chevtest/eval.mjs`（真实 ST MacroCstWalker +
 *   chevrotain 11.2.0）裁定；
 * - if / trim / scoped 行为断言：SillyTavern 1.18.0 `tests/frontend/MacroEngine.e2e.js`；
 * - pick 链路：Node 复刻 cyrb53（与 WorldInfoEngine.getStringHash 金标准对齐后）计算。
 */
class MacroEngineTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerMacros() {
            MacroRegistry.clear()
            MacroDefinitions.registerAll()
        }
    }

    // ==================== 工具 ====================

    private class FixedRandom(private val values: DoubleArray) : Random() {
        private var idx = 0
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = values[idx++ % values.size]
    }

    private fun eval(input: String?, env: MacroEnv = MacroEnv()): String =
        MacroEngine.evaluate(input, env)

    private fun assertSeedSequence(seed: String, expected: DoubleArray) {
        val rng = StSeedrandom.create(seed)
        expected.forEachIndexed { i, e ->
            val a = rng()
            assertEquals("seed=${seed.substring(0, minOf(20, seed.length))} index=$i", e.toRawBits(), a.toRawBits())
        }
    }

    private fun fixedTimeEnv(): MacroEnv {
        val zone = ZoneId.of("Asia/Shanghai")
        return MacroEnv(
            clock = Clock.fixed(Instant.parse("2026-09-13T01:30:00Z"), zone),
            zone = zone,
            locale = Locale.ENGLISH,
        )
    }

    // ==================== StSeedrandom：bit-exact 金标准 ====================

    @Test
    fun `seedrandom matches JS golden vectors bit-exact`() {
        assertSeedSequence(
            "hello.",
            doubleArrayOf(0.9282578795792454, 0.3752569768646784, 0.7316977468919549),
        )
        assertSeedSequence(
            "1234567890123456",
            doubleArrayOf(0.23691131745469576, 0.301161735093768, 0.9853992885738685),
        )
        assertSeedSequence(
            "982451653-1234567890123456-42",
            doubleArrayOf(0.6708231180262622, 0.7233352476021697, 0.37388986169595),
        )
        assertSeedSequence(
            "0-0-0",
            doubleArrayOf(0.3287419580334417, 0.12091081072617609, 0.6795676340495219),
        )
    }

    @Test
    fun `seedrandom empty seed treated as key 0`() {
        assertSeedSequence(
            "",
            doubleArrayOf(0.23144008215179881, 0.27404636548159655, 0.7901279251811976),
        )
    }

    @Test
    fun `seedrandom wraps key index at 256 for long seeds`() {
        assertSeedSequence(
            "a".repeat(300),
            doubleArrayOf(0.7896636759683583, 0.46838180524185613, 0.23467569070185423),
        )
    }

    @Test
    fun `seedrandom pick chain intermediate seeds`() {
        assertSeedSequence("5666147980718751", doubleArrayOf(0.3507212152873704))
        assertSeedSequence("5816938514428498", doubleArrayOf(0.9424125003543194))
        assertSeedSequence("6728600462010650", doubleArrayOf(0.3467273917977318))
        assertSeedSequence("3369774786067250", doubleArrayOf(0.48674455642269565))
    }

    // ==================== StDroll ====================

    @Test
    fun `droll parses formulas matching JS regex`() {
        StDroll.parse("1d20")!!.let {
            assertEquals(1, it.numDice); assertEquals(20, it.numSides); assertEquals(0, it.modifier)
        }
        StDroll.parse("3d6+4")!!.let {
            assertEquals(3, it.numDice); assertEquals(6, it.numSides); assertEquals(4, it.modifier)
        }
        StDroll.parse("10d10-2")!!.let {
            assertEquals(10, it.numDice); assertEquals(10, it.numSides); assertEquals(-2, it.modifier)
        }
        StDroll.parse("d6")!!.let {
            assertEquals(1, it.numDice); assertEquals(6, it.numSides); assertEquals(0, it.modifier)
        }
        assertEquals(null, StDroll.parse("0d6"))
        assertEquals(null, StDroll.parse("d0"))
        assertEquals(null, StDroll.parse("6"))
        assertEquals(null, StDroll.parse("1d6x"))
        assertTrue(StDroll.validate("2D8+1")) // 大小写不敏感
    }

    @Test
    fun `droll rolls dice with deterministic random`() {
        assertEquals(1, StDroll.roll("1d6", FixedRandom(doubleArrayOf(0.0))))
        assertEquals(6, StDroll.roll("1d6", FixedRandom(doubleArrayOf(0.999))))
        assertEquals(4, StDroll.roll("d6", FixedRandom(doubleArrayOf(0.5))))
        assertEquals(15, StDroll.roll("3d6+4", FixedRandom(doubleArrayOf(0.0, 0.5, 0.999))))
        assertEquals(null, StDroll.roll("nope", FixedRandom(doubleArrayOf(0.5))))
    }

    // ==================== Core：基础宏 ====================

    @Test
    fun `space and newline macros`() {
        assertEquals("a b", eval("a{{space}}b"))
        assertEquals("a   b", eval("a{{space::3}}b"))
        assertEquals("a\nb", eval("a{{newline}}b"))
        assertEquals("a\n\nb", eval("a{{newline::2}}b"))
    }

    @Test
    fun `space and newline reject non-integer count (strict, kept raw)`() {
        assertEquals("{{space::x}}", eval("{{space::x}}"))
        assertEquals("{{space::1::2}}", eval("{{space::1::2}}"))
    }

    @Test
    fun `noop returns empty`() {
        assertEquals("ab", eval("a{{noop}}b"))
    }

    @Test
    fun `input banned outlet return empty`() {
        assertEquals("", eval("{{input}}"))
        assertEquals("", eval("{{banned::word}}"))
        assertEquals("", eval("{{outlet::key}}"))
    }

    @Test
    fun `max token macros`() {
        val env = MacroEnv().apply {
            system.maxContextTokens = 8000
            system.maxResponseTokens = 2000
        }
        assertEquals("6000", eval("{{maxPrompt}}", env))
        assertEquals("6000", eval("{{maxPromptTokens}}", env))
        assertEquals("8000", eval("{{maxContext}}", env))
        assertEquals("8000", eval("{{maxContextTokens}}", env))
        assertEquals("2000", eval("{{maxResponse}}", env))
        assertEquals("2000", eval("{{maxResponseTokens}}", env))
        assertEquals("", eval("{{maxPrompt}}", MacroEnv()))
    }

    @Test
    fun `reverse macro uses code points`() {
        assertEquals("anaL ma I", eval("{{reverse::I am Lana}}"))
        assertEquals("b\uD83D\uDE00a", eval("{{reverse::a\uD83D\uDE00b}}"))
    }

    @Test
    fun `legacy single colon and whitespace argument forms`() {
        assertEquals("fed::cba", eval("{{reverse:abc::def}}"))
        assertEquals("fed:cba", eval("{{reverse:abc:def}}"))
        assertEquals("fed cba", eval("{{reverse abc def}}"))
        assertEquals("1", eval("{{roll 1d1}}"))
    }

    @Test
    fun `roll macro`() {
        assertEquals("1", eval("{{roll::1d1}}"))
        val v = eval("{{roll::6}}").toInt()
        assertTrue("roll result $v in 1..6", v in 1..6)
        assertEquals("", eval("{{roll::abc}}"))
        assertEquals("{{roll}}", eval("{{roll}}"))
    }

    @Test
    fun `comment macros produce empty output`() {
        assertEquals("x", eval("{{// hello}}x"))
        assertEquals("x", eval("{{comment::hi}}x"))
        assertEquals("", eval("{{///}}"))
        assertEquals("a", eval("{{//}}a{{///}}"))
        assertEquals("", eval("{{//}}"))
        assertEquals("First\nSecond", eval("{{//}}First\nSecond{{///}}"))
    }

    @Test
    fun `trim macro non-scoped legacy strips surrounding newlines`() {
        assertEquals("foobar", eval("foo\n\n{{trim}}\n\nbar"))
    }

    @Test
    fun `trim macro scoped`() {
        assertEquals("hello world", eval("{{trim}}  hello world  {{/trim}}"))
        assertEquals("content", eval("{{trim}}\n\n  content{{/trim}}"))
        assertEquals("content", eval("{{trim}}content  \n\n{{/trim}}"))
        assertEquals("Hello User", eval("{{trim}}  Hello {{user}}  {{/trim}}", MacroEnv().apply { names.user = "User" }))
        assertEquals("outer inner outer", eval("{{trim}}  outer {{trim}}  inner  {{/trim}} outer  {{/trim}}"))
    }

    @Test
    fun `escaped braces are unescaped but not treated as macros`() {
        assertEquals("{{user}}", eval("\\{\\{user}}"))
        assertEquals("{a}", eval("\\{a\\}"))
    }

    @Test
    fun `legacy angle bracket markers are rewritten`() {
        val env = MacroEnv().apply {
            names.user = "User"
            names.char = "Character"
            names.group = "Group"
            names.groupNotMuted = "GroupNM"
        }
        assertEquals("User hi Character", eval("<USER> hi <BOT>", env))
        assertEquals("Character", eval("<CHAR>", env))
        assertEquals("Group", eval("<GROUP>", env))
        // charIfNotGroup 是 {{group}} 的别名（对齐 ST env-macros.js）；群聊 → 群成员；非群聊由 env 构建方填角色名（ST e2e 回退语义）
        assertEquals("Group", eval("<CHARIFNOTGROUP>", env))
        assertEquals("Character", eval("<CHARIFNOTGROUP>", MacroEnv().apply { names.char = "Character"; names.group = "Character" }))
    }

    @Test
    fun `unknown macros keep syntax with nested resolved`() {
        assertEquals("{{unknown}}", eval("{{unknown}}"))
        assertEquals("{{unknown::a}}", eval("{{unknown::a}}"))
        assertEquals("{{unknown::User}}", eval("{{unknown::{{user}}}}", MacroEnv().apply { names.user = "User" }))
    }

    @Test
    fun `unmatched closing macro stays raw`() {
        assertEquals("{{/unknown}}", eval("{{/unknown}}"))
        assertEquals("{{/if}}", eval("{{/if}}"))
    }

    /**
     * 重要：以下「未知宏 + scoped」输出由真实 ST MacroCstWalker 实测裁定
     * （refdeps/chevtest/eval.mjs）。ST 对未知宏返回 `{{rawInner}}`，rawInner 重建
     * 会把 scoped 内容替换进开标签内部区间——闭合标签在输出中被吞掉（保留另一对花括号）。
     * 这是 ST 的原样行为（怪癖），移植须逐字符一致。
     */
    @Test
    fun `scoped unknown macro reproduces ST rawInner quirk`() {
        val env = MacroEnv().apply { names.user = "User" }
        assertEquals("{{unknown}}x}}", eval("{{unknown}}x{{/unknown}}", env))
        assertEquals("{{unknown}}aUserb}}", eval("{{unknown}}a{{user}}b{{/unknown}}", env))
        assertEquals("a{{unknown}}b}}c", eval("a{{unknown}}b{{/unknown}}c", env))
        assertEquals("{{unknown}}x}}", eval("{{unknown}} x {{/unknown}}", env))
        assertEquals("{{unknown}}}}", eval("{{unknown}} {{/unknown}}", env))
        assertEquals("{{#unknown}} x }}", eval("{{#unknown}} x {{/unknown}}", env))
        assertEquals("{{unknown::a}}x}}", eval("{{unknown::a}}x{{/unknown}}", env))
        assertEquals("{{unknown::a::b}}x}}", eval("{{unknown::a::b}}x{{/unknown}}", env))
        assertEquals("{{unknown zz}}x}}", eval("{{unknown zz}}x{{/unknown}}", env))
        assertEquals(
            "{{unknown}}x}}y{{unknown}}z}}",
            eval("{{unknown}}x{{/unknown}}y{{unknown}}z{{/unknown}}", env),
        )
    }

    @Test
    fun `known macro that cannot accept scoped content keeps both tags raw`() {
        assertEquals("{{user}}x{{/user}}", eval("{{user}}x{{/user}}", MacroEnv().apply { names.user = "User" }))
    }

    // ==================== if / else ====================

    @Test
    fun `if basic truthiness`() {
        assertEquals("content", eval("{{if something}}content{{/if}}"))
        assertEquals("then", eval("{{if yes}}then{{else}}else{{/if}}"))
        assertEquals("else", eval("{{if::}}then{{else}}else{{/if}}"))
        assertEquals("no", eval("{{if::false}}yes{{else}}no{{/if}}"))
    }

    @Test
    fun `if resolves zero-arg macro names in condition`() {
        val env = MacroEnv().apply {
            names.char = "Character"
            names.user = "User"
        }
        assertEquals("Character exists", eval("{{if char}}{{char}} exists{{/if}}", env))
        assertEquals("Has char", eval("{{if char}}Has char{{else}}No char{{/if}}", env))
        assertEquals("Hello User", eval("{{if yes}}Hello {{user}}{{else}}Goodbye {{char}}{{/if}}", env))
        assertEquals("Goodbye Character", eval("{{if::}}Hello {{user}}{{else}}Goodbye {{char}}{{/if}}", env))
    }

    @Test
    fun `if with noop condition takes else branch`() {
        assertEquals("Empty", eval("{{if noop}}Has value{{else}}Empty{{/if}}"))
    }

    @Test
    fun `if inverted condition`() {
        assertEquals("[end]", eval("{{if !yes}}shown{{/if}}[end]"))
        assertEquals("shown", eval("{{if !false}}shown{{/if}}"))
    }

    @Test
    fun `if trims branches unless preserveWhitespace flag`() {
        assertEquals("then", eval("{{if yes}}  then  {{else}}  else  {{/if}}"))
        assertEquals("then", eval("{{if yes}}\n  then\n{{else}}\n  else\n{{/if}}"))
        assertEquals("else", eval("{{if::}}\n  then\n{{else}}\n  else\n{{/if}}"))
        assertEquals("\nX\n", eval("{{#if true}}\nX\n{{/if}}"))
    }

    @Test
    fun `if nested branches`() {
        assertEquals(
            "outer-theninner-then",
            eval("{{if yes}}outer-then{{if yes}}inner-then{{else}}inner-else{{/if}}{{else}}outer-else{{/if}}"),
        )
        assertEquals(
            "outer-elseinner-then",
            eval("{{if::}}outer-then{{else}}outer-else{{if yes}}inner-then{{else}}inner-else{{/if}}{{/if}}"),
        )
        assertEquals("BD", eval("{{if::}}A{{else}}B{{if::}}C{{else}}D{{/if}}{{/if}}"))
    }

    @Test
    fun `if empty else branch yields empty`() {
        assertEquals("[end]", eval("{{if::}}content{{/if}}[end]"))
    }

    @Test
    fun `if variable shorthand conditions`() {
        val env = MacroEnv()
        env.variables.local.set("flag", "1")
        assertEquals("Y", eval("{{if .flag}}Y{{/if}}", env))
        env.variables.local.set("flag", "0")
        assertEquals("N", eval("{{if .flag}}Y{{else}}N{{/if}}", env))
        env.variables.global.set("g", "1")
        assertEquals("G", eval("{{if \$g}}G{{/if}}", env))
        env.variables.global.set("g", "false")
        assertEquals("", eval("{{if \$g}}G{{/if}}", env))
    }

    // ==================== Scoped 机制 ====================

    @Test
    fun `scoped setvar trims and dedents`() {
        assertEquals(
            "[content with whitespace]",
            eval("{{setvar::myvar}}\n  content with whitespace  \n{{/setvar}}[{{getvar::myvar}}]"),
        )
    }

    @Test
    fun `scoped whitespace only content auto-trimmed unless flag`() {
        assertEquals("[]", eval("{{setvar::ws}}   {{/setvar}}[{{getvar::ws}}]"))
        assertEquals("[   ]", eval("{{#setvar::ws}}   {{/setvar}}[{{getvar::ws}}]"))
    }

    @Test
    fun `nested scoped setvar`() {
        assertEquals(
            "AC|B",
            eval("{{setvar::outer}}A{{setvar::inner}}B{{/setvar}}C{{/setvar}}{{getvar::outer}}|{{getvar::inner}}"),
        )
    }

    @Test
    fun `scoped macro at boundaries and consecutive`() {
        assertEquals("result:value", eval("{{setvar::x}}value{{/setvar}}result:{{getvar::x}}"))
        assertEquals("prefix ", eval("prefix {{setvar::x}}value{{/setvar}}"))
        assertEquals(
            "123",
            eval("{{setvar::a}}1{{/setvar}}{{setvar::b}}2{{/setvar}}{{setvar::c}}3{{/setvar}}{{getvar::a}}{{getvar::b}}{{getvar::c}}"),
        )
    }

    @Test
    fun `lone else outside if is removed in postprocessing`() {
        assertEquals("ab", eval("a{{else}}b"))
    }

    // ==================== 变量宏 ====================

    @Test
    fun `setvar getvar basic`() {
        assertEquals("kaiju", eval("{{setvar::x::kaiju}}{{getvar::x}}"))
        assertEquals("", eval("{{getvar::missing}}"))
    }

    @Test
    fun `incvar decvar addvar semantics`() {
        assertEquals("6", eval("{{setvar::n::5}}{{incvar::n}}"))
        assertEquals("4", eval("{{setvar::n::5}}{{decvar::n}}"))
        assertEquals("8", eval("{{setvar::n::5}}{{addvar::n::3}}{{getvar::n}}"))
        assertEquals("ab", eval("{{setvar::s::a}}{{addvar::s::b}}{{getvar::s}}"))
        assertEquals("1", eval("{{incvar::fresh}}"))
    }

    @Test
    fun `hasvar and deletevar`() {
        assertEquals("false", eval("{{hasvar::x}}"))
        assertEquals("true", eval("{{setvar::x::1}}{{hasvar::x}}"))
        assertEquals("false", eval("{{setvar::x::1}}{{flushvar::x}}{{hasvar::x}}"))
        assertEquals("", eval("{{setvar::x::1}}{{deletevar::x}}{{getvar::x}}"))
        assertEquals("true", eval("{{setvar::x::1}}{{varexists::x}}"))
    }

    @Test
    fun `global variable macros`() {
        assertEquals("gv", eval("{{setglobalvar::g::gv}}{{getglobalvar::g}}"))
        assertEquals("3", eval("{{setglobalvar::g::2}}{{incglobalvar::g}}"))
        assertEquals("true", eval("{{setglobalvar::g::1}}{{hasglobalvar::g}}"))
        assertEquals("true", eval("{{setglobalvar::g::1}}{{globalvarexists::g}}"))
        assertEquals("false", eval("{{setglobalvar::g::1}}{{flushglobalvar::g}}{{hasglobalvar::g}}"))
        assertEquals("", eval("{{setglobalvar::g::1}}{{deleteglobalvar::g}}{{getglobalvar::g}}"))
    }

    @Test
    fun `getvar normalizes numeric strings`() {
        assertEquals("3", eval("{{setvar::v::3.0}}{{getvar::v}}"))
        // incvar/decvar 返回新值（ST variable-macros.js）；缺失变量从 0 起 → 1，再自减 → 0
        assertEquals("10", eval("{{incvar::v}}{{decvar::v}}"))
    }

    @Test
    fun `variable shorthand operators`() {
        assertEquals("5", eval("{{.x =5}}{{.x}}"))
        assertEquals("1", eval("{{.c++}}"))
        assertEquals("12", eval("{{.c++}}{{.c++}}"))
        assertEquals("3", eval("{{.n =5}}{{.n -=2}}{{.n}}"))
        assertEquals("true", eval("{{.q =5}}{{.q ==5}}"))
        assertEquals("false", eval("{{.q =5}}{{.q !=5}}"))
        assertEquals("true", eval("{{.n =4}}{{.n >3}}"))
        assertEquals("true", eval("{{.n =4}}{{.n >=4}}"))
        assertEquals("hihi", eval("{{.m ??=hi}}{{.m}}"))
        assertEquals("zbzb", eval("{{.z ||=zb}}{{.z ||=x}}"))
        assertEquals("fb", eval("{{.missing || fb}}"))
    }

    @Test
    fun `variable shorthand global`() {
        assertEquals("g1", eval("{{\$g =g1}}{{\$g}}"))
        assertEquals("2", eval("{{\$g =1}}{{\$g++}}"))
    }

    // ==================== InMemoryMacroVariableStore ====================

    @Test
    fun `in memory store json array push`() {
        val store = InMemoryMacroVariableStore()
        store.set("arr", "[\"a\"]")
        store.add("arr", "b")
        assertEquals("[\"a\",\"b\"]", store.get("arr"))
    }

    @Test
    fun `in memory store add string vs number`() {
        val store = InMemoryMacroVariableStore()
        assertEquals("3", store.add("x", "3").let { store.get("x") })
        store.add("x", "2")
        assertEquals("5", store.get("x"))
        assertEquals("ab", store.add("s", "a").let { store.get("s") }.let { store.add("s", "b"); store.get("s") })
    }

    @Test
    fun `js number normalizer`() {
        assertEquals("3", JsNumberNormalizer.getNormalized("3.0"))
        assertEquals("abc", JsNumberNormalizer.getNormalized("abc"))
        assertEquals("   ", JsNumberNormalizer.getNormalized("   ")) // 空白串按 falsy 保留原样
        assertEquals("", JsNumberNormalizer.getNormalized(""))
        assertEquals("NaN", JsNumberNormalizer.format(Double.NaN))
    }

    // ==================== Chat 宏 ====================

    @Test
    fun `chat macros basic`() {
        val env = MacroEnv(
            chat = listOf(
                MacroChatMessage("Hello", isUser = true),
                MacroChatMessage("Hi there", isUser = false, currentSwipeId = 1, swipeCount = 1),
            ),
        )
        assertEquals("Hi there", eval("{{lastMessage}}", env))
        assertEquals("1", eval("{{lastMessageId}}", env))
        assertEquals("Hello", eval("{{lastUserMessage}}", env))
        assertEquals("Hi there", eval("{{lastCharMessage}}", env))
        assertEquals("1", eval("{{lastSwipeId}}", env))
        assertEquals("1", eval("{{currentSwipeId}}", env))
        assertEquals("0-1", eval("{{allChatRange}}", env))
        assertEquals("0", eval("{{firstDisplayedMessageId}}", env))
        assertEquals("", eval("{{firstIncludedMessageId}}", env))
    }

    @Test
    fun `chat macros skip swipe in progress for last message`() {
        val env = MacroEnv(
            chat = listOf(
                MacroChatMessage("Hello", isUser = true),
                MacroChatMessage("Hi there", isUser = false, currentSwipeId = 1, swipeCount = 1),
                MacroChatMessage("generating...", isUser = false, currentSwipeId = 2, swipeCount = 1),
            ),
        )
        assertEquals("Hi there", eval("{{lastMessage}}", env))
        assertEquals("1", eval("{{lastMessageId}}", env))
        assertEquals("1", eval("{{lastSwipeId}}", env))
        assertEquals("2", eval("{{currentSwipeId}}", env))
    }

    @Test
    fun `first included message id from system`() {
        val env = MacroEnv()
        env.system.lastInContextMessageId = 7
        assertEquals("7", eval("{{firstIncludedMessageId}}", env))
    }

    @Test
    fun `chat macros on empty chat`() {
        val env = MacroEnv()
        assertEquals("", eval("{{lastMessage}}", env))
        assertEquals("", eval("{{lastMessageId}}", env))
        assertEquals("", eval("{{allChatRange}}", env))
        assertEquals("", eval("{{firstDisplayedMessageId}}", env))
    }

    // ==================== Env 宏 ====================

    @Test
    fun `name macros`() {
        val env = MacroEnv().apply {
            names.user = "User"
            names.char = "Character"
            names.group = "Group"
            names.groupNotMuted = "GroupNM"
            names.notChar = "Other"
        }
        assertEquals("User", eval("{{user}}", env))
        assertEquals("Character", eval("{{char}}", env))
        assertEquals("Group", eval("{{group}}", env))
        assertEquals("Group", eval("{{charIfNotGroup}}", env))
        assertEquals("GroupNM", eval("{{groupNotMuted}}", env))
        assertEquals("Other", eval("{{notChar}}", env))
    }

    @Test
    fun `character field macros and aliases`() {
        val env = MacroEnv()
        env.character.description = "desc"
        env.character.personality = "pers"
        env.character.scenario = "scen"
        env.character.persona = "persona"
        env.character.mesExamplesRaw = "raw"
        env.character.charDepthPrompt = "depth"
        env.character.creatorNotes = "notes"
        env.character.charPrompt = "prompt"
        env.character.charInstruction = "instr"
        env.character.version = "2.0"
        assertEquals("desc", eval("{{charDescription}}", env))
        assertEquals("desc", eval("{{description}}", env))
        assertEquals("pers", eval("{{charPersonality}}", env))
        assertEquals("pers", eval("{{personality}}", env))
        assertEquals("scen", eval("{{charScenario}}", env))
        assertEquals("scen", eval("{{scenario}}", env))
        assertEquals("persona", eval("{{persona}}", env))
        assertEquals("raw", eval("{{mesExamplesRaw}}", env))
        assertEquals("depth", eval("{{charDepthPrompt}}", env))
        assertEquals("notes", eval("{{charCreatorNotes}}", env))
        assertEquals("notes", eval("{{creatorNotes}}", env))
        assertEquals("prompt", eval("{{charPrompt}}", env))
        assertEquals("instr", eval("{{charInstruction}}", env))
        assertEquals("2.0", eval("{{charVersion}}", env))
        assertEquals("2.0", eval("{{version}}", env))
        assertEquals("2.0", eval("{{char_version}}", env))
    }
    // ==================== W1：导演备注宏（authors-note.js:604-614） ====================

    @Test
    fun `authors note macros read env author notes`() {
        // W1 验收：{{authorsNote}}/{{charAuthorsNote}}/{{defaultAuthorsNote}} 三宏注册并读 env.authorNotes。
        val env = MacroEnv()
        env.authorNotes.current = "CURRENT NOTE"
        env.authorNotes.character = "CHAR NOTE"
        env.authorNotes.default = "DEFAULT NOTE"
        assertEquals("CURRENT NOTE", eval("{{authorsNote}}", env))
        assertEquals("CHAR NOTE", eval("{{charAuthorsNote}}", env))
        assertEquals("DEFAULT NOTE", eval("{{defaultAuthorsNote}}", env))
    }

    @Test
    fun `authors note macros fall back to empty when unset`() {
        // 未提供数据源（无会话 / 无备注）时三宏均为空串，而不是保留原文
        val env = MacroEnv()
        assertEquals("", eval("{{authorsNote}}", env))
        assertEquals("", eval("{{charAuthorsNote}}", env))
        assertEquals("", eval("{{defaultAuthorsNote}}", env))
        assertEquals("", eval("{{AuthorsNote}}", env))
    }

    @Test
    fun `authors note macros match rikkaST mapping`() {
        // rikkaST 映射（语义差异已登记在 MacroEnv.AuthorNotes）：
        // Settings.authorNote 同时充当 authorsNote 与 defaultAuthorsNote；charAuthorsNote 无数据源恒空。
        val env = MacroEnv().apply {
            authorNotes.current = "全局备注"
            authorNotes.default = "全局备注"
            authorNotes.character = ""
        }
        assertEquals("全局备注", eval("{{authorsNote}}", env))
        assertEquals("全局备注", eval("{{defaultAuthorsNote}}", env))
        assertEquals("", eval("{{charAuthorsNote}}", env))
        // 与其它宏混排时不破坏上下文
        env.names.user = "U"
        assertEquals("[U] 全局备注", eval("[{{user}}] {{authorsNote}}", env))
    }

    @Test
    fun `preset context macros S4 six macros`() {
        // S4 验收：{{original}}/{{charPrompt}}/{{charJailbreak}}/{{personality}}/{{scenario}}/{{mesExamples}}
        // 金标准：ST script.js:2924-2945 getCharacterCardFields（charInstruction 与 charJailbreak 同源）
        val env = MacroEnv()
        env.character.charPrompt = "SYS"
        env.character.charInstruction = "PHI"
        env.character.personality = "PERS"
        env.character.scenario = "SCEN"
        env.character.mesExamplesRaw = "<START>\nHi"
        assertEquals("SYS", eval("{{charPrompt}}", env))
        assertEquals("PHI", eval("{{charJailbreak}}", env))
        assertEquals("PHI", eval("{{charInstruction}}", env))
        assertEquals("PERS", eval("{{personality}}", env))
        assertEquals("PERS", eval("{{charPersonality}}", env))
        assertEquals("SCEN", eval("{{scenario}}", env))
        assertEquals("SCEN", eval("{{charScenario}}", env))
        assertEquals("<START>\nHi\n", eval("{{mesExamples}}", env))
        assertEquals("", eval("{{mesExamples}}", MacroEnv()))
    }

    @Test
    fun `original macro substitutes once then empties`() {
        // S4 验收：{{original}} 单次替换（ST env.functions.original 闭包语义）
        val env = MacroEnv(originalFn = { "ORIG" })
        assertEquals("ORIG", eval("{{original}}", env))
        assertEquals("", eval("{{original}}", env))
    }

    @Test
    fun `charFirstMessage index semantics`() {
        val env = MacroEnv()
        env.character.firstMessage = "Hi!"
        env.character.alternateGreetings = listOf("Alt1", "Alt2")
        assertEquals("Hi!", eval("{{charFirstMessage}}", env))
        assertEquals("Hi!", eval("{{charFirstMessage::0}}", env))
        assertEquals("Hi!", eval("{{greeting}}", env))
        assertEquals("Alt1", eval("{{charFirstMessage::1}}", env))
        assertEquals("Alt2", eval("{{charFirstMessage::2}}", env))
        assertEquals("", eval("{{charFirstMessage::3}}", env))
    }

    @Test
    fun `mesExamples builds start blocks`() {
        val env = MacroEnv()
        env.character.mesExamplesRaw = "<START>\nHello\n<START>\nWorld"
        assertEquals("<START>\nHello\n<START>\nWorld\n", eval("{{mesExamples}}", env))
        env.character.mesExamplesRaw = "Hello"
        assertEquals("<START>\nHello\n", eval("{{mesExamples}}", env))
        env.character.mesExamplesRaw = ""
        assertEquals("", eval("{{mesExamples}}", env))
    }

    @Test
    fun `model and isMobile`() {
        val env = MacroEnv()
        env.system.model = "gpt-4o"
        assertEquals("gpt-4o", eval("{{model}}", env))
        assertEquals("true", eval("{{isMobile}}", env))
    }

    @Test
    fun `original macro consumed once`() {
        val env = MacroEnv(originalFn = { "ORIG" })
        assertEquals("ORIG|", eval("{{original}}|{{original}}", env))
    }

    @Test
    fun `original without provider keeps raw`() {
        assertEquals("{{original}}", eval("{{original}}", MacroEnv()))
    }

    // ==================== State 宏 ====================

    @Test
    fun `lastGenerationType and hasExtension`() {
        val env = MacroEnv()
        env.system.generationType = "regenerate"
        env.system.enabledExtensions = setOf("MyExt", "third-party/Other")
        assertEquals("regenerate", eval("{{lastGenerationType}}", env))
        assertEquals("true", eval("{{hasExtension::myext}}", env))
        assertEquals("true", eval("{{hasExtension::other}}", env))
        assertEquals("false", eval("{{hasExtension::nope}}", env))
    }

    // ==================== 动态宏 ====================

    @Test
    fun `dynamic macros value handler and definition`() {
        val env = MacroEnv(
            dynamicMacros = mapOf(
                "mystr" to DynamicMacro.Value("S"),
                "myfn" to DynamicMacro.Handler { ctx -> "H:${ctx.args.size}" },
                "mydef" to DynamicMacro.Definition(
                    MacroDefinition(name = "mydef", minArgs = 1, maxArgs = 1, handler = { it.unnamedArgs[0] ?: "" }),
                ),
            ),
        )
        assertEquals("S", eval("{{mystr}}", env))
        assertEquals("S", eval("{{MYSTR}}", env))
        assertEquals("{{mystr::x}}", eval("{{mystr::x}}", env))
        assertEquals("H:0", eval("{{myfn}}", env))
        assertEquals("ok", eval("{{mydef::ok}}", env))
    }

    @Test
    fun `dynamic macro overrides registered macro`() {
        val env = MacroEnv(dynamicMacros = mapOf("user" to DynamicMacro.Value("DYN")))
        assertEquals("DYN", eval("{{user}}", env))
    }

    // ==================== pick：确定性链路 ====================

    @Test
    fun `pick chain selects stable value`() {
        val env1 = MacroEnv().apply { system.chatIdHash = 123456789L }
        assertEquals("b", eval("{{pick::a::b::c}}", env1))

        // 不同位置（offset=1）→ 不同种子 → 不同选择
        val env2 = MacroEnv().apply { system.chatIdHash = 123456789L }
        // "Z" 前缀使 globalOffset=1 → 不同种子 → 选择 "c"；普通文本 "Z" 原样保留
        assertEquals("Zc", eval("Z{{pick::a::b::c}}", env2))

        // reroll 种子参与
        val env3 = MacroEnv().apply {
            system.chatIdHash = 123456789L
            system.pickRerollSeed = "99"
        }
        assertEquals("b", eval("{{pick::a::b::c}}", env3))

        // 无会话哈希
        val env4 = MacroEnv()
        assertEquals("b", eval("{{pick::a::b::c::d}}", env4))
    }

    @Test
    fun `pick is deterministic across evaluations`() {
        val env = MacroEnv().apply { system.chatIdHash = 123456789L }
        val first = eval("{{pick::a::b::c}}", env)
        val second = eval("{{pick::a::b::c}}", env)
        assertEquals(first, second)
    }

    @Test
    fun `pick seed chain composition matches hash goldens`() {
        // 内容哈希（cyrb53 与 WorldInfoEngine.getStringHash 对齐）
        assertEquals(3259054761512980L, MacroHash.getStringHash("hello world"))
        // 链路拼接：chatIdHash + "-" + contentHash + "-" + offset
        assertEquals(1401696794030500L, MacroHash.getStringHash("123456789-3259054761512980-42"))
    }

    // ==================== 时间宏（固定时钟） ====================

    @Test
    fun `time date weekday iso formats`() {
        val env = fixedTimeEnv()
        assertEquals("9:30 AM", eval("{{time}}", env))
        assertEquals("3:30 AM", eval("{{time::UTC+2}}", env))
        assertEquals("3:30 PM", eval("{{time_UTC-10}}", env))
        assertEquals("September 13, 2026", eval("{{date}}", env))
        assertEquals("Sunday", eval("{{weekday}}", env))
        assertEquals("09:30", eval("{{isotime}}", env))
        assertEquals("2026-09-13", eval("{{isodate}}", env))
        assertEquals("2026-09-13 09:30", eval("{{datetimeformat::YYYY-MM-DD HH:mm}}", env))
    }

    @Test
    fun `idleDuration humanizes time since last user message`() {
        val now = Instant.parse("2026-09-13T01:30:00Z")
        val env = MacroEnv(
            chat = listOf(
                MacroChatMessage("Hello", isUser = true, sentAt = now.minusSeconds(100)),
                MacroChatMessage("Hi", isUser = false, sentAt = now.minusSeconds(10)),
            ),
        )
        val fixed = MacroEnv(
            chat = env.chat,
            clock = Clock.fixed(now, ZoneId.of("Asia/Shanghai")),
            zone = ZoneId.of("Asia/Shanghai"),
            locale = Locale.ENGLISH,
        )
        assertEquals("2 minutes", eval("{{idleDuration}}", fixed))
        assertEquals("2 minutes", eval("{{idle_duration}}", fixed))
        assertEquals("just now", eval("{{idleDuration}}", MacroEnv()))
    }

    @Test
    fun `timeDiff humanizes with suffix`() {
        val env = fixedTimeEnv()
        assertEquals("in an hour", eval("{{timeDiff::2026-09-13 10:00:00::2026-09-13 09:00:00}}", env))
        assertEquals("an hour ago", eval("{{timeDiff::2026-09-13 09:00:00::2026-09-13 10:00:00}}", env))
        assertEquals("Invalid date", eval("{{timeDiff::nope::2026-09-13 09:00:00}}", env))
    }

    // ==================== 引擎工具函数 ====================

    @Test
    fun `split on top level else`() {
        assertEquals("then" to "other", MacroEngine.splitOnTopLevelElse("then{{else}}other"))
        assertEquals("no else here" to null, MacroEngine.splitOnTopLevelElse("no else here"))
        assertEquals(
            "a{{if true}}b{{else}}c{{/if}}d" to "e",
            MacroEngine.splitOnTopLevelElse("a{{if true}}b{{else}}c{{/if}}d{{else}}e"),
        )
    }

    @Test
    fun `trim scoped content dedents`() {
        assertEquals("x\ny", MacroEngine.trimScopedContent("\n x\n y\n"))
        assertEquals("", MacroEngine.trimScopedContent(" \n "))
        assertEquals("z", MacroEngine.trimScopedContent(" z "))
    }

    @Test
    fun `boolean helpers`() {
        assertTrue(MacroEngine.isTrueBoolean(" ON "))
        assertTrue(MacroEngine.isTrueBoolean("true"))
        assertTrue(MacroEngine.isTrueBoolean("1"))
        assertTrue(MacroEngine.isFalseBoolean("off"))
        assertTrue(MacroEngine.isFalseBoolean("FALSE"))
        assertTrue(MacroEngine.isFalseBoolean("0"))
        assertEquals(false, MacroEngine.isFalseBoolean(""))
        assertEquals(false, MacroEngine.isTrueBoolean("yes"))
    }

    @Test
    fun `normalize macro result`() {
        assertEquals("", MacroEngine.normalizeMacroResult(null))
        assertEquals("3", MacroEngine.normalizeMacroResult(3))
        assertEquals("3.5", MacroEngine.normalizeMacroResult(3.5))
        assertEquals("true", MacroEngine.normalizeMacroResult(true))
        assertEquals("s", MacroEngine.normalizeMacroResult("s"))
    }

    @Test
    fun `evaluate empty and null input`() {
        assertEquals("", eval(""))
        assertEquals("", eval(null))
    }
}
