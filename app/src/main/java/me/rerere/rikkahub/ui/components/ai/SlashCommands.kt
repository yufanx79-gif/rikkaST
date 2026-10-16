package me.rerere.rikkahub.ui.components.ai

import android.content.Context

import me.rerere.rikkahub.data.files.CommandFile
import me.rerere.rikkahub.R

/**
 * 斜杠命令 — 来自 skill 的 commands 目录下的 .md 文件
 * Claude Code 风格：每个 commands/xxx.md 自动变成 /xxx 命令
 */
data class SlashCommand(
    val name: String,                    // 命令名，如 "code-review"
    val description: String,             // 简短描述
    val allowedTools: List<String> = emptyList(), // 允许的工具（内置命令默认空）
    // 参数提示，如 "[文本]" / "[key]"。约定：空 = 点击建议时直接执行；
    // 非空 = 点击建议时填入输入框，等用户补参数后按发送执行（需要参数的命令必须填，漏填会被当无参数直接执行）
    val argumentHint: String = "",
    val disableModelInvocation: Boolean = false, // 纯脚本不调模型
    val content: String,                 // 指令正文
    val filePath: String,                // 源文件路径，如 "commands/code-review.md"
    val builtinKind: BuiltinSlashKind? = null, // 内置命令类型（非空时为官方风格内置命令）
    // 帮助弹窗中逐参数的中文说明与例句（内置命令使用）
    val params: List<SlashParam> = emptyList(),
    // 帮助弹窗中的完整命令例句（含效果说明）
    val examples: List<SlashExample> = emptyList(),
)

/** 单个命名参数的说明与例句（全部走本地化资源） */
data class SlashParam(
    val nameRes: Int,        // 参数名，如 "name="
    val descRes: Int,        // 说明文字
    val exampleRes: Int,     // 例句（含参数用法）
)

/** 完整命令例句与效果说明（全部走本地化资源） */
data class SlashExample(
    val commandRes: Int,  // 完整命令，如 "/sys name=旁白 雨夜"
    val descRes: Int,     // 效果说明
)

/**
 * 内置斜杠命令类型（对齐酒馆官方常用命令，全局可用）
 */
enum class BuiltinSlashKind {
    /** 帮助：列出内置命令 */
    HELP,

    /** 系统消息：插入 SYSTEM 角色消息，不触发生成 */
    SYS,

    /** 以助手身份发言（官方 sendas），不触发生成 */
    SENDAS,

    /** 以用户身份发言（官方 send），不触发生成 */
    SEND,

    /** 切换用户人设 */
    PERSONA,

    /** 触发生成：不添加新消息，直接让 AI 回复一次 */
    TRIGGER,

    /** 生成系统旁白：按提示词让 AI 写一条系统消息并插入 */
    SYSGEN,

    /** 变量命令：设置/读取/增删宏变量（官方变量家族） */
    VAR,

    /** 角色操作：修改当前助手 */
    RENAME,

    /** 续写上一条助手回复（官方 /continue） */
    CONTINUE,

    /** 以角色身份生成一条回复（官方 /impersonate） */
    IMPERSONATE,

    /** 安静生成：用提示词让 AI 生成文本，结果填入输入框（官方 /gen） */
    GEN,

    /** 重新掷 {{pick}} 稳定随机（官方 /reroll-pick） */
    REROLL_PICK,

    /** 修改当前角色卡字段（字段=值） */
    UPDATE_CHAR,

    /** 复制当前角色卡（需要页面回调） */
    DUPLICATE,

    /** 在酒馆运行时（WebView）中执行 JavaScript（/js） */
    JS,
    /** 酒馆运行时控制：重载 MVU / 查看状态（/tavern） */
    TAVERN,

    /** 隐藏消息：从提示词中排除（官方 /hide） */
    HIDE,

    /** 取消隐藏消息（官方 /unhide） */
    UNHIDE,

