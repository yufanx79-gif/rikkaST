package me.rerere.rikkahub.data.st.regex

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0-A 真机取证：预设正则是否被 ReDoS 守卫判定为危险（= 会被静默跳过）。
 * 日志 2026-11-19 里出现过 14 次 skipped，每次 3 条，全部来自预设。
 */
@RunWith(AndroidJUnit4::class)
class RegexGuardDeviceTest {

    // 与真机日志逐字一致的两条预设正则
    private val summaryPattern = "/" + "(?<!<details>\\s*)<summary>(((?!<summary>)[\\s\\S])*?)<\\/summary>" + "/gi"
    private val thinkPattern = "/" + "(?<!<!--\\s*)<think(?:\\s[^>]*)?>(?:(?!(?:<think_nya|<report_nya))[\\s\\S])*?(?:<\\/think>(?!\\s*-->)?" + "/gi"

    @Test
    fun presetRegexPatternsAreFlaggedDangerous() {
        val c1 = RegexScriptEngine.isPotentiallyCatastrophicRegex(summaryPattern)
        val l1 = RegexScriptEngine.isDangerousVariableLookbehind(summaryPattern)
        val c2 = RegexScriptEngine.isPotentiallyCatastrophicRegex(thinkPattern)
        val l2 = RegexScriptEngine.isDangerousVariableLookbehind(thinkPattern)

        val report = "summary[catastrophic=" + c1 + " lookbehind=" + l1 + "] " +
            "think[catastrophic=" + c2 + " lookbehind=" + l2 + "] " +
            "MIN_LEN=" + RegexScriptEngine.MIN_CATASTROPHIC_INPUT_LENGTH

        // 只有 4096 字符以上的输入才会触发跳过；日志里触发时的输入是 5713/7283/17709
        assertTrue("[REGEX-GUARD-NOT-FLAGGED] 两条预设正则都没被判定为危险: " + report, c1 || l1 || c2 || l2)
    }
}