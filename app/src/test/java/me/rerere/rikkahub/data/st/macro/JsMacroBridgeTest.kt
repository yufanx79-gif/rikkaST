package me.rerere.rikkahub.data.st.macro

import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.runtime.TavernVariableStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsMacroBridgeTest {
    @Before
    fun setUp() {
        MacroDefinitions.registerAll()
        TavernVariableStore.clearForTest()
        TavernRuntimeManager.setActiveConversation("conv-macro-test")
    }

    @Test
    fun `75 plus builtin macros are registered in MacroRegistry`() {
        val allNames = MacroRegistry.getAllMacros().map { it.name }.toSet()
        assertTrue("Expected at least 75 primary macros, got ${allNames.size}", allNames.size >= 75)
        assertTrue(MacroRegistry.hasMacro("char"))
        assertTrue(MacroRegistry.hasMacro("user"))
        assertTrue(MacroRegistry.hasMacro("time"))
        assertTrue(MacroRegistry.hasMacro("date"))
        assertTrue(MacroRegistry.hasMacro("random"))
        assertTrue(MacroRegistry.hasMacro("pick"))
        assertTrue(MacroRegistry.hasMacro("getvar"))
        assertTrue(MacroRegistry.hasMacro("setvar"))
        assertTrue(MacroRegistry.hasMacro("setvarkey"))
        assertTrue(MacroRegistry.hasMacro("getvarkey"))
        assertTrue(MacroRegistry.hasMacro("if"))
        assertTrue(MacroRegistry.hasMacro("//"))
    }

    @Test
    fun `substituteParams bridge evaluates core variable conditional and comment macros`() {
        val ctxJson = """{"name1":"Alice","name2":"Bob","original":"BasePrompt"}"""
        val out1 = TavernRuntimeManager.evaluateMacrosFromJs(
            "Hello {{user}} & {{char}}! {{setvar::hp::100}}{{getvar::hp}} {{if 1}}YES{{else}}NO{{/if}} {{// hidden}}",
            ctxJson,
        )
        assertEquals("Hello Alice & Bob! 100 YES ", out1)

        val out2 = TavernRuntimeManager.evaluateMacrosFromJs(
            "{{setvarkey:: bag :: gold :: 50}}{{getvarkey:: bag :: gold}} {{unknown_macro_xyz}}",
            ctxJson,
        )
        assertEquals("50 {{unknown_macro_xyz}}", out2)
    }
}
