package me.rerere.rikkahub.data.st.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * [v234 S1] varkey 系宏 + instruct 系宏 + systemPrompt 宏 单测。
 *
 * 金标准：
 * - variables.js:28-75（get/set index 分支语义）；
 * - instruct-macros.js:69-84（systemPrompt：sysprompt 关闭→''，prefer_character_prompt→charPrompt 优先）；
 * - power-user.js:91-92（exampleSeparator/chatStart 默认 '***'）。
 */
class MacroVarKeyTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerMacros() {
            MacroRegistry.clear()
            MacroDefinitions.registerAll()
        }
    }

    private fun eval(text: String, env: MacroEnv): String = MacroEngine.evaluate(text, env)

    private fun envWith(
        local: Map<String, String> = emptyMap(),
        global: Map<String, String> = emptyMap(),
        syspromptEnabled: Boolean = false,
        syspromptContent: String = "",
        charPrompt: String = "",
        instructEnabled: Boolean = false,
        userPrefix: String = "U>",
    ): MacroEnv {
        val localStore = InMemoryMacroVariableStore()
        local.forEach { (k, v) -> localStore.set(k, v) }
        val globalStore = InMemoryMacroVariableStore()
        global.forEach { (k, v) -> globalStore.set(k, v) }
        return MacroEnv(
            character = MacroEnv.CharacterInfo(charPrompt = charPrompt),
            system = MacroEnv.SystemInfo(
                syspromptEnabled = syspromptEnabled,
                syspromptContent = syspromptContent,
            ),
            variables = VariableStores(local = localStore, global = globalStore),
            instruct = MacroEnv.InstructInfo(
                enabled = instructEnabled,
                userPrefix = userPrefix,
            ),
        )
    }

    // ==================== getvarkey / setvarkey ====================

    @Test
    fun setvarkey_object_stringKey() {
        val env = envWith()
        assertEquals("", eval("{{setvarkey::obj::health::100}}", env))
        assertEquals("100", eval("{{getvarkey::obj::health}}", env))
    }

    @Test
    fun setvarkey_array_numericKey() {
        val env = envWith(local = mapOf("arr" to "[\"a\",\"b\"]"))
        assertEquals("", eval("{{setvarkey::arr::1::B}}", env))
        assertEquals("B", eval("{{getvarkey::arr::1}}", env))
        // 原数组其他元素保持
        assertEquals("a", eval("{{getvarkey::arr::0}}", env))
    }

    @Test
    fun setvarkey_array_appendAtEnd() {
        val env = envWith(local = mapOf("arr" to "[\"a\"]"))
        eval("{{setvarkey::arr::1::b}}", env)
        assertEquals("b", eval("{{getvarkey::arr::1}}", env))
    }

    @Test
    fun setvarkey_array_stringKey_degradesToIndexObject() {
        // 对齐 JS 数组命名属性语义：arr["k"]=v → {"0":"a","k":"v"}
        val env = envWith(local = mapOf("arr" to "[\"a\"]"))
        eval("{{setvarkey::arr::k::v}}", env)
        assertEquals("v", eval("{{getvarkey::arr::k}}", env))
        assertEquals("a", eval("{{getvarkey::arr::0}}", env))
    }

    @Test
    fun getvarkey_missingRoot_returnsEmpty() {
        val env = envWith()
        assertEquals("", eval("{{getvarkey::nobody::key}}", env))
    }

    @Test
    fun getvarkey_arrayOutOfBounds_returnsEmpty() {
        val env = envWith(local = mapOf("arr" to "[\"a\"]"))
        assertEquals("", eval("{{getvarkey::arr::5}}", env))
    }

    @Test
    fun getvarkey_arrayStringKey_returnsEmpty() {
        val env = envWith(local = mapOf("arr" to "[\"a\"]"))
        assertEquals("", eval("{{getvarkey::arr::k}}", env))
    }

    @Test
    fun getvarkey_nestedObjectResult_serializes() {
        val env = envWith(local = mapOf("root" to "{\"a\":{\"b\":1}}"))
        // 取出的对象按 ST 语义序列化为 JSON
        assertTrue(eval("{{getvarkey::root::a}}", env).replace(" ", "").contains("\"b\":1"))
    }

    // ==================== globalvarkey 系 ====================

    @Test
    fun setglobalvarkey_roundTrip() {
        val env = envWith()
        eval("{{setglobalvarkey::g::k::v}}", env)
        assertEquals("v", eval("{{getglobalvarkey::g::k}}", env))
    }

    @Test
    fun setglobalvarkey_local_isolated() {
        val env = envWith()
        eval("{{setglobalvarkey::g::k::gv}}", env)
        assertEquals("", eval("{{getvarkey::g::k}}", env))
    }

    // ==================== systemPrompt（instruct-macros.js:69-84） ====================

    @Test
    fun systemPrompt_disabled_returnsEmpty() {
        val env = envWith(syspromptEnabled = false, syspromptContent = "SYS", charPrompt = "CHAR")
        assertEquals("", eval("{{systemPrompt}}", env))
    }

    @Test
    fun systemPrompt_charPromptPreferred() {
        // ST prefer_character_prompt 默认 true
        val env = envWith(syspromptEnabled = true, syspromptContent = "SYS", charPrompt = "CHAR")
        assertEquals("CHAR", eval("{{systemPrompt}}", env))
    }

    @Test
    fun systemPrompt_fallbackToSysprompt() {
        val env = envWith(syspromptEnabled = true, syspromptContent = "SYS", charPrompt = "")
        assertEquals("SYS", eval("{{systemPrompt}}", env))
    }

    // ==================== instruct 系（registerSimple 门控语义） ====================

    @Test
    fun instructMacros_disabled_returnsEmpty() {
        val env = envWith(instructEnabled = false)
        assertEquals("", eval("{{instructUserPrefix}}", env))
        assertEquals("", eval("{{instructInput}}", env))
    }

    @Test
    fun instructMacros_enabled_expandsValue() {
        val env = envWith(instructEnabled = true, userPrefix = "USER>")
        assertEquals("USER>", eval("{{instructUserPrefix}}", env))
        assertEquals("USER>", eval("{{instructInput}}", env))
    }

    @Test
    fun exampleSeparator_defaultStar3_always() {
        // 不受 instruct.enabled 门控（对齐 ST 注册处 `() => true`）
        val env = envWith(instructEnabled = false)
        assertEquals("***", eval("{{exampleSeparator}}", env))
        assertEquals("***", eval("{{chatStart}}", env))
    }

    @Test
    fun registry_containsVarkeyMacros() {
        assertTrue(MacroRegistry.hasMacro("setvarkey"))
        assertTrue(MacroRegistry.hasMacro("getvarindex"))
        assertTrue(MacroRegistry.hasMacro("setglobalvarkey"))
        assertTrue(MacroRegistry.hasMacro("getglobalvarindex"))
        assertTrue(MacroRegistry.hasMacro("systemPrompt"))
        assertTrue(MacroRegistry.hasMacro("exampleSeparator"))
        assertFalse(MacroRegistry.hasMacro("definitely_not_a_macro"))
    }
}
