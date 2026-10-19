# 工程任务状态（TASK_STATE.md）

## 一、各阶段完成状态

- **Phase 1（输入归档清点与只读基线建立）**：已完成（`audit/INPUT_MANIFEST.md`）。
- **Phase 2（上游 SillyTavern `1.18.0 → 1.19.0` 差异审计、Workspace/PRoot 对比、五扩展契约映射与基线测试）**：已完成（`audit/upstream/*`、`docs/porting/ST119_AUDIT_BASELINE.md`、`audit/plugins/*.md`、`audit/baseline-test-results.md`）。
- **Phase 3（线上 `2.5.6-public` 高优 Bug A/B/C 根因修复与 7 阶段发送诊断链路）**：已完成（`NavContext.kt`、`RouteActivity.kt`、`ImeAutoScroller.kt`、`MessageHtmlBlock.kt`、`ChatInput.kt`、`ChatPage.kt`、`ChatVM.kt`、`ChatService.kt`、`ConversationSession.kt`、`ConversationRepository.kt`、`PreferencesStore.kt`、`GenerationLoop.kt`、`TavernRuntimeManager.kt`）。
- **Phase 4（SillyTavern `1.19.0` 兼容层与五个扩展宿主 API / 生成链路 / 目录别名兼容）**：已完成（补齐 `st-compat` 全部缺失模块与 19 个具名导出、修复 `StRegexedText.kt` 主线程非阻塞异步 `MessageFormatter` 渲染、支持扩展目录版本/分支后缀别名解析）。
- **Phase 5（共享 Linux Workspace Node.js 运行时管理器与宿主服务防伪造隔离加固）**：已完成（修正 `NodeRuntimeManager.kt` 官方 Node.js v20.18.1 SHA-256 校验值、在 `TavernNodeServiceManager.kt` 与 `server-bootstrap.cjs` 中实施严格会话身份绑定与 401/403 越权拦截）。
- **Phase 6（真实五扩展集成测试与两轮对抗式独立代码审查）**：已完成（15 项自动化集成与回归测试 100% 通过，`verify-node-runtime-on-device.sh` 全部验证层通过，全量 5 扩展 ESM 静态链接扫描 0 缺失）。
- **Phase 7（完整源码归档、零遗漏 Unified Diff 补丁、修改文件清单与诊断报告打包交付）**：已完成。

## 二、环境限制与待目标环境实测项（诚实披露）

1. **Android APK 构建状态：未在当前容器内执行 Gradle 构建（当前容器无 Android SDK、无离线 Gradle/Maven 缓存，且禁外网）**
   - 已对全部修改后的 Kotlin 源码执行词法级括号/作用域平衡与标签静态校验（修复了原候选补丁中 `GenerationLoop.kt:863` 的非法 `return@channelFlow`），并提供本地复测与打包命令：`./gradlew :workspace:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`。
2. **共享 Node.js 运行时验收状态：当前 Linux 容器实测通过；Android / PRoot 目标真机待实测**
   - 在当前 Linux 环境中，`server-bootstrap.cjs` 与 `littlewhitebox-server`（22 条路由）已通过真实 Node.js 进程启动、Loopback 鉴权、`401/403` 防伪造拦截、`/health`、`/v1/draw-runs/probe` 回环挑战、`/api/backends/chat-completions/{status,generate}` 流式与非流式转发、以及多助手/多会话隔离实测。
   - 在真实 Android ARM64 设备 PRoot 环境中运行官方预编译 `node-v20.18.1-linux-arm64.tar.xz`，可通过应用内「共享 Node.js 服务端运行时」卡片或执行 `bash scripts/verify-node-runtime-on-device.sh` 完成最终真机验收。