    /** 切换最后一条消息的 swipe 变体 / 生成新变体（官方 /swipe） */
    SWIPE,

    /** 创建检查点：复制聊天快照（官方 /checkpoint-create） */
    CHECKPOINT_CREATE,

    /** 打开消息上挂的检查点（官方 /checkpoint-go） */
    CHECKPOINT_GO,

    /** 从检查点返回父会话（官方 /checkpoint-exit） */
    CHECKPOINT_EXIT,

    /** 显示检查点父会话名字（官方 /checkpoint-parent） */
    CHECKPOINT_PARENT,

    /** 读取消息上的检查点链接（官方 /checkpoint-get） */
    CHECKPOINT_GET,

    /** 列出检查点链接（官方 /checkpoint-list） */
    CHECKPOINT_LIST,

    /** 在消息处创建分支并打开（官方 /branch-create） */
    BRANCH_CREATE,

    /** 通用 STscript 命令（交由 StSlashExecutor 执行） */
    STSCRIPT,
}

/**
 * 内置命令 — 官方 SillyTavern 常用命令的实用子集，全局可用
 */
fun builtinSlashCommands(context: Context): List<SlashCommand> = listOf(
    SlashCommand(
        name = "help",
        description = context.getString(R.string.slash_help_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.HELP,
    ),
    SlashCommand(
        name = "sys",
        description = context.getString(R.string.slash_sys_desc),
        argumentHint = context.getString(R.string.slash_sys_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.SYS,
        params = listOf(
            SlashParam(R.string.slash_param_sys_name_name, R.string.slash_param_sys_name_desc, R.string.slash_param_sys_name_example),
            SlashParam(R.string.slash_param_sys_at_name, R.string.slash_param_sys_at_desc, R.string.slash_param_sys_at_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_sys_1_cmd, R.string.slash_example_sys_1_desc),
            SlashExample(R.string.slash_example_sys_2_cmd, R.string.slash_example_sys_2_desc),
        ),
    ),
    SlashCommand(
        name = "sendas",
        description = context.getString(R.string.slash_sendas_desc),
        argumentHint = context.getString(R.string.slash_sendas_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.SENDAS,
        params = listOf(
            SlashParam(R.string.slash_param_sendas_name_name, R.string.slash_param_sendas_name_desc, R.string.slash_param_sendas_name_example),
            SlashParam(R.string.slash_param_sendas_at_name, R.string.slash_param_sendas_at_desc, R.string.slash_param_sendas_at_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_sendas_1_cmd, R.string.slash_example_sendas_1_desc),
            SlashExample(R.string.slash_example_sendas_2_cmd, R.string.slash_example_sendas_2_desc),
        ),
    ),
    SlashCommand(
        name = "send",
        description = context.getString(R.string.slash_send_desc),
        argumentHint = context.getString(R.string.slash_send_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.SEND,
        params = listOf(
            SlashParam(R.string.slash_param_send_name_name, R.string.slash_param_send_name_desc, R.string.slash_param_send_name_example),
            SlashParam(R.string.slash_param_send_at_name, R.string.slash_param_send_at_desc, R.string.slash_param_send_at_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_send_1_cmd, R.string.slash_example_send_1_desc),
            SlashExample(R.string.slash_example_send_2_cmd, R.string.slash_example_send_2_desc),
        ),
    ),
    SlashCommand(
        name = "persona",
        description = context.getString(R.string.slash_persona_desc),
        argumentHint = context.getString(R.string.slash_persona_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.PERSONA,
        params = listOf(
            SlashParam(R.string.slash_param_persona_mode_name, R.string.slash_param_persona_mode_desc, R.string.slash_param_persona_mode_example),
            SlashParam(R.string.slash_param_persona_off_name, R.string.slash_param_persona_off_desc, R.string.slash_param_persona_off_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_persona_1_cmd, R.string.slash_example_persona_1_desc),
            SlashExample(R.string.slash_example_persona_2_cmd, R.string.slash_example_persona_2_desc),
            SlashExample(R.string.slash_example_persona_3_cmd, R.string.slash_example_persona_3_desc),
        ),
    ),
    SlashCommand(
        name = "trigger",
        description = context.getString(R.string.slash_trigger_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.TRIGGER,
    ),
    SlashCommand(
        name = "sysgen",
        description = context.getString(R.string.slash_sysgen_desc),
        argumentHint = context.getString(R.string.slash_sysgen_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.SYSGEN,
        params = listOf(
            SlashParam(R.string.slash_param_sysgen_name_name, R.string.slash_param_sysgen_name_desc, R.string.slash_param_sysgen_name_example),
            SlashParam(R.string.slash_param_sysgen_at_name, R.string.slash_param_sysgen_at_desc, R.string.slash_param_sysgen_at_example),
            SlashParam(R.string.slash_param_sysgen_trim_name, R.string.slash_param_sysgen_trim_desc, R.string.slash_param_sysgen_trim_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_sysgen_1_cmd, R.string.slash_example_sysgen_1_desc),
            SlashExample(R.string.slash_example_sysgen_2_cmd, R.string.slash_example_sysgen_2_desc),
        ),
    ),
    SlashCommand(
        name = "char-update",
        description = context.getString(R.string.slash_char_update_desc),
        argumentHint = context.getString(R.string.slash_char_update_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.UPDATE_CHAR,
        params = listOf(
            SlashParam(R.string.slash_param_char_update_field_name, R.string.slash_param_char_update_field_desc, R.string.slash_param_char_update_field_example),
            SlashParam(R.string.slash_param_char_update_tags_name, R.string.slash_param_char_update_tags_desc, R.string.slash_param_char_update_tags_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_char_update_1_cmd, R.string.slash_example_char_update_1_desc),
            SlashExample(R.string.slash_example_char_update_2_cmd, R.string.slash_example_char_update_2_desc),
            SlashExample(R.string.slash_example_char_update_3_cmd, R.string.slash_example_char_update_3_desc),
        ),
    ),
    SlashCommand(
        name = "char-duplicate",
        description = context.getString(R.string.slash_char_duplicate_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.DUPLICATE,
    ),
    SlashCommand(
        name = "rename-char",
        description = context.getString(R.string.slash_rename_char_desc),
        argumentHint = context.getString(R.string.slash_rename_char_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.RENAME,
        params = listOf(
            SlashParam(R.string.slash_param_rename_char_name_name, R.string.slash_param_rename_char_name_desc, R.string.slash_param_rename_char_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_rename_char_1_cmd, R.string.slash_example_rename_char_1_desc),
        ),
    ),
    SlashCommand(
        name = "continue",
        description = context.getString(R.string.slash_continue_desc),
        argumentHint = context.getString(R.string.slash_continue_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.CONTINUE,
        params = listOf(
            SlashParam(R.string.slash_param_continue_text_name, R.string.slash_param_continue_text_desc, R.string.slash_param_continue_text_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_continue_1_cmd, R.string.slash_example_continue_1_desc),
            SlashExample(R.string.slash_example_continue_2_cmd, R.string.slash_example_continue_2_desc),
        ),
    ),
    SlashCommand(
        name = "impersonate",
        description = context.getString(R.string.slash_impersonate_desc),
        argumentHint = context.getString(R.string.slash_impersonate_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.IMPERSONATE,
        params = listOf(
            SlashParam(R.string.slash_param_impersonate_prompt_name, R.string.slash_param_impersonate_prompt_desc, R.string.slash_param_impersonate_prompt_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_impersonate_1_cmd, R.string.slash_example_impersonate_1_desc),
            SlashExample(R.string.slash_example_impersonate_2_cmd, R.string.slash_example_impersonate_2_desc),
        ),
    ),
    SlashCommand(
        name = "gen",
        description = context.getString(R.string.slash_gen_desc),
        argumentHint = context.getString(R.string.slash_gen_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.GEN,
        params = listOf(
            SlashParam(R.string.slash_param_gen_trim_name, R.string.slash_param_gen_trim_desc, R.string.slash_param_gen_trim_example),
            SlashParam(R.string.slash_param_gen_as_name, R.string.slash_param_gen_as_desc, R.string.slash_param_gen_as_example),
            SlashParam(R.string.slash_param_gen_length_name, R.string.slash_param_gen_length_desc, R.string.slash_param_gen_length_example),
            SlashParam(R.string.slash_param_gen_name_name, R.string.slash_param_gen_name_desc, R.string.slash_param_gen_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_gen_1_cmd, R.string.slash_example_gen_1_desc),
            SlashExample(R.string.slash_example_gen_2_cmd, R.string.slash_example_gen_2_desc),
            SlashExample(R.string.slash_example_gen_3_cmd, R.string.slash_example_gen_3_desc),
        ),
    ),
    SlashCommand(
        name = "reroll-pick",
        description = context.getString(R.string.slash_reroll_pick_desc),
        argumentHint = context.getString(R.string.slash_reroll_pick_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.REROLL_PICK,
    ),
    SlashCommand(
        name = "js",
        description = context.getString(R.string.slash_js_desc),
        argumentHint = context.getString(R.string.slash_js_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.JS,
        params = listOf(
            SlashParam(R.string.slash_param_js_code_name, R.string.slash_param_js_code_desc, R.string.slash_param_js_code_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_js_1_cmd, R.string.slash_example_js_1_desc),
            SlashExample(R.string.slash_example_js_2_cmd, R.string.slash_example_js_2_desc),
        ),
    ),
    SlashCommand(
        name = "tavern",
        description = context.getString(R.string.slash_tavern_desc),
        argumentHint = context.getString(R.string.slash_tavern_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.TAVERN,
    ),
    SlashCommand(
        name = "hide",
        description = context.getString(R.string.slash_hide_desc),
        argumentHint = context.getString(R.string.slash_hide_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.HIDE,
        params = listOf(
            SlashParam(R.string.slash_param_hide_name_name, R.string.slash_param_hide_name_desc, R.string.slash_param_hide_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_hide_1_cmd, R.string.slash_example_hide_1_desc),
            SlashExample(R.string.slash_example_hide_2_cmd, R.string.slash_example_hide_2_desc),
        ),
    ),
    SlashCommand(
        name = "unhide",
        description = context.getString(R.string.slash_unhide_desc),
        argumentHint = context.getString(R.string.slash_unhide_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.UNHIDE,
        params = listOf(
            SlashParam(R.string.slash_param_unhide_name_name, R.string.slash_param_unhide_name_desc, R.string.slash_param_unhide_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_unhide_1_cmd, R.string.slash_example_unhide_1_desc),
            SlashExample(R.string.slash_example_unhide_2_cmd, R.string.slash_example_unhide_2_desc),
        ),
    ),
    SlashCommand(
        name = "swipe",
        description = context.getString(R.string.slash_swipe_desc),
        argumentHint = context.getString(R.string.slash_swipe_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.SWIPE,
        params = listOf(
            SlashParam(R.string.slash_param_swipe_direction_name, R.string.slash_param_swipe_direction_desc, R.string.slash_param_swipe_direction_example),
            SlashParam(R.string.slash_param_swipe_await_name, R.string.slash_param_swipe_await_desc, R.string.slash_param_swipe_await_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_swipe_1_cmd, R.string.slash_example_swipe_1_desc),
            SlashExample(R.string.slash_example_swipe_2_cmd, R.string.slash_example_swipe_2_desc),
            SlashExample(R.string.slash_example_swipe_3_cmd, R.string.slash_example_swipe_3_desc),
        ),
    ),
    SlashCommand(
        name = "checkpoint-create",
        description = context.getString(R.string.slash_checkpoint_create_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.CHECKPOINT_CREATE,
        params = listOf(
            SlashParam(R.string.slash_param_checkpoint_mesid_name, R.string.slash_param_checkpoint_mesid_desc, R.string.slash_param_checkpoint_mesid_example),
            SlashParam(R.string.slash_param_checkpoint_name_name, R.string.slash_param_checkpoint_name_desc, R.string.slash_param_checkpoint_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_checkpoint_create_1_cmd, R.string.slash_example_checkpoint_create_1_desc),
            SlashExample(R.string.slash_example_checkpoint_create_2_cmd, R.string.slash_example_checkpoint_create_2_desc),
        ),
    ),
    SlashCommand(
        name = "checkpoint-go",
        description = context.getString(R.string.slash_checkpoint_go_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.CHECKPOINT_GO,
        params = listOf(
            SlashParam(R.string.slash_param_checkpoint_mesid_name, R.string.slash_param_checkpoint_mesid_desc, R.string.slash_param_checkpoint_mesid_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_checkpoint_go_1_cmd, R.string.slash_example_checkpoint_go_1_desc),
        ),
    ),
    SlashCommand(
        name = "checkpoint-exit",
        description = context.getString(R.string.slash_checkpoint_exit_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.CHECKPOINT_EXIT,
        examples = listOf(
            SlashExample(R.string.slash_example_checkpoint_exit_1_cmd, R.string.slash_example_checkpoint_exit_1_desc),
        ),
    ),
    SlashCommand(
        name = "checkpoint-parent",
        description = context.getString(R.string.slash_checkpoint_parent_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.CHECKPOINT_PARENT,
        examples = listOf(
            SlashExample(R.string.slash_example_checkpoint_parent_1_cmd, R.string.slash_example_checkpoint_parent_1_desc),
        ),
    ),
    SlashCommand(
        name = "checkpoint-get",
        description = context.getString(R.string.slash_checkpoint_get_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.CHECKPOINT_GET,
        params = listOf(
            SlashParam(R.string.slash_param_checkpoint_mesid_name, R.string.slash_param_checkpoint_mesid_desc, R.string.slash_param_checkpoint_mesid_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_checkpoint_get_1_cmd, R.string.slash_example_checkpoint_get_1_desc),
        ),
    ),
    SlashCommand(
        name = "checkpoint-list",
        description = context.getString(R.string.slash_checkpoint_list_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.CHECKPOINT_LIST,
        params = listOf(
            SlashParam(R.string.slash_param_checkpoint_links_name, R.string.slash_param_checkpoint_links_desc, R.string.slash_param_checkpoint_links_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_checkpoint_list_1_cmd, R.string.slash_example_checkpoint_list_1_desc),
            SlashExample(R.string.slash_example_checkpoint_list_2_cmd, R.string.slash_example_checkpoint_list_2_desc),
        ),
    ),
    SlashCommand(
        name = "branch-create",
        description = context.getString(R.string.slash_branch_create_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.BRANCH_CREATE,
        params = listOf(
            SlashParam(R.string.slash_param_checkpoint_mesid_name, R.string.slash_param_checkpoint_mesid_desc, R.string.slash_param_checkpoint_mesid_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_branch_create_1_cmd, R.string.slash_example_branch_create_1_desc),
        ),
    ),
) + varSlashCommands(context) + stScriptSlashCommands()

/**
 * 常用 STscript 扩展命令自动补全项（全部交由 StSlashExecutor 执行）。
 */
fun stScriptSlashCommands(): List<SlashCommand> = listOf(
    SlashCommand(name = "echo", description = "显示提示消息并透传管道文本", argumentHint = "[文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "pass", description = "透传文本或当前管道值", argumentHint = "[文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "return", description = "返回指定值并结束当前脚本", argumentHint = "[值]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "let", description = "定义当前脚本作用域局部变量", argumentHint = "[变量名] [值]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "var", description = "读写当前脚本作用域变量", argumentHint = "[变量名] [值]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "if", description = "条件分支执行命令或闭包", argumentHint = "left=.. rule=.. right=.. {: ... :}", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "else", description = "上一次 /if 未命中时的分支", argumentHint = "{: ... :}", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "while", description = "条件循环执行命令或闭包", argumentHint = "left=.. rule=.. right=.. {: ... :}", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "times", description = "按指定次数循环执行闭包或子命令", argumentHint = "[次数] {: ... :}", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "break", description = "跳出当前循环或闭包", argumentHint = "[返回值]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "abort", description = "中止整条 STscript 脚本执行", argumentHint = "[原因]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "delay", description = "延迟等待指定毫秒数", argumentHint = "[毫秒]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "run", description = "调用闭包字面量或已保存的闭包变量", argumentHint = "[闭包名或 {: ... :}]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "genraw", description = "不带聊天历史静默生成文本并返回至管道", argumentHint = "[system=..] [提示词]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "setglobalvar", description = "设置全局宏变量", argumentHint = "[key] [value]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "getglobalvar", description = "读取全局宏变量", argumentHint = "[key]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "addglobalvar", description = "累加或拼接全局宏变量", argumentHint = "[key] [value]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "incglobalvar", description = "全局宏变量自增 1", argumentHint = "[key]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "decglobalvar", description = "全局宏变量自减 1", argumentHint = "[key]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "flushglobalvar", description = "删除指定全局宏变量", argumentHint = "[key]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "listglobalvar", description = "列出所有全局宏变量", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "del", description = "删除指定数量或范围的消息", argumentHint = "[数量或 start-end]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "cut", description = "剪切指定范围的消息并返回文本", argumentHint = "[数量或 start-end]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "inject", description = "向当前会话注入临时提示词", argumentHint = "id=.. position=.. depth=.. [内容]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "listinjects", description = "列出当前会话的临时注入提示词", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "flushinject", description = "清除指定或全部临时注入提示词", argumentHint = "[id]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "world", description = "绑定或解绑指定名称的世界书", argumentHint = "[state=on|off] [世界书名]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "getentryfield", description = "读取世界书条目字段", argumentHint = "file=.. field=.. [uid]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "setentryfield", description = "修改世界书条目字段", argumentHint = "file=.. uid=.. field=.. [值]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "createentry", description = "在世界书中新建条目", argumentHint = "file=.. key=.. [内容]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "regex", description = "启用或禁用指定正则脚本", argumentHint = "[state=on|off] [名称或ID]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "setinput", description = "设置聊天输入框草稿内容", argumentHint = "[文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "messages", description = "读取指定楼层或范围的消息文本", argumentHint = "[names=on|off] [楼层或范围]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "note", description = "读取或设置作者备注（Author's Note）", argumentHint = "[文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "note-depth", description = "设置作者备注注入深度", argumentHint = "[深度]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "note-frequency", description = "设置作者备注注入间隔", argumentHint = "[间隔]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "note-position", description = "设置作者备注注入位置", argumentHint = "[chat|prompt|before]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "note-role", description = "设置作者备注注入角色", argumentHint = "[system|user|assistant]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "qr", description = "按标题触发快速回复", argumentHint = "[标题]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "qr-arg", description = "设置快速回复上下文参数", argumentHint = "[键] [值]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "translate", description = "将文本翻译为目标语言", argumentHint = "[target=语言] [文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "summarize", description = "总结当前对话剧情并保存至 {{summary}}", argumentHint = "[提示词]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "caption", description = "生成当前画面或场景的描述", argumentHint = "[提示词]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "popup", description = "显示弹窗提示", argumentHint = "[title=标题] [文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "input", description = "获取输入值（支持 default=默认值）", argumentHint = "[default=..] [提示文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
    SlashCommand(name = "buttons", description = "按钮选择命令（支持 labels=[...]）", argumentHint = "labels=[...] [提示文本]", content = "", filePath = "builtin", builtinKind = BuiltinSlashKind.STSCRIPT),
)

/**
 * 官方变量命令家族（对齐 SillyTavern variables.js，仅保留本对话级 7 个）。
 */
fun varSlashCommands(context: Context): List<SlashCommand> = listOf(
    SlashCommand(
        name = "listvar",
        description = context.getString(R.string.slash_listvar_desc),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.VAR,
    ),
    SlashCommand(
        name = "setvar",
        description = context.getString(R.string.slash_setvar_desc),
        argumentHint = context.getString(R.string.slash_setvar_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.VAR,
        params = listOf(
            SlashParam(R.string.slash_param_setvar_name_name, R.string.slash_param_setvar_name_desc, R.string.slash_param_setvar_name_example),
            SlashParam(R.string.slash_param_setvar_value_name, R.string.slash_param_setvar_value_desc, R.string.slash_param_setvar_value_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_setvar_1_cmd, R.string.slash_example_setvar_1_desc),
            SlashExample(R.string.slash_example_setvar_2_cmd, R.string.slash_example_setvar_2_desc),
        ),
    ),
    SlashCommand(
        name = "getvar",
        description = context.getString(R.string.slash_getvar_desc),
        argumentHint = context.getString(R.string.slash_getvar_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.VAR,
        params = listOf(
            SlashParam(R.string.slash_param_getvar_name_name, R.string.slash_param_getvar_name_desc, R.string.slash_param_getvar_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_getvar_1_cmd, R.string.slash_example_getvar_1_desc),
            SlashExample(R.string.slash_example_getvar_2_cmd, R.string.slash_example_getvar_2_desc),
        ),
    ),
    SlashCommand(
        name = "addvar",
        description = context.getString(R.string.slash_addvar_desc),
        argumentHint = context.getString(R.string.slash_addvar_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.VAR,
        params = listOf(
            SlashParam(R.string.slash_param_addvar_name_name, R.string.slash_param_addvar_name_desc, R.string.slash_param_addvar_name_example),
            SlashParam(R.string.slash_param_addvar_value_name, R.string.slash_param_addvar_value_desc, R.string.slash_param_addvar_value_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_addvar_1_cmd, R.string.slash_example_addvar_1_desc),
            SlashExample(R.string.slash_example_addvar_2_cmd, R.string.slash_example_addvar_2_desc),
        ),
    ),
    SlashCommand(
        name = "incvar",
        description = context.getString(R.string.slash_incvar_desc),
        argumentHint = context.getString(R.string.slash_incvar_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.VAR,
        params = listOf(
            SlashParam(R.string.slash_param_incvar_name_name, R.string.slash_param_incvar_name_desc, R.string.slash_param_incvar_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_incvar_1_cmd, R.string.slash_example_incvar_1_desc),
        ),
    ),
    SlashCommand(
        name = "decvar",
        description = context.getString(R.string.slash_decvar_desc),
        argumentHint = context.getString(R.string.slash_decvar_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.VAR,
        params = listOf(
            SlashParam(R.string.slash_param_decvar_name_name, R.string.slash_param_decvar_name_desc, R.string.slash_param_decvar_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_decvar_1_cmd, R.string.slash_example_decvar_1_desc),
        ),
    ),
    SlashCommand(
        name = "flushvar",
        description = context.getString(R.string.slash_flushvar_desc),
        argumentHint = context.getString(R.string.slash_flushvar_hint),
        content = "",
        filePath = "builtin",
        builtinKind = BuiltinSlashKind.VAR,
        params = listOf(
            SlashParam(R.string.slash_param_flushvar_name_name, R.string.slash_param_flushvar_name_desc, R.string.slash_param_flushvar_name_example),
        ),
        examples = listOf(
            SlashExample(R.string.slash_example_flushvar_1_cmd, R.string.slash_example_flushvar_1_desc),
        ),
    ),
)

/**
 * 从已启用的 skills 中收集所有 commands 和 user-invocable skills 作为斜杠命令
 */
fun collectSlashCommands(
    enabledSkills: List<me.rerere.rikkahub.data.files.SkillMetadata>,
    context: Context,
): List<SlashCommand> {
    val commands = enabledSkills.flatMap { skill ->
        skill.commands.map { cmd ->
            SlashCommand(
                name = cmd.name,
                description = cmd.description,
                allowedTools = cmd.allowedTools,
                argumentHint = cmd.argumentHint,
                disableModelInvocation = cmd.disableModelInvocation,
                content = cmd.content,
                filePath = cmd.filePath,
            )
        }
    }
    // 同时收集 user-invocable 的 skill 本身作为斜杠命令
    val invocableSkills = enabledSkills.filter { it.userInvocable }.map { skill ->
        SlashCommand(
            name = skill.name,
            description = skill.description,
            allowedTools = skill.allowedTools,
            argumentHint = "",
            disableModelInvocation = false,
            content = skill.name, // 触发时用 skill name 匹配 auto-trigger
            filePath = "SKILL.md (${skill.name})",
        )
    }
    return commands + invocableSkills + builtinSlashCommands(context)
}

/**
 * 匹配用户输入的斜杠命令
 */
fun matchSlashCommand(
    input: String,
    commands: List<SlashCommand>,
): SlashCommand? {
    val trimmed = input.trimStart()
    if (!trimmed.startsWith("/")) return null
    val name = trimmed.substring(1).split(Regex("\\s+")).first().lowercase()
    return commands.find { it.name.lowercase() == name }
}

/**
 * 判断输入框中的 `/` 文本是否应直接交由 [me.rerere.rikkahub.data.st.script.StSlashExecutor] 执行：
 * 1. 以 `//` 开头的 STscript 注释；
 * 2. 包含管道符 `|`（引号外）、闭包字面量 `{:` / `{|`、宏 `{{...}}` 或多行脚本；
 * 3. `matched == null`（不在旧版单命令表中，如 50+ 条 STscript 命令或 JS 扩展注册命令）；
 * 4. 命中 `BuiltinSlashKind.STSCRIPT`、检查点族或分支创建命令。
 */
fun shouldRouteToStSlashExecutor(
    text: String,
    matched: SlashCommand? = null,
): Boolean {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("/")) return false
    if (trimmed.startsWith("//")) return true
    if (trimmed.contains('\n') || trimmed.contains("{:") || trimmed.contains("{|") || trimmed.contains("{{")) {
        return true
    }
    // 检查引号外是否包含顶层管道符 '|'
    var inSingle = false
    var inDouble = false
    var i = 0
    while (i < trimmed.length) {
        val c = trimmed[i]
        if (c == '\\' && i + 1 < trimmed.length) {
            i += 2
            continue
        }
        if (c == '\'' && !inDouble) inSingle = !inSingle
        else if (c == '"' && !inSingle) inDouble = !inDouble
        else if (c == '|' && !inSingle && !inDouble) return true
        i++
    }
    if (matched == null) return true
    return when (matched.builtinKind) {
        BuiltinSlashKind.STSCRIPT,
        BuiltinSlashKind.CHECKPOINT_CREATE,
        BuiltinSlashKind.CHECKPOINT_GO,
        BuiltinSlashKind.CHECKPOINT_EXIT,
        BuiltinSlashKind.CHECKPOINT_PARENT,
        BuiltinSlashKind.CHECKPOINT_GET,
        BuiltinSlashKind.CHECKPOINT_LIST,
        BuiltinSlashKind.BRANCH_CREATE -> true
        else -> false
    }
}
