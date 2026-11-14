package me.rerere.workspace

data class Workspace(
    val id: String,
    val name: String,
    val root: String,
    val shellStatus: WorkspaceShellStatus = WorkspaceShellStatus.DISABLED,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessAt: Long? = null,
    /**
     * 工作区类型，对齐上游 rikkahub 的 `workspaces.type`。
     *
     * 上游支持 `REMOTE`（SSH 远程工作区），本分支未实现。声明这个字段只为：
     *  1. 让恢复上游备份时能识别出哪些是远程工作区；
     *  2. 在 UI 上给出「当前版本不支持」的明确提示，而不是当成本地路径访问。
     */
    val type: String = WORKSPACE_TYPE_LOCAL,
) {
    /** 是否为上游的远程 SSH 工作区（当前版本不支持）。 */
    val isRemoteUnsupported: Boolean
        get() = type.equals(WORKSPACE_TYPE_REMOTE, ignoreCase = true)
}

const val WORKSPACE_TYPE_LOCAL = "LOCAL"
const val WORKSPACE_TYPE_REMOTE = "REMOTE"

enum class WorkspaceShellStatus {
    DISABLED,
    INSTALLING,
    READY,
    BROKEN,
}

enum class WorkspaceStorageArea {
    FILES,
    LINUX,
}

enum class RootfsInstallStage {
    DOWNLOADING,
    EXTRACTING,
    INSTALLED,
}

data class RootfsInstallProgress(
    val stage: RootfsInstallStage,
    val bytesRead: Long = 0,
    val totalBytes: Long? = null,
    val entriesExtracted: Int = 0,
    val currentEntry: String? = null,
)

data class WorkspaceConfig(
    val maxReadBytes: Long = 512 * 1024,
    val maxWriteBytes: Long = 2 * 1024 * 1024,
    val maxListEntries: Int = 500,
    val maxSearchResults: Int = 100,
)

data class WorkspaceFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val updatedAt: Long,
)

data class WorkspaceSearchMatch(
    val path: String,
    val line: Int,
    val text: String,
)

data class WorkspaceCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
)
