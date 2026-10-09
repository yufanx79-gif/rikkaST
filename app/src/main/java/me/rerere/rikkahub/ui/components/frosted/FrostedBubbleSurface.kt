package me.rerere.rikkahub.ui.components.frosted

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.BlurStyle
import me.rerere.rikkahub.data.datastore.MessageBubbleStyle
import me.rerere.rikkahub.data.datastore.MessageStyleSetting
import me.rerere.rikkahub.data.datastore.resolveBubbleStyle
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * [v222 R1-(3) / R4] 磨砂玻璃气泡容器（只画容器，不做 blur）。
 *
 * ## 性能设计（R4 硬门槛）
 * Kelivo 的磨砂气泡**不是**每条都开实时 BackdropFilter —— 它用一层共享的、预先模糊好的
 * backdrop 快照（`frosted_surface.dart` 的 Tier0/Tier1），气泡只贴一张裁剪图 + 半透明 tint。
 * 我们沿用同一个思路，但更省：
 * **壁纸侧一次性模糊 + 气泡侧只做半透明底 + 细边框。**
 * 壁纸侧的 `Modifier.blur` 落在 `ChatPage.kt` 的 `AssistantBackground` 上（由
 * `Settings.messageStyle.blurStyle == FROSTED_GLASS` + `BlurStrength.sigmaDp(blurStrength)` 门控，
 * 0 档 / TRANSPARENT 风格完全不产生 blur）。
 * 因此本组件**没有任何 blur/RenderEffect**，N 条气泡 = N 个纯色矩形，零额外 GPU 成本。
 *
 * 观感指纹（对标 Kelivo 图一/图三）：
 * - 半透明底（`frostedOpacity`，默认 0.66）
 * - 可见细边框 hairline（`borderWidth`，默认 0.8dp；仅在 [BlurStyle.FROSTED_GLASS] 下绘制）
 * - 大圆角（`cornerRadius`，默认 16dp）
 *
 * ⚠️ 红线：本组件**不包裹内容层**（不加额外的 clip/滚动容器），只提供背景 / 边框 / 圆角 / 文字色。
 */
@Composable
fun FrostedBubbleSurface(
    setting: MessageStyleSetting,
    isUser: Boolean,
    style: MessageBubbleStyle,
    modifier: Modifier = Modifier,
    wrapContent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = LocalDarkMode.current
    val colorScheme = MaterialTheme.colorScheme
    val fallbackBackground = if (isUser) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val fallbackBorder = colorScheme.outlineVariant

    val resolved = resolveBubbleStyle(
        setting = setting,
        style = style,
        dark = dark,
        fallbackBackgroundArgb = fallbackBackground.toArgb(),
        fallbackBorderArgb = fallbackBorder.toArgb(),
    )

    // 「透明模糊」= RikkaHub 现状：不加细边框；「磨砂玻璃」= Kelivo 风：加 hairline 细边框。
    val showBorder = setting.blurStyle == BlurStyle.FROSTED_GLASS
    val background = Color(resolved.backgroundArgb)
    val borderColor = Color(resolved.borderArgb)
    val shape = RoundedCornerShape(resolved.cornerRadiusDp.dp)
    val contentColor = resolved.textArgb?.let { Color(it) } ?: LocalContentColor.current

    // R6-①：助手气泡贴合内容。用 IntrinsicSize.Max 让「子级 fillMaxWidth」也被夹到
    // 内容自然宽度（短消息收缩、长消息仍占满可用宽度）。调用方必须保证内容不是 WebView 面板
    // （AndroidView 不实现 intrinsics），见 ChatMessage.kt 的 looksLikeInteractiveHtml 守卫。
    var m = modifier
    if (wrapContent) {
        // [v226 D2] 安全网：width(IntrinsicSize.Max) 之后再套一层 widthIn(min = 24.dp)。
        // SizeNode.maxIntrinsicWidth 会把子级 intrinsic 夹进自身 min/max（javap 实证：
        // foundation-layout 1.12.0 SizeNode#maxIntrinsicWidth = constrainWidth(child, own constraints)），
        // 所以即使将来有 0-intrinsics 内容（AndroidView 面板等）混进来，气泡最多收缩到 24dp，
        // 不会退化成「很细很长的一条竖线」。正常短消息（自然宽度 > 24dp）行为不变。
        m = m.width(IntrinsicSize.Max).widthIn(min = 24.dp)
    }
    m = m
        .clip(shape)
        .background(background)
    if (showBorder && resolved.borderWidthDp > 0f) {
        m = m.border(resolved.borderWidthDp.dp, borderColor, shape)
    }
    if (onClick != null) {
        m = m.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(modifier = m) {
            content()
        }
    }
}
