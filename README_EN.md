<div align="center">

<img src="docs/icon.png" alt="rikkaST Icon" width="128" height="128" />

# rikkaST

**Native Android AI Chat Client · SillyTavern 1.19.0 Compatible**

*Side-by-side install · Native Tavern cards/lorebooks/presets · Third-party extensions & dual-engine companion server · Long-chat HTML panel pool*

<p>
<a href="README.md">简体中文</a> ·
<b>English</b> ·
<a href="README_JA.md">日本語</a>
</p>

<p>
<a href="https://github.com/yufanx79-gif/rikkaST/releases"><img src="https://img.shields.io/github/v/release/yufanx79-gif/rikkaST?style=flat-square&label=Release&color=111111" alt="Release" /></a>
<a href="https://github.com/yufanx79-gif/rikkaST/stargazers"><img src="https://img.shields.io/github/stars/yufanx79-gif/rikkaST?style=flat-square&color=f59e0b" alt="Stars" /></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-2563eb?style=flat-square" alt="License" /></a>
<img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84%3Fstyle%3Dflat-square" alt="Android" />
<img src="https://img.shields.io/badge/ABI-arm64--v8a-555555?style=flat-square" alt="ABI" />
</p>

<p>
<a href="https://github.com/yufanx79-gif/rikkaST/releases"><b>Download Latest APK</b></a> ·
<a href="https://github.com/yufanx79-gif/rikkaST/issues">Report an Issue</a> ·
<a href="THIRD-PARTY-NOTICES.md">Third-Party Notices</a> ·
<a href="docs/PRIVACY-NETWORK.md">Privacy &amp; Network</a>
</p>

</div>

