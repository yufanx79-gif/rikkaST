package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.theme.AppPillShape
import me.rerere.rikkahub.ui.theme.AppSizes
import me.rerere.rikkahub.ui.theme.AppSpacing

/** 骨架标题条高度（组件私有尺寸：Dimens 只收敛跨页面通用值，私有值留在本文件）。 */
private val SkeletonHeaderHeight = 24.dp

/** 骨架正文主行高度。 */
private val SkeletonLineHeight = 14.dp

/** 骨架正文次行高度（更细，模拟次要文案）。 */
private val SkeletonLineHeightSmall = 12.dp

/**
 * 三态组件之一：页面级 Loading 骨架屏（v217 / A3）。
 *
 * 用于 History / Stats / Favorite / Search 等页面首帧数据到达前的占位。
 * 用主题色块 + shimmer 扫光代替转圈指示器，避免加载期间的布局跳动与白屏。
 *
 * - [showHeader] 控制是否渲染页面标题占位（大标题 + 副标题两行）。
 * - [rows] 控制列表行占位数量。
 *
 * 颜色全部来自 [MaterialTheme.colorScheme]（不硬编码）；间距 / 圆角 / 图标槽位优先取
 * Dimens token（[AppSpacing] / [AppSizes] / [AppPillShape]）。
 * 骨架屏是纯装饰内容，整体已从无障碍树中移除，读屏不会逐个播报无意义的占位节点。
 *
 * @param modifier 外部修饰符。
 * @param rows 列表行占位数量；小于等于 0 时只渲染标题区。
 * @param showHeader 是否渲染页面标题占位。
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    rows: Int = 3,
    showHeader: Boolean = true,
) {
    val skeletonColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shimmer(
                isLoading = true,
                // shimmer 的两种颜色在 DstIn 混合模式下只取 alpha：
                // 亮部 alpha 更低，于是骨架块被"擦亮"，形成扫光。
                shimmerColor = skeletonColor.copy(alpha = 0.35f),
                backgroundColor = skeletonColor.copy(alpha = 0.95f),
            )
            // 骨架屏是纯装饰：从无障碍树摘掉，避免读屏播报无意义的占位节点。
            .clearAndSetSemantics { }
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        if (showHeader) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SkeletonBar(widthFraction = 0.6f, height = SkeletonHeaderHeight)
                SkeletonBar(widthFraction = 0.35f, height = SkeletonLineHeightSmall)
            }
        }

        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(AppSizes.iconSlot)
                        .clip(CircleShape)
                        .background(skeletonColor),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    SkeletonBar(widthFraction = 0.8f, height = SkeletonLineHeight)
                    SkeletonBar(widthFraction = 0.5f, height = SkeletonLineHeightSmall)
                }
            }
        }
    }
}

/** 单个骨架条：胶囊形，颜色跟随主题。 */
@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(AppPillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
    LoadingState(rows = 4)
}
