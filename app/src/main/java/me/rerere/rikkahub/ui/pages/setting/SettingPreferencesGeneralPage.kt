package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BlurStrength
import me.rerere.rikkahub.data.datastore.BlurStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.MessageBubbleStyle
import me.rerere.rikkahub.data.datastore.MessageStyleSetting
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.LevelPickerRow
import me.rerere.rikkahub.ui.components.ui.PageScaffold
import me.rerere.rikkahub.ui.components.ui.SegmentedTabs
import me.rerere.rikkahub.ui.pages.setting.components.DeveloperModeSwitch
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesGeneralPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val messageStyle = settings.messageStyle

    fun updateDisplaySetting(setting: DisplaySetting) {
        // [v227 D4] 本地镜像立刻更新（按下即有反馈，不等写回），写回走**窄接口**：
        // 只改 DISPLAY_SETTING 一个 key，且变换作用在 settingsFlow 的当前值上。
        // 旧实现 `vm.updateSettings(settings.copy(displaySetting = setting))` 有两个坑：
        //   ① 重写 ~80 个 key（每次点击一大坨写盘 + 一次全量事件）；
        //   ② 用组合期捕获的 settings 快照覆盖并发改动 -> 连点两个开关时前一个会被回滚。
        displaySetting = setting
        vm.patchDisplaySetting { setting }
    }

    // [v222 R1] 「启用模糊效果」的真源 = messageStyle.style（ON <-> style != DEFAULT）。
    // 写入时同步 v221 旧开关 enableBlurEffect，保证回滚/旧版本仍读得到；旧值迁移见 notes/SPEC。
    fun updateMessageStyle(new: MessageStyleSetting) {
        vm.updateSettings(
            settings.copy(
                messageStyle = new,
                displaySetting = settings.displaySetting.copy(
                    enableBlurEffect = new.style != MessageBubbleStyle.DEFAULT,
                ),
            )
        )
    }


    PageScaffold(
        title = stringResource(R.string.setting_page_preferences_general),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                    "create_new_conversation_on_start",
                    true
                )
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    // [v229 P0-4] 开发者模式总开关：设置一级入口、一点即开。写回的就是运行时读的
                    // 同一个设置项（PreferencesStore.DEVELOPER_MODE = "developer_mode"），并同步
                    // 运行时日志门控（TavernRuntimeManager.setDeveloperMode），开完立刻能写 [event] 日志。
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_developer_mode_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_developer_mode_desc)) },
                        trailingContent = {
                            DeveloperModeSwitch(
                                checked = settings.developerMode,
                                onCheckedChange = { enabled ->
                                    TavernRuntimeManager.setDeveloperMode(enabled)
                                    vm.patchSettings { current -> current.copy(developerMode = enabled) }
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc)) },
                        trailingContent = {
                            Switch(
                                checked = createNewConversationOnStart,
                                onCheckedChange = { createNewConversationOnStart = it }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.sendOnEnter,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(sendOnEnter = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.showMessageJumper,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showMessageJumper = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.showMessageJumper) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_title)) },
                            supportingContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = displaySetting.messageJumperOnLeft,
                                    onCheckedChange = {
                                        updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it))
                                    }
                                )
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableAutoScroll,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableAutoScroll = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("DeepSeek缓存优化") },
                        supportingContent = { Text("动态上下文（日期/记忆）移至请求末尾，最大化前缀缓存命中率（省钱）；关闭则恢复旧行为") },
                        trailingContent = {
                            Switch(
                                checked = settings.deepseekCacheOptimization,
                                onCheckedChange = {
                                    vm.patchSettings { s -> s.copy(deepseekCacheOptimization = it) }
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("EJS模板渲染") },
                        supportingContent = { Text("发送前渲染提示词/世界书/角色卡中的 <% %> EJS 模板（ST-Prompt-Template 语义，可用 getvar/setvar 等）；仅在文本含模板时生效") },
                        trailingContent = {
                            Switch(
                                checked = settings.tavernEjsRendering,
                                onCheckedChange = {
                                    vm.patchSettings { s -> s.copy(tavernEjsRendering = it) }
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_title)) },
                        supportingContent = {
                            Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.useAppIconStyleLoadingIndicator,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(useAppIconStyleLoadingIndicator = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_desc)) },
                        trailingContent = {
                            // [v225 P2] 这个开关就是「输入栏模糊」的总闸（见 strings：
                            // setting_display_page_enable_blur_effect_desc = 在聊天输入栏启用模糊效果）。
                            // 显示真源 = displaySetting.enableBlurEffect（ChatInput.kt 读同一个值），
                            // 两个写入入口（本页 / 消息样式页）都会把 messageStyle.style 与它同步。
                            Switch(
                                checked = settings.displaySetting.enableBlurEffect,
                                onCheckedChange = { on ->
                                    updateMessageStyle(
                                        messageStyle.copy(
                                            style = if (on) MessageBubbleStyle.FROSTED else MessageBubbleStyle.DEFAULT
                                        )
                                    )
                                }
                            )
                        },
                    )
                    // [v222 R1-(1)] A1：开关下面加「模糊风格」子选项（关闭时隐藏）。
                    if (messageStyle.style != MessageBubbleStyle.DEFAULT) {
                        item(
                            headlineContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.message_style_blur_style_title))
                                    val blurStyleLabels = mapOf(
                                        BlurStyle.TRANSPARENT to blurStyleLabel(BlurStyle.TRANSPARENT),
                                        BlurStyle.FROSTED_GLASS to blurStyleLabel(BlurStyle.FROSTED_GLASS),
                                    )
                                    SegmentedTabs(
                                        items = BlurStyle.entries.toList(),
                                        selected = messageStyle.blurStyle,
                                        onSelect = { updateMessageStyle(messageStyle.copy(blurStyle = it)) },
                                        label = { blurStyleLabels.getValue(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = blurStyleSubtitle(messageStyle.blurStyle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                        // [v222 R1-(2)] A2：「模糊强度」独立模块 —— 复用思考强度模块的形态与手感
                        // （底部弹窗 + 档位 pill + 自绘滑杆 + 弹簧吸附），与「消息样式」页的模糊强度
                        // 是同一份数据（Settings.messageStyle.blurStrength）。
                        item(
                            headlineContent = {
                                LevelPickerRow(
                                    title = stringResource(R.string.message_style_blur_title),
                                    subtitle = stringResource(R.string.message_style_blur_hint),
                                    options = blurLevelOptions(),
                                    selectedIndex = messageStyle.blurStrength,
                                    onSelect = {
                                        updateMessageStyle(
                                            messageStyle.copy(blurStrength = BlurStrength.clampIndex(it))
                                        )
                                    },
                                )
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableMessageGenerationHapticEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.skipCropImage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(skipCropImage = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.pasteLongTextAsFile,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.pasteLongTextAsFile) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_threshold_title)) },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = displaySetting.pasteLongTextThreshold.toFloat(),
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it.toInt()))
                                        },
                                        valueRange = 100f..10000f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${displaySetting.pasteLongTextThreshold}")
                                }
                            },
                        )
                    }
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.enableVolumeKeyScroll,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it))
                                }
                            )
                        },
                    )
                    if (displaySetting.enableVolumeKeyScroll) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_ratio)) },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = displaySetting.volumeKeyScrollRatio,
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                                        },
                                        valueRange = 0.25f..1.0f,
                                        steps = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%")
                                }
                            }
                        )
                    }
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_tts_settings)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.ttsOnlyReadQuoted,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.ttsOnlyReadOutsideBrackets,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it))
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_desc)) },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.autoPlayTTSAfterGeneration,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it))
                                }
                            )
                        },
                    )
                }
            }
        }
    }
}
