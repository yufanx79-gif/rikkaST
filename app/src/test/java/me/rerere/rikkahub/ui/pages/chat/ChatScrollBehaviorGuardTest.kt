package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 聊天列表滚动行为守卫。
 *
 * ## 用户报告的 bug（2026-11-18）
 *
 * > 我在输入框输入文字之后点击发送，它有时候会跳到最顶部。
 * > 如果只有开场白的话，它就会跳到开场白最上面。
 * > 假如一共 5 层楼，我在第 3 层楼看了消息，发送之后应当正常发送，
 * > 然后就直接自动滑到最下面。
 *
 * ## 两个根因
 *
 * 1. **`requestScrollToItem` 把目标对齐到视口顶部**
 *    它是 Compose 的 API，语义是「目标 item 顶边对齐视口顶边」。
 *    聊天列表用它是错的 —— 最后一条消息会跳到屏幕最上方。
 *    必须改成 `scrollToItem(index, viewportHeight)` 才能贴底。
 *
 * 2. **`shouldScroll = !canScrollForward` 的语义反了**
 *    `canScrollForward == false` 表示「已经在底部」。
 *    旧代码意味着「只有已经在底部时才滚」，于是用户在第 3 楼发消息时
 *    列表纹丝不动，看不到自己刚发的那条。
 *
 * 3. **`isAtBottom()` 在内容不足一屏时恒真**
 *    只有开场白时 `lastPos` 远小于 `inputPos`，于是每次可见项变化都触发
 *    一次「滚到最后一项」→ 表现就是跳到开场白最上面。
 *
 * ## 为什么用结构守卫
 *
 * `LazyListState` 需要 Compose 运行时与布局信息，单元测试里无法构造
 * 有意义的实例。本仓库已有同类先例（KotlinNestedCommentGuardTest、
 * PlaceholderTransformerPerfGuardTest）：用源码结构断言锁住修复。
 */
class ChatScrollBehaviorGuardTest {

