package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Blur
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BlurStrength
import me.rerere.rikkahub.data.datastore.BlurStyle
import me.rerere.rikkahub.data.datastore.BubbleStyleTheme
import me.rerere.rikkahub.data.datastore.MessageBubbleStyle
import me.rerere.rikkahub.data.datastore.MessageStyleSetting
import me.rerere.rikkahub.ui.components.frosted.MessageBubbleContainer
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.LevelOption
import me.rerere.rikkahub.ui.components.ui.LevelPickerRow
import me.rerere.rikkahub.ui.components.ui.PageScaffold
import me.rerere.rikkahub.ui.components.ui.SegmentedTabs
import me.rerere.rikkahub.ui.theme.AppRadii
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * [v222 R5] 设置 -> 偏好设置 -> 消息样式。
 *
 * 全量对齐 Kelivo `message_style_settings_page.dart` 的 11 项（缺一不算完）：
 * ①样式三选一 ②助手气泡贴合内容 ③分段显示为多个气泡 ④模糊强度 ⑤背景颜色+不透明度
 * ⑥边框颜色+不透明度+宽度 ⑦文字颜色 ⑧圆角半径 ⑨浅色/深色两套 ⑩实时预览区 ⑪重置（二次确认）
 *
 * ★ 关键约束（主人 2026-10-04 明确）：**只能全局设置，不做 per-assistant 覆盖**。
 * ★ 「模糊强度」与显示设置里的「模糊强度」模块**是同一份数据**（`Settings.messageStyle.blurStrength`），
 *   两处入口、同一个值。
 */
