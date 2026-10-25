package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TavernCharacterData

/**
 * End-to-end (pure JVM) test for the card-script chain:
 * legacy storage -> listCardScripts -> buildTavernScriptsJson -> buildCharacterJson.
 * Compiled together with the production TavernScripts.kt + TavernScriptCompat.kt.
 */
object TavernScriptsChainSelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val legacyRaw = """{"world":"W","TavernHelper_scripts":[
            {"type":"script","enabled":true,"name":"Legacy","id":"legacy-1","content":"legacy()"},
            {"type":"folder","id":"folder-1","name":"Folder","value":[
                {"enabled":true,"name":"Nested","id":"nested-1","content":"nested()"}
            ]}
        ],"TavernHelper_characterScriptVariables":{"hp":9}}"""

        val assistant = Assistant(
            name = "legacy-card",
            tavernData = TavernCharacterData(name = "legacy-card", extensionsRaw = legacyRaw),
        )

        // 1) UI list: legacy fields are visible, folder is flattened.
        val briefs = listCardScripts(assistant)
        check(briefs.size == 2) { "expected 2 card scripts, got=${briefs.size}" }
        check(briefs.map { it.name }.toSet() == setOf("Legacy", "Nested"))
        check(briefs.first { it.id == "nested-1" }.content == "nested()")

        // 2) Runtime bridge: merged JSON contains real script content and variables, not legacy wrappers.
        val runtime = buildTavernScriptsJson(assistant, null)
        val helper = Json.parseToJsonElement(runtime).jsonObject["tavern_helper"]!!.jsonObject
        val scripts = helper["scripts"]!!.jsonArray
        check(scripts.size == 2) { "runtime expected 2 scripts, got=${scripts.size}" }
        check(scripts.all { it.jsonObject["type"]!!.jsonPrimitive.contentOrNull == "script" })
        check(scripts.any { it.jsonObject["content"]!!.jsonPrimitive.contentOrNull == "legacy()" })
        check(helper["variables"]!!.jsonObject["hp"]!!.jsonPrimitive.contentOrNull == "9")

        // 3) JSR itself reads characters[i].data.extensions.tavern_helper: must be normalized there too.
        val character = Json.parseToJsonElement(buildCharacterJson(assistant)).jsonObject
        val ext = character["data"]!!.jsonObject["extensions"]!!.jsonObject
        val normalized = ext["tavern_helper"]!!.jsonObject
        check(normalized["scripts"]!!.jsonArray.size == 2)
        check(ext.containsKey("TavernHelper_scripts")) // original legacy data stays lossless

        // 4) Toggle a nested legacy script -> new tavern_helper written, legacy fields retained.
        val toggled = requireNotNull(setCardScriptEnabled(legacyRaw, "nested-1", false))
        val toggledRoot = Json.parseToJsonElement(toggled).jsonObject
        val toggledHelper = toggledRoot["tavern_helper"]!!.jsonObject
        val nested = toggledHelper["scripts"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["type"]!!.jsonPrimitive.contentOrNull == "folder" }
            .get("scripts")!!.jsonArray
            .map { it.jsonObject }
            .first { it["id"]!!.jsonPrimitive.contentOrNull == "nested-1" }
        check(nested["enabled"]!!.jsonPrimitive.contentOrNull == "false")
        check(toggledRoot.containsKey("TavernHelper_scripts"))

        // 5) Import JSON: old {type:"script", value:{...}} wrapper is unwrapped.
        val imported = extractScriptsFromJson(
            """{"tavern_helper":{"scripts":[{"type":"script","value":{"id":"imp","name":"Imported","content":"i()"}}]}}""",
        )
        check(imported.size == 1)
        check(imported[0].jsonObject["name"]!!.jsonPrimitive.contentOrNull == "Imported")
        check(imported[0].jsonObject["type"]!!.jsonPrimitive.contentOrNull == "script")

        // 6) Old persisted Map only (extensionsRaw empty) still lists scripts.
        val mapOnly = Assistant(
            name = "map-only",
            tavernData = TavernCharacterData(
                name = "map-only",
                extensionsRaw = "",
                extensions = mapOf(
                    "TavernHelper_scripts" to """[{"type":"script","name":"Map","id":"m","content":"m()"}]""",
                ),
            ),
        )
        check(listCardScripts(mapOnly).single().name == "Map")
        val mapToggled = requireNotNull(setCardScriptEnabled(mapOnly, "m", false))
        val mapToggledScript = Json.parseToJsonElement(mapToggled).jsonObject["tavern_helper"]!!.jsonObject["scripts"]!!.jsonArray[0].jsonObject
        check(mapToggledScript["enabled"]!!.jsonPrimitive.contentOrNull == "false")

        println("TAVERN_SCRIPTS_CHAIN_SELFTEST_OK")
    }
}