    private fun chatListSource(): String {
        val f = listOf(
            File("src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt"),
            File("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 ChatList.kt，cwd=${File(".").absolutePath}")
        return f.readText()
    }

    private fun chatPageSource(): String {
        val f = listOf(
            File("src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt"),
            File("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 ChatPage.kt，cwd=${File(".").absolutePath}")
        return f.readText()
    }

    // ---------------------------------------------------------------- 1

    @Test
    fun `send paths always scroll to bottom`() {
        val src = chatPageSource()

        assertFalse(
            "发送路径不应再出现 `!chatListState.canScrollForward` —— 那是" +
                "「只在已经在底部时才滚」，会让第 3 楼发消息时列表不动。" +
                "应改为总是滚到底部。",
            src.contains("val shouldAutoScroll = !chatListState.canScrollForward") ||
                src.contains("val shouldScroll = !chatListState.canScrollForward"),
        )

        assertTrue(
            "onSendClick 应显式声明发送后总是滚到底部",
            src.contains("val shouldAutoScroll = true"),
        )
        assertTrue(
            "面板发送路径也应总是滚到底部",
            src.contains("val shouldScroll = true"),
        )
    }

    // ---------------------------------------------------------------- 2

    @Test
    fun `send and initial scroll use bottom aligned helper`() {
        val page = chatPageSource()
        val list = chatListSource()

        assertTrue(
            "必须提供 scrollToBottomAligned 顶层函数（贴底滚动）",
            list.contains("suspend fun LazyListState.scrollToBottomAligned(lastIndex: Int)"),
        )
        assertTrue(
            "ChatPage 的发送后滚动必须调用 scrollToBottomAligned",
            page.contains("scrollToBottomAligned("),
        )
        assertTrue(
            "进入会话时的初始定位也必须用贴底滚动",
            page.contains("chatListState.scrollToBottomAligned("),
        )
    }

    /**
     * `requestScrollToItem` 只允许出现在「跳到某条历史消息」的场景
     * （那里对齐顶部是正确行为），不允许出现在发送后 / 初始定位。
     */
    @Test
    fun `requestScrollToItem only used for jump to specific message`() {
        val page = chatPageSource()
        val occurrences = Regex("""requestScrollToItem\(""").findAll(page).toList()

        assertTrue(
            "ChatPage 里 requestScrollToItem 只应剩 1 处（onJumpToMessage：跳到指定消息，" +
                "对齐顶部是期望行为）。实际 ${occurrences.size} 处。",
            occurrences.size == 1,
        )

        // 那一处必须在 onJumpToMessage 附近
        val idx = occurrences.first().range.first
        val context = page.substring((idx - 600).coerceAtLeast(0), (idx + 200).coerceAtMost(page.length))
        assertTrue(
            "仅剩的 requestScrollToItem 应位于 onJumpToMessage 内。实际上下文：\n$context",
            context.contains("onJumpToMessage"),
        )
    }

    // ---------------------------------------------------------------- 3

    @Test
    fun `isAtBottom handles content shorter than viewport`() {
        val src = chatListSource()
        assertTrue(
            "isAtBottom 必须显式处理「内容不足一屏」的情况：" +
                "此时列表全部可见，不需要任何滚动。",
            src.contains("!state.canScrollForward && !state.canScrollBackward"),
        )
    }

    @Test
    fun `bottom aligned helper does not scroll when content fits`() {
        val src = chatListSource()
        val body = bodyOfScrollToBottomAligned(src)

        assertTrue(
            "scrollToBottomAligned 在内容不足一屏时必须直接返回",
            body.contains("!canScrollForward && !canScrollBackward"),
        )
        assertTrue(
            "负数索引必须被忽略",
            body.contains("if (lastIndex < 0) return"),
        )
    }

    private fun bodyOfScrollToBottomAligned(src: String): String {
        val start = src.indexOf("suspend fun LazyListState.scrollToBottomAligned")
        assertTrue("找不到 scrollToBottomAligned", start > 0)
        return src.substring(start, (start + 2600).coerceAtMost(src.length))
    }

    // ---------------------------------------------------------------- 4

    /**
     * [2026-11-19] 用户第二轮反馈：
     *
     * > 假如我的助手有一个很长的开场白，我在开场白的中间发送了一条消息，
     * > 此时我希望它能滑动到最下方，固定定位到我的消息。
     * > 或者假如有 10 层楼，我在第 5 层楼发送了消息，我也希望它能直接滑动到第 5 层楼。
     * > 目前是它好像会自动到处跳转，比如我在第二层楼发送消息，就可能跳转到第一层、
     * > 跳转到开场白上。
     *
     * 三个根因（全部已修）：
     *
     * 1. `isAtBottom()` 用了 `visibleItemsInfo.lastOrNull()` ——
     *    那是**视口内最后可见项**，不是列表最后一项。
     *    在 10 层楼停在第 5 层时，视口内最后一项是第 6 层，
     *    它的底边当然在视口内 → isAtBottom 恒为 true → 每帧把用户拽到底。
     *
     * 2. `snapshotFlow { visibleItemsInfo }.collect` 在滚动期间**每帧发射**，
     *    而滚到底又会改变 visibleItemsInfo → 再次发射 → 回弹抖动。
     *
     * 3. `scrollToItem(lastIndex, viewport)` 的 offset 是「第一项顶部滚出视口多少像素」，
     *    传一个视口高度 = 要求最后一项滚出视口一整屏 → clamp 后视口顶部出现大量历史
     *    消息，观感就是「跳到上面去了」。
     */
    @Test
    fun `isAtBottom uses true last item not last visible item`() {
        val src = chatListSource()
        val start = src.indexOf("fun List<LazyListItemInfo>.isAtBottom()")
        assertTrue("找不到 isAtBottom", start > 0)
        val body = src.substring(start, (start + 1800).coerceAtMost(src.length))

        // 必须按「列表真正的最后一项」判断，而不是视口内最后可见项
        assertTrue(
            "isAtBottom 必须用 totalItemsCount - 1 找出真正的最后一项",
            body.contains("total - 1") || body.contains("totalItemsCount - 1"),
        )
        assertFalse(
            "isAtBottom 不能用 `lastOrNull()` —— 那是视口内最后可见项，" +
                "会让「10 层楼停在第 5 层」被判成已在底部",
            Regex("""val\s+lastItem\s*=\s*lastOrNull\(\)""").containsMatchIn(body),
        )
    }

    @Test
    fun `auto scroll is driven by message count not scroll frames`() {
        val src = chatListSource()

        assertFalse(
            "自动跟随不能再由 snapshotFlow { visibleItemsInfo } 驱动 ——" +
                "滚动期间每帧发射，且滚动本身又改变它，形成回弹抖动",
            src.contains("snapshotFlow { state.layoutInfo.visibleItemsInfo }"),
        )
        assertTrue(
            "自动跟随必须改为「消息条数变化」驱动",
            src.contains("LaunchedEffect(conversationUpdated.messageNodes.size)"),
        )
        assertTrue(
            "用户手动上滑过（不在底部附近）时不应打扰",
            src.contains("isCloseToBottom()"),
        )
    }

    @Test
    fun `bottom aligned helper never passes viewport as scroll offset`() {
        val src = chatListSource()
        val body = bodyOfScrollToBottomAligned(src)

        assertFalse(
            "不能再写 scrollToItem(lastIndex, viewport) —— " +
                "offset 语义是「滚出视口多少像素」，传视口高度会让最后一项滚出一整屏",
            body.contains("scrollToItem(lastIndex, viewport)"),
        )
        assertTrue(
            "必须以 scrollToItem(lastIndex) 起步（无 offset 时才 clamp 到最底部）",
            body.contains("scrollToItem(lastIndex)"),
        )
        assertTrue(
            "最后一项比视口高时必须继续 scrollBy 补齐余量，否则长开场白/长回复只看到开头",
            body.contains("scrollBy("),
        )
    }
}
