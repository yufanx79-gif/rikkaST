package me.rerere.rikkahub.ui.pages.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.writeBooleanPreference
import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantImporter
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

/**
 * 新手首启 3 步闭环引导页（[OnboardingPage]）：
 * 1. 配置 AI 服务商与默认聊天模型（直达 2 步极简模型向导 `Screen.ModelWizard`）；
 * 2. 一键导入酒馆角色卡（支持 `.png` / `.json` / `.charx` / `.byaf` 自动识别并激活）；
 * 3. 立即进入首个会话开聊。
 */
@Composable
fun OnboardingPage(
    assistantVM: AssistantVM = koinViewModel(),
) {
    val settings by assistantVM.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val readiness = evaluateOnboardingReadiness(settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快速上手向导（3 步开聊）") },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(
                        onClick = {
                            context.writeBooleanPreference("onboarding_dismissed", true)
                            navController.popBackStack()
                        }
                    ) {
                        Text("跳过")
                    }
                }
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
            Text(
                text = "欢迎使用 rikkaST 酒馆增强版！只需完成以下两步配置，即可体验完整角色卡、世界书、MVU 状态栏与小手机交互：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Step 1: 模型配置
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (readiness.hasSelectedChatModel) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "第一步：配置并选择聊天模型",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatusBadge(done = readiness.hasSelectedChatModel)
                    }
                    Text(
                        text = if (readiness.hasSelectedChatModel) {
                            "已就绪：当前已选中可用聊天模型。"
                        } else {
                            "支持 DeepSeek、OpenRouter、硅基流动、Gemini 及任意 OpenAI 兼容接口。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { navController.navigate(Screen.ModelWizard) },
                        ) {
                            Text(if (readiness.hasSelectedChatModel) "重新配置模型" else "2 步极速配置模型")
                        }
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.SettingProvider) },
                        ) {
                            Text("高级服务商设置")
                        }
                    }
                }
            }

            // Step 2: 导入角色卡
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (readiness.hasImportedCard) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "第二步：导入酒馆角色卡",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StatusBadge(done = readiness.hasImportedCard)
                    }
                    Text(
                        text = "支持 SillyTavern V2/V3 角色卡（.png / .json / .charx / .byaf），自动提取内嵌世界书、正则脚本与头像。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AssistantImporter(
                        onImport = { result ->
                            assistantVM.importAndActivateAssistant(result, activateAsCurrent = true)
                            if (readiness.hasSelectedChatModel) {
                                context.writeBooleanPreference("onboarding_dismissed", true)
                                navigateToChatPage(navController, chatId = Uuid.random())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Step 3: 开始对话
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    context.writeBooleanPreference("onboarding_dismissed", true)
                    navigateToChatPage(navController, chatId = Uuid.random())
                },
                enabled = readiness.hasSelectedChatModel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (readiness.hasSelectedChatModel) "立即开始对话" else "请先完成第一步（配置聊天模型）")
            }
        }
    }
}

@Composable
private fun StatusBadge(done: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (done) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Text(
            text = if (done) "已完成" else "待配置",
            style = MaterialTheme.typography.labelSmall,
            color = if (done) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
