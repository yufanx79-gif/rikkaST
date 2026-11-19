package me.rerere.rikkahub.ui.pages.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [2026-11-19 P5] 交互流畅度守卫。
 *
 * ## 用户原话
 * > 主要就是滑动，比如聊天页面有很多消息、很多楼层的消息，滑动不能卡顿；
 * > 返回到下一级菜单，以及加载，也不能卡顿。
 *
 * 并且特别强调：
 * > 供应商只是我发现的问题，肯定还有其他问题。其他问题就是体验问题。
 *
 * 所以这里锁的不是单个页面，而是**四类会在全应用范围内造成掉帧的模式**。
 *
 * ## 为什么用结构断言
 * Compose 的重组/跳过行为是编译期与运行时属性，JVM 单测无法直接测帧率。
 * 本仓库已有先例（`ChatScrollBehaviorGuardTest`、`PlaceholderTransformerPerfGuardTest`）。
 * 这些断言锁的是「已知会掉帧的写法不许回来」。
 */
class ComposePerformanceGuardTest {

    private fun find(rel: String): File? = listOf(
        File(rel),
        File("app/$rel"),
        File("../$rel"),
        File("../app/$rel"),
    ).firstOrNull { it.isFile }

    private fun src(rel: String): String {
        val f = find(rel) ?: error("找不到 $rel（cwd=${File(".").absolutePath}）")
        return f.readText()
    }

    // ---------------------------------------------------------------- 1

    @Test
    fun `导航转场必须跟随全局 standard MotionScheme`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/RouteActivity.kt")

        // Theme.kt 已把全局 MotionScheme 设为 standard（为了 172 处 Switch 的弹簧窗口减半）。
        // 导航这里若独自用 expressive，就绕过了那次优化：
        // expressive 的 spatial spec 弹簧更软、settle 更久，
        // 在「返回上一级菜单」这种高频操作上表现为拖尾 = 用户说的「卡一下」。
        // ⚠️ 必须行锚定并排除注释行。
        // 这个文件会在注释里说明「原来硬编码 MotionScheme.expressive()」，
        // 用裸 contains() 会匹配到注释 —— 本仓库已经在这上面栽过三次
        // （StEventBusContractTest / StSlashSilentNoOpGuardTest / 这里）。
        val codeLines = s.lines().filter { !it.trim().startsWith("//") && !it.trim().startsWith("*") }
        assertFalse(
            "RouteActivity 不应再在**代码**里硬编码 MotionScheme.expressive() —— " +
                "它会绕过 Theme.kt:186 的全局 standard 优化，让返回上一级菜单的动画拖尾。" +
                "应改为 MotionScheme.standard()。",
            codeLines.any { it.contains("MotionScheme.expressive()") },
        )
        assertTrue(
            "导航转场应使用 MotionScheme.standard()",
            s.contains("MotionScheme.standard()"),
        )
    }

    // ---------------------------------------------------------------- 2

    @Test
    fun `设置页的草稿状态不能用整个 settings 当 remember key`() {
        val pages = listOf(
            "app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesGeneralPage.kt",
            "app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesNotificationPage.kt",
            "app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesUIPage.kt",
        )
        for (p in pages) {
            val s = src(p)
            assertFalse(
                "$p 里 `remember(settings)` 的 key 是全量 Settings —— " +
                    "任何一处设置变更都会重置本地草稿状态，表现为输入框闪回 + 卡顿。" +
                    "应收窄为实际用到的字段（如 `remember(settings.displaySetting)`）。",
                Regex("""remember\(settings\)\s*\{\s*mutableStateOf\(settings\.""").containsMatchIn(s),
            )
        }
    }

    // ---------------------------------------------------------------- 3

    @Test
    fun `聊天列表的自动滚动不能由滚动帧驱动`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt")

        // snapshotFlow { visibleItemsInfo } 在滚动期间**每帧发射**，
        // 而「滚到底」这个动作又会改变 visibleItemsInfo → 再次发射 → 回弹抖动。
        // 这是「上下滑动卡一下」的直接来源之一。
        assertFalse(
            "ChatList 不应再用 `snapshotFlow { state.layoutInfo.visibleItemsInfo }` 驱动滚动 —— " +
                "它对滚动帧敏感，会与滚动动作本身形成正反馈。",
            s.contains("snapshotFlow { state.layoutInfo.visibleItemsInfo }"),
        )
        assertTrue(
            "自动跟随应改为「消息条数变化」驱动",
            s.contains("LaunchedEffect(conversationUpdated.messageNodes.size)"),
        )
    }

    // ---------------------------------------------------------------- 4

    @Test
    fun `聊天列表 item 必须用稳定 id 做 key`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt")

        // 用 index 当 key 会让「在中间插入一条消息」导致后面所有 item 全部重组 +
        // 丢失各自的 remembered 状态（滚动位置、展开状态、代码块高亮缓存）。
        // 长对话里这是最明显的卡顿来源之一。
        val indexOnlyKey = Regex("""key\s*=\s*\{\s*_?\s*,\s*index\s*->""")
        assertFalse(
            "LazyColumn 的 key 不能只用 index（${indexOnlyKey.pattern}）—— " +
                "中间插入消息会让后续所有 item 重组并丢失 remember 状态。",
            indexOnlyKey.containsMatchIn(s),
        )
        assertTrue(
            "messages 列表必须用 message id 作为 key",
            Regex("""key\s*=\s*\{\s*index\s*,\s*item\s*->\s*item\.id\s*\}""").containsMatchIn(s),
        )
    }

    // ---------------------------------------------------------------- 5

    @Test
    fun `稳定性配置必须登记高频 UI 参数类型`() {
        val conf = src("app/compose_compiler_config.conf")
        // 这两个类被 100+ 处 Composable 当参数用，不登记 → 全部无法跳过重组。
        val required = listOf(
            "me.rerere.rikkahub.data.datastore.Settings",
            "me.rerere.rikkahub.data.model.Assistant",
        )
        val missing = required.filter { !conf.contains(it) }
        assertTrue(
            "compose_compiler_config.conf 必须登记：$missing。" +
                "它们是 UI 层最高频的参数类型（settings 107 处 / assistant 105 处），" +
                "不登记会导致任何设置变更都让整棵 UI 树重组。",
            missing.isEmpty(),
        )
    }
}
