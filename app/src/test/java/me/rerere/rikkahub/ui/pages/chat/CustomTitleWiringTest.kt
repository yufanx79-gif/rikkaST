package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「自定义标题」链路完整性守卫。
 *
 * 用户要求：在聊天页左滑进入的会话列表里，长按某条会话，
 * 弹出的菜单（现有「置顶」「重新生成标题」等）里增加一项「自定义标题」。
 *
 * ## 这个测试防什么
 *
 * `renameConversation` 与 `onRenameTitle` 回调其实早就存在，
 * 但从没接到 UI 上 —— 典型的「做了但没起效」。
 * 这条链路跨 4 个文件，任何一处断开用户都点不到：
 *
 *   ConversationList（菜单项 + 输入框）
 *     → ConversationList（内部 ConversationItem 的 onRenameTitle）
 *       → ConversationList（对外参数 onRenameTitle）
 *         → ChatDrawer（接到 vm.renameConversation）
 *           → ChatVM.renameConversation（真正写库）
 */
class CustomTitleWiringTest {

    private fun src(rel: String): String {
        val f = listOf(File("src/main/java/$rel"), File("app/src/main/java/$rel"))
            .firstOrNull { it.isFile }
            ?: error("找不到 $rel，cwd=${File(".").absolutePath}")
        return f.readText().replace("\r\n", "\n")
    }

    private val listFile = "me/rerere/rikkahub/ui/pages/chat/ConversationList.kt"
    private val drawerFile = "me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt"
    private val vmFile = "me/rerere/rikkahub/ui/pages/chat/ChatVM.kt"

    @Test
    fun `menu has custom title entry`() {
        val s = src(listFile)
        assertTrue(
            "会话长按菜单里必须有「自定义标题」入口",
            s.contains("自定义标题"),
        )
        assertTrue(
            "「自定义标题」必须是一个 DropdownMenuItem（和置顶/重新生成标题同级）",
            s.contains("""text = { Text("自定义标题") }"""),
        )
    }

    @Test
    fun `custom title opens input dialog and submits via onRenameTitle`() {
        val s = src(listFile)

        assertTrue("必须有 showRenameDialog 状态", s.contains("showRenameDialog"))
        assertTrue("必须有输入框", s.contains("OutlinedTextField("))
        assertTrue(
            "对话框提交时必须调用 onRenameTitle(conversation, ...)",
            Regex("""onRenameTitle\(conversation,\s*trimmed\)""").containsMatchIn(s),
        )
        assertTrue(
            "空白标题不允许提交（避免把会话名清空）",
            s.contains("enabled = renameText.isNotBlank()") ||
                s.contains("if (trimmed.isNotEmpty())"),
        )
    }

    @Test
    fun `onRenameTitle is threaded through ConversationList`() {
        val s = src(listFile)
        // 对外参数 + 内部 ConversationItem 参数 + 转发，至少 3 处
        val occurrences = Regex("""onRenameTitle""").findAll(s).count()
        assertTrue(
            "onRenameTitle 应至少出现 3 次（对外参数 / 内部参数 / 转发调用），实际 $occurrences",
            occurrences >= 3,
        )
    }

    @Test
    fun `ChatDrawer routes onRenameTitle to viewModel`() {
        val s = src(drawerFile)
        assertTrue(
            "ChatDrawer 必须把 onRenameTitle 接到 vm.renameConversation",
            s.contains("onRenameTitle = {") && s.contains("vm.renameConversation("),
        )
    }

    @Test
    fun `renameConversation writes through repository`() {
        val s = src(vmFile)
        assertTrue("ChatVM 必须有 renameConversation", s.contains("fun renameConversation("))
        assertTrue(
            "renameConversation 必须真正写库（不能只改内存）",
            s.contains("conversationRepo.updateConversation("),
        )
        assertTrue(
            "空白标题必须被拒绝（防御性：UI 之外也可能调用）",
            Regex("""fun renameConversation\([^)]*\)\s*\{[^}]*title\.isBlank\(\)""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(s),
        )
    }
}