@Composable
fun SettingPreferencesMessageStylePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val messageStyle = settings.messageStyle
    val systemDark = LocalDarkMode.current.let { it } || isSystemInDarkTheme()
    var isDarkTab by rememberSaveable { mutableStateOf(systemDark) }
    var showResetDialog by remember { mutableStateOf(false) }

    // 唯一写入口：同时维护 v221 旧开关的回滚镜像（enableBlurEffect <-> style != DEFAULT）。
    fun updateMessageStyle(new: MessageStyleSetting) {
        // [v227 D4] 窄写回：只改 MESSAGE_STYLE / DISPLAY_SETTING 两个 key（旧实现重写 ~80 个 key），
        // 且变换作用在 settingsFlow 当前值上（不再用组合期捕获的 settings 快照覆盖并发改动）。
        vm.patchMessageStyle { new }
        vm.patchDisplaySetting {
            it.copy(enableBlurEffect = new.style != MessageBubbleStyle.DEFAULT)
        }
    }

    fun updateTheme(transform: (BubbleStyleTheme) -> BubbleStyleTheme) {
        // [v229 A4/P0-2] 4 个滑块的写回路径。
        //   旧写法：`messageStyle.themeFor(isDarkTab)` —— messageStyle 是**组合期快照**，滑块连拖时
        //   每一帧都用同一个旧基线回写；中间任一帧被旧回声拉回后，后续帧就以被拉回的值继续，
        //   真机表现 =「松手后数值自己慢慢挪」。
        //   新写法：把变换作用在**当前存储值**上（`patchMessageStyle` 内部用的是 settingsFlow.value），
        //   基线永远最新；配合写回层的旧回声门控（SettingsEchoGate），数值不会再被拉走。
        //   注意：这里只改 dark/light 主题分支，`style` 字段不变 -> 无需顺带镜像 enableBlurEffect，
        //   也省掉一次多余的 key 写盘（拖 4 个滑块时每帧少一次 edit）。
        val dark = isDarkTab
        vm.patchMessageStyle { current ->
            val base = current.themeFor(dark)
            val next = transform(base)
            if (dark) current.copy(dark = next) else current.copy(light = next)
        }
    }

    val styleEnabled = messageStyle.style != MessageBubbleStyle.DEFAULT

    // SegmentedTabs 的 label 是普通函数类型，@Composable 文案必须先在组合上下文里求值好。
    val lightTabLabel = stringResource(R.string.message_style_page_light)
    val darkTabLabel = stringResource(R.string.message_style_page_dark)
    val styleLabels = mapOf(
        MessageBubbleStyle.DEFAULT to styleLabel(MessageBubbleStyle.DEFAULT),
        MessageBubbleStyle.FROSTED to styleLabel(MessageBubbleStyle.FROSTED),
        MessageBubbleStyle.SOLID to styleLabel(MessageBubbleStyle.SOLID),
    )
    val blurStyleLabels = mapOf(
        BlurStyle.TRANSPARENT to blurStyleLabel(BlurStyle.TRANSPARENT),
        BlurStyle.FROSTED_GLASS to blurStyleLabel(BlurStyle.FROSTED_GLASS),
    )

    PageScaffold(
        title = stringResource(R.string.message_style_page_title),
        actions = {
            TextButton(onClick = { showResetDialog = true }) {
                Text(stringResource(R.string.message_style_page_reset))
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- ⑨ 浅色 / 深色（作用范围 = 下面所有「每套」参数） ----
            item {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    SegmentedTabs(
                        items = listOf(false, true),
                        selected = isDarkTab,
                        onSelect = { isDarkTab = it },
                        // label 是普通函数类型 (T)->String，不能内联 @Composable 的 stringResource
                        label = { if (it) darkTabLabel else lightTabLabel },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- ① 样式三选一（作用范围 = 气泡 + 输入栏） ----
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.message_style_section_style)) },
                ) {
                    item(
                        headlineContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                SegmentedTabs(
                                    items = MessageBubbleStyle.entries.toList(),
                                    selected = messageStyle.style,
                                    onSelect = { updateMessageStyle(messageStyle.copy(style = it)) },
                                    label = { styleLabels.getValue(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = styleSubtitle(messageStyle.style),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    // 模糊风格（R1-(1)：挂在「启用模糊效果」下的子选项；关闭时不可选）
                    item(
                        headlineContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                Text(
                                    text = stringResource(R.string.message_style_blur_style_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (styleEnabled) 1f else 0.38f
                                    ),
                                )
                                SegmentedTabs(
                                    items = BlurStyle.entries.toList(),
                                    selected = messageStyle.blurStyle,
                                    onSelect = { updateMessageStyle(messageStyle.copy(blurStyle = it)) },
                                    label = { blurStyleLabels.getValue(it) },
                                    enabled = styleEnabled,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = blurStyleSubtitle(messageStyle.blurStyle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (styleEnabled) 1f else 0.38f
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            // ---- ②③ 助手气泡 ----
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.message_style_section_assistant)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.message_style_fit_content_title)) },
                        supportingContent = { Text(stringResource(R.string.message_style_fit_content_subtitle)) },
                        trailingContent = {
                            Switch(
                                checked = messageStyle.assistantBubbleWrapContent,
                                onCheckedChange = {
                                    updateMessageStyle(messageStyle.copy(assistantBubbleWrapContent = it))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.message_style_split_paragraphs_title)) },
                        supportingContent = { Text(stringResource(R.string.message_style_split_paragraphs_subtitle)) },
                        trailingContent = {
                            Switch(
                                checked = messageStyle.splitSegmentsAsBubbles,
                                onCheckedChange = {
                                    updateMessageStyle(messageStyle.copy(splitSegmentsAsBubbles = it))
                                },
                            )
                        },
                    )
                }
            }

            // ---- ⑤ [v229 P1-4] 顶栏形态（两版参数，主人自己挑」） ----
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.message_style_section_top_fade)) },
                ) {
                    // [v233] 总开关（默认关）：关 = 不渲染任何顶栏遮罩
                    item(
                        headlineContent = { Text(stringResource(R.string.message_style_top_fade_enable_title)) },
                        supportingContent = {
                            Text(stringResource(R.string.message_style_top_fade_enable_subtitle))
                        },
                        trailingContent = {
                            Switch(
                                checked = messageStyle.topFadeEnabled,
                                onCheckedChange = { on ->
                                    vm.patchMessageStyle { it.copy(topFadeEnabled = on) }
                                },
                            )
                        },
                    )
                    // [v233] 样式选择（总开关开启后才有意义；关时不渲染，选哪版都无效果）
                    item(
                        headlineContent = { Text(stringResource(R.string.message_style_top_fade_style_title)) },
                        supportingContent = {
                            Text(stringResource(R.string.message_style_top_fade_style_subtitle))
                        },
                        trailingContent = {
                            Switch(
                                checked = messageStyle.topFadeBottomAnchored,
                                // [v229 P0-3 语义] 直接写目标布尔值（非 toggle）；写回走当前值变换
                                onCheckedChange = { on ->
                                    vm.patchMessageStyle { it.copy(topFadeBottomAnchored = on) }
                                },
                            )
                        },
                    )
                }
            }

            // ---- ④ 模糊强度（与显示设置里的模块同一份数据） ----
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = {
                            LevelPickerRow(
                                title = stringResource(R.string.message_style_blur_title),
                                subtitle = stringResource(R.string.message_style_blur_hint),
                                options = blurLevelOptions(),
                                selectedIndex = messageStyle.blurStrength,
                                onSelect = {
                                    updateMessageStyle(messageStyle.copy(blurStrength = BlurStrength.clampIndex(it)))
                                },
                                enabled = styleEnabled,
                                contentPadding = PaddingValues(0.dp),
                            )
                        },
                    )
                }
            }

            // ---- ⑤⑥⑦⑧ 外观（每套：亮/暗各一份） ----
            if (!styleEnabled) {
                item {
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        item(
                            headlineContent = { Text(stringResource(R.string.message_style_page_default_hint)) },
                        )
                    }
                }
            } else {
                val theme = messageStyle.themeFor(isDarkTab)
                val isFrosted = messageStyle.style == MessageBubbleStyle.FROSTED
                item {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text(stringResource(R.string.message_style_section_appearance)) },
                    ) {
                        item(
                            headlineContent = { Text(stringResource(R.string.message_style_background_color)) },
                            trailingContent = {
                                ColorSwatch(
                                    argb = theme.backgroundArgb,
                                    onPick = { updateTheme { t -> t.copy(backgroundArgb = it) } },
                                )
                            },
                        )
                        item(
                            headlineContent = {
                                LabeledSlider(
                                    title = stringResource(R.string.message_style_background_opacity),
                                    value = if (isFrosted) theme.frostedOpacity else theme.solidOpacity,
                                    valueRange = 0.05f..1f,
                                    display = { "${(it * 100).toInt()}%" },
                                    onValueChange = { v ->
                                        updateTheme { t ->
                                            if (isFrosted) t.copy(frostedOpacity = v) else t.copy(solidOpacity = v)
                                        }
                                    },
                                )
                            },
                        )
                        item(
                            headlineContent = { Text(stringResource(R.string.message_style_border_color)) },
                            trailingContent = {
                                ColorSwatch(
                                    argb = theme.borderArgb,
                                    onPick = { updateTheme { t -> t.copy(borderArgb = it) } },
                                )
                            },
                        )
                        item(
                            headlineContent = {
                                LabeledSlider(
                                    title = stringResource(R.string.message_style_border_opacity),
                                    value = theme.borderOpacity,
                                    valueRange = 0f..1f,
                                    display = { "${(it * 100).toInt()}%" },
                                    onValueChange = { v -> updateTheme { t -> t.copy(borderOpacity = v) } },
                                )
                            },
                        )
                        item(
                            headlineContent = {
                                LabeledSlider(
                                    title = stringResource(R.string.message_style_border_width),
                                    value = theme.borderWidth,
                                    valueRange = 0f..4f,
                                    display = { String.format(Locale.US, "%.1f", it) },
                                    onValueChange = { v -> updateTheme { t -> t.copy(borderWidth = v) } },
                                )
                            },
                        )
                        item(
                            headlineContent = { Text(stringResource(R.string.message_style_text_color)) },
                            trailingContent = {
                                ColorSwatch(
                                    argb = theme.textArgb,
                                    onPick = { updateTheme { t -> t.copy(textArgb = it) } },
                                )
                            },
                        )
                        item(
                            headlineContent = {
                                LabeledSlider(
                                    title = stringResource(R.string.message_style_corner_radius),
                                    value = theme.cornerRadius,
                                    valueRange = 0f..40f,
                                    display = { "${it.toInt()}dp" },
                                    onValueChange = { v -> updateTheme { t -> t.copy(cornerRadius = v) } },
                                )
                            },
                        )
                    }
                }
            }

            // ---- ⑩ 实时预览区 ----
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.message_style_section_preview)) },
                ) {
                    item(
                        headlineContent = {
                            // [v227 D5] 预览区三条硬要求（逐条对应主人 v226 真机反馈）：
                            // ① 1:1 复刻聊天页 -> 容器走 MessageBubbleContainer（判据与聊天页同源）；
                            // ② 调参立刻可见 -> 用 isDarkTab 覆盖 LocalDarkMode：旧实现读「真实系统模式」，
                            //    在「非当前模式」那一页调参时预览**完全不变**（主人：「看不到任何变化」）；
                            // ③ 必须有气泡框 -> 底色从「卡片色」换成「模拟壁纸」渐变：卡片色 surfaceCard
                            //    恰好 == 助手气泡的 fallback 底色 surfaceContainerHigh（SurfaceLadder.kt:201），
                            //    半透明气泡叠在同色底上等于不可见（主人：「预览框里没有气泡框」）。
                            CompositionLocalProvider(LocalDarkMode provides isDarkTab) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AppRadii.md))
                                        .background(previewWallpaperBrush())
                                        .padding(AppSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                ) {
                                    PreviewRoleLabel(
                                        text = stringResource(R.string.message_style_role_assistant),
                                        alignEnd = false,
                                    )
                                    MessageBubbleContainer(
                                        setting = messageStyle,
                                        isUser = false,
                                        bubbleOpacity = settings.displaySetting.bubbleOpacity,
                                        showAssistantBubble = settings.displaySetting.showAssistantBubble,
                                        wrapContent = messageStyle.assistantBubbleWrapContent,
                                        modifier = Modifier.align(Alignment.Start),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.message_style_preview_assistant),
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    PreviewThinkingRow(modifier = Modifier.align(Alignment.Start))
                                    PreviewRoleLabel(
                                        text = stringResource(R.string.message_style_role_user),
                                        alignEnd = true,
                                    )
                                    MessageBubbleContainer(
                                        setting = messageStyle,
                                        isUser = true,
                                        bubbleOpacity = settings.displaySetting.bubbleOpacity,
                                        showAssistantBubble = settings.displaySetting.showAssistantBubble,
                                        modifier = Modifier.align(Alignment.End),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.message_style_preview_user),
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.message_style_page_title)) },
            text = { Text(stringResource(R.string.message_style_page_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateMessageStyle(MessageStyleSetting())
                        showResetDialog = false
                    }
                ) { Text(stringResource(R.string.message_style_page_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.message_style_page_cancel))
                }
            },
        )
    }
}

