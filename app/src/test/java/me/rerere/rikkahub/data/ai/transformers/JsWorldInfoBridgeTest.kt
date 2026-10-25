package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsWorldInfoBridgeTest {
    @Test
    fun `checkWorldInfo returns found true on keyword match and found false when unmatched`() {
        val prevLorebookSupplier = TavernRuntimeManager.tavernLorebookSupplier
        val prevWorldNamesSupplier = TavernRuntimeManager.worldNamesSupplier
        try {
            TavernRuntimeManager.worldNamesSupplier = {
                """{"selected_world_info":["TestWorld"]}"""
            }
            TavernRuntimeManager.tavernLorebookSupplier = { name ->
                if (name == "TestWorld") {
                    """
                    {
                      "entries": {
                        "0": {
                          "uid": 0,
                          "key": ["excalibur"],
                          "keysecondary": ["arthur"],
                          "selective": true,
                          "selectiveLogic": 0,
                          "content": "Excalibur is the legendary sword of King Arthur.",
                          "position": 0,
                          "enabled": true,
                          "disable": false
                        }
                      }
                    }
                    """.trimIndent()
                } else "{}"
            }

            val hitRaw = TavernRuntimeManager.evaluateWorldInfoFromJs(
                chatJson = """[{"mes":"Arthur drew Excalibur from the stone."}]""",
                maxContext = 4096,
                isDryRun = true,
                globalScanDataJson = "{}",
            )
            val hitObj = JSONObject(hitRaw)
            assertTrue(hitObj.getBoolean("found"))
            assertTrue(hitObj.getString("worldInfoString").contains("legendary sword"))
            assertEquals(1, hitObj.getJSONArray("allActivatedEntries").length())

            val missRaw = TavernRuntimeManager.evaluateWorldInfoFromJs(
                chatJson = """[{"mes":"Just a quiet day in the village."}]""",
                maxContext = 4096,
                isDryRun = true,
                globalScanDataJson = "{}",
            )
            val missObj = JSONObject(missRaw)
            assertFalse(missObj.getBoolean("found"))
            assertEquals("", missObj.getString("worldInfoString"))
        } finally {
            TavernRuntimeManager.tavernLorebookSupplier = prevLorebookSupplier
            TavernRuntimeManager.worldNamesSupplier = prevWorldNamesSupplier
        }
    }
}
