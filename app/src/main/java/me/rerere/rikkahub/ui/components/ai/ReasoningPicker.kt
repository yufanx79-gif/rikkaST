package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.components.ui.icons.ReasoningHigh
import me.rerere.rikkahub.ui.components.ui.icons.ReasoningLow
import me.rerere.rikkahub.ui.components.ui.icons.ReasoningMedium
import me.rerere.rikkahub.ui.theme.AppMotion
import me.rerere.rikkahub.ui.theme.AppSizes
import me.rerere.rikkahub.ui.theme.AppSpacing
import kotlin.math.roundToInt

/*
 * 思考强度（Reasoning Effort）选择器 —— Kelivo 形态（v220 重做）。
 *
 * 为什么重做：v219 那版是「自绘渐变条（primary→tertiary）+ 无 thumb + 6 列 11sp 文字刻度行」，
 * 主人实测反馈「太丑」，并给了 Kelivo 的截图作为目标形态（要么退回、要么做得跟他一样）。
 * 本轮选择「做得跟他一样」，并按主人要求把配色收敛到主题色。
 *
 * 目标形态（对照 Kelivo 1.3.0 `_EffortSlider` / `_SliderPainter` / `_buildLabelPill`，
 * 见 kelivo-src/kelivo-1.3.0/lib/features/chat/widgets/reasoning_budget_sheet.dart:254-323、471-685）：
 *   1. 顶部「当前档 pill」：图标 + 档位名（primary、17sp 量级、semibold）+ 副标题
 *      （onSurfaceVariant、12sp），整体居中、**没有卡片外壳**；切换档位时淡入淡出；
 *   2. 一条 56dp 高的自绘滑杆：34dp 全胶囊轨道 + 已选段 `primary@50% → primary` 水平渐变
 *      + 刻度圆点（已过 = onPrimary、未到 = onSurface@25%）+ 独立白色 thumb（38dp，带阴影，
 *      拖动时放大 1.14）；
 *   3. **没有文字刻度行**（档位名只在 pill 里显示，避免中文标签在窄屏挤成一团）。
 *
 * 为什么这样取色（主人要求「颜色跟主题色有关」）：
 *   - 填充 = `primary`（渐变从 50% alpha 到 100%，与 Kelivo 完全同款）；
 *   - 已过刻度点 = `onPrimary`（在 primary 填充上永远可读，浅色/深色主题自动翻转）；
 *   - 未到刻度点 = `onSurface@25%`、轨道底色 = `onSurface@10%`（Kelivo 同款比例）；
 *   - thumb = `surfaceContainerLowest`（浅色主题下即 Kelivo 的白；深色主题自动翻成最暗档），
 *     阴影用平台 `Modifier.shadow`（≈ Kelivo 的 BoxShadow(blur 10, offset (0,3))）。
 *   本文件不出现硬编码颜色，也不使用 `.sp` 字面量；尺寸字面量只以**具名常量**形式出现在
 *   [ReasoningSliderSpec] / [ThumbElevation]（Kelivo 组件私有几何，不入 Dimens token 层，
 *   与 EmptyState / LoadingState 的既有写法一致）。
 *
 * 手感（只对齐机制，未复制 Kelivo 的 Dart 代码）：
 *   - 拖动：连续 position 直接跟手，拖动期间**不写回**（v219 起的改进，比 Kelivo 少写设置）；
 *   - 松手 / 点击：spring 吸附到最近档位（弹簧常数照抄 Kelivo 的 mass 1 / stiffness 320 / damping 28
 *     → ζ≈0.783，见 [ReasoningSliderSpec.SnapDampingRatio]），触感反馈 + 写回；
 *   - 点击轨道任意位置 → 吸附到最近档位（Kelivo 的 onTapDown 语义）。
 *
 * 约束：
 *   - 纯观感 / 手感改动，不动数据模型：档位仍来自 [ReasoningLevel.entries]（6 档，无 Max）；
 *   - [ReasoningButton] 的调用签名保持不变（ChatInput / AssistantBasicPage / SettingModelPromptPage）；
 *   - 尺寸 / 间距走 AppSpacing / AppSizes，动效走 MaterialTheme.motionScheme 与 AppMotion，
 *     取色走 colorScheme。
 *
 * 回归：几何纯函数见 app/src/test/.../ui/components/ai/ReasoningSliderSpecTest.kt；
 *       观感回归 = 真机截图对照（notes/recon-reasoning-slider-ref-20261003.md 的参考图量测表）。
 */

