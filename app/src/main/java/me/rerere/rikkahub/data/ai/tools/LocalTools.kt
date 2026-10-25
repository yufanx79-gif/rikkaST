package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool

typealias LocalToolOption = me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
typealias LocalTools = me.rerere.rikkahub.data.ai.tools.local.LocalTools

fun deduplicateTools(tools: List<Tool>): List<Tool> =
    me.rerere.rikkahub.data.ai.tools.local.deduplicateTools(tools)

internal fun javascriptToolSchema(): InputSchema =
    me.rerere.rikkahub.data.ai.tools.local.javascriptToolSchema()
