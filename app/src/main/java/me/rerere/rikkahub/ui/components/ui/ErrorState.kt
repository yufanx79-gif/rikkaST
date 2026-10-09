package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.AppSizes
import me.rerere.rikkahub.ui.theme.AppSpacing

/** 错误态主图标视觉尺寸（组件私有尺寸：Dimens 只收敛跨页面通用值，私有值留在本文件）。 */
private val ErrorStateIconSize = 48.dp

/** 文案区最大宽度：避免在平板 / 折叠屏上错误信息被拉成一整行。 */
private val ErrorStateMaxContentWidth = 420.dp

/**
 * 三态组件之一：通用错误态 + 重试（v217 / A3）。
 *
 * 错误图标 + 可选标题 + 错误信息 + 可选「重试」按钮，整体居中；
 * 内容超出可视区时可垂直滚动，因此小屏 / 大字体下也不会裁切。
 *
 * 视觉语言与 [ErrorCard] 对齐：同样是 error 色系图标 + 主次两行文字，
 * 区别是本组件面向整页占位（居中、可重试），[ErrorCard] 面向顶部的临时错误条。
 *
 * 除「重试」按钮文案取自 [R.string.common_retry] 外，组件内不含硬编码文案。
 *
 * @param message 错误信息，必填。
 * @param modifier 外部修饰符。
 * @param title 可选标题，传 null 时只显示 message。
 * @param onRetry 重试回调；传 null 时不渲染重试按钮。
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ErrorStateMaxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = HugeIcons.AlertCircle,
                // 装饰性图标：错误语义由下方 title / message 文字承载，读屏不应重复播报。
                contentDescription = null,
                modifier = Modifier.size(ErrorStateIconSize),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(AppSpacing.lg))

            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AppSpacing.sm))
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (onRetry != null) {
                Spacer(Modifier.height(AppSpacing.xl))
                // M3 Button 内部经 Surface(onClick) 应用 minimumInteractiveComponentSize()，
                // 触摸目标自动不低于 48dp（即 AppSizes.minTouchTarget），无需额外撑大视觉高度。
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = HugeIcons.Refresh01,
                        // 装饰性图标：按钮文案已经表达「重试」，避免读屏重复播报。
                        contentDescription = null,
                        modifier = Modifier.size(AppSizes.icon),
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(text = stringResource(R.string.common_retry))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    ErrorState(
        title = "Something went wrong",
        message = "The request failed. Check your connection and try again.",
        onRetry = {},
    )
}
