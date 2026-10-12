# rikkaST 工作区 README

> 记录时间：2026-10-10 13:30（CST）
> 仓库状态：**已转公开**
> 主仓库：<https://github.com/yufanx79-gif/rikkaST>
> 当前主线：`main` / `a94f236` / tag `v2.4.6`

---

## 0. 一句话

在 Linux（WSL2 ext4）里开发和维护 rikkaST 源码，在 Windows 上用 Windows JDK + Android SDK 编译 APK，再用 Windows 的 adb 安装到手机。

当前工作区里已经有一套完整的 rikkaST 主仓库、一个 9.6 GB 的酒馆/上游参考库，以及上一阶段的旧工作区备份。完整 SillyTavern 源码也在参考库里，不在主仓库根目录。

---

## 1. 最终目标

在 rikkaST 上继续完善，实现 **SillyTavern 除 TTS 和生图之外的全部功能**。

范围约定：

- 目标范围：角色卡、世界书、预设、正则、宏、STscript、变量、人设、导演备注、群聊、插件运行时、官方扩展兼容等酒馆核心与生态能力。
- 明确不做：**酒馆 TTS、酒馆生图**。
- 上游 RikkaHub 自带的 TTS/ASR、图像生成能力是否保留，和「酒馆兼容目标」分开讨论；本目标的“不做 TTS/生图”指的是不再为酒馆兼容层补这两块。

---

## 2. 当前进度（截至 2026-10-10）

### 2.1 基线

- 直接基座：RikkaHub Plus 2.4.6 / 上游 RikkaHub
- 包名：`me.rerere.rikkahub.st`
- 版本：`versionName = 2.4.6`，`versionCode = 237`
- 构建：Kotlin + Jetpack Compose + Chaquopy Python 3.12
- SDK：`compileSdk = 37`，`minSdk = 26`，`targetSdk = 37`
- 当前 `main` 工作区干净，已推送 `origin/main`

### 2.2 已完成的主线

| 层 | 状态 | 内容 |
|---|---|---|
| 数据层 | 基本完整 | 角色卡 V2/V3 导入导出、25 个顶层字段、`extensionsRaw` 无损往返、内嵌世界书/正则、预设与备份导入 |
| 世界书 | 基本完整 | 44 字段条目、四档关键词逻辑、递归、粘性、冷却、分组选胜、token 预算、插入策略 |
| 引擎层 | 基本完整 | 宏引擎、STscript 核心子集、正则引擎、EJS、MVU 变量解析、变量作用域 |
| 对话系统 | 已实现 | 人设、导演备注、群聊、多角色提示词、选人策略、自动接话 |
| UI | 已实现 | 角色卡编辑、世界书编辑、群聊页、人设页、导演备注页、扩展中心 |
| 插件运行时 | 能跑，有边界 | WebView + `st-runtime` + `st-compat` + `shims`，SillyTavern 前端 API 仿真 |
| 插件适配 | 明确适配 3 条线 | 酒馆助手（JS-Slash-Runner）、ST-Prompt-Template、MVU |
| 测试 | 已具备 | 仓库提交记录显示已有 651 个单测用例/探针素材 |

### 2.3 插件运行时已覆盖

- `runtime.js`：ST API / DOM / 事件仿真
- 36 个 `st-compat` 模块：`script.js`、`extensions.js`、`world-info.js`、`preset-manager.js`、`slash-commands/*` 等
- 10 个 `shims`：事件、变量、世界书、注入、按钮、EJS 等
- `TavernHelper` 55 个 API 键
- 82 个 ST 事件常量登记，约 30 个有真实发射点
- 前端 DOM 容器：`#extensions_settings`、`#extensions_settings2`、`#extensionsMenu`、`#send_form`、`#tavern_helper` 等
- 宿主端点已实现 2 个：
  - `POST /api/backends/chat-completions/status`
  - `POST /api/backends/chat-completions/generate`
- 离线 vendor：jsDelivr 裸 import 本地化
- MVU：内置变量框架 bundle

### 2.4 v241 已合入的兼容修复

- 角色卡 `character_book.entries[].enabled` 按 ST 1.18 规范读取
- 导出世界书改为 `entries` 数组，显式写 `id`、`enabled`、`extensions.role` 数字
- 真实卡正则脚本改为宽容解析，避免字段类型漂移导致整条丢失
- JSR `injectPrompts` / `uninjectPrompts` 移植

### 2.5 主要缺口

