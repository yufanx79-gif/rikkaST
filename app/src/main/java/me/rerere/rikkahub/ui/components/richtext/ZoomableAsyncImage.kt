package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.st.runtime.TavernResourceRoute
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.theme.LocalDarkMode

@Composable
fun ZoomableAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
) {
    var showImageViewer by remember { mutableStateOf(false) }
    var failed by remember(model) { mutableStateOf(false) }
    var retryAttempt by remember(model) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val placeholder = if (LocalDarkMode.current) R.drawable.placeholder_dark else R.drawable.placeholder
    val export = LocalExportContext.current
    val resolvedData: Any? = remember(model, context) {
        val m = model?.trim().orEmpty()
        if (m.isEmpty()) {
            model
        } else {
            TavernRuntimeManager.resolveLocalAssetForCoil(context, m) ?: model
        }
    }

    // 外链图片统一先走宿主的 VPN/直连双通道下载 + 磁盘缓存，再交给 Coil；
    // 下载失败保留原始 URL，让 Coil 自己重试/走系统网络，不静默丢图。
    val remoteUrl = model?.trim().orEmpty()
    val isRemote = remoteUrl.startsWith("http://", ignoreCase = true) ||
        remoteUrl.startsWith("https://", ignoreCase = true)
    var routedData by remember(model, retryAttempt) {
        mutableStateOf<Any?>(if (isRemote) null else resolvedData)
    }
    LaunchedEffect(model, retryAttempt) {
        if (isRemote) {
            val cached = TavernResourceRoute.fetchImageToCache(context, remoteUrl, timeoutMs = 3_500L)
            routedData = cached ?: resolvedData
        } else {
            routedData = resolvedData
        }
    }

    if (failed) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.86f))
                .clickable {
                    failed = false
                    if (remoteUrl.isNotBlank()) {
                        // 缓存文件损坏/过期时，重试必须真正重新走网络，而不是再次解同一份坏缓存。
                        TavernResourceRoute.evictCachedUrl(context, remoteUrl)
                    }
                    retryAttempt++
                }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "图片加载失败\n点击重试",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    if (isRemote && routedData == null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val coilModel = ImageRequest.Builder(context)
        .data(routedData)
        .placeholder(placeholder)
        .error(placeholder)
        .crossfade(false)
        .allowHardware(!export)
        .build()
    var loading by remember { mutableStateOf(false) }

    key(retryAttempt) {
        AsyncImage(
            model = coilModel,
            contentDescription = contentDescription,
            modifier = modifier
                .shimmer(isLoading = loading)
                .clickable { showImageViewer = true },
            contentScale = contentScale,
            alpha = alpha,
            alignment = alignment,
            onLoading = {
                loading = true
            },
            onSuccess = {
                loading = false
                failed = false
            },
            onError = {
                loading = false
                failed = true
            },
        )
    }
    if (showImageViewer) {
        val previewTarget = (routedData as? java.io.File)?.absolutePath ?: (model ?: "")
        ImagePreviewDialog(images = listOf(previewTarget)) {
            showImageViewer = false
        }
    }
}
