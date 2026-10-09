package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.ui.theme.AppMotion
import me.rerere.rikkahub.ui.theme.AppRadii
import me.rerere.rikkahub.ui.theme.AppSizes
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.AppStroke
import me.rerere.rikkahub.ui.theme.AppType
import me.rerere.rikkahub.ui.theme.appSurfaceColors

/** 设置行按压洗色强度：与 CardGroup 的行按压保持一致。 */
private const val SettingsRowPressAlpha = 0.06f

/** 设置行按压缩放：IosCardPress 推荐的轻微缩放（spec 第 5.4 节）。 */
private const val SettingsRowPressedScale = 0.98f

/**
 * iOS 分组设置卡（Kelivo section_card.dart / notes/spec-kelivo-port-20261002.md 第 5.1 节）。
 *
 * 整组一张卡：圆角 [AppRadii.md]（12dp）、[AppStroke.hairline]（0.6dp）描边、
 * 填充 [appSurfaceColors].surfaceCard、描边色 [appSurfaceColors].hairline。
 * 用 Material3 Surface 承载，内容会被 antiAlias 裁剪到圆角内，圆角处不会露边。
 *
 * @param title 可选分组标题（13sp semibold、onSurface @ 80%）；为 null 时只渲染卡片。
 * @param modifier 外层修饰符。
 * @param content 卡片内容（通常是若干 [SettingsNavRow]，分隔线由调用方自行插入）。
 */
@Composable
fun SettingsSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaceColors = MaterialTheme.appSurfaceColors

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(
                    start = AppSpacing.md,
                    end = AppSpacing.md,
                    top = AppSpacing.xs,
                    bottom = AppSpacing.xs,
                ),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = AppType.label,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadii.md),
            color = surfaceColors.surfaceCard,
            border = BorderStroke(AppStroke.hairline, surfaceColors.hairline),
        ) {
            Column(content = content)
        }
    }
}

/**
 * iOS 分组设置行（Kelivo ios_settings_rows.dart 的 IosNavRow / spec 第 5.2、5.4 节）。
 *
 * 布局：左侧 36dp 图标槽（[AppSizes.iconSlot]，20dp 图标 [AppSizes.icon]，
 * 槽底 [appSurfaceColors].surfaceCardFill）→ 12dp 间距 → 主文案 15sp（[AppType.body]、
 * onSurface @ 90%）+ 可选明细 13sp（[AppType.label]、onSurface @ 60%）
 * → 尾部自定义内容，或 16dp chevron（[AppSizes.chevron]）。
 *
 * 按压：参考 IosCardPress —— 零涟漪（indication = null）、onSurface @ 6% 洗色 + 0.98 缩放，
 * 200ms 补间（[AppMotion.press]）；没有 [onClick] 时整行不可点、也不显示 chevron。
 *
 * @param title 主文案。
 * @param subtitle 可选明细；单行省略。
 * @param icon 可选左侧图标；为 null 时仍保留 36dp 槽位，保证多行文案对齐。
 * @param onClick 点击回调；为 null 时整行不可点。
 * @param trailing 尾部自定义内容；非 null 时优先于 chevron 显示。
 */
@Composable
fun SettingsNavRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressTint by animateColorAsState(
        targetValue = if (isPressed && onClick != null) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = SettingsRowPressAlpha)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = AppMotion.press, easing = FastOutSlowInEasing),
        label = "SettingsNavRowPressTint",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) SettingsRowPressedScale else 1f,
        animationSpec = tween(durationMillis = AppMotion.press, easing = FastOutSlowInEasing),
        label = "SettingsNavRowPressScale",
    )
    val surfaceColors = MaterialTheme.appSurfaceColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .background(pressTint)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(AppSizes.iconSlot),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(AppSizes.iconSlot)
                        .clip(RoundedCornerShape(AppRadii.sm))
                        .background(surfaceColors.surfaceCardFill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(AppSizes.icon),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(AppSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = AppType.body),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = AppType.label),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            trailing()
        } else if (onClick != null) {
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier.size(AppSizes.chevron),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsSectionPreview() {
    Column(modifier = Modifier.padding(AppSpacing.lg)) {
        SettingsSection(title = "通用") {
            SettingsNavRow(
                title = "语言",
                subtitle = "简体中文",
                icon = HugeIcons.ArrowRight01,
                onClick = {},
            )
            SettingsNavRow(
                title = "主题",
                onClick = {},
            )
            SettingsNavRow(
                title = "版本",
                trailing = { Text("2.4.6") },
            )
        }
    }
}
