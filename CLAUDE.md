# Repository Guidelines

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
```

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.

---

# 开发铁律（用户明确要求，必须遵守）

> 以下四条来自用户 2026-11-19 夜间的明确指示，**优先级高于本文档其它任何内容**。
> 违反任一条 = 返工。

## 铁律 1：优先复用现有项目，不要自己造轮子

用户原话：
> 日后的开发我会一直开着 VPN，所以尽量去搜索别人已有的项目，或者对我们有帮助的项目。
> 我们可以考虑用一下别人项目里的代码，尽量不要自己造轮子。

**执行要求**：
- 用户长期开 VPN，**网络可用**，遇到问题时先搜 GitHub / 官方文档 / 社区实现
- 实现某个能力前，先确认是否已有成熟开源方案（尤其是 SlashCommandParser、面板渲染、
  WebView 池化、Markdown/HTML 渲染这类复杂子系统）
- 复用时要注明来源与许可证
- 「自己写一个」必须是**搜索并评估过替代方案之后**的结论，不能是默认动作

## 铁律 2（强化）：先全面了解原版怎么跑，搞清楚再思考怎么适配

# 先全面了解某个东西在原版上是怎么运行的，搞清楚再思考怎么适配。

用户 2026-11-19 夜间追加的措辞，**比下面的原始表述更严格**：

- 不是「大致看一下」，而是**全面了解**
- 不是「了解完就能动手」，而是了解完**先思考怎么适配**，再动手
- 顺序固定：**全面了解原版 → 搞清楚它怎么跑 → 思考适配方案 → 才允许动代码**

**已发生的违反案例（必须引以为戒）**：
在 `ChatSlashHost.executeExtendedCommand` 里加了 12 条斜杠命令分支，
但**从未查清用户在输入框敲 `/xxx` 时实际走哪条链路**。
结果用户实测 5 条命令**全部没反应**，连设计好的「命令不可用」报错都没出现
→ 证明改的位置压根不是用户触发的那条路。

### 原始表述

用户原话：
> 在开始某一个层面的适配或者是兼容的时候，先要搞清楚它在原版里是怎么跑的，
> 代码里是怎么运行的。把原版代码的链路各个细节整理清楚之后，再进行适配和兼容，
> 还有对齐。不允许在缺少探勘的情况下，就开始适配、兼容和对齐。
> 写完之后，也要再让它去跑一下，看它能不能真的跑出和原版一样的效果和逻辑

**执行要求**：
- 动手前必须产出**上游 vs 本项目链路对照表**（关键函数、调用顺序、数据结构）
- 上游基准目录：`C:\Users\13239\Desktop\rikkaST-工作区\11-SillyTavern-1.19.0-底座\SillyTavern-1.19.0`
- 写完后必须**实际运行**验证效果与上游一致，不能只做静态断言
- **禁止**在没有链路对照表的情况下改兼容层代码

## 铁律 3：确认真的启用 + 测试工具必须先验证有效

用户原话：
> 写完之后要确认它是真的在启用，真的运用在项目里了，而不是只写了没有启用。
> 另外一个问题，就是做测试的时候不要盲目地直接去写，先确认这个测试的脚本或者是工具
> 可以帮助到我们，再去写这个东西因为我们这次已经踩了很多坑了，你写了很多脚本，
> 但是很多都没有真正帮到我们，反而是帮了我们很多倒忙，所以这是一个问题。
> 多去思考吧 等确认了再去做多想想有没有方案。
> 发现你并没有去真正思考方案，只是想了一些常规的方案。
> 有没有什么更适配我们这个项目的方案，你有没有去想。

**执行要求（启用验证）**：
- 「写了」≠「启用了」。必须验证运行时**实际到达**了该代码路径
- 反例：`StSlashSilentNoOpGuardTest` 断言「源码里有 when 分支」全绿，
  但用户实测 5 条斜杠命令**全部没反应** —— 结构断言无法证明运行时可达
- 正确做法：加运行时埋点（`TavernRuntimeManager.appendLog`）确认命中，
  或用真实 UI 走一遍操作路径

**执行要求（测试工具）**：
- 写任何脚本/测试前先回答：**它能测出什么？能抓住我要防的 bug 类型吗？有没有现成工具？**
- 已知「帮倒忙」案例：
  - `slash_coverage.mjs`：全文件 grep 字面量 → 报「缺 0」假绿（真实 12 条命令无行为）
  - `_api_runtime_probe.mjs` 早期版：手写 `realCtx` → 掩盖 `SlashCommandParser.commands` 是空对象
  - `StEventBusContractTest` 早期版：`contains()` 命中注释里的旧代码 → 假失败
- **规矩**：「必须不存在」的正则断言一律**行锚定并排除注释行**（此坑已踩 3 次）

**执行要求（方案思考）**：
- 至少给出 2 个方案，说明**为什么选这个**，且必须**针对本项目约束**
- 不要只给「常规方案」（如「调大上限」「加个缓存」），要先问「这个项目的真实瓶颈在哪」
- 涉及性能/架构改动时，先确认**有没有量化手段**（本项目目前无 benchmark 模块，
  baselineprofile 只覆盖启动 —— 缺少滚动/导航的量化工具，需先解决）

## 铁律 4：没有明确许可，不得编译 APK

用户原话：
> 没有我的明确要求，不能去编译 APK；只有我说"现在可以编译 APK 了"，才可以编译

**执行要求**：
- **默认动作是「不编译」**
- 只有用户说出「现在可以编译 APK 了」这类明确指令时才编译
- 编译前自检：代码已全部提交 / 全量测试通过 / 用户明确同意
- 原因：每次测试后返工代价极高，编译要给「做完了」的版本，不是中间态