- 酒馆服务端 API 只实现了 2 个，其余 `/api/**` 多数返回 404
- 酒馆官方内置扩展大多未实现：`vectors`、`caption`、`summarize`、`tts`、`sd`、`gallery`、`assets` 等
  - `expressions` 已做原生重写
  - `regex/engine.js` 有兼容 shim
- 82 个事件常量中约 50 个只登记、不发射
- 3 个 iframe 事件已登记未接线
- 没有扩展商店 / 在线索引
- 部分字段仅数据兼容：`group_only_greetings`、`automation_id`、`vectorized`、`display_index/position`、`WORLD_INFO` 正则位等

更细的逐项状态见：

- `docs/ST-COMPATIBILITY.md`：插件接入层兼容报告
- `docs/ST-FEATURE-MATRIX.md`：官方功能逐项实现矩阵
- `DIVERGENCE.md`：与上游 RikkaHub 的差异与合并手册

---

## 3. 当前 Linux 工作区有什么

```text
/home/xiaoqi/
├── rikkaST/                   471M   ← 当前主线仓库
├── rikkaST-refs/              9.6G   ← 参考仓库库，完整酒馆源码在这里
├── rikkaST-docs-backup/       200K   ← 旧文档备份
├── operit-workspace/          2.9G   ← 上一阶段工作区
└── MIGRATION-NOTES.md                ← Windows D 盘 → WSL2 ext4 迁移说明
```

### 3.1 `~/rikkaST` 主仓库

```text
app/ ai/ common/ document/ highlight/ material3/ search/ speech/
web/ web-ui/ workspace/ docs/ locale-tui/ trace-cli/ sample-skills/
disabled_tests/ build-logic/ gradle/
README.md README_EN.md BUILDING.md DIVERGENCE.md update.json ...
```

酒馆兼容核心代码：

```text
app/src/main/java/me/rerere/rikkahub/data/st/
app/src/main/assets/st-runtime/
```

注意：`~/rikkaST` 根目录里没有完整酒馆源码。  
能看到 `world-info.js`、`slash-commands.js`、`PromptManager.js`，是因为它们在：

```text
app/src/main/assets/st-runtime/st-compat/
```

这些是兼容层 shim，不是完整 SillyTavern 源码。

### 3.2 `~/rikkaST-refs` 参考库

9.6 GB，按主题分类，包含约 283 个二级仓库/目录。重点：

| 目录 | 内容 |
|---|---|
| `A-rikkahub/` | RikkaHub 上游、rikkahub-plus 直接基座、酒馆向二改分支 |
| `B-sillytavern/` | SillyTavern 本体、文档、TauriTavern、SillyDroid、NativeTavern 等安卓移植 |
| `C-st-ext/` | 酒馆助手 JSR、ST-Prompt-Template、MVU、TavernHelper 模板 |
| `D-st-official/` | SillyTavern 官方扩展 |
| `E-worldinfo-memory/` | 世界书、长期记忆、总结、时间线 |
| `F-cards-render/` | 角色卡解析/编辑、HTML 面板渲染 |
| `G-group-interaction/` | 群聊工具 |
| `H-ext-popular/` | 高人气扩展 |
| `I-cards-data/` | 测试角色卡合集 |
| `J-vendor-libs/` | KaTeX、Mermaid、tiktoken、handlebars 等 |
| `K-st-official2/` | 官方第二批扩展 |
| `L-st-ext2/` | LenAnderson 等第二批扩展 |
| `M-ecosystem-rag/` | RAG / 向量 / 文档解析生态 |
| `N-host-android/` | 安卓宿主实现参考 |
| `O-cards-tools/` | 角色卡工具 |
| `P-worldbook-preset/` | 世界书 / 预设工具 |
| `Q-rikkahub-forks/` | RikkaHub 分支 |
| `R-interaction-prompt/` | 交互 / 提示词 / 快捷回复扩展 |
| `Z-st118-full/` | **SillyTavern 1.18.0 完整源码** |

机器可读清单：

```bash
~/rikkaST-refs/README.md
~/rikkaST-refs/_manifest.csv
```

### 3.3 完整 SillyTavern 源码位置

