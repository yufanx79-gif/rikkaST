package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.PageScaffold
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.plus

@Composable
fun SettingPreferencesPage() {
    val navController = LocalNavController.current

    PageScaffold(
        title = stringResource(R.string.setting_page_preferences),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                        leadingContent = { Icon(HugeIcons.Sun01, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_theme)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_theme_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                        leadingContent = { Icon(HugeIcons.Notification01, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                        leadingContent = { Icon(HugeIcons.Settings03, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_general)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_general_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                        leadingContent = { Icon(HugeIcons.PaintBoard, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_desc)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesMessageStyle) },
                        leadingContent = { Icon(HugeIcons.BubbleChat, null) },
                        headlineContent = { Text(stringResource(R.string.message_style_page_title)) },
                        supportingContent = { Text(stringResource(R.string.message_style_style_frosted_subtitle)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPreferencesExpression) },
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        headlineContent = { Text(stringResource(R.string.setting_preferences_expression)) },
                        supportingContent = { Text(stringResource(R.string.setting_preferences_expression_desc)) },
                    )
                }
            }
        }
    }
}
