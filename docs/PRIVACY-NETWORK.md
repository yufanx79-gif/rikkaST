# 网络请求与隐私说明

> 供使用者与社区审查：本文件披露 rikkaST 会发起的网络请求、是否存在数据收集，以及敏感权限用途。
> 最后更新：v241（2026-10）

## 1. 数据收集：无

- **不包含**任何埋点（Analytics）、崩溃上报（Crashlytics / Sentry 等）、广告或用户画像 SDK。
- **不会**把聊天内容、角色卡、世界书、设置或设备信息上传给作者或任何第三方。
- 所有数据（聊天记录、角色卡、世界书、设置、扩展文件）都保存在本机 App 私有目录 / 数据库中。

## 2. 网络请求清单（白名单外的第三方域名）

| 用途 | 域名 | 触发条件 |
|---|---|---|
| 检查更新 | raw.githubusercontent.com | 用户打开「关于 / 更新」时 |
| 下载更新包 | github.com、objects.githubusercontent.com | 用户点击更新后（交给系统下载器） |
| 安装酒馆扩展 | github.com、api.github.com、gitlab.com、raw.githubusercontent.com | 用户在扩展中心安装扩展时 |
| 技能 / 工作区资源 | github.com、raw.githubusercontent.com | 用户安装技能或整仓库下载时 |
| 消息渲染资源 | cdn.jsdelivr.net | 消息引用了 jsDelivr 资源、且内置 vendor/ 未命中时 |
| AI 服务 | **用户在「提供商」中选择/配置的那一个**（内置预设含 api.openai.com、generativelanguage.googleapis.com、api.anthropic.com、api.x.ai、api.deepseek.com、api.stepfun.com、api.xiaomimimo.com、aihubmix.com、sui-xiang.com 等） | 每次对话 / 工具调用 / 生图 / TTS 时 |
| 自定义端点 | 任意（用户自己填写的 Base URL） | 同上 |

> 除「检查更新」外，以上请求都由**用户主动操作**触发（装扩展、发消息、生成图片等）。

## 3. 敏感权限与用途

| 权限 | 用途 |
|---|---|
| INTERNET、ACCESS_NETWORK_STATE、ACCESS_WIFI_STATE | 访问用户配置的 AI 服务 |
| CAMERA | 拍照 / 扫码导入（可选） |
| RECORD_AUDIO | 语音输入（ASR） |
| READ_EXTERNAL_STORAGE、WRITE_EXTERNAL_STORAGE、MANAGE_EXTERNAL_STORAGE | 导入导出角色卡 / 世界书、写运行时日志、安装扩展文件 |
| READ_CALENDAR、WRITE_CALENDAR | 内置「日历」本地工具（可关闭） |
| PACKAGE_USAGE_STATS | 内置「屏幕时间」本地工具（可关闭） |
| POST_NOTIFICATIONS、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE | 生成中的前台服务与完成通知 |
| ACCESS_LOCAL_NETWORK | 局域网直连（例如局域网内的 OpenAI 兼容端点） |

## 4. 第三方插件（酒馆扩展）的安全边界

- 扩展的 JS 在内置 WebView 运行时中执行，**可以**读取当前会话上下文（消息、变量、世界书等），并通过运行时接口写回。
- 扩展由用户自行安装，其行为由扩展作者负责 —— 请只安装可信来源的扩展。
- 运行时日志（含扩展输出）写入 Android/data/<包名>/files/tavern-runtime.log，仅存本机。
