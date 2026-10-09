package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.components.ai.ReasoningSliderSpec
import me.rerere.rikkahub.ui.theme.AppRadii
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.appSurfaceColors

/**
 * [v222 R1-(2)] 公共「档位选择」组件。
 *
 * 设计约束（主人 2026-10-04 拍板）：
 * - 「模糊强度」（显示参数）复刻「思考强度」（模型参数）的**形态与手感**，但**不得把两件事耦合成一个组件** ——
 *   所以这里是一份**通用**的档位选择器，思考强度与模糊强度**各自调用**它。
 * - 几何与手感**必须复用** `ui/components/ai/ReasoningPicker.kt` 里的 [ReasoningSliderSpec]
 *   （`thumbX` / `stopX` / `fractionForX` + v220 已验收的弹簧常量），**不重写一套**。
 * - 交互契约：**拖动不写回、松手弹簧吸附后才写回**（`onCommit` 只在吸附结束时回调一次）。
 *
 * ⚠️ 本文件是**新增**的公共组件；`ReasoningPicker` 目前仍保留自己的 pill/slider 绘制实现
 * （它已被主人验收「非常漂亮」，本轮刻意零改动其对外行为），但两边**共享同一份几何纯函数**，
 * 因此手感一致。把 `ReasoningPicker` 也切到本组件属于后续收敛项（见 notes/STATUS.md）。
 */
@Immutable
data class LevelOption(
    val label: String,
    val description: String,
    val icon: ImageVector,
)

/** 档位名（越界自动夹紧），便于设置行右侧显示当前档位。 */
fun levelLabelOf(options: List<LevelOption>, index: Int): String {
    if (options.isEmpty()) return ""
    return options[index.coerceIn(0, options.lastIndex)].label
}

/** 单个档位 pill：图标 + 档位名 + 副标题。 */
@Composable
fun LevelPill(
    option: LevelOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primary.copy(alpha = 0.14f) else MaterialTheme.appSurfaceColors.surfaceCard
    val content = if (selected) colors.primary else colors.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadii.md),
        color = container,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 自绘档位滑杆：圆点刻度 + 弹簧吸附 thumb。
 *
 * 手柄：拖动期间不写回，松手后按 [ReasoningSliderSpec.SnapDampingRatio] /
 * [ReasoningSliderSpec.SnapStiffness] 弹簧吸附到最近档位，再回调 [onCommit]。
 */