@Composable
internal fun styleLabel(style: MessageBubbleStyle): String = when (style) {
    MessageBubbleStyle.DEFAULT -> stringResource(R.string.message_style_style_default)
    MessageBubbleStyle.FROSTED -> stringResource(R.string.message_style_style_frosted)
    MessageBubbleStyle.SOLID -> stringResource(R.string.message_style_style_solid)
}

@Composable
internal fun styleSubtitle(style: MessageBubbleStyle): String = when (style) {
    MessageBubbleStyle.DEFAULT -> stringResource(R.string.message_style_style_default_subtitle)
    MessageBubbleStyle.FROSTED -> stringResource(R.string.message_style_style_frosted_subtitle)
    MessageBubbleStyle.SOLID -> stringResource(R.string.message_style_style_solid_subtitle)
}

@Composable
internal fun blurStyleLabel(style: BlurStyle): String = when (style) {
    BlurStyle.TRANSPARENT -> stringResource(R.string.message_style_blur_style_transparent)
    BlurStyle.FROSTED_GLASS -> stringResource(R.string.message_style_blur_style_frosted_glass)
}

@Composable
internal fun blurStyleSubtitle(style: BlurStyle): String = when (style) {
    BlurStyle.TRANSPARENT -> stringResource(R.string.message_style_blur_style_transparent_subtitle)
    BlurStyle.FROSTED_GLASS -> stringResource(R.string.message_style_blur_style_frosted_glass_subtitle)
}

