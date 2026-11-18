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
        val start = src.indexOf("suspend fun LazyListState.scrollToBottomAligned")
        assertTrue("找不到 scrollToBottomAligned", start > 0)
        val body = src.substring(start, (start + 900).coerceAtMost(src.length))

        assertTrue(
            "scrollToBottomAligned 在内容不足一屏时必须直接返回",
            body.contains("!canScrollForward && !canScrollBackward"),
        )
        assertTrue(
            "scrollToBottomAligned 必须用 scrollToItem(index, viewport) 贴底，" +
                "而不是默认的顶对齐",
            body.contains("scrollToItem(lastIndex, viewport)"),
        )
        assertTrue(
            "负数索引必须被忽略",
            body.contains("if (lastIndex < 0) return"),
        )
    }
}