private val levels = ReasoningLevel.entries
private val levelCount = levels.size
private val lastIndex = (levelCount - 1).toFloat()

/** thumb 阴影高度：近似 Kelivo 的 BoxShadow(blurRadius 10dp, offset (0,3dp), black 25%)。 */
private val ThumbElevation = 6.dp

/**
 * 滑杆几何 / 手感常量与纯函数。
 *
 * 数值对齐 Kelivo 1.3.0 的 `_EffortSlider` + `_SliderPainter`（组件私有几何，不入 token 层）。
 * 全部是不依赖 Density / Composition 的纯计算，便于单测；像素与 dp 的换算由调用点负责。
 */
internal object ReasoningSliderSpec {
    /** 56dp —— 控件总高。thumb（38dp）比轨道（34dp）高，必须有独立槽位容纳溢出。 */
    val Height = 56.dp

    /** 34dp —— 轨道高度；圆角取高度一半 → 全胶囊。 */
    val TrackHeight = 34.dp

    /** 38dp —— thumb 直径（Kelivo 半径 19dp）。 */
    val ThumbSize = 38.dp

    /** 7dp —— 刻度点直径（Kelivo 半径 3.5dp）。 */
    val DotSize = 7.dp

    /**
     * 停靠点内缩 = thumb 半径。
     * 于是首 / 末档的 thumb 恰好与轨道端头齐平 —— 极端档位不会露出轨道残边（Kelivo 同款约定）。
     */
    val StopInset = ThumbSize / 2

    /** 拖动时 thumb 的放大倍数（Kelivo：AnimatedScale 1.14 + easeOutBack）。 */
    const val DragScale = 1.14f

    /**
     * 吸附弹簧 = Kelivo 的 `SpringSimulation(mass 1, stiffness 320, damping 28)`：
     * 阻尼比 ζ = 28 / (2·√320) ≈ 0.783（略带过冲，Q 弹但不晃），固有频率 ω = √320。
     */
    const val SnapDampingRatio = 0.783f

    /** 见 [SnapDampingRatio]。 */
    const val SnapStiffness = 320f

    /** 连续档位 [position] 对应的 thumb 中心 x（像素）。 */
    fun thumbX(position: Float, width: Float, count: Int, inset: Float): Float {
        val span = width - 2f * inset
        if (count <= 1 || span <= 0f) return inset
        val t = (position / (count - 1)).coerceIn(0f, 1f)
        return inset + span * t
    }

    /** 第 [index] 档停靠点（刻度点 / 吸附目标）的 x（像素）。 */
    fun stopX(index: Int, width: Float, count: Int, inset: Float): Float =
        thumbX(index.toFloat(), width, count, inset)

    /** 手指 x（像素）→ [0, count - 1] 的连续档位；超出两端夹紧。 */
    fun fractionForX(x: Float, width: Float, count: Int, inset: Float): Float {
        val span = width - 2f * inset
        if (count <= 1 || span <= 0f) return 0f
        return ((x - inset) / span * (count - 1)).coerceIn(0f, (count - 1).toFloat())
    }
}

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    reasoningLevel: ReasoningLevel,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ReasoningPicker(
            reasoningLevel = reasoningLevel,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningLevel = onUpdateReasoningLevel
        )
    }

    ToggleSurface(
        checked = reasoningLevel.isEnabled,
        onClick = { showPicker = true },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = AppSpacing.sm, horizontal = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Box(
                modifier = Modifier.size(AppSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                ReasoningIcon(reasoningLevel)
            }
            if (!onlyIcon) Text(stringResource(R.string.setting_provider_page_reasoning))
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningLevel: ReasoningLevel,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    val currentIndex = levels.indexOf(reasoningLevel).coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // 连续档位位置（0f..lastIndex）：拖动时直接跟手，松手后 spring 吸附到整数档位。
    var position by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var snapJob by remember { mutableStateOf<Job?>(null) }

    val snapSpec = remember {
        spring<Float>(
            dampingRatio = ReasoningSliderSpec.SnapDampingRatio,
            stiffness = ReasoningSliderSpec.SnapStiffness,
        )
    }

    // 提交一档：触感 + 写回。拖动过程中不写回，只在松手 / 点击 / 外部变更时写。
    fun commit(index: Int) {
        val safe = index.coerceIn(0, levelCount - 1)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onUpdateReasoningLevel(levels[safe])
    }

    // 把连续位置弹到 targetIndex；新的吸附会取消上一次未完成的动画。
    fun settleTo(targetIndex: Int) {
        val target = targetIndex.coerceIn(0, levelCount - 1).toFloat()
        snapJob?.cancel()
        snapJob = scope.launch {
            animate(
                initialValue = position,
                targetValue = target,
                animationSpec = snapSpec,
            ) { value, _ -> position = value }
        }
    }

    // 外部档位变化（父级回写）→ 也走同一条吸附动画。
    LaunchedEffect(currentIndex) {
        if (!dragging) settleTo(currentIndex)
    }

    // 拖动 / 点击过程中 pill 预览「当前最近档位」；松手才写回。
    val previewIndex = position.roundToInt().coerceIn(0, levelCount - 1)
    val previewLevel = levels[previewIndex]

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxl)
                .padding(bottom = AppSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xl),
        ) {
            // 当前档 pill（拖动时实时预览）
            ReasoningLevelPill(level = previewLevel)

            ReasoningSlider(
                position = position,
                dragging = dragging,
                onDragStart = { fraction ->
                    snapJob?.cancel()
                    dragging = true
                    position = fraction
                },
                onDrag = { fraction -> position = fraction },
                onDragEnd = {
                    dragging = false
                    val target = position.roundToInt().coerceIn(0, levelCount - 1)
                    settleTo(target)
                    commit(target)
                },
                onDragCancel = {
                    dragging = false
                    settleTo(currentIndex)
                },
                onTapStop = { index ->
                    dragging = false
                    settleTo(index)
                    commit(index)
                },
            )
        }
    }
}

