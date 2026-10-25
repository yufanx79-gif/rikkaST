# Node.js & PRoot Workspace Integration Audit (`10-22-0`)

## 1. Existing Node Runtime & PRoot Subsystems

1. **`NodeRuntimeManager.kt` (`workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt`)**:
   - Downloads, SHA-256 verifies (`v20.18.1` official Linux `arm64` / `x64` tar.xz), extracts, and runs the shared Node.js runtime under `<filesDir>/workspaces/shared_tavern_node/linux/usr/local/node`.
   - Exposes `ROOTFS_NODE_DIR = "/usr/local/node"` and `resolveSharedNodeDir(linuxDir: File): File?`, which strictly resolves ONLY `workspaces/shared_tavern_node/linux/usr/local/node` when the target workspace `linuxDir` does not have a local `usr/local/node/bin/node`. It never scans or auto-selects arbitrary sibling assistant workspaces.
2. **`TavernNodeServiceManager.kt` (`app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernNodeServiceManager.kt`)**:
   - Application-scoped bridge between SillyTavern runtime plugins (`littlewhitebox-server`, etc.) and `NodeRuntimeManager` on `127.0.0.1:18181`, using per-session bearer token authentication (`RIKKA_NODE_TOKEN`).
3. **`RootfsPatcher.kt` (`workspace/src/main/java/me/rerere/workspace/RootfsPatcher.kt`)**:
   - `ensureNodeEnvironment(linuxDir, etcDir)` creates the `/usr/local/node` mountpoint, creates `/usr/local/bin/{node,npm,npx}` relative symlinks (`../node/bin/<binName>`), and provisions `/etc/profile.d/rikka-node.sh` so login shells (`bash -l`) preserve `/usr/local/node/bin` in `PATH` and isolate `NPM_CONFIG_CACHE=/tmp/.npm-cache` and `NODE_COMPILE_CACHE=/tmp/.node-compile-cache`.
4. **`ProotShellRunner.kt` & `WorkspaceTerminalSession.kt`**:
   - Bind-mount `NodeRuntimeManager.resolveSharedNodeDir(linuxDir)` to `/usr/local/node` (`NodeRuntimeManager.ROOTFS_NODE_DIR`), prepend `/usr/local/node/bin` to `PATH`, and isolate `NPM_CONFIG_CACHE` and `NODE_COMPILE_CACHE` inside `/tmp`, while keeping `/workspace` strictly bound to each assistant's own `filesDir` and never exposing `RIKKA_NODE_TOKEN`.
