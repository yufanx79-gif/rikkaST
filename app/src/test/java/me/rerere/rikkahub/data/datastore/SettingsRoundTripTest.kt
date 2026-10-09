package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [v227 D4] 设置「写盘 -> 读回」的**值级往返一致性**（纯函数，可单测）。
 *
 * ## 为什么要这个测试（v225 与 v227 是同一类缺陷）
 * `SettingsStore.update()` 的语义是「先乐观改内存，再写 DataStore」，随后 DataStore 重新发射、
 * `settingsFlowRaw.map { ... }` 把盘上的值**解回内存**。如果「写下去的」和「读回来的」不是同一个值
 * （键漏了 / 字段没被序列化 / 默认值每次都不一样），那么：
 *   1. `distinctUntilChanged()` 挡不住这次发射；
 *   2. 内存里刚改好的值被**读回来的值覆盖**；
 *   3. 真机表现 = 「开关弹回去 / 跳一下 / 参数不生效」。
 *
 * v225 的「messageStyle 漏读漏写」就是这个机制的**键级**版本（已在 `_audit_keys227.py` 里审计：
 * 73 读 / 73 写，0 不对称）。本测试补**值级**防线：对设置页开关直接会改的那两个数据类，
 * 钉死 `decode(encode(x)) == x`。
 */
class SettingsRoundTripTest {

    private fun displayRoundTrip(v: DisplaySetting): DisplaySetting =
        JsonInstant.decodeFromString(DisplaySetting.serializer(), JsonInstant.encodeToString(DisplaySetting.serializer(), v))

    private fun styleRoundTrip(v: MessageStyleSetting): MessageStyleSetting =
        decodeMessageStyle(encodeMessageStyle(v))

    @Test
    fun displaySettingDefaultsRoundTrip() {
        assertEquals(DisplaySetting(), displayRoundTrip(DisplaySetting()))
    }

    @Test
    fun displaySettingSwitchFieldsRoundTrip() {
        // 设置页开关直接改的字段（SettingPreferencesUIPage 的 13 个 Switch）
        val v = DisplaySetting(
            showUserAvatar = false,
            showAssistantBubble = true,
            showModelIcon = false,
            showModelName = false,
            showDateTimeInMessage = true,
            showTokenUsage = false,
            showThinkingContent = false,
            autoCloseThinking = false,
            showMessageJumper = false,
            messageJumperOnLeft = true,
            enableAutoScroll = false,
            enableLatexRendering = true,
            sendOnEnter = true,
        )
        assertEquals(v, displayRoundTrip(v))
    }

    @Test
    fun displaySettingNumericAndStringFieldsRoundTrip() {
        // 滑杆 / 取色器 / 字体导入改的字段
        val v = DisplaySetting(
            bubbleOpacity = 0.05f,
            fontSizeRatio = 1.35f,
            pasteLongTextThreshold = 4096,
            enableBlurEffect = false,
            chatFontFamily = ChatFontFamily.CUSTOM,
            chatCustomFontPath = "fonts/custom-abc.ttf",
            chatCustomFontName = "My Font",
            quoteColor = "#E18A24",
            italicsColor = "#919191",
            volumeKeyScrollRatio = 0.5f,
        )
        assertEquals(v, displayRoundTrip(v))
    }

    @Test
    fun messageStyleDefaultsRoundTrip() {
        assertEquals(MessageStyleSetting(), styleRoundTrip(MessageStyleSetting()))
    }

    @Test
    fun messageStyleEveryFieldRoundTrip() {
        // 消息样式页 11 项里所有可调参数一次给全（含「改参数退出重进还在」依赖的持久化字段）
        val v = MessageStyleSetting(
            style = MessageBubbleStyle.FROSTED,
            assistantBubbleWrapContent = true,
            splitSegmentsAsBubbles = true,
            blurStrength = 4,
            blurStyle = BlurStyle.FROSTED_GLASS,
            light = BubbleStyleTheme(
                backgroundArgb = 0xFF112233.toInt(),
                frostedOpacity = 0.42f,
                solidOpacity = 0.87f,
                borderArgb = 0xFF445566.toInt(),
                borderOpacity = 0.33f,
                borderWidth = 2.5f,
                textArgb = 0xFF778899.toInt(),
                cornerRadius = 28f,
            ),
            dark = BubbleStyleTheme(
                backgroundArgb = 0xFF010203.toInt(),
                frostedOpacity = 0.11f,
                solidOpacity = 0.22f,
                borderArgb = 0xFF040506.toInt(),
                borderOpacity = 0.44f,
                borderWidth = 3.5f,
                textArgb = 0xFF070809.toInt(),
                cornerRadius = 4f,
            ),
        )
        assertEquals(v, styleRoundTrip(v))
    }

    @Test
    fun roundTripIsStableAcrossRepeatedEncodes() {
        // 幂等：encode(decode(encode(x))) 必须与 encode(x) 完全一致（否则每次写回都会「微调」盘上值）
        val v = MessageStyleSetting(style = MessageBubbleStyle.SOLID, light = BubbleStyleTheme(cornerRadius = 7f))
        val once = encodeMessageStyle(v)
        val twice = encodeMessageStyle(decodeMessageStyle(once))
        assertEquals(once, twice)
    }

    @Test
    fun diskKeyNameIsStable() {
        // 改名 = 老用户存档读不回来（v225 的键名契约）
        assertEquals("message_style", MESSAGE_STYLE_KEY_NAME)
    }

    @Test
    fun decodeFallsBackToDefaultsForMissingKey() {
        // 老存档没有这个键（null）-> 回落默认值，且**不等于**一个被改过的值（防「静默吞掉」）
        assertEquals(MessageStyleSetting(), decodeMessageStyle(null))
        assertNotEquals(
            MessageStyleSetting(style = MessageBubbleStyle.SOLID),
            decodeMessageStyle(null),
        )
    }
}
