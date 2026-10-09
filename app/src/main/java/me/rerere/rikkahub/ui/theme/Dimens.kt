package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * rikkaST 设计 token 层（v217 / A1）
 *
 * 为什么要有这一层
 *   v216 之前 ui/ 下 0 个顶层尺寸常量：.dp 字面量 2062 处 / 138 文件、RoundedCornerShape( 152 处 /
 *   54 文件、fontSize = N.sp 27 处。同一件事（卡片间距 / 圆角 / 最小字号）在几十个文件里各写一遍，
 *   想整体调一次就得改几十处，而且必然漏改。
 *
 * 使用约定（重要）
 *   1. 新增或修改 UI 一律优先取这里的 token，不要再写新的字面量。
 *   2. 本层只放「跨页面通用」的值；单页私有的尺寸（某张插画的高度之类）留在页面里。
 *   3. 颜色不走这里 —— 颜色一律走 MaterialTheme.colorScheme / CustomColors / extendColors（见 Color.kt）。
 *   4. 动效优先走 MaterialTheme.motionScheme；AppMotion 只用于 MotionScheme 覆盖不到的场景。
 *   5. 字号优先走 MaterialTheme.typography；AppType 只用于 M3 字阶里没有的档位。
 *
 * 兼容性
 *   AppSpacing / AppRadii / AppShapes 的取值刻意与 M3 的 4dp 基准网格、ShapeTokens 默认值对齐，
 *   因此「把字面量换成 token」是零视觉变化的纯重构。AppShapes 挂进 MaterialExpressiveTheme 后，
 *   MaterialTheme.shapes 的来源就从「库默认值」变成「我们自己的 token」，取值不变。
 */

/** 间距：4dp 基准网格。对应 Arrangement.spacedBy / padding / Spacer 等。 */
object AppSpacing {
    /** 0dp —— 显式表达「无间距」，比写 0.dp 更可读。 */
    val none = 0.dp

    /** 2dp —— 极紧场景（分段控件段间缝隙）。 */
    val xxs = 2.dp

    /** 4dp —— 最小常规间距。 */
    val xs = 4.dp

    /** 8dp —— 最常用的紧凑间距。 */
    val sm = 8.dp

    /** 12dp —— 卡片内元素间距。 */
    val md = 12.dp

    /** 16dp —— 页面左右安全边距（最常用）。 */
    val lg = 16.dp

    /** 20dp —— 大块之间的间距。 */
    val xl = 20.dp

    /** 24dp —— 区块分隔。 */
    val xxl = 24.dp

    /** 32dp —— 大段落分隔。 */
    val xxxl = 32.dp
}

/** 圆角档位。与 material3 ShapeTokens 的经典五档对齐，另加项目高频的 20 / 32。 */
object AppRadii {
    /** 4dp —— 小徽标 / 内联标签。 */
    val xs = 4.dp

    /** 8dp —— 小控件。 */
    val sm = 8.dp

    /** 12dp —— 卡片（M3 medium）。 */
    val md = 12.dp

    /** 16dp —— 大卡片 / 气泡（M3 large）。 */
    val lg = 16.dp

    /** 20dp —— CardGroup 外壳。 */
    val xl = 20.dp

    /** 28dp —— M3 extraLarge。 */
    val xxl = 28.dp

    /** 32dp —— 输入区外壳等大圆角。 */
    val xxxl = 32.dp
}

/** 描边宽度。 */
object AppStroke {
    /** 0.6dp —— hairline；比 1dp 更「纸感」，用于卡片描边。 */
    val hairline = 0.6.dp

    /** 1dp —— 常规边框 / 分割线。 */
    val thin = 1.dp
}

/** 固定尺寸槽位（控件 / 图标）。 */
object AppSizes {
    /** 48dp —— Material 最小可点区域。 */
    val minTouchTarget = 48.dp

    /** 36dp —— 设置行图标槽（20dp 图标 + 四周留白）。 */
    val iconSlot = 36.dp

    /** 20dp —— 设置行 / 列表行图标。 */
    val icon = 20.dp

    /** 16dp —— 行尾 chevron。 */
    val chevron = 16.dp
}

/**
 * 动效时长（毫秒）。
 * 能用 MaterialTheme.motionScheme 的地方一律用它；这里的值只给 MotionScheme 覆盖不到的补间用
 * （按压着色、分段控件指示器位移等）。
 */
object AppMotion {
    /** 180ms —— 快速反馈（颜色 / 透明度）。 */
    const val fast = 180

    /** 200ms —— 按压着色补间、分段控件指示器位移。 */
    const val press = 200

    /** 240ms —— 常规尺寸 / 位移过渡。 */
    const val standard = 240

    /** 320ms —— 大范围 / 整页过渡。 */
    const val slow = 320
}

/** 字号：只放 M3 字阶里没有的档位。 */
object AppType {
    /**
     * 11sp —— 本项目允许的最小字号（等于 M3 labelSmall）。
     *
     * v217 起禁止 8sp / 9sp / 10sp 字面量：这些字号在 1080p 手机上已经不可读，
     * 而且不跟随系统字体缩放，等于对开了大字号的用户完全失效。
     * （例外：UIAvatar 的 minFontSize 是自动缩放的下界，不是固定字号的文案；
     *   Markdown 引用角标是画在固定圆形里的装饰性徽标，都保留原值。）
     */
    val micro = 11.sp

    /** 13sp —— micro 的配套行高（保持 1.18 的行高比）。 */
    val microLineHeight = 13.sp

    /**
     * 13sp —— 设置行明细 / 分组标题 / 分段控件文案。
     *
     * M3 字阶里没有正好这一档（labelMedium = 12sp、labelLarge = 14sp），
     * 而 Kelivo 的设置行明细与分组标题就是 13sp（spec-kelivo-port-20261002 第 5.1 / 5.2 节），
     * 所以单独给一个 token，避免在几十个设置页里各写一遍 13.sp。
     */
    val label = 13.sp

    /**
     * 15sp —— 设置行主文案。
     *
     * 同样不在 M3 字阶里（bodyMedium = 14sp、bodyLarge = 16sp）；
     * Kelivo 设置行的主文案是 15sp（spec-kelivo-port-20261002 第 5.2 节）。
     */
    val body = 15.sp
}

/** 全圆角（胶囊）：搜索框 / 标签用。 */
val AppPillShape: CornerBasedShape = RoundedCornerShape(percent = 50)

private val LibraryShapes: Shapes = Shapes()

/**
 * 全局 Shape token。
 *
 * 只覆盖 M3 的经典五档；expressive 新增的三档（largeIncreased / extraLargeIncreased /
 * extraExtraLarge）保持 material3 库默认值，避免跟着上游 alpha 版本漂移。
 *
 * 经典五档取值等于 ShapeTokens 默认值（4 / 8 / 12 / 16 / 28），
 * 所以挂到 MaterialExpressiveTheme(shapes = AppShapes) 上不产生视觉回归 —— 只是把
 * 「形状从哪来」这件事收敛到一处。
 */
val AppShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadii.xs),
    small = RoundedCornerShape(AppRadii.sm),
    medium = RoundedCornerShape(AppRadii.md),
    large = RoundedCornerShape(AppRadii.lg),
    extraLarge = RoundedCornerShape(AppRadii.xxl),
    largeIncreased = LibraryShapes.largeIncreased,
    extraLargeIncreased = LibraryShapes.extraLargeIncreased,
    extraExtraLarge = LibraryShapes.extraExtraLarge,
)
