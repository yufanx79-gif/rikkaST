package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.PageScaffold
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.ModeInjectionsContent
import me.rerere.rikkahub.ui.components.ai.QuickMessagesContent
import me.rerere.rikkahub.ui.components.ai.SkillsContent
import me.rerere.rikkahub.ui.components.ai.EmbeddedRegexSection
import me.rerere.rikkahub.ui.components.ai.TavernPresetsSection
import me.rerere.rikkahub.ui.components.ai.TavernScriptsSection
import me.rerere.rikkahub.ui.components.ai.ThirdPartyExtensionsSection
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.ui.pages.extensions.RegexScriptEditorDialog
import me.rerere.rikkahub.ui.pages.extensions.scriptSummary
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.st.runtime.TavernScriptBrief
import me.rerere.rikkahub.data.st.runtime.appendGlobalScripts
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups
import me.rerere.rikkahub.data.st.runtime.extractScriptsFromJson
import me.rerere.rikkahub.data.st.runtime.listCardScripts
import me.rerere.rikkahub.data.st.runtime.listGlobalScripts
import me.rerere.rikkahub.data.st.runtime.listPresetScriptBuckets
import me.rerere.rikkahub.data.st.runtime.removeGlobalScript
import me.rerere.rikkahub.data.st.runtime.setCardScriptEnabled
import me.rerere.rikkahub.data.st.runtime.setGlobalScriptEnabled
import me.rerere.rikkahub.data.st.runtime.setPresetScriptEnabled
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.data.model.PromptInjection

@Composable
fun AssistantExtensionsPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 8 }

    PageScaffold(
        title = stringResource(R.string.assistant_extensions_page_title),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_quick_messages)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_mode_injections)) }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_lorebooks)) }
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    text = { Text(stringResource(R.string.assistant_extensions_page_tab_skills)) }
                )
                Tab(
                    selected = pagerState.currentPage == 4,
                    onClick = { scope.launch { pagerState.animateScrollToPage(4) } },
                    text = { Text(stringResource(R.string.tavern_ext_tab_regex)) }
                )
                Tab(
                    selected = pagerState.currentPage == 5,
                    onClick = { scope.launch { pagerState.animateScrollToPage(5) } },
                    text = { Text(stringResource(R.string.tavern_ext_tab_presets)) }
                )
                Tab(
                    selected = pagerState.currentPage == 6,
                    onClick = { scope.launch { pagerState.animateScrollToPage(6) } },
                    text = { Text(stringResource(R.string.tavern_ext_tab_scripts)) }
                )
                // [v209] §5.C：第 8 个「第三方」tab —— 复用扩展中心的 ThirdPartyExtensionsSection。
                // 交接文档 §5.C 遗留项：第三方扩展此前只有「扩展中心」一个入口，助手扩展页看不到。
                Tab(
                    selected = pagerState.currentPage == 7,
                    onClick = { scope.launch { pagerState.animateScrollToPage(7) } },
                    text = { Text(stringResource(R.string.tavern_ext_tab_third_party)) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        if (settings.quickMessages.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_quick_messages),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_extensions),
                                onAction = { navController.navigate(Screen.QuickMessages) },
                            )
                        } else {
                            Column {
                                QuickMessagesContent(
                                    modifier = Modifier.weight(1f),
                                    quickMessages = settings.quickMessages,
                                    selectedIds = assistant.quickMessageIds,
                                    onToggle = { quickMessageId, checked ->
                                        val newIds = if (checked) assistant.quickMessageIds + quickMessageId
                                        else assistant.quickMessageIds - quickMessageId
                                        vm.update(assistant.copy(quickMessageIds = newIds))
                                    },
                                )
                                TextButton(
                                    onClick = { navController.navigate(Screen.QuickMessages) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_extensions))
                                }
                            }
                        }
                    }

                    1 -> {
                        if (settings.modeInjections.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_mode_injections),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            Column {
                                ModeInjectionsContent(
                                    modifier = Modifier.weight(1f),
                                    modeInjections = settings.modeInjections,
                                    selectedIds = assistant.modeInjectionIds,
                                    onToggle = { injId, checked ->
                                        val newIds = if (checked) assistant.modeInjectionIds + injId
                                        else assistant.modeInjectionIds - injId
                                        vm.update(assistant.copy(modeInjectionIds = newIds))
                                    },
                                )
                                TextButton(
                                    onClick = { navController.navigate(Screen.Prompts) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_prompts))
                                }
                            }
                        }
                    }

                    2 -> {
                        if (settings.lorebooks.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_lorebooks),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            Column {
                                LorebooksContent(
                                    modifier = Modifier.weight(1f),
                                    lorebooks = settings.lorebooks,
                                    selectedIds = assistant.lorebookIds,
                                    onToggle = { injId, checked ->
                                        val newIds = if (checked) assistant.lorebookIds + injId
                                        else assistant.lorebookIds - injId
                                        vm.update(assistant.copy(lorebookIds = newIds))
                                    },
                                )
                                TextButton(
                                    onClick = { navController.navigate(Screen.Prompts) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_prompts))
                                }
                            }
                        }
                    }

                    3 -> {
                        if (skills.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_skills),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_extensions),
                                onAction = { navController.navigate(Screen.Skills) },
                            )
                        } else {
                            Column {
                                SkillsContent(
                                    modifier = Modifier.weight(1f),
                                    skills = skills,
                                    enabledSkills = assistant.enabledSkills,
                                    onToggle = { name, checked ->
                                        val newSkills = if (checked) assistant.enabledSkills + name
                                        else assistant.enabledSkills - name
                                        vm.update(assistant.copy(enabledSkills = newSkills))
                                    },
                                )
                                TextButton(
                                    onClick = { navController.navigate(Screen.Skills) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_extensions))
                                }
                            }
                        }
                    }
                    4 -> {
                        EmbeddedRegexSection(
                            assistant = assistant,
                            onUpdateAssistant = { vm.update(it) },
                            onNavigateToRegexScripts = { navController.navigate(Screen.RegexScripts) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    5 -> {
                        TavernPresetsSection(
                            modeInjections = settings.modeInjections,
                            selectedIds = assistant.modeInjectionIds,
                            onChangeSelectedIds = { newIds -> vm.update(assistant.copy(modeInjectionIds = newIds)) },
                            onNavigateToPrompts = { navController.navigate(Screen.Prompts) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    6 -> {
                        TavernScriptsSection(
                            assistant = assistant,
                            settings = settings,
                            onUpdateAssistant = { vm.update(it) },
                            onUpdateSettings = { vm.updateSettings(it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // [v209] §5.C：第 8 个「第三方」tab。第三方扩展是全局配置（不绑定角色卡），
                    // 与 扩展中心 → 第三方扩展 共用同一组件；此前助手扩展页只有 7 个 tab 看不到它。
                    7 -> {
                        ThirdPartyExtensionsSection(
                            settings = settings,
                            onUpdateSettings = { vm.updateSettings(it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
