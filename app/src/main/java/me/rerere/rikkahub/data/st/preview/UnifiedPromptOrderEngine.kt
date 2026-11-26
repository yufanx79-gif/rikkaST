package me.rerere.rikkahub.data.st.preview

import me.rerere.ai.ui.UIMessage

/**
 * 统一槽位组装引擎（纯函数、无 I/O、无副作用、O(N) 确定性执行）。
 *
 * 同时服务：
 * 1. 真实发送路径（[me.rerere.rikkahub.data.ai.GenerationLoop]）：使全部 12 个内置 Marker
 *    （含 main / personaDescription / charDescription / charPersonality / scenario /
 *    dialogueExamples / chatHistory 等）严格遵循预设 `prompt_order` 中的自由排序；
 * 2. 预览组装路径（[PromptPreviewAssembler]）：保证预览顺序与真实发送顺序 100% 一致。
 *
 * 防卡死与兼容性不变量：
 * - `orderedIdentifiers` 为空时直接退回 [fallbackOrder]，与旧版默认顺序完全一致；
 * - 使用 `consumed` 集合确保每个槽位至多消费一次，损坏 JSON 中的重复 identifier 绝不死循环或重复注入；
 * - 未出现在 `orderedIdentifiers` 中的兜底槽位（如 chatHistory）按 [fallbackOrder] 安全补齐，绝不静默丢消息。
 */
object UnifiedPromptOrderEngine {

    fun assembleSlotsInOrder(
        orderedIdentifiers: List<String>,
        disabledIdentifiers: Set<String>,
        slotMessages: Map<String, List<UIMessage>>,
        fallbackOrder: List<String> = ST_BUILTIN_MARKERS,
    ): List<UIMessage> {
        val effectiveOrder = if (orderedIdentifiers.isEmpty()) fallbackOrder else orderedIdentifiers
        val totalEstimate = slotMessages.values.sumOf { it.size }.coerceAtLeast(8)
        val result = ArrayList<UIMessage>(totalEstimate)
        val consumed = HashSet<String>(effectiveOrder.size + fallbackOrder.size + slotMessages.size)

        for (id in effectiveOrder) {
            if (!consumed.add(id)) continue
            if (id in disabledIdentifiers) continue
            slotMessages[id]?.let { result.addAll(it) }
        }
        // 安全兜底：确保任何未出现在 prompt_order 中的标准槽位不会丢失
        for (id in fallbackOrder) {
            if (!consumed.add(id)) continue
            if (id in disabledIdentifiers) continue
            slotMessages[id]?.let { result.addAll(it) }
        }
        // 额外兜底：slotMessages 中存在但既不在 effectiveOrder 也不在 fallbackOrder 的非标准槽位
        for ((id, msgs) in slotMessages) {
            if (!consumed.add(id)) continue
            if (id in disabledIdentifiers) continue
            result.addAll(msgs)
        }
        return result
    }

    /**
     * 判断 `orderedMarkers` 是否包含自定义顺序（相对于内置 12 marker 的默认相对次序）。
     * 用于发送路径快速判定：未自定义顺序时保持旧链路零开销直通。
     */
    fun hasCustomMarkerOrder(orderedMarkers: List<String>): Boolean {
        if (orderedMarkers.size < 2) return false
        var lastDefaultIndex = -1
        for (id in orderedMarkers) {
            val defaultIdx = ST_BUILTIN_MARKERS.indexOf(id)
            if (defaultIdx < 0) continue
            if (defaultIdx < lastDefaultIndex) return true
            lastDefaultIndex = defaultIdx
        }
        return false
    }
}