@Composable
internal fun blurLevelOptions(): List<LevelOption> {
    // 5 档：关 / 弱 / 中 / 强 / 极强；副标题 = 实际 sigma（dp），让「数值感」可见。
    val labels = listOf(
        R.string.message_style_blur_level_off,
        R.string.message_style_blur_level_weak,
        R.string.message_style_blur_level_medium,
        R.string.message_style_blur_level_strong,
        R.string.message_style_blur_level_extreme,
    )
    val icon = HugeIcons.Blur
    // @Composable 的 stringResource 必须逐个在组合上下文求值，不能塞进 mapIndexed 的非组合 lambda。
    val names = listOf(
        stringResource(labels[0]),
        stringResource(labels[1]),
        stringResource(labels[2]),
        stringResource(labels[3]),
        stringResource(labels[4]),
    )
    return names.mapIndexed { index, name ->
        val sigma = BlurStrength.sigmaDp(index)
        LevelOption(
            label = name,
            description = if (sigma <= 0f) "0dp" else "${sigma.toInt()}dp",
            icon = icon,
        )
    }
}

/**
 * [v227 D5] 预览区「模拟壁纸」渐变。
 *
 * 为什么必须有它：`appSurfaceColors.surfaceCard` == `colorScheme.surfaceContainerHigh`
 * （SurfaceLadder.kt:201 明写这条等价），而助手气泡的 fallback 底色**就是** surfaceContainerHigh
 * -> 半透明 / 磨砂气泡叠在同色底上等于**不可见**（主人真机：「预览框里没有气泡框」）。
 * 这里取与「用户气泡底色 primaryContainer」「助手气泡底色 surfaceContainerHigh」都不撞的三档容器色，
 * 既让气泡可见，又不引入任何硬编码色（红线 5：主题一致性）。
 *
 * ★ [v228 C] 上面三档主题容器色被**主人明确要求**替换为固定中性近黑（「预览的背景就统一改成黑色…
 *   灰色比较好。或是黑色也比较好」）：部分主题下三档容器色与气泡底色明度接近 -> 气泡依旧不显眼。
 *   这是红线 5 的豁免项（主人点名预览底色），实现见下方 [PreviewWallpaperDark]。
 */
