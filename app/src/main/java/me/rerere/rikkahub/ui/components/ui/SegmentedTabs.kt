package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import me.rerere.rikkahub.ui.theme.AppMotion
import me.rerere.rikkahub.ui.theme.AppRadii
import me.rerere.rikkahub.ui.theme.AppSizes
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.AppType
import me.rerere.rikkahub.ui.theme.appSurfaceColors

/**
 * 通用分段控件（iOS / Kelivo 风格，见 notes/spec-kelivo-port-20261002.md 第 5.3 节）。
 *
 * 视觉：
 * - 外壳圆角 [AppRadii.md]（12dp）、内边距 [AppSpacing.xs]（4dp）、外壳填充 [appSurfaceColors].surfaceCard；
 * - 选中段原地叠 primary @ 14% 底色，未选中透明；切换用 200ms 颜色补间（[AppMotion.press]），
 *   与上游 Kelivo 的 AnimatedContainer 一致 —— 不做滑动指示器、不做 Crossfade；
 * - 每段等宽铺满，点击区高度不低于 [AppSizes.minTouchTarget]（48dp）。
 *
 * 交互：零涟漪（indication = null，Kelivo 的 iOS 触感语言）；点击已选中段不会回调。
 *
 * @param items 段数据源；相等性用于判定选中（items.indexOf(selected)）。
 * @param selected 当前选中项；不在 [items] 内时没有任何段高亮。
 * @param onSelect 选中回调；点击已选中项不会触发。
 * @param modifier 外层修饰符（宽度由调用方约束，各段等宽）。
 * @param label 段文案映射。
 * @param enabled 是否可交互；false 时文案降为 38% alpha 且不响应点击。
 */
@Composable
fun <T> SegmentedTabs(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val selectedIndex = items.indexOf(selected)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.md))
            .background(MaterialTheme.appSurfaceColors.surfaceCard)
            .height(AppSizes.minTouchTarget)
            .padding(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            SegmentedTabItem(
                text = label(item),
                isSelected = index == selectedIndex,
                enabled = enabled,
                onClick = { if (index != selectedIndex) onSelect(item) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SegmentedTabItem(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = AppMotion.press, easing = FastOutSlowInEasing),
        label = "SegmentedTabsSegmentColor",
    )
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.sm))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { selected = isSelected }
            .padding(horizontal = AppSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = AppType.label,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SegmentedTabsPreview() {
    var selected by remember { mutableStateOf("全部") }
    SegmentedTabs(
        items = listOf("全部", "已收藏", "最近"),
        selected = selected,
        onSelect = { selected = it },
        modifier = Modifier.padding(AppSpacing.lg),
    )
}
