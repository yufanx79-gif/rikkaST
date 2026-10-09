package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * [v229 P0-4] 「开发者模式」开关（设置一级页面「通用设置」入口）。
 *
 * 语义（主人 P0-3 口径）：
 * ① 整体动画：复用项目既有 Material3 [Switch]（轨道色 / thumb 色 / 位置
 *    由 M3 内部同一个 transition 一起过渡），不引入第三方依赖，也不自创一套组件；
 * ② 连点串行吸收、一次点击 = 一次翻转、最终状态 = 最后一次点击：
 *    - 组件持有**局部镜像** [state]，在点击当帧同步翻转；
 *    - 回调里的 `!state` 读的是**调用时刻**的值（Kotlin 委托属性在
 *      lambda 内每次调用都会重新 getValue，不是组合期快照），所以即便
 *      重组还没跑，第 2 次点击也会基于第 1 次的结果取反；
 *    - 对外只回调**目标布尔值**（不是 toggle），调用方串行写回同一个
 *      key，最后一次点击必胜出。
 * ③ 外部真值追平：`remember(checked)` —— 真值被其它入口改掉时镜像跟随；
 *    未变化时（含自己写回的回声）不重置，连点期间不抖动。
 */
@Composable
fun DeveloperModeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember(checked) { mutableStateOf(checked) }
    Switch(
        checked = state,
        onCheckedChange = { _ ->
            val next = !state
            state = next
            onCheckedChange(next)
        },
        modifier = modifier,
    )
}
