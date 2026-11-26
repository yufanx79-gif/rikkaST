package me.rerere.rikkahub.data.st.preview

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上游消息结构预览与自由拖拽调序面板回归守卫。
 */
class PromptPreviewFeatureRegressionGuardTest {

    private fun src(rel: String): String {
        val f = listOf(
            File(rel),
            File("app/$rel"),
            File("../$rel"),
            File("../app/$rel"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 $rel")
        return f.readText()
    }

    @Test
    fun `ChatInput 必须在思考强度旁挂载入口按钮且不受推理模型限制`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt")
        assertTrue(s.contains("onOpenPromptPreview: (() -> Unit)? = null"))
        assertTrue(s.contains("PromptPreviewButton("))
    }

    @Test
    fun `弹窗必须采用 Dialog 形态、仅右侧三横杠拖拽且含底部取消与保存按钮`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/components/ai/PromptStructurePreviewDialog.kt")
        assertTrue(s.contains("Dialog(") && s.contains("fillMaxHeight(0.80f)"))
        assertTrue(s.contains("longPressDraggableHandle(") && s.contains("HugeIcons.Menu03"))
        assertTrue(s.contains("\"取消\"") && s.contains("\"保存\""))
    }

    @Test
    fun `GenerationLoop 必须接入 UnifiedPromptOrderEngine 实现 12 marker 自由重排`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt")
        assertTrue(s.contains("UnifiedPromptOrderEngine.hasCustomMarkerOrder"))
        assertTrue(s.contains("UnifiedPromptOrderEngine.assembleSlotsInOrder"))
    }
}
