package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors

/**
 * 页面骨架形态 —— 与 `notes/recon-pagescaffold-20261003.md` §1 的 A/B 型一一对应。
 *
 * 57 个顶层页面里有 45 个是 [Large]（LargeFlexibleTopAppBar + exitUntilCollapsedScrollBehavior），
 * 11 个是 [Small]（普通 TopAppBar）。迁移时按 recon 清单逐页选择，视觉零变化。
 */
enum class PageScaffoldType {
    /** A 型：LargeFlexibleTopAppBar + exitUntilCollapsedScrollBehavior + nestedScroll。 */
    Large,

    /** B 型：普通 TopAppBar，不折叠、不接管 nestedScroll。 */
    Small,
}

/**
 * 统一页面骨架（UI 二期 Batch 0）。
 *
 * 把 57 个页面各自手写的 `Scaffold + topBar + (scrollBehavior/nestedScroll) + topBarColors`
 * 收敛到一处，保证标题栏高度、滚动折叠行为、容器色、返回按钮完全一致。
 *
 * 设计约束（红线）：
 * - 只做「结构收敛」，不改任何页面内容、不改气泡 / 面板链路。
 * - `content` 收到的是 `Scaffold` 的 `innerPadding`，调用方自行 `innerPadding + PaddingValues(x.dp)`
 *   （与迁移前的写法保持逐字一致，避免间距漂移）。
 * - 档 3 六页（ChatPage / GroupChatPage / WebViewPage / WorkspaceTerminalPage /
 *   WorkspaceFileEditorPage / TavernExtensionSettingsPage）**不要**用本组件。
 *
 * @param title 标题文案（调用方自行 `stringResource(...)`）。
 * @param modifier 外层修饰符（[PageScaffoldType.Large] 时会叠加 `nestedScroll`）。
 * @param type 骨架形态，默认 A 型（45/57 页）。
 * @param backEnabled 为 true 且未传 [navigationIcon] 时使用统一的 [BackButton]；无返回栈的页面（如分享入口）传 false。
 * @param navigationIcon 自定义左侧图标；传了就以它为准（优先于 [backEnabled]）。
 * @param actions 标题栏右侧操作区。
 * @param bottomBar 底部栏（输入框 / NavigationBar 等）。
 * @param snackbarHost Snackbar 宿主。
 * @param floatingActionButton 悬浮按钮。
 * @param containerColor Scaffold 容器色，默认与标题栏容器色一致（迁移前各页同款）。
 * @param topBarColors 标题栏配色，默认 [CustomColors.topBarColors]。
 * @param content 内容槽，参数为 Scaffold 的 innerPadding。
 */
@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    type: PageScaffoldType = PageScaffoldType.Large,
    backEnabled: Boolean = true,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = CustomColors.topBarColors.containerColor,
    topBarColors: TopAppBarColors = CustomColors.topBarColors,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val navSlot: @Composable (() -> Unit) = when {
        navigationIcon != null -> navigationIcon
        backEnabled -> ({ BackButton() })
        else -> ({})
    }

    Scaffold(
        modifier = if (type == PageScaffoldType.Large) {
            modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            modifier
        },
        topBar = {
            when (type) {
                PageScaffoldType.Large -> LargeFlexibleTopAppBar(
                    title = { Text(title) },
                    navigationIcon = navSlot,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors = topBarColors,
                )

                PageScaffoldType.Small -> TopAppBar(
                    title = { Text(title) },
                    navigationIcon = navSlot,
                    actions = actions,
                    colors = topBarColors,
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
    ) { innerPadding ->
        content(innerPadding)
    }
}
