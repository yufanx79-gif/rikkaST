package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.files.SkillPaths

// 与 Agent Skills 规范的 description 上限一致
private const val MAX_SKILL_DESCRIPTION_LENGTH = 1024

/**
 * Skill 工具 — Claude Code / Agent Skills 风格
 *
 * 同时保留 rikkaST 本地能力：分类、触发词、linked_files 展开、file_path/path 两种参数名，
 * 以及上游 2.5.6 的 XML 转义与 description 限长安全加固。
 */
fun createSkillTools(
    enabledSkills: Set<String>,
    allSkills: List<SkillMetadata>,
    skillManager: SkillManager? = null,
): List<Tool> {
    val available = allSkills.filter { it.name in enabledSkills }
    if (available.isEmpty()) return emptyList()

    val byCategory = available.groupBy { it.category ?: "其他" }

    return listOf(
        Tool(
            name = "use_skill",
            description = """
                Load and apply a skill to get specialized instructions or capabilities.
                Call this tool when the user's request matches one of the available skills.
                Use this tool before starting a task that matches a skill's domain — it contains
                API endpoints, tool commands, and proven approaches.
                Do NOT use file action="list" or file action="search" to browse skill directories.
            """.trimIndent(),
            systemPrompt = { _, _ ->
                buildString {
                    appendLine("**Skills**")
                    appendLine("You have access to the following skills. Use the `use_skill` tool to load a skill's instructions when the user's request matches.")
                    appendLine("<available_skills>")
                    byCategory.forEach { (category, skills) ->
                        appendLine("  <!-- $category -->")
                        skills.forEach { skill ->
                            appendLine("  <skill>")
                            appendLine("    <name>${skill.name.escapeXml()}</name>")
                            val extra = buildString {
                                if (skill.triggers.isNotEmpty()) {
                                    append(" [triggers: ${skill.triggers.take(3).joinToString().escapeXml()}]")
                                }
                                if (skill.linkedFiles.isNotEmpty()) {
                                    append(" (+${skill.linkedFiles.values.sumOf { it.size }} files)")
                                }
                            }
                            appendLine(
                                "    <description>${
                                    skill.description.take(MAX_SKILL_DESCRIPTION_LENGTH).escapeXml()
                                }$extra</description>"
                            )
                            appendLine("  </skill>")
                        }
                    }
                    append("</available_skills>")
                    appendLine()
                }
            },
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "The name of the skill to use")
                        })
                        put("file_path", buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Optional relative path to a file inside the skill directory. Omit for SKILL.md. " +
                                    "Only use paths extracted from Markdown links in the SKILL.md content."
                            )
                        })
                        put("path", buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Alias of file_path (Agent Skills compatibility). Omit to read SKILL.md."
                            )
                        })
                    },
                    required = listOf("name")
                )
            },
            execute = { args ->
                val obj = args.jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.content
                    ?: error("name is required")
                // 模型可能照抄系统提示中转义后的名称，两种形式都接受
                val skill = available.firstOrNull { skill ->
                    skill.name == rawName || skill.name.escapeXml() == rawName
                } ?: error("Skill '$rawName' is not available. Available skills: ${available.joinToString { it.name }}")
                if (skill.name !in enabledSkills) error("'${skill.name}' not available")

                val filePath = obj["file_path"]?.jsonPrimitive?.content
                    ?: obj["path"]?.jsonPrimitive?.content
                val content = if (filePath.isNullOrBlank()) {
                    val body = skillManager?.readSkillBody(skill.name)
                        ?: skill.skillFile.takeIf { it.exists() }
                            ?.let { SkillFrontmatterParser.extractBody(it.readText()) }
                        ?: error("Skill '${skill.name}' not found")
                    buildString {
                        appendLine(body)
                        if (skill.linkedFiles.isNotEmpty()) {
                            appendLine()
                            appendLine("--- linked_files ---")
                            skill.linkedFiles.forEach { (dir, files) ->
                                files.forEach { f -> appendLine("$dir/$f") }
                            }
                        }
                    }
                } else {
                    val target = skillManager?.resolveSkillFile(skill.name, filePath)
                        ?: SkillPaths.resolveSkillFile(skill.skillDir, filePath)
                        ?: error("Path '$filePath' is outside the skill directory")
                    require(target.exists()) { "File '$filePath' not found in skill '${skill.name}'" }
                    target.readText()
                }
                listOf(UIMessagePart.Text(content))
            }
        )
    )
}

private fun String.escapeXml(): String = buildString(length) {
    for (c in this@escapeXml) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }
}
