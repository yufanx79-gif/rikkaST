package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberCurrentColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun RikkahubTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    // [v222 R2-P0] 第二根因：`ColorScheme` **没有 equals/hashCode**（javap 实证），
    // 而 `dynamicLightColorScheme(context)` 每次都返回新实例 → `colorSchemeConverted` 新实例
    // → `MaterialTheme`(staticCompositionLocalOf) 判定失效 → **整个 app 重组**（含开关动画期间）。
    // 修法：按影响颜色身份的 key 做 remember，让 ColorScheme 在设置未变时保持同一实例。
    val schemeContext = LocalContext.current
    val colorScheme = remember(
        settings.dynamicColor,
        settings.themeId,
        settings.customThemes,
        darkTheme,
        schemeContext,
    ) {
        when {
            settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(schemeContext) else dynamicLightColorScheme(schemeContext)
            }
            else -> {
                val theme = findThemeById(settings.themeId, settings.customThemes)
                    ?: findPresetTheme(settings.themeId)
                theme.getColorScheme(dark = darkTheme)
            }
        }
    }
    val colorSchemeWithAmoled = remember(darkTheme, amoledDarkMode, colorScheme) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND,
            )
        } else {
            colorScheme
        }
    }

    // ---- v217 / A2：Surface Ladder -------------------------------------------------
    // 只对「动态色」与「自定义主题」两条路径启用；7 套预设保持现状（回滚 = 切回任一预设）。
    // 启用后：页面 = 卡片下沉 4 个 tone，card / fill / hairline / surfaceContainer 五档全部从
    // 页面色推导 —— 换任何种子色容器层级都会跟着走，不会再出现某档 surface 落到 M3 默认灰。
    val usesDynamicColor = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isCustomThemeSelected = remember(settings.themeId, settings.customThemes) {
        settings.customThemes.any { it.id == settings.themeId } &&
            PresetThemes.none { it.id == settings.themeId }
    }
    val surfaceLadderEnabled = usesDynamicColor || isCustomThemeSelected

    val surfaceLadder = remember(surfaceLadderEnabled, darkTheme, colorSchemeWithAmoled) {
        if (!surfaceLadderEnabled) {
            null
        } else {
            surfaceLadderFromScheme(
                // 下沉只在这里做一次：暗色 / AMOLED 原样返回，不会双重偏移。
                surface = applyPageSurface(
                    surface = colorSchemeWithAmoled.surface,
                    isDark = darkTheme,
                ),
                onSurface = colorSchemeWithAmoled.onSurface,
                outlineVariant = colorSchemeWithAmoled.outlineVariant,
                isDark = darkTheme,
            )
        }
    }

    val colorSchemeConverted = remember(colorSchemeWithAmoled, surfaceLadder) {
        if (surfaceLadder == null) {
            colorSchemeWithAmoled
        } else {
            colorSchemeWithAmoled.copy(
                background = surfaceLadder.page,
                surface = surfaceLadder.page,
                surfaceContainerLowest = surfaceLadder.surfaceContainerLowest,
                surfaceContainerLow = surfaceLadder.surfaceContainerLow,
                surfaceContainer = surfaceLadder.surfaceContainer,
                surfaceContainerHigh = surfaceLadder.surfaceContainerHigh,
                surfaceContainerHighest = surfaceLadder.surfaceContainerHighest,
            )
        }
    }

    val appSurfaceColors = surfaceLadder?.let {
        AppSurfaceColors(
            surfaceCard = it.card,
            surfaceCardFill = it.surfaceCardFill,
            surfaceFill = it.surfaceFill,
            hairline = it.hairline,
            hairlineStrong = it.hairlineStrong,
        )
    } ?: AppSurfaceColors.fromScheme(colorSchemeConverted)

    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    // 更新状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalAppSurfaceColors provides appSurfaceColors,
        LocalOverscrollFactory provides null
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            shapes = AppShapes,
            typography = Typography,
            content = content,
            // [v222 R2 收尾] 全局默认改用 standard：
            // M3 1.5.0-alpha26 的 Switch 走 LocalMotionScheme.fastSpatialSpec ——
            //   expressive = spring(dampingRatio 0.6, stiffness 800) → 过冲 9.5%、1% 稳定 ≈271ms
            //   standard   = spring(dampingRatio 0.9, stiffness 1400) → 1% 稳定 ≈137ms
            // 主人反馈「开关 thumb 走左→中→右」= 掉帧 + 动画窗口被拉长，这里把窗口砍掉一半。
            // ⚠️ 导航转场的 expressive 观感已在 RouteActivity 里显式固定，不受本改动影响。
            motionScheme = MotionScheme.standard()
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
