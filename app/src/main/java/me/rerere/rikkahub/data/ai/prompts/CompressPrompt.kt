package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_COMPRESS_PROMPT = """
    You are a conversation compression assistant. Compress the following conversation into a concise summary.

    Requirements:
    1. Preserve key facts, decisions, and important context that would be needed to continue the conversation
    2. Keep the summary in the same language as the original conversation
    3. Target approximately {target_tokens} tokens
    4. Output the summary directly without any explanations or meta-commentary
    5. Format the summary as context information that can be used to continue the conversation
    6. Use {locale} language
    7. Start the output with a clear indicator that this is a summary (e.g., "[Summary of previous conversation]" or equivalent in the target language)

    {additional_context}

    <conversation>
    {content}
    </conversation>
""".trimIndent()

/**
 * 压缩指令，作为一条 user 消息追加在对话末尾，模型直接总结上方的对话历史。
 * 摘要会替代之前的全部历史，按固定的结构输出，避免遗漏继续对话需要的信息。
 */
internal fun buildCompressPrompt(
    additionalPrompt: String,
    locale: String,
): String = buildString {
    appendLine(
        """
        Stop the current task and write a structured summary of the conversation so far. The summary will replace the conversation history as the context for continuing it, so anything not in the summary will be lost.

        Do not call any tools. Respond with the summary only, using exactly the following Markdown structure. Omit a section if there is nothing to put in it.

        [Summary of previous conversation]

        ## User Goals
        What the user wants to achieve, including how their requests evolved over the conversation.

        ## Key Context
        Facts, background, constraints, and preferences the user stated or that were established, including any earlier summary at the start of the conversation.

        ## Decisions and Conclusions
        Choices that were made, answers that were reached, and the reasons behind them.

        ## Tool Results and Artifacts
        Important details from tool calls and their results that are needed to continue: file paths, commands, code, data, links, and search findings. Quote exact values instead of describing them.

        ## Errors and Resolutions
        Problems that came up and how they were resolved, or that they remain unresolved.

        ## Open Items
        Unfinished tasks, open questions, and anything the user is waiting for.

        ## Current State
        What was being worked on right before this summary, and the next step if one is clear.

        Requirements:
        - Keep the section headings as given above, but write the content in the same language as the conversation; if unclear, use $locale
        - Be specific and concrete; prefer exact names and values over general descriptions
        - Do not add any explanation or commentary outside the summary
        """.trimIndent()
    )
    if (additionalPrompt.isNotBlank()) {
        appendLine()
        appendLine("Additional instructions from user: $additionalPrompt")
    }
}.trimEnd()
