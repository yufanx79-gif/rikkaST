package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.ui.theme.AppSizes
import me.rerere.rikkahub.ui.theme.AppSpacing

/** 空态主图标视觉尺寸（组件私有尺寸：Dimens 只收敛跨页面通用值，私有值留在本文件）。 */
private val EmptyStateIconSize = 48.dp

/** 文案区最大宽度：避免在平板 / 折叠屏上标题被拉成一整行。 */
private val EmptyStateMaxContentWidth = 420.dp

/**
 * 三态组件之一：通用空态（v217 / A3）。
 *
 * 图标 + 标题 + 可选说明 + 可选操作，整体居中；内容超出可视区时可垂直滚动，
 * 因此小屏 / 大字体下也不会裁切。
 *
 * 组件内不含任何硬编码文案，标题与说明由调用方传入。
 *
 * @param title 主标题，必填。
 * @param modifier 外部修饰符。
 * @param description 可选补充说明。
 * @param icon 可选图标；传 null 时不渲染图标区。
 * @param action 可选操作区（建议放一个 M3 Button / Surface(onClick) 之类的单个控件）。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = EmptyStateMaxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // 装饰性图标：空态语义由下方 title / description 文字承载，
                    // 读屏不应重复播报，因此 contentDescription 传 null。
                    contentDescription = null,
                    modifier = Modifier.size(EmptyStateIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.lg))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            if (description != null) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (action != null) {
                Spacer(Modifier.height(AppSpacing.xl))
                Box(
                    // 保证操作区不低于无障碍最小点击尺寸（48dp）。
                    // 传入的控件本身建议用 M3 Button / Surface(onClick)，它们已自带 48dp 触摸目标。
                    modifier = Modifier.defaultMinSize(minHeight = AppSizes.minTouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    action()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    EmptyState(
        title = "Nothing here yet",
        description = "New items will show up here once they are created.",
        icon = HugeIcons.Search01,
    )
}