/** [v228 C] 预览底色主色（近黑）。主人点名要求的中性色（红线 5 豁免）。 */
private val PreviewWallpaperDark = Color(0xFF1E1E1E)

/** [v228 C] 微渐变中段：纯平底会让磨砂 / 半透明气泡与底完全融为一体，留一点明度差才能看清边界。 */
private val PreviewWallpaperDarkMid = Color(0xFF2A2A2A)

/** [v228 C] 预览区角色标签在近黑底上的固定浅灰（主题 onSurfaceVariant 在亮色主题下是深灰 -> 看不清）。 */
private val PreviewLabelOnDark = Color(0xFFB4B4B4)

/** [v228 C] 固定中性近黑底色（亮暗两页统一），代替 v227 的三档主题容器色。 */
@Composable
private fun previewWallpaperBrush(): Brush = Brush.linearGradient(
    colors = listOf(PreviewWallpaperDark, PreviewWallpaperDarkMid, PreviewWallpaperDark),
)

@Composable
private fun PreviewRoleLabel(text: String, alignEnd: Boolean) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            // [v228 C] 固定浅灰：预览底色已改近黑（见 previewWallpaperBrush），主题色会看不清
            color = PreviewLabelOnDark,
            modifier = Modifier.align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}

/** 预览区「思考中」胶囊行（对标图三的磨砂胶囊）。 */
@Composable
private fun PreviewThinkingRow(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.66f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = stringResource(R.string.message_style_preview_thinking),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 标题 + 数值 + 滑杆 的一行（右侧实时显示数值）。 */
@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    // [v228 B] 滑块「松手后自己滑」收口（主人 v227 验收 P0）。
    //
    // 真机现象：拖到某处松手后，滑杆自己继续滑到终点 / 滑回某处。
    // 根因链（notes/review-v227b + v228 S9 ②）：onValueChange 每帧走 patchMessageStyle（乐观写）
    //   -> DataStore 落盘后再发一次同一个 flow -> toMutableStateFlow 用写前快照把 StateFlow 覆盖回去
    //   -> 滑杆下一帧读到被拉回的旧值 -> 看起来就是「自己滑」。
    //
    // 修法（本地状态前置 + 松手提交锁）：
    //   1. 拖动期本地值权威，**完全不读**外部 value（旧回声影响不到画面）；
    //   2. 松手记下最后拖到的值（pendingCommit）并继续用本地值渲染；
    //   3. 外部值追平该值（最后一笔写回的回声到了）才解除锁定、交回外部值；
    //      期间到达的**旧回声**被忽略（否则会闪一下再弹回来）。
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    var pendingCommit by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value, dragging, pendingCommit) {
        if (dragging) return@LaunchedEffect
        val pending = pendingCommit
        if (pending == null) {
            local = value
        } else if (kotlin.math.abs(value - pending) <= 0.001f) {
            pendingCommit = null
            local = value
        } else {
            local = pending
        }
    }
    val shown = if (dragging || pendingCommit != null) local else value

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = display(shown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = shown.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { v ->
                local = v
                dragging = true
                onValueChange(v)
            },
            onValueChangeFinished = {
                // 松手：进入「提交锁」，直到外部值追平（吸收 DataStore 旧回声）
                dragging = false
                pendingCommit = local
            },
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 颜色行右侧的色块 + hex（null = 跟随主题）。点击打开取色弹窗。 */
@Composable
private fun ColorSwatch(argb: Int?, onPick: (Int?) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AppRadii.xs)
    Surface(
        color = Color.Transparent,
        onClick = { showSheet = true },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = if (argb == null) stringResource(R.string.message_style_follow_theme)
                else String.format(Locale.US, "#%06X", argb and 0xFFFFFF),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(shape)
                    .background(argb?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            )
        }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            ColorPickerSheet(initial = argb, onPick = onPick)
        }
    }
}

/** 轻量取色器：跟随主题 + RGB 三条滑杆 + 实时 hex 预览（每次改动即时写回）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerSheet(initial: Int?, onPick: (Int?) -> Unit) {
    val base = initial ?: 0xFFFFFFFF.toInt()
    var r by remember { mutableFloatStateOf(((base shr 16) and 0xFF).toFloat()) }
    var g by remember { mutableFloatStateOf(((base shr 8) and 0xFF).toFloat()) }
    var b by remember { mutableFloatStateOf((base and 0xFF).toFloat()) }
    val current = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg)
            .padding(bottom = AppSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(AppRadii.xs))
                        .background(Color(current))
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppRadii.xs)),
                )
                Text(
                    text = String.format(Locale.US, "#%06X", current and 0xFFFFFF),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(onClick = { onPick(null) }) {
                Text(stringResource(R.string.message_style_follow_theme))
            }
        }
        ChannelSlider("R", r) { r = it; onPick((0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()) }
        ChannelSlider("G", g) { g = it; onPick((0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()) }
        ChannelSlider("B", b) { b = it; onPick((0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()) }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
