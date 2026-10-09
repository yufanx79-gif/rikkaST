package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.DEFAULT_CONTEXT_TEMPLATE
import me.rerere.rikkahub.data.model.InstructNamesBehavior
import me.rerere.rikkahub.data.model.InstructTemplate
import me.rerere.rikkahub.data.model.PersonaInjectionPosition
import me.rerere.rikkahub.data.model.assembleContext
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Switch
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TextArea
import me.rerere.rikkahub.ui.components.ui.PageScaffold
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.insertAtCursor
import kotlinx.coroutines.flow.drop
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 上下文模板可用的 ADF 风格宏（与 Assistant.assembleContext 的替换集对应）。
 */
private val CONTEXT_TEMPLATE_MACROS = listOf(
    "char", "user", "system", "description", "personality",
    "scenario", "mesExamples", "persona", "original",
)

/**
 * 高级格式化页（对齐 SillyTavern Advanced Formatting 的上下文模板部分）。
 *
 * - 上下文模板（故事串）：空/默认 = 官方 Chat-Completion 拆分；自定义 = 模板组装（assembleContext）
 * - 实时预览：按当前助手与用户设置渲染模板结果
 */
@Composable
fun AssistantFormattingPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    PageScaffold(
        title = stringResource(R.string.assistant_page_tab_formatting),
    ) { innerPadding ->
        AssistantFormattingContent(
            innerPadding = innerPadding,
            assistant = assistant,
            settings = settings,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
private fun AssistantFormattingContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit,
) {
    val userName = settings.displaySetting.userNickname.ifBlank { "User" }
    val activePersona = settings.personas
        .find { it.id == settings.activePersonaId }
        ?.takeIf { it.enabled && (it.lockedCharacterIds.isEmpty() || assistant.id in it.lockedCharacterIds) }
    // 与 GenerationHandler 一致：人设只在 IN_PROMPT 位置通过 {{persona}} 嵌入
    val personaDescForPrompt = if (activePersona?.position == PersonaInjectionPosition.IN_PROMPT) {
        activePersona.description
    } else {
        ""
    }
    val isDefaultTemplate = assistant.contextTemplate.isBlank() ||
        assistant.contextTemplate.trim() == DEFAULT_CONTEXT_TEMPLATE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // —— 上下文模板（故事串）——
        Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val templateValue = rememberTextFieldState(
                    initialText = assistant.contextTemplate,
                )
                // 回写动作包在 rememberUpdatedState 中：确保始终基于最新的 assistant 快照构建 copy
                val updateTemplate = rememberUpdatedState<(String) -> Unit> { text ->
                    onUpdate(assistant.copy(contextTemplate = text))
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { templateValue.text }.collect {
                        updateTemplate.value(it.toString())
                    }
                }

                Text(
                    text = stringResource(R.string.assistant_formatting_context_template),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.assistant_formatting_context_template_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextArea(
                    state = templateValue,
                    label = stringResource(R.string.assistant_formatting_context_template),
                    minLines = 6,
                    maxLines = 14,
                )

                Text(
                    text = stringResource(R.string.assistant_formatting_macros),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CONTEXT_TEMPLATE_MACROS.forEach { macro ->
                        Tag(onClick = { templateValue.insertAtCursor("{{$macro}}") }) {
                            Text("{{$macro}}")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            if (isDefaultTemplate) {
                                R.string.assistant_formatting_mode_official
                            } else {
                                R.string.assistant_formatting_mode_custom
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            templateValue.setTextAndPlaceCursorAtEnd(DEFAULT_CONTEXT_TEMPLATE)
                        }
                    ) {
                        Icon(HugeIcons.Refresh03, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.assistant_formatting_reset_default))
                    }
                }
            }
        }

        // —— Instruct 序列（高级格式化 · Instruct 模式）——
        InstructSection(assistant = assistant, onUpdate = onUpdate)

        // —— 预览 ——
        Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.assistant_formatting_preview),
                    style = MaterialTheme.typography.titleSmall,
                )
                val preview = assistant.assembleContext(
                    userName = userName,
                    personaDesc = personaDescForPrompt,
                )
                Text(
                    text = preview.ifBlank { stringResource(R.string.assistant_formatting_empty_preview) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Instruct 序列配置区（高级格式化 · Instruct 模式）。
 * 开关默认关闭；启用后展示全部序列字段（故事串/系统/用户/助手 + 选项）。
 */
@Composable
private fun InstructSection(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
) {
    val instruct = assistant.instructTemplate
    val updateInstruct: (InstructTemplate) -> Unit = { updated ->
        onUpdate(assistant.copy(instructTemplate = updated))
    }

    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.assistant_formatting_instruct_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.assistant_formatting_instruct_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = instruct.enabled,
                    onCheckedChange = { updateInstruct(instruct.copy(enabled = it)) },
                )
            }

            if (instruct.enabled) {
                InstructGroupTitle(stringResource(R.string.assistant_formatting_instruct_story))
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_story_prefix),
                    value = instruct.storyStringPrefix,
                    onValueChange = { updateInstruct(instruct.copy(storyStringPrefix = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_story_suffix),
                    value = instruct.storyStringSuffix,
                    onValueChange = { updateInstruct(instruct.copy(storyStringSuffix = it)) },
                )

                HorizontalDivider()

                InstructGroupTitle(stringResource(R.string.assistant_formatting_instruct_system))
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_system_seq),
                    value = instruct.systemSequence,
                    onValueChange = { updateInstruct(instruct.copy(systemSequence = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_system_suffix),
                    value = instruct.systemSuffix,
                    onValueChange = { updateInstruct(instruct.copy(systemSuffix = it)) },
                )
                InstructToggleRow(
                    label = stringResource(R.string.assistant_formatting_instruct_system_same_as_user),
                    description = stringResource(R.string.assistant_formatting_instruct_system_same_as_user_desc),
                    checked = instruct.systemSameAsUser,
                    onCheckedChange = { updateInstruct(instruct.copy(systemSameAsUser = it)) },
                )

                HorizontalDivider()

                InstructGroupTitle(stringResource(R.string.assistant_formatting_instruct_user))
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_input_seq),
                    value = instruct.inputSequence,
                    onValueChange = { updateInstruct(instruct.copy(inputSequence = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_input_suffix),
                    value = instruct.inputSuffix,
                    onValueChange = { updateInstruct(instruct.copy(inputSuffix = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_first_input),
                    value = instruct.firstInputSequence,
                    onValueChange = { updateInstruct(instruct.copy(firstInputSequence = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_last_input),
                    value = instruct.lastInputSequence,
                    onValueChange = { updateInstruct(instruct.copy(lastInputSequence = it)) },
                )

                HorizontalDivider()

                InstructGroupTitle(stringResource(R.string.assistant_formatting_instruct_assistant))
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_output_seq),
                    value = instruct.outputSequence,
                    onValueChange = { updateInstruct(instruct.copy(outputSequence = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_output_suffix),
                    value = instruct.outputSuffix,
                    onValueChange = { updateInstruct(instruct.copy(outputSuffix = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_first_output),
                    value = instruct.firstOutputSequence,
                    onValueChange = { updateInstruct(instruct.copy(firstOutputSequence = it)) },
                )
                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_last_output),
                    value = instruct.lastOutputSequence,
                    onValueChange = { updateInstruct(instruct.copy(lastOutputSequence = it)) },
                )

                HorizontalDivider()

                InstructToggleRow(
                    label = stringResource(R.string.assistant_formatting_instruct_wrap),
                    description = stringResource(R.string.assistant_formatting_instruct_wrap_desc),
                    checked = instruct.wrap,
                    onCheckedChange = { updateInstruct(instruct.copy(wrap = it)) },
                )
                InstructToggleRow(
                    label = stringResource(R.string.assistant_formatting_instruct_macro),
                    description = stringResource(R.string.assistant_formatting_instruct_macro_desc),
                    checked = instruct.macro,
                    onCheckedChange = { updateInstruct(instruct.copy(macro = it)) },
                )
                InstructToggleRow(
                    label = stringResource(R.string.assistant_formatting_instruct_skip_examples),
                    description = stringResource(R.string.assistant_formatting_instruct_skip_examples_desc),
                    checked = instruct.skipExamples,
                    onCheckedChange = { updateInstruct(instruct.copy(skipExamples = it)) },
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.assistant_formatting_instruct_names),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Select(
                        options = InstructNamesBehavior.entries.toList(),
                        selectedOption = instruct.namesBehavior,
                        onOptionSelected = { updateInstruct(instruct.copy(namesBehavior = it)) },
                        optionToString = { behavior ->
                            stringResource(
                                when (behavior) {
                                    InstructNamesBehavior.NONE -> R.string.assistant_formatting_instruct_names_none
                                    InstructNamesBehavior.FORCE -> R.string.assistant_formatting_instruct_names_force
                                    InstructNamesBehavior.ALWAYS -> R.string.assistant_formatting_instruct_names_always
                                }
                            )
                        },
                    )
                }

                InstructTextField(
                    label = stringResource(R.string.assistant_formatting_instruct_activation_regex),
                    value = instruct.activationRegex,
                    onValueChange = { updateInstruct(instruct.copy(activationRegex = it)) },
                )
                Text(
                    text = stringResource(R.string.assistant_formatting_instruct_activation_regex_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstructGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun InstructToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * 带回写的单行序列输入框（模式对齐 AssistantPromptPage 的 TextFieldState 回写）。
 * - drop(1)：跳过初始值，避免首次组合时触发无效回写
 * - rememberUpdatedState：保证回写动作始终使用最新的 assistant 快照
 */
@Composable
private fun InstructTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val state = rememberTextFieldState(initialText = value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    LaunchedEffect(Unit) {
        snapshotFlow { state.text }
            .drop(1)
            .collect { currentOnValueChange.value(it.toString()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.MultiLine(
                minHeightInLines = 1,
                maxHeightInLines = 3,
            ),
        )
    }
}