| 路径 | 体积 | 说明 |
|---|---:|---|
| `~/rikkaST-refs/Z-st118-full/SillyTavern-1.18.0` | 62M | **完整 ST 1.18.0 源码，契约真源** |
| `~/rikkaST-refs/B-sillytavern/SillyTavern__SillyTavern` | 99M | **官方 ST git 仓库**，当前 package 版本 `1.19.0` |
| `~/rikkaST-refs/B-sillytavern/SillyTavern__SillyTavern-Docs` | - | 官方文档仓库 |
| `~/rikkaST-refs/B-sillytavern/SillyTavern__SillyTavern-Extras` | - | 旧 Extensions API |
| `~/operit-workspace/workspace/rikkahubst/sillytavern/SillyTavern-1.18.0` | 32M | 旧手机工作区副本 |
| `~/operit-workspace/workspace/rikkahubst/assets/SillyTavern-1.18.0.zip` | 37M | ST 1.18.0 压缩包 |
| `~/operit-workspace/refdeps/sillytavern` | 488K | 仅抽出的 `public_lib.js`、`scripts_world-info.js` 等，不是完整源码 |

### 3.4 `~/operit-workspace` 旧工作区

| 路径 | 体积 | 内容 |
|---|---:|---|
| `backup/` | 393M | git bundle 备份、旧交接文档 |
| `dist/` | 540M | Gradle 分发包、历史构建产物 |
| `pcbuild/` | 795M | PC 构建工作副本 |
| `workspace/` | 992M | 手机工作区镜像，含 `rikkahubst` 工程 |
| `refdeps/` | 95M | JSR、ST-Prompt-Template、sillytavern 抽文件等 |
| `cards/` | 1.1M | 测试角色卡 |
| `notes/` | 72K | 历史开发笔记 |

---

## 4. Linux 工作 + Windows 编译 + adb 安装

### 4.1 目标流程

```text
Linux（WSL2 ext4）
  ├─ 编辑源码 / 查参考 / 跑 Linux 侧检查
  └─ 同步源码
        ↓
Windows（D:\rikkaST）
  ├─ Windows JDK
  ├─ Windows Android SDK
  ├─ gradlew.bat :app:assembleDebug / assembleRelease
  └─ adb install -r ...
        ↓
手机
```

### 4.2 现有 Windows 侧资源

- Windows 构建副本：`D:\rikkaST`（WSL 路径 `/mnt/d/rikkaST`）
- Windows 签名密钥：`D:\rikkaST-keys`
- Windows SDK：`E:\android-sdk`
- Windows `local.properties` 已配置 Windows SDK / Chaquopy Python / release 签名

`local.properties` 已被 `.gitignore` 忽略，里面包含签名口令明文，**不要提交、不要公开、不要同步覆盖**。

### 4.3 推荐同步方式：Git 为主，rsync 为辅

#### 方式 A：Git push / pull（推荐）

适合正式改动、需要历史记录、需要多端一致。

```bash
# Linux
cd ~/rikkaST
git add ...
git commit -m "..."
git push origin main

# Windows
cd /d D:\rikkaST
git pull origin main
gradlew.bat :app:assembleDebug -x :app:installDebugPythonRequirements
```

优点：

- 不传构建产物和缓存
- 不碰 Windows 的 `local.properties`
- 有版本历史，出错可回退

缺点：

- 需要先 commit / push
- 网络不稳定时麻烦

#### 方式 B：rsync 直传 Windows 构建副本

适合临时改、快速验证、还不想 commit。

```bash
rsync -a --delete \
  --exclude '.git/' \
  --exclude 'build/' \
  --exclude '*/build/' \
  --exclude '.gradle/' \
  --exclude '.kotlin/' \
  --exclude 'local.properties' \
  --exclude 'dist/' \
  --exclude 'web-ui/node_modules/' \
  ~/rikkaST/ /mnt/d/rikkaST/
```

注意：

- `--delete` 会删除 Windows 侧多余文件；第一次建议先不加 `--delete` 做 dry-run。
- 不要同步 `local.properties`、`build/`、`.gradle/`、`.kotlin/`、`dist/`。
- WSL 写 `/mnt/d` 比 ext4 慢，但只传源码可以接受。
- `web/src/main/resources/static/` 是前端预构建产物，是否同步要单独确认。

#### 方式 C：直接编辑 `/mnt/d/rikkaST`

不推荐。  
`/mnt/d` 在 WSL 下是 9p 文件系统，`git status`、`find`、Gradle 相关元数据操作都会明显变慢，且可能遇到大小写/换行符问题。

#### 方式 D：在 Linux/WSL 内直接编译 APK

也可以做，但需要另外准备：

