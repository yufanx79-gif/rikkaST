package me.rerere.rikkahub.ui.theme

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v217 / A5：锁死「自定义主题 JSON」这个对外 schema。
 *
 * 背景：Kelivo（Flutter / AGPL-3.0）的 lib/theme/custom_theme.dart 导出的 key 与我们的
 * [CustomTheme] 字段逐字同名（primaryColorArgb / secondaryColorArgb / tertiaryColorArgb），
 * 两边互相导入是本项目唯一一个真实的互操作点。
 *
 * 这个测试防的是「有人顺手改字段名 / 改 Json 配置」，把主人的 Kelivo 主题导入搞坏 —— 改坏了这里会红。
 */
class CustomThemeJsonTest {

    private val primary = 0xFF6750A4L
    private val secondary = 0xFF0B57D0L
    private val tertiary = 0xFF7D5260L

    /**
     * Kelivo 1.3.0 实际导出的形状：
     *  - 带我们认识的三个 colorArgb key；
     *  - 带我们【不认识】的 key（brightness / seedColorArgb）—— 必须被忽略；
     *  - 【不带 id】—— 必须自动补一个 UUID。
     */
    private val kelivoExport = """
        {
          "name": "Kelivo Sakura",
          "primaryColorArgb": $primary,
          "secondaryColorArgb": $secondary,
          "tertiaryColorArgb": $tertiary,
          "brightness": "dark",
          "seedColorArgb": $primary
        }
    """.trimIndent()

    private val sampleTheme = CustomTheme(
        id = "t1",
        name = "Round Trip",
        primaryColorArgb = primary,
        secondaryColorArgb = secondary,
        tertiaryColorArgb = tertiary,
    )

    @Test
    fun importsKelivoExportAndIgnoresUnknownKeys() {
        val theme = decodeCustomTheme(kelivoExport)

        assertEquals("Kelivo Sakura", theme.name)
        assertEquals(primary, theme.primaryColorArgb)
        assertEquals(secondary, theme.secondaryColorArgb)
        assertEquals(tertiary, theme.tertiaryColorArgb)
    }

    @Test
    fun missingIdGetsAFreshUuid() {
        val a = decodeCustomTheme(kelivoExport)
        val b = decodeCustomTheme(kelivoExport)

        assertTrue("缺 id 必须补一个非空 id", a.id.isNotBlank())
        assertNotEquals("两次导入不能撞同一个 id", a.id, b.id)
    }

    @Test
    fun blankIdAlsoGetsAFreshUuid() {
        val theme = decodeCustomTheme("""{"id":"   ","name":"X","primaryColorArgb":$primary}""")
        assertTrue("全空白 id 也要兜底", theme.id.isNotBlank())
        assertNotEquals("   ", theme.id)
    }

    @Test
    fun explicitIdIsKept() {
        val theme = decodeCustomTheme("""{"id":"my-theme","name":"X","primaryColorArgb":$primary}""")
        assertEquals("my-theme", theme.id)
    }

    @Test
    fun onlyPrimaryColorIsRequired() {
        val theme = decodeCustomTheme("""{"primaryColorArgb":$primary}""")

        assertEquals(primary, theme.primaryColorArgb)
        assertNull(theme.secondaryColorArgb)
        assertNull(theme.tertiaryColorArgb)
        assertEquals("", theme.name)
        assertTrue(theme.id.isNotBlank())
    }

    @Test
    fun ownExportRoundTrips() {
        assertEquals(sampleTheme, decodeCustomTheme(encodeCustomTheme(sampleTheme)))
    }

    /**
     * schema 锁：导出的 JSON 必须**正好**带这五个 key。
     *
     * 这条测试抓到过一个真 bug：kotlinx.serialization 默认 encodeDefaults=false，
     * 当主题的 primaryColorArgb 恰好等于默认值 0xFF6750A4 时，导出的 JSON 会**丢掉
     * primaryColorArgb**，Kelivo 导入后只能退回它自己的默认色。
     * 所以 [CustomThemeJson] 显式设了 encodeDefaults = true —— 谁改回去这里就红。
     */
    @Test
    fun exportSchemaIsCompleteAndStable() {
        val keys = CustomThemeJson.parseToJsonElement(encodeCustomTheme(sampleTheme)).jsonObject.keys

        assertEquals(
            "导出 schema 是对外契约（Kelivo 反向导入靠它），不要随手加/删/改字段",
            setOf("id", "name", "primaryColorArgb", "secondaryColorArgb", "tertiaryColorArgb"),
            keys,
        )
    }

    /** 回归：primary 恰好等于默认色时也必须导出（见上一条注释里的真 bug）。 */
    @Test
    fun exportKeepsPrimaryEvenWhenItEqualsTheDefault() {
        val defaultPrimary = 0xFF6750A4L
        val json = encodeCustomTheme(
            CustomTheme(name = "Default Purple", primaryColorArgb = defaultPrimary)
        )
        val obj = CustomThemeJson.parseToJsonElement(json).jsonObject

        assertTrue(
            "primary 等于默认值时不能省略 primaryColorArgb，实际: $json",
            obj.containsKey("primaryColorArgb"),
        )
        assertEquals(defaultPrimary, decodeCustomTheme(json).primaryColorArgb)
    }

    @Test
    fun malformedJsonThrows() {
        var threw = false
        try {
            decodeCustomTheme("{ this is not json")
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("坏 JSON 必须抛异常，导入对话框才有机会提示用户", threw)
    }
}
