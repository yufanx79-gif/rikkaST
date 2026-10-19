package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
) {
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    var imeHeigh by remember { mutableIntStateOf(0) }
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            ime.getBottom(localDensity)
        }.collect { keyboardHeight ->
            val previousHeight = imeHeigh
            imeHeigh = keyboardHeight
            // 仅在软键盘弹出（高度递增）且列表当前未处于用户手势/程序跳转滚动时补偿正向位移；
            // 软键盘收起（如点击发送后 keyboardController.hide()）时清零高度，绝不反向抢占 scroll MutatorMutex，
            // 防止在长消息中间点击发送时与 requestScrollToItem 发生滚动锁争用导致卡顿或吞滚动。
            val delta = keyboardHeight - previousHeight
            if (keyboardHeight > 0 && delta > 0 && !lazyListState.isScrollInProgress && lazyListState.canScrollForward) {
                runCatching {
                    lazyListState.scrollBy(delta.toFloat())
                }
            }
        }
    }
}
