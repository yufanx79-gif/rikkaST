package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.WORKSPACE_TYPE_LOCAL
import me.rerere.workspace.WORKSPACE_TYPE_REMOTE
import me.rerere.workspace.Workspace
import me.rerere.workspace.WorkspaceShellStatus

@Entity(
    tableName = "workspaces",
    indices = [
        Index(value = ["root"], unique = true),
        Index(value = ["updated_at"]),
    ],
)
data class WorkspaceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("root")
    val root: String,
    @ColumnInfo("shell_status")
    val shellStatus: String = WorkspaceShellStatus.DISABLED.name,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
    @ColumnInfo("last_access_at")
    val lastAccessAt: Long? = null,
    // 工具审批的用户覆盖项 (toolName -> needsApproval)，未覆盖的工具沿用默认值
    @ColumnInfo("tool_approvals", defaultValue = "{}")
    val toolApprovals: String = "{}",
    @ColumnInfo("shell_compatibility_mode", defaultValue = "0")
    val shellCompatibilityMode: Boolean = false,
    /**
     * 上游 rikkahub 的「工作区类型」列（`LOCAL` / `REMOTE`）。
     *
     * ## 为什么我们声明一个用不到的列
     *
     * 上游从 v32 起支持**远程 SSH 工作区**，为此在 `workspaces` 表加了 `type` 和 `ssh_config`。
     * 我们这条分支没有实现 SSH，但 Room 的 TableInfo 校验是**双向精确相等**：
     * 库里有的列，实体必须全部声明，否则恢复上游备份时会报
     * `Migration didn't properly handle workspaces` 并拒绝打开数据库。
     *
     * 所以这里声明它们**只为通过校验**，业务代码不读不写。
     * 恢复上游备份后，`REMOTE` 工作区会被识别并在 UI 上提示「当前版本不支持」，
     * 而不是当成本地路径去访问（见 [me.rerere.workspace.Workspace.isRemoteUnsupported]）。
     */
    @ColumnInfo("type", defaultValue = "LOCAL")
    val type: String = WORKSPACE_TYPE_LOCAL,
    /** 上游的 SSH 连接配置（JSON）。我们不解析，仅原样保留以便将来支持。 */
    @ColumnInfo("ssh_config")
    val sshConfig: String? = null,
) {
    /** 是否为上游的远程 SSH 工作区（当前版本不支持）。 */
    val isRemote: Boolean get() = type.equals(WORKSPACE_TYPE_REMOTE, ignoreCase = true)

    fun toolApprovalOverrides(): Map<String, Boolean> = runCatching {
        JsonInstant.decodeFromString<Map<String, Boolean>>(toolApprovals)
    }.getOrDefault(emptyMap())

    fun toWorkspace(): Workspace = Workspace(
        id = id,
        name = name,
        root = root,
        shellStatus = runCatching { WorkspaceShellStatus.valueOf(shellStatus) }
            .getOrDefault(WorkspaceShellStatus.DISABLED),
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastAccessAt = lastAccessAt,
        type = type,
    )
}

