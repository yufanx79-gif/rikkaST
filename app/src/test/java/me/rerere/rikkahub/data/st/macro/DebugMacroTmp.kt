package me.rerere.rikkahub.data.st.macro

import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.runtime.TavernVariableStore
import org.junit.Test

class DebugMacroTmp {
    @Test
    fun debug() {
        MacroDefinitions.registerAll()
        TavernVariableStore.clearForTest()
        TavernRuntimeManager.setActiveConversation("conv-macro-test")
        val ctx = """{"name1":"Alice","name2":"Bob","original":"BasePrompt"}"""
        println("TEST-A: " + TavernRuntimeManager.evaluateMacrosFromJs("Hello {{user}} & {{char}}!", ctx))
        println("TEST-B: " + TavernRuntimeManager.evaluateMacrosFromJs("{{user}}", ctx))
        println("TEST-C: " + TavernRuntimeManager.evaluateMacrosFromJs("{{char}}", ctx))
        val ctxObj = org.json.JSONObject(ctx)
        println("TEST-D name1=" + ctxObj.optString("name1", "") + " name2=" + ctxObj.optString("name2", ""))
    }
}
