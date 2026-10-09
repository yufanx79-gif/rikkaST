package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.rikkahub.ui.theme.AppMotion
import me.rerere.rikkahub.ui.theme.AppRadii
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.AppStroke
import me.rerere.rikkahub.ui.theme.AppType
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.appSurfaceColors

// v217 A4：视觉对齐 Kelivo 的 iOS 分组卡（spec-kelivo-port-20261002 第 5.1 节）
private val CardGroupCorner = AppRadii.md
private val CardGroupDividerAlpha = 0.18f

private data class CardGroupItem(
    val onClick: (() -> Unit)?,
    val modifier: Modifier,
    val overlineContent: (@Composable () -> Unit)?,
    val headlineContent: @Composable () -> Unit,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val colors: ListItemColors?,
)

@DslMarker
private annotation class CardGroupDsl

@CardGroupDsl
interface CardGroupScope {
    fun item(
        onClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        overlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        colors: ListItemColors? = null,
        headlineContent: @Composable () -> Unit,
    )
}

private class CardGroupScopeImpl : CardGroupScope {
    val items = mutableListOf<CardGroupItem>()

    override fun item(
        onClick: (() -> Unit)?,
        modifier: Modifier,
        overlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        colors: ListItemColors?,
        headlineContent: @Composable () -> Unit,
    ) {
        items.add(
            CardGroupItem(
                onClick = onClick,
                modifier = modifier,
                overlineContent = overlineContent,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = colors,
            )
        )
    }
}

/**
 * 一行设置项。
 *
 * v217 A4：整组共用一张卡片（见 [CardGroup]），所以这里不再自绘圆角，
 * 按压反馈也从「圆角弹开」换成「200ms 颜色补间 + 零涟漪」（Kelivo 的 iOS 触感语言，
 * 见 spec-kelivo-port-20261002 第 5.1 / 5.4 节）。
 */
@Composable
private fun CardGroupListItem(
    item: CardGroupItem,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressTint by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = AppMotion.press, easing = FastOutSlowInEasing),
        label = "CardGroupPressTint",
    )

    ListItem(
        headlineContent = item.headlineContent,
        modifier = item.modifier
            .fillMaxWidth()
            .background(pressTint)
            .then(
                if (item.onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        // 零涟漪：Kelivo 的 iOS 触感语言（上游 ios_tactile.dart:133-148）
                        indication = null,
                        onClick = item.onClick,
                    )
                } else {
                    Modifier
                }
            ),
        overlineContent = item.overlineContent,
        supportingContent = item.supportingContent,
        leadingContent = item.leadingContent,
        trailingContent = item.trailingContent,
        // 容器透明：底色由外层那张 surfaceCard 提供，行内只叠按压着色。
        colors = item.colors ?: ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * iOS 风格分组设置卡。
 *
 * v217 A4：API 与 v216 完全一致（item DSL + title），只换视觉 ——
 * 由「每个 item 各自一张圆角 20 的 surfaceBright 卡、中间留 2dp 缝」
 * 改成「整组一张 surfaceCard 卡（圆角 12 + 0.6dp hairline 描边）+ 行间 0.6dp 分隔线」。
 * 填充与描边取 [MaterialTheme.appSurfaceColors]，因此会跟着 Surface Ladder（A2）走：
 * 自定义主题/动态色下自动协调，7 套预设回落 M3 容器档位。
 */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()

    Column(modifier = modifier) {
        if (title != null) {
            // 分组标题：13sp semibold、中性色、左边与卡片内容对齐（Kelivo settings_page 规格）。
            ProvideTextStyle(
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = AppType.label,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(
                        modifier = Modifier.padding(
                            start = AppSpacing.lg,
                            top = AppSpacing.sm,
                            bottom = AppSpacing.sm,
                        )
                    ) {
                        title()
                    }
                }
            }
        }

        val count = scope.items.size
        if (count > 0) {
            val surfaceColors = MaterialTheme.appSurfaceColors
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardGroupCorner),
                color = surfaceColors.surfaceCard,
                border = BorderStroke(AppStroke.hairline, surfaceColors.hairline),
            ) {
                Column {
                    scope.items.fastForEachIndexed { index, item ->
                        CardGroupListItem(item = item)
                        if (index != count - 1) {
                            HorizontalDivider(
                                thickness = AppStroke.hairline,
                                color = MaterialTheme.colorScheme.outlineVariant
                                    .copy(alpha = CardGroupDividerAlpha),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CardGroupPreview() {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("Card Group")
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            CardGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = { Text("About") },
            ) {
                item(
                    headlineContent = { Text("第一项") },
                )
                item(
                    headlineContent = { Text("第二项") },
                    supportingContent = { Text("支持文本") },
                )
                item(
                    onClick = {},
                    headlineContent = { Text("第三项") },
                    trailingContent = { Text("→") },
                )
            }
        }
    }
}