/**
 * 当前档位 pill：图标 + 档位名（primary）+ 副标题，整体居中、无卡片外壳（Kelivo 形态）。
 *
 * 档位切换用淡入淡出 + SizeTransform（副标题长短会改变宽度，必须让容器宽度跟着动，
 * 否则居中块会跳帧）。
 */
@Composable
private fun ReasoningLevelPill(level: ReasoningLevel) {
    val accent = MaterialTheme.colorScheme.primary

    AnimatedContent(
        targetState = level,
        transitionSpec = {
            (fadeIn(tween(AppMotion.fast)) togetherWith fadeOut(tween(AppMotion.fast)))
                .using(SizeTransform(clip = false))
        },
        label = "reasoningLevelPill",
    ) { animatedLevel ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                ReasoningIcon(
                    level = animatedLevel,
                    modifier = Modifier.size(AppSizes.icon),
                    tint = accent,
                )
                Text(
                    text = animatedLevel.label(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = accent,
                )
            }
            Text(
                text = animatedLevel.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/**
 * 自绘档位滑杆（Kelivo `_EffortSlider` 形态）。
 *
 * 视觉：34dp 全胶囊轨道（onSurface@10%）+ 已选段 `primary@50% → primary` 水平渐变
 * + 刻度圆点 + 独立 thumb（surfaceContainerLowest，带平台阴影）。
 *
 * 交互：
 *   - 横向拖动跟手（只改 position，不写回）；按下并越过横向 slop 才进入拖动；
 *   - 点击任意位置 → 吸附到最近档位（与 Kelivo 的 onTapDown 语义一致）；
 *   - 松手由调用方吸附 + 写回。
 *
 * @param position 连续档位位置（0f..lastIndex）
 * @param dragging 是否正在拖动（驱动 thumb 放大）
 * @param onDragStart 越过横向 slop：报告按下处的连续档位
 * @param onDrag 拖动中：报告手指当前 x 对应的连续档位
 * @param onDragEnd 松手：由调用方吸附并写回
 * @param onDragCancel 手势被取消：由调用方回弹到已提交档位
 * @param onTapStop 点击：报告吸附目标档位下标
 */
@Composable
private fun ReasoningSlider(
    position: Float,
    dragging: Boolean,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTapStop: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val passedDotColor = MaterialTheme.colorScheme.onPrimary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val upcomingDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val thumbColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val density = LocalDensity.current
    val insetPx = with(density) { ReasoningSliderSpec.StopInset.toPx() }
    val thumbRadiusPx = with(density) { ReasoningSliderSpec.ThumbSize.toPx() } / 2f

    // pointerInput 的 key 固定 → 回调走 rememberUpdatedState，避免捕获旧 lambda。
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnTapStop by rememberUpdatedState(onTapStop)

    // 拖动时 thumb 放大（Kelivo：AnimatedScale 1.14）。
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) ReasoningSliderSpec.DragScale else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "reasoningThumbScale",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(ReasoningSliderSpec.Height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = position,
                    range = 0f..lastIndex,
                    steps = (levelCount - 2).coerceAtLeast(0),
                )
            }
            // 点击直达：只有「按下 → 抬起且没有任何人消费移动」才成立，
            // 所以不会抢走拖动（拖动越 slop 后消费 change，tap 自动取消）。
            .pointerInput(levelCount, insetPx) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    currentOnTapStop(
                        ReasoningSliderSpec
                            .fractionForX(offset.x, width, levelCount, insetPx)
                            .roundToInt()
                    )
                }
            }
            .pointerInput(levelCount, insetPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat()
                        currentOnDragStart(
                            ReasoningSliderSpec.fractionForX(offset.x, width, levelCount, insetPx)
                        )
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                    onHorizontalDrag = { change, _ ->
                        val width = size.width.toFloat()
                        currentOnDrag(
                            ReasoningSliderSpec.fractionForX(change.position.x, width, levelCount, insetPx)
                        )
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbCenterX = ReasoningSliderSpec.thumbX(position, widthPx, levelCount, insetPx)
        val passedIndex = position.roundToInt()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeightPx = ReasoningSliderSpec.TrackHeight.toPx()
            val corner = trackHeightPx / 2f
            val centerY = size.height / 2f
            val top = centerY - corner

            // 未选区间：onSurface@10% 胶囊
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeightPx),
                cornerRadius = CornerRadius(corner, corner),
            )

            // 已选区间：primary@50% → primary 水平渐变，铺满 [0, thumbCenterX]。
            // 渐变端点跟着 thumb 走（等价于 Kelivo 把 shader 建在 fillRect 上），
            // 再裁到胶囊内，避免极左档位 / 窄屏露出直角。
            if (thumbCenterX > 0.5f) {
                val capsule = Path().apply {
                    addRoundRect(
                        RoundRect(0f, top, size.width, top + trackHeightPx, corner, corner)
                    )
                }
                clipPath(capsule) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primary.copy(alpha = 0.5f), primary),
                            startX = 0f,
                            endX = thumbCenterX,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(thumbCenterX, trackHeightPx),
                    )
                }
            }

            // 刻度点：thumb 左侧已过的用 onPrimary（压在 primary 填充上恒可读），
            // 右侧未到的用 onSurface@25%。
            val dotRadius = ReasoningSliderSpec.DotSize.toPx() / 2f
            for (index in 0 until levelCount) {
                drawCircle(
                    color = if (index <= passedIndex) passedDotColor else upcomingDotColor,
                    radius = dotRadius,
                    center = Offset(
                        x = ReasoningSliderSpec.stopX(index, size.width, levelCount, insetPx),
                        y = centerY,
                    ),
                )
            }
        }

        // thumb 单独一层：Canvas 画不出柔和阴影，用平台 Modifier.shadow 更接近 Kelivo 的
        // BoxShadow(blur 10dp / offset (0,3dp))。scale 必须在最外层，否则阴影与底色不在同一层。
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((thumbCenterX - thumbRadiusPx).roundToInt(), 0) }
                .scale(thumbScale)
                .size(ReasoningSliderSpec.ThumbSize)
                .shadow(elevation = ThumbElevation, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
private fun ReasoningIcon(
    level: ReasoningLevel,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    when (level) {
        ReasoningLevel.OFF -> Icon(HugeIcons.Idea, null, modifier = modifier, tint = tint)
        ReasoningLevel.AUTO -> Icon(HugeIcons.Idea01, null, modifier = modifier, tint = tint)
        ReasoningLevel.LOW -> Icon(ReasoningLow, null, modifier = modifier, tint = tint)
        ReasoningLevel.MEDIUM -> Icon(ReasoningMedium, null, modifier = modifier, tint = tint)
        ReasoningLevel.HIGH -> Icon(ReasoningHigh, null, modifier = modifier, tint = tint)
        ReasoningLevel.XHIGH -> Icon(ReasoningHigh, null, modifier = modifier, tint = tint)
    }
}

@Composable
private fun ReasoningLevel.label(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
}

@Composable
private fun ReasoningLevel.description(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off_desc)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto_desc)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light_desc)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium_desc)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy_desc)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh_desc)
}

@Preview(showBackground = true)
@Composable
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var level by remember { mutableStateOf(ReasoningLevel.AUTO) }
        ReasoningPicker(
            reasoningLevel = level,
            onUpdateReasoningLevel = { level = it }
        )
    }
}