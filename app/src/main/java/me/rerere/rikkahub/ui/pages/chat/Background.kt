package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant

@Composable
fun AssistantBackground(setting: Settings, modifier: Modifier) {
    val assistant = setting.getCurrentAssistant()
    if (assistant.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    // [v251 根因修复] 无自定义背景时必须**仍然应用 modifier**（它带着 hazeSource）。
    //
    // 旧实现在「无渐变背景 + 无背景图」（= 绝大多数用户的默认状态）时直接走到函数末尾，
    // modifier 从未被应用 → hazeSource 从未挂上 → 输入栏的 hazeBlur 没有任何采样源。
    // 输入栏在「开磨砂总闸 + 非磨砂玻璃」档位下 color = Color.Transparent（见 ChatInput.kt:469），
    // 完全依赖 haze 采样出内容，没有采样源时就渲染成一片空白 = 用户看到的「输入栏变透明」。
    //
    // 这里补一个纯色底：既让 modifier（hazeSource）真正生效，也给 haze 一份可采样的内容，
    // 保证输入栏在任何背景配置下都能正常显示磨砂效果。
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundImage = assistant.background
    if (backgroundImage == null) {
        Box(modifier = modifier.background(backgroundColor))
        return
    }
    val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
    Box(modifier = modifier) {
        AsyncImage(
            model = backgroundImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(backgroundOpacity)
        )

        // 全屏渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.2f),
                            backgroundColor.copy(alpha = 0.5f)
                        )
                    )
                )
        )
    }
}
