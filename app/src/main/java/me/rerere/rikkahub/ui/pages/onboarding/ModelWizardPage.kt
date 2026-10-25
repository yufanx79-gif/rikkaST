package me.rerere.rikkahub.ui.pages.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private data class QuickProviderPreset(
    val title: String,
    val providerName: String,
    val baseUrl: String,
    val defaultModelId: String,
    val defaultDisplayName: String,
    val isGoogle: Boolean = false,
)

private val QUICK_PROVIDER_PRESETS = listOf(
    QuickProviderPreset(
        title = "DeepSeek 官方",
        providerName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        defaultModelId = "deepseek-chat",
        defaultDisplayName = "DeepSeek V3",
    ),
    QuickProviderPreset(
        title = "OpenRouter",
        providerName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModelId = "deepseek/deepseek-chat",
        defaultDisplayName = "DeepSeek V3 (OpenRouter)",
    ),
    QuickProviderPreset(
        title = "硅基流动 SiliconFlow",
        providerName = "SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1",
        defaultModelId = "deepseek-ai/DeepSeek-V3",
        defaultDisplayName = "DeepSeek V3 (SiliconFlow)",
    ),
    QuickProviderPreset(
        title = "Google Gemini",
        providerName = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModelId = "gemini-2.5-flash",
        defaultDisplayName = "Gemini 2.5 Flash",
        isGoogle = true,
    ),
    QuickProviderPreset(
        title = "自定义 OpenAI 兼容反代",
        providerName = "自定义 OpenAI 接口",
        baseUrl = "https://api.openai.com/v1",
        defaultModelId = "gpt-4o-mini",
        defaultDisplayName = "GPT-4o Mini",
    ),
)

/**
 * 2 步极简模型配置向导（[ModelWizardPage]）：
 * 1. 选择常用服务商模板（DeepSeek / OpenRouter / 硅基流动 / Gemini / 自定义 OpenAI 反代）；
 * 2. 填入 API Key（与可选模型 ID），支持一键测试连接并自动设为当前默认聊天模型。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelWizardPage(
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val providerManager = koinInject<ProviderManager>()
    val scope = rememberCoroutineScope()

    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    val preset = QUICK_PROVIDER_PRESETS[selectedPresetIdx]

    var providerName by remember(selectedPresetIdx) { mutableStateOf(preset.providerName) }
    var baseUrl by remember(selectedPresetIdx) { mutableStateOf(preset.baseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember(selectedPresetIdx) { mutableStateOf(preset.defaultModelId) }
    var modelDisplayName by remember(selectedPresetIdx) { mutableStateOf(preset.defaultDisplayName) }
    var isTesting by remember { mutableStateOf(false) }
    var testStatusText by remember { mutableStateOf<String?>(null) }

    fun buildProviderAndModel(): Pair<ProviderSetting, Model> {
        val model = Model(
            id = Uuid.random(),
            modelId = modelId.trim().ifBlank { preset.defaultModelId },
            displayName = modelDisplayName.trim().ifBlank { modelId.trim().ifBlank { preset.defaultDisplayName } },
            type = ModelType.CHAT,
        )
        val provider = if (preset.isGoogle) {
            ProviderSetting.Google(
                id = Uuid.random(),
                enabled = true,
                name = providerName.trim().ifBlank { preset.providerName },
                models = listOf(model),
                apiKey = apiKey.trim(),
                baseUrl = baseUrl.trim().ifBlank { preset.baseUrl },
            )
        } else {
            ProviderSetting.OpenAI(
                id = Uuid.random(),
                enabled = true,
                name = providerName.trim().ifBlank { preset.providerName },
                models = listOf(model),
                apiKey = apiKey.trim(),
                baseUrl = baseUrl.trim().ifBlank { preset.baseUrl },
            )
        }
        return provider to model
    }

    fun saveAndActivateModel() {
        if (apiKey.isBlank()) {
            toaster.show("请先填写 API Key", type = ToastType.Warning)
            return
        }
        val (newProvider, newModel) = buildProviderAndModel()
        val updatedAssistants = settings.assistants.map { a ->
            if (a.id == settings.assistantId && a.chatModelId == null) {
                a.copy(chatModelId = newModel.id)
            } else {
                a
            }
        }
        vm.updateSettings(
            settings.copy(
                providers = settings.providers + newProvider,
                chatModelId = newModel.id,
                assistants = updatedAssistants,
            )
        )
        toaster.show("已保存并设为默认聊天模型：${newModel.displayName}", type = ToastType.Success)
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("2 步极速配置聊天模型") },
                navigationIcon = { BackButton() },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "第 1 步：选择接口类型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QUICK_PROVIDER_PRESETS.forEachIndexed { idx, item ->
                            FilterChip(
                                selected = selectedPresetIdx == idx,
                                onClick = { selectedPresetIdx = idx },
                                label = { Text(item.title) },
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "第 2 步：填写 API Key 与模型标识",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = providerName,
                        onValueChange = { providerName = it },
                        label = { Text("服务商名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API 地址 (Base URL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key（必填）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("模型 ID（如 deepseek-chat / gpt-4o）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modelDisplayName,
                        onValueChange = { modelDisplayName = it },
                        label = { Text("显示名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!testStatusText.isNullOrBlank()) {
                        Text(
                            text = testStatusText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testStatusText!!.startsWith("✅")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (apiKey.isBlank()) {
                                    toaster.show("请先填写 API Key", type = ToastType.Warning)
                                    return@OutlinedButton
                                }
                                isTesting = true
                                testStatusText = "正在测试连接…"
                                val (testProvider, testModel) = buildProviderAndModel()
                                val handler = providerManager.getProviderByType(testProvider)
                                scope.launch {
                                    runCatching {
                                        handler.generateText(
                                            providerSetting = testProvider,
                                            messages = listOf(UIMessage.user("ping")),
                                            params = TextGenerationParams(model = testModel),
                                        )
                                    }.onSuccess {
                                        isTesting = false
                                        testStatusText = "✅ 连接成功！点击右侧按钮即可保存并开聊。"
                                    }.onFailure { err ->
                                        isTesting = false
                                        testStatusText = "❌ 连接失败：${err.message ?: err.javaClass.simpleName}"
                                    }
                                }
                            },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isTesting) "测试中…" else "测试连接")
                        }

                        Button(
                            onClick = { saveAndActivateModel() },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("保存并启用模型")
                        }
                    }
                }
            }
        }
    }
}