**rikkaST** is an independent open-source fork of [RikkaHub](https://github.com/rikkahub/rikkahub), built natively with Kotlin and Jetpack Compose.

* **Looking for a solid Android AI client?** Plug in an API key and start chatting. Multi-provider protocols, streaming, voice mode, web search, proactive memory, local knowledge base, MCP / Skills, built-in Python 3.12, and a PRoot Linux workspace all stay intact, while Tavern features stay out of your way until you need them.

* **Want a native SillyTavern experience on Android?** Character cards, World Info / Lorebooks, Chat Completion presets & SPreset, regex scripts, macros, slash commands, interactive HTML status panels, and third-party extensions run directly inside the app — without juggling Termux web tabs or background process kills.

> [!TIP]
> rikkaST uses an independent package ID (`me.rerere.rikkahub.st`), so it installs alongside official RikkaHub without overwriting your data. You can directly import RikkaHub backups (excluding SSH remote workspaces) as well as SillyTavern backup ZIP archives.

<!-- Recommended: add 3–4 device screenshots here (Chat & HTML Panel / Character Card & Lorebook / Extensions / Themes) -->

## ✨ Feature Highlights

| Area | What's Built In | 
| ----- | ----- | 
| 🍺 **SillyTavern 1.19.0 Compatibility** | Pins 205 upstream SillyTavern 1.19.0 public modules; end-to-end support for character cards, lorebooks, presets, regex, macros, slash commands, and Quick Replies | 
| 🧩 **Third-Party Extension Host** | Install extensions from local ZIP or Git URL; serves `/scripts/extensions/third-party/` over same-origin HTTPS with bundled MVU and offline frontend libraries | 
| ⚙️ **Dual-Engine Companion Server** | Zero-config in-process JVM companion server on `127.0.0.1:18181` out of the box; hands off seamlessly to a PRoot Node.js v20.18.1 daemon when installed | 
| 🖼️ **Long-Chat HTML Panel Pool** | 5-tier static panel classification, priority-based LRU WebView pooling, bitmap snapshot freezing with tap-to-hydrate, nested scroll coordination, and crash recovery | 
| 🤖 **Multi-Model & Multi-Protocol** | Native OpenAI (Chat Completions & Responses API), Anthropic Claude, Google Gemini (Vertex AI & Interactions API), xAI Grok, ChatGPT login, and multi-key rotation | 
| 🧠 **Memory & Knowledge Base** | File-based memory, proactive memory reminders (customizable prompt), scheduled consolidation, and local document parsing (PDF / DOCX / XLSX) | 
| 🛠️ **Agent Tools & Local Compute** | MCP & Skills support, embedded Python 3.12 (Chaquopy), PRoot Linux workspace with PTY terminal, JS sandbox, and 19+ web search providers | 
| 🔊 **Voice, Group Chat & Media** | Multi-character group chat scheduling, character expressions/sprites, Tavern audio bridge, voice mode (multi-provider ASR / TTS), and image/video generation | 
| 🖥️ **Multi-Device & Sync** | Built-in LAN Web UI with NSD discovery, WebDAV & S3 (AWS SigV4) cloud sync, plus Chatbox and CherryStudio import | 
| 🛡️ **Privacy & Test Coverage** | Firebase / Crashlytics stripped, SPreset `LogUpload` telemetry blocked by design, backed by **175 unit test files** covering core regressions | 

## 🚀 Quick Start

1. Download the latest `arm64-v8a` APK from [Releases](https://github.com/yufanx79-gif/rikkaST/releases) (Android 8.0 / API 26+).

2. Open **Settings → Providers** to add your model API and pick a default model.

3. Bring over your existing data:

   * **Character Cards**: One-tap import for `.png`, `.json`, `.charx`, and `.byaf` (extension-first detection to avoid Android file manager MIME mismatches).

   * **SillyTavern Archives**: Import a SillyTavern data folder or backup ZIP; automatically detects `characters/`, `worlds/`, `OpenAI Settings/`, `QuickReplies/`, and global regex in `settings.json`.

   * **RikkaHub & Other Clients**: Restore RikkaHub backup archives directly (except SSH remote workspaces), or import configs from Chatbox / CherryStudio.

## 🍺 SillyTavern Compatibility (Aligned to 1.19.0)

rikkaST embeds a dedicated `st-runtime` aligned with SillyTavern 1.19.0 semantics rather than flattening cards into plain system prompts.

| Asset / Subsystem | Verified Implementation | 
| ----- | ----- | 
| **Character Cards** | Import and edit `.png` / `.json` / `.charx` / `.byaf` cards while preserving character fields, embedded lorebooks, and extension payloads | 
| **World Info / Lorebook** | Constant & keyword triggers, recursive scanning, Sticky / Cooldown / Delay state tracking, decorators (`@@activate`, `@@dont_activate`, etc.), Outlet injection, and local semantic vector triggers | 
| **Presets & SPreset** | Chat Completion presets and SPreset keys (`ChatSquash`, `RegexBinding`, `MacroNest`, etc.) with safe QuickJS fallback; explicitly skips `LogUpload` third-party debug uploads | 
| **Macros & Regex Scripts** | Dedicated macro engine (variables, random, time, state, nested macros); multi-source regex engine (global / card / preset) with deduplication and staged execution | 
| **Slash Commands & Quick Replies** | Full slash command parser and executor with closures and variable scopes, plus Quick Replies import and execution | 
| **Context & Immersion** | Author's Note, Instruct Mode, Persona, group chat speaker scheduling, local TikToken counting (`cl100k_base` / `o200k_base`), character expressions/sprites, and Tavern audio bridge | 

<details>
<summary><b>📖 Architecture Details: Third-Party Extensions, Dual-Engine Companion Server &amp; HTML Panel Pool</b></summary>

### Third-Party Extension Host

* **Native Asset Endpoints**: Install extensions from local ZIP archives (enforcing 64 MB archive size, 256 MB extraction caps against zip bombs, and path-traversal checks) or Git URLs. The WebView asset interceptor serves `/scripts/extensions/third-party/<folder>/` over same-origin HTTPS.

* **Bundled Offline Libraries**: Ships an MVU bundle alongside locally intercepted copies of EJS, KaTeX, Mermaid, jQuery, Lodash, Showdown, DOMPurify, Zod, and YAML to avoid blank panels on flaky mobile networks.

### Dual-Engine Companion Server (`127.0.0.1:18181`)

For extensions that expect a server plugin backend (such as `littlewhitebox-server`), rikkaST runs a two-stage local daemon:

1. **Zero-Config Built-In JVM Companion (`BuiltinNodeCompanionServer`)**: Starts in-process with zero extra downloads, handling `/health`, per-assistant/per-conversation isolated host context, `/api/backends/chat-completions/{status,generate}`, and plugin routes.

2. **PRoot Linux + Node.js v20.18.1 Daemon (`NodeRuntimeManager`)**: Once you opt in to install the shared Linux rootfs and Node.js runtime, it seamlessly takes over `127.0.0.1:18181` running `server-bootstrap.cjs` with an Express-compatible router for server plugins.

Both modes bind strictly to `127.0.0.1`, enforce random per-session token auth (Bearer / X-CSRF-Token), cap request body sizes, and redact API keys and tokens from logs.

### Long-Chat HTML / JS Panel Engine (`MessageHtmlBlock.kt`)

Interactive status bars and rich HTML cards can easily lag or crash mobile WebViews across long chats. rikkaST manages panel lifecycles explicitly:

* **5-Class Automatic Classification**: Statically classifies panels into `STATIC`, `ANIMATED`, `LIGHT`, `SEND`, or `BIG` to match lifecycle cost to actual interactivity.

* **Priority-Based Active Pool**: Keeps concurrent live panel WebViews strictly bounded (default 2–3 active, configurable up to a hard cap of 16) with priority-aware LRU eviction.

* **Snapshot Freezing & Tap-to-Hydrate**: Captures snapshots for older floors and releases their WebViews, remounting on tap when needed.

* **Nested Scroll & Crash Guard**: Coordinates inner JS scroll zones with the outer Compose list, falls back to software layers for tall panels to avoid GPU texture overflow, and auto-rebuilds crashed renderer processes (capped at 5 retries).

</details>

## 🤖 Native AI Client Capabilities

* **Multi-Provider & Protocols**: OpenAI (Chat Completions & Responses API), Anthropic Claude, Google Gemini (Vertex AI & Interactions API), xAI Grok, and ChatGPT account login; multi-key rotation and quota checks.

* **Memory & Knowledge Base**: File-backed memory management, proactive memory reminders with customizable prompts, scheduled background consolidation, and local RAG parsing for PDF (MuPDF / PDFBox), DOCX, and XLSX.

* **Agent Toolbox & Local Runtimes**:

  * MCP (Model Context Protocol) and Skills system.

  * Embedded **Python 3.12** via Chaquopy (pre-installed with `requests`, `beautifulsoup4`, `pypdf`, `openpyxl`, `markdownify`, `tabulate`, etc.).

  * **PRoot Linux workspace & PTY terminal**, plus local file tools, JavaScript sandbox, calculator, calendar, clipboard, and chart rendering.

  * 19+ web search and fetch engines (Bing, Brave, Tavily, Exa, Perplexity, Firecrawl, Jina, SearXNG, Serper, Bocha, Metaso, Zhipu, and more).

* **Voice, Media & Multi-Device**:

  * Voice mode supporting multiple ASR (OpenAI Realtime, DashScope, Volcengine, Step, MiMo) and TTS (ElevenLabs, Fish Audio, OpenAI, Gemini, MiniMax, Qwen, Volcengine, Groq, xAI, System TTS, etc.) providers.

  * Conversation branching, context checkpoints, message queue, and image/video generation.

  * Built-in **LAN Web UI** (Ktor + NSD discovery), plus **WebDAV** and **S3 (AWS SigV4)** cloud sync.

* **Themes & Localization**: Built-in themes (Minimal, Claude, Ocean, Sakura, Spring, Autumn, Black), custom JSON themes, frosted message bubbles, and 7 UI languages (Simplified Chinese, Traditional Chinese, English, Japanese, Korean, Russian, Arabic).

## 🛡️ Privacy & Engineering Quality

* **No Built-In Telemetry**: Firebase and Crashlytics plugins are removed from the build configuration.

* **Defensive Defaults**: SPreset `LogUpload` uploads are intentionally skipped; extension ZIPs enforce size and path-traversal limits; companion server binds to `127.0.0.1` only with log secret redaction.

* **175 Unit Test Files**: Regression suites covering real-world character card & preset imports, World Info decorators and vector fallback chains, macro/regex/slash contracts, event bus & host bridge wiring, HTML panel pool priorities, Compose performance guards, database migrations, and backup path healing.

## 🛠️ Building from Source

> [!NOTE]
> Requires **JDK 21**, **Android SDK** (`compileSdk 37` / `minorApiLevel 2`, `minSdk 26`), and **Python 3.12** (for Chaquopy packaging). Current toolchain: Gradle 9.6.0, AGP 9.4.0, Kotlin 2.4.21.

<details>
<summary><b>📦 Module Layout &amp; Tech Stack</b></summary>

| Module / Path | Responsibility | 
| ----- | ----- | 
| `:app` | Main app, Jetpack Compose UI, Room / DataStore persistence, Tavern compatibility pipeline (`data/st/`, `data/ai/transformers/`), HTML panel pool, and LAN Web UI | 
| `app/src/main/assets/st-runtime` | SillyTavern 1.19.0 runtime, shims, MVU bundle, offline vendor libraries, and `node-host` bootstrap daemon | 
| `:ai` | Multi-provider abstractions, SSE stream decoders, Agent loop, key rotation, and quota runner | 
| `:workspace` | PRoot Linux rootfs installer & patcher, native C++ PTY terminal, and shared Node.js runtime manager | 
| `:search` / `:speech` / `:document` | Web search engines, ASR/TTS controllers, and local PDF/Office parsers | 
| `:mediagen` / `:videogen` / `:oauth` | Image/video generation and OAuth credential flows | 
| `:highlight` / `:material3` / `:ui` / `:common` / `:web` | Syntax highlighting, Material 3 components, shared utilities, and Web UI assets | 

Core stack: Kotlin · Jetpack Compose · Material 3 Expressive · Navigation 3 · Koin · Room · DataStore · OkHttp · Ktor · Coil 3 · Chaquopy · QuickJS

</details>

```
git clone https://github.com/yufanx79-gif/rikkaST.git
cd rikkaST
cp local.properties.example local.properties
# Set sdk.dir and chaquopy.python in local.properties (add release signing keys locally if needed; never commit them)

# Run unit tests
./gradlew :app:testDebugUnitTest --console=plain

# Build Debug APK
./gradlew :app:assembleDebug --console=plain

# Build Release APK (requires signing config)
./gradlew :app:assembleRelease --console=plain

```

On Windows, replace `./gradlew` with `gradlew.bat`.

## 🙏 Credits & License

| Project | Relationship | License | 
| ----- | ----- | ----- | 
| [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) | Upstream project providing the native Android multi-LLM client foundation and UI | AGPL-3.0 | 
| [SillyTavern/SillyTavern](https://github.com/SillyTavern/SillyTavern) | Compatibility target (aligned to 1.19.0 core semantics and public modules) | AGPL-3.0 | 
| MVU / Chaquopy / PRoot / Termux & others | Runtime, sandbox, and toolchain components — see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) | See notices | 

Licensed under the [**GNU Affero General Public License v3.0 (AGPL-3.0)**](LICENSE).

## ⭐ Star History

If rikkaST is useful to you, consider giving it a Star ⭐

<a href="https://www.star-history.com/#yufanx79-gif/rikkaST&Date">
<picture>
<source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date&theme=dark" />
<source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date" />
<img alt="Star History Chart" src="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date" />
</picture>
</a>
