package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨步骤去重必须感知「世界状态已改变」。
 *
 * 真机复现（2026-11-14，模型全量跑工具那轮）：
 *   memory_list → memory_delete 删掉文件 → 再 memory_list 想验证删除是否生效
 * 第二次 list 参数完全相同，被当成冗余调用拦下，模型只能退而用 memory_read 逐路径探测。
 *
 * 修法：写操作执行后清空去重集合，只读工具仍受保护。
 */
class StatefulToolDedupeTest {

    @Test
    fun `memory mutations are stateful`() {
        assertTrue("memory_write" in STATEFUL_TOOL_NAMES)
        assertTrue("memory_append" in STATEFUL_TOOL_NAMES)
        assertTrue("memory_str_replace" in STATEFUL_TOOL_NAMES)
        assertTrue("memory_delete" in STATEFUL_TOOL_NAMES)
    }

    @Test
    fun `memory reads are not stateful so repeat protection still works`() {
        assertFalse("memory_read" in STATEFUL_TOOL_NAMES)
        assertFalse("memory_list" in STATEFUL_TOOL_NAMES)
    }

    @Test
    fun `file and shell mutations are stateful`() {
        assertTrue("workspace_write_file" in STATEFUL_TOOL_NAMES)
        assertTrue("workspace_edit_file" in STATEFUL_TOOL_NAMES)
        assertTrue("workspace_shell" in STATEFUL_TOOL_NAMES)
        assertTrue("execute_command" in STATEFUL_TOOL_NAMES)
        assertTrue("execute_python" in STATEFUL_TOOL_NAMES)
    }

    @Test
    fun `pure read tools stay protected from repeat calls`() {
        assertFalse("workspace_read_file" in STATEFUL_TOOL_NAMES)
        assertFalse("search_web" in STATEFUL_TOOL_NAMES)
        assertFalse("web_fetch" in STATEFUL_TOOL_NAMES)
        assertFalse("calculator" in STATEFUL_TOOL_NAMES)
        assertFalse("get_time_info" in STATEFUL_TOOL_NAMES)
    }

    @Test
    fun `task creation is stateful so listing again reflects it`() {
        assertTrue("task_create" in STATEFUL_TOOL_NAMES)
        assertTrue("task_mgmt" in STATEFUL_TOOL_NAMES)
        assertFalse("task_list" in STATEFUL_TOOL_NAMES)
        assertFalse("task_get" in STATEFUL_TOOL_NAMES)
    }
}