@Composable
fun LevelSlider(
    count: Int,
    selectedIndex: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spec = ReasoningSliderSpec
    val insetPx = with(density) { spec.StopInset.toPx() }
    val thumbSizePx = with(density) { spec.ThumbSize.toPx() }
    val dotRadiusPx = with(density) { spec.DotSize.toPx() / 2f }
    // [v225 T4] 与「思考强度」滑杆同一套配色（主人：两边颜色要一样，思考强度变色它也一起变）：
    // 轨道 onSurface@10% + 已选段 primary@50% -> primary 水平渐变 + 已过刻度 onPrimary /
    // 未到刻度 onSurface@25% + thumb surfaceContainerLowest。全部取自 colorScheme。
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val passedDotColor = MaterialTheme.colorScheme.onPrimary
    val upcomingDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val thumbColor = MaterialTheme.colorScheme.surfaceContainerLowest

    var widthPx by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(selectedIndex.coerceIn(0, count - 1).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }

    // 外部值变化（重置 / 别处改动）时跟随；拖动与吸附过程中不打断。
    LaunchedEffect(selectedIndex, count) {
        if (!dragging && !settling) {
            value = selectedIndex.coerceIn(0, count - 1).toFloat()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(spec.Height)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(count, widthPx) {
                detectDragGestures(
                    onDragStart = { start ->
                        if (widthPx <= 0f) return@detectDragGestures
                        dragging = true
                        settling = false
                        value = spec.fractionForX(start.x, widthPx, count, insetPx)
                    },
                    onDrag = { change, _ ->
                        if (widthPx <= 0f) return@detectDragGestures
                        change.consume()
                        value = spec.fractionForX(change.position.x, widthPx, count, insetPx)
                    },
                    onDragEnd = {
                        val target = value.roundToInt().coerceIn(0, count - 1)
                        dragging = false
                        settling = true
                        scope.launch {
                            animate(
                                initialValue = value,
                                targetValue = target.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = spec.SnapDampingRatio,
                                    stiffness = spec.SnapStiffness,
                                ),
                            ) { v, _ -> value = v }
                            settling = false
                            // 吸附结束后才写回
                            onCommit(target)
                        }
                    },
                    onDragCancel = {
                        val target = value.roundToInt().coerceIn(0, count - 1)
                        dragging = false
                        settling = true
                        scope.launch {
                            animate(
                                initialValue = value,
                                targetValue = target.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = spec.SnapDampingRatio,
                                    stiffness = spec.SnapStiffness,
                                ),
                            ) { v, _ -> value = v }
                            settling = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(spec.TrackHeight),
        ) {
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                size = size,
            )
            val thumbCenterX = ReasoningSliderSpec.thumbX(value, size.width, count, insetPx)
            if (thumbCenterX > 0.5f) {
                val capsule = Path().apply {
                    addRoundRect(
                        RoundRect(0f, 0f, size.width, size.height, size.height / 2f, size.height / 2f)
                    )
                }
                clipPath(capsule) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primary.copy(alpha = 0.5f), primary),
                            startX = 0f,
                            endX = thumbCenterX,
                        ),
                        topLeft = Offset.Zero,
                        size = Size(thumbCenterX, size.height),
                    )
                }
            }
            val passedIndex = value.roundToInt().coerceIn(0, count - 1)
            for (i in 0 until count) {
                val cx = ReasoningSliderSpec.stopX(i, size.width, count, insetPx)
                drawCircle(
                    color = if (i <= passedIndex) passedDotColor else upcomingDotColor,
                    radius = dotRadiusPx,
                    center = Offset(cx, size.height / 2f),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val cx = ReasoningSliderSpec.thumbX(value, widthPx, count, insetPx)
                    IntOffset((cx - thumbSizePx / 2f).roundToInt(), 0)
                }
                .size(spec.ThumbSize)
                .graphicsLayer {
                    val s = if (dragging) ReasoningSliderSpec.DragScale else 1f
                    scaleX = s
                    scaleY = s
                }
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/** 底部弹窗：标题 + 档位 pill 行 + 自绘滑杆。形态对齐「思考强度」弹窗。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelPickerSheet(
    title: String,
    options: List<LevelOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg)
                .padding(bottom = AppSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                options.forEachIndexed { index, option ->
                    LevelPill(
                        option = option,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                    )
                }
            }
            LevelSlider(
                count = options.size,
                selectedIndex = selectedIndex,
                onCommit = onSelect,
            )
        }
    }
}

/**
 * 设置行：左侧标题 + 副标题，右侧显示当前档位名；点击打开 [LevelPickerSheet]。
 *
 * @param enabled false 时置灰且不响应点击（例如「消息样式 = 默认」时模糊强度不可调）。
 */
@Composable
fun LevelPickerRow(
    title: String,
    subtitle: String?,
    options: List<LevelOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppSpacing.lg,
        vertical = AppSpacing.md,
    ),
) {
    var showSheet by remember { mutableStateOf(false) }
    val current = levelLabelOf(options, selectedIndex)
    val alpha = if (enabled) 1f else 0.38f

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
        onClick = { if (enabled) showSheet = true },
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    )
                }
            }
            Text(
                text = current,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
        }
    }

    if (showSheet) {
        LevelPickerSheet(
            title = title,
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            onDismiss = { showSheet = false },
        )
    }
}