- Linux 侧 Android SDK / NDK
- Linux 侧 JDK
- Chaquopy Python 3.12
- `local.properties` 指向 Linux SDK 路径
- Gradle 代理/镜像

如果目标是“少踩环境坑”，当前阶段仍建议 Windows 编译。

### 4.4 Windows 编译与安装命令

```bat
cd /d D:\rikkaST

REM Debug
gradlew.bat :app:assembleDebug -x :app:installDebugPythonRequirements

REM Release
gradlew.bat :app:assembleRelease -x :app:installDebugPythonRequirements

REM 安装到手机
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Release APK 路径通常为：

```text
app\build\outputs\apk\release\app-release.apk
```

如果手机通过 Wi-Fi adb 连接，先：

```bat
adb connect <手机IP>:<端口>
adb devices
```

### 4.5 建议的日常节奏

1. Linux 里改源码、查 `~/rikkaST-refs` 里的 ST/插件源码对照。
2. 改动小：rsync 到 `/mnt/d/rikkaST`；改动大：commit + push。
3. Windows 侧 `git pull` 或接收 rsync。
4. Windows 执行 `gradlew.bat :app:assembleDebug`。
5. Windows 执行 `adb install -r`。
6. 手机实测，问题回 Linux 定位。

---

## 5. 需要讨论的点

1. **主副本放哪边？**
   - 建议：Linux `~/rikkaST` 作为唯一权威源码；Windows `D:\rikkaST` 只作为构建/装机副本。
2. **同步机制选哪种？**
   - 建议：正式改动走 Git；临时快速验证走 rsync。
3. **是否需要在 Linux 侧也跑单测？**
   - 如果要，需要配置 Linux SDK / Python；否则单测可以统一放 Windows 构建前跑。
4. **Release 签名怎么管理？**
   - 签名密钥和 `local.properties` 目前只在 Windows 侧；不要提交到仓库。
5. **是否需要从 Linux 直接调 Windows 构建？**
   - 可以后续尝试用 `cmd.exe` / `powershell.exe` 从 WSL 调 `gradlew.bat`，但路径、权限和输出解析会多一层复杂度，不是第一优先级。
6. **前端 `web-ui` 要不要在 Linux 重建？**
   - 当前仓库有预构建静态资源；如果改 React 前端，需要确认 `web/src/main/resources/static` 的同步策略。

---

## 6. 常用参考文档

| 文档 | 用途 |
|---|---|
| `README.md` | 项目公开介绍 |
| `BUILDING.md` | 构建说明 |
| `DIVERGENCE.md` | 与上游 RikkaHub 的差异地图、合并手册 |
| `docs/ST-COMPATIBILITY.md` | 插件接入层兼容报告 |
| `docs/ST-FEATURE-MATRIX.md` | 官方功能逐项实现矩阵 |
| `docs/PRIVACY-NETWORK.md` | 隐私与网络说明 |
| `~/MIGRATION-NOTES.md` | D 盘到 WSL2 的迁移记录 |
| `~/rikkaST-refs/README.md` | 参考仓库索引 |

---

## 7. 风险与注意事项

- `local.properties` 含签名口令明文，已忽略，但不要外传整个目录。
- Windows `D:\rikkaST` 里有自己的 `local.properties`、`build/`、`.gradle/`；rsync 时不要覆盖。
- 不要直接把 `~/rikkaST` 整个目录 rsync 到 Windows，构建缓存和 node_modules 会拖慢同步。
- WSL 和 Windows 之间注意 CRLF/LF 与大小写敏感差异。
- 完整 SillyTavern 源码只用于参考和契约对照，注意 AGPL / 其他许可证边界。
- 当前进度以 `docs/ST-COMPATIBILITY.md` 和 `docs/ST-FEATURE-MATRIX.md` 为准；本 README 只做工作区和流程汇总。

---

## 8. 当前结论

- 仓库已公开。
- 主线：`~/rikkaST`，`main` / `a94f236` / `v2.4.6`。
- 完整酒馆源码：`~/rikkaST-refs/Z-st118-full/SillyTavern-1.18.0` 和 `~/rikkaST-refs/B-sillytavern/SillyTavern__SillyTavern`。
- 目标：继续完善 rikkaST 的 SillyTavern 兼容，范围排除 TTS 和生图。
- 工作方式：Linux 开发，Windows 编译 APK，Windows adb 安装到手机。
- 下一步先确定同步机制：Git 为主还是 rsync 为主。
