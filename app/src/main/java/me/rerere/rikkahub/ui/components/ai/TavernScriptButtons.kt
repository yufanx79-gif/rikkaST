package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager

/**
 * 脚本按钮行（JSR 语义）：聊天页输入区上方渲染酒馆脚本注册的按钮。
 * 点击经 [TavernRuntimeManager.fireScriptButton] 派发回脚本按钮事件。
 */
@Composable
fun TavernScriptButtonsRow(
    modifier: Modifier = Modifier,
) {
    val buttons by TavernRuntimeManager.scriptButtonsFlow.collectAsStateWithLifecycle()
    if (buttons.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(buttons, key = { "${it.scriptId}::${it.name}" }) { button ->
            AssistChip(
                onClick = { TavernRuntimeManager.fireScriptButton(button) },
                label = {
                    Text(
                        text = button.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
