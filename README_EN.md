# rikkaST — SillyTavern, natively on Android

[**English**](README_EN.md) | [**简体中文**](README.md)

> A customized fork of [RikkaHub](https://github.com/rikkahub/rikkahub) — a native Android LLM chat client —
> with a **built-in SillyTavern compatibility layer**: character cards, lorebooks, macros, slash commands,
> personas, group chats, and a runtime that actually runs real Tavern third-party extensions.
>
> Per-file differences and the upstream merge workflow live in [DIVERGENCE.md](DIVERGENCE.md).
> The full ecosystem-compatibility audit is in [docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md).

### A note before we start

I'm a beginner developer, and this project was built step by step with AI assistance — a lot of it is me learning as I go.
It works mainly because it stands on other people’s shoulders: the code, docs and design of [RikkaHub](https://github.com/rikkahub/rikkahub), [SillyTavern](https://github.com/SillyTavern/SillyTavern) and [Kelivo](https://github.com/Chevey339/kelivo) helped me a lot (see [Credits](#credits)).

So **please keep your expectations modest**: it is not perfect, it certainly has bugs, and plenty is still unfinished (see [Known limitations](#known-limitations)).
If something is wrong, badly written, or you know a better way, please open an issue or just tell me — I'll fix it.


---

## Table of contents

- [A note before we start](#a-note-before-we-start)
- [Why this exists](#why-this-exists)
- [What you get (organised by experience)](#what-you-get-organised-by-experience)
- [Tavern compatibility: the honest version](#tavern-compatibility-the-honest-version)
- [Relationship to upstream RikkaHub](#relationship-to-upstream-rikkahub)
- [Build and install](#build-and-install)
- [Known limitations](#known-limitations)
- [Credits](#credits)
- [License](#license)

---

## Why this exists

SillyTavern is a web app, and its whole ecosystem — cards, lorebooks, presets, regex, extensions — is built around the browser.
I play Tavern on my phone, and the browser experience felt sluggish to me. I was also using [RikkaHub](https://github.com/rikkahub/rikkahub), so I wondered: why not combine the two? That is how this project started.

- **Native semantics.** Cards, lorebooks, macros and presets don't need a browser. They're reimplemented in Kotlin — fast, battery-friendly, lossless, and visually editable.
- **Extensions stay as they are.** Tavern's JS extension ecosystem is far too large to rewrite one by one. So the app ships a WebView runtime that emulates Tavern's front-end API (`SillyTavern.getContext()`, `eventSource`, `TavernHelper`, the `#extensions_settings` DOM…), letting **real Tavern extensions run unmodified**.

My goal: implement the browser-independent parts natively in Kotlin, and for the extension ecosystem, adapt a few popular extensions first, then work through other kinds of extensions over time.

---

## What you get (organised by experience)

> Code size reference (lines per file): card import `AssistantImporter.kt` 902 lines · lorebook engine `PromptInjectionTransformer.kt` 994 · lorebook editor `PromptPage.kt` 2717 · card editor `TavernCharacterCard.kt` 1942 · exporter `CardExporter.kt` 316 · macro engine `MacroEngine.kt` 866 · slash commands `SlashCommands.kt` 630 · group chat `GroupChatPage.kt` 1558 · extension runtime `TavernRuntimeManager.kt` 1178.

### 🎴 Character cards: import, then keep editing

Upstream doesn't parse every field of a Tavern character card, so this fork fills in and completes them.

Here:

- **All 25 top-level fields kept structurally**: example dialogue, alternate greetings, creator notes (incl. multilingual), post-history instructions (PHI), character version, tags, nickname, assets, group-only greetings, creation/modification dates, embedded lorebook, embedded regex, depth prompt (with depth and role).
- **`extensionsRaw` round-trips losslessly** — the raw JSON of extension fields is stored and exported as-is.
- **Official Chat Completion injection structure**: main prompt, standalone character-field messages, `<START>`-split example dialogue turned into real user/assistant turns, PHI appended after history, depth prompts injected at their configured depth and role.
- **PNG / JSON export** (`CardExporter.kt`) using official field names.
- **Visual editor**: all 25 fields + embedded-lorebook management + export, on one screen.
- **Complex-card regression sample**: the test resources include a structurally equivalent synthetic V3 card (47 lorebook entries, 11 regex scripts, 3 Tavern Helper scripts) used for regression tests.

### 📚 Lorebooks: so characters actually *remember*

Lorebook entry handling is aligned rule by rule with official `world-info.js` — **44 fields per entry**:

| Capability | Notes |
|---|---|
| Four secondary-keyword modes | `and_any` / `and_all` / `not_any` / `not_all` |
| Matching | whole-word, regex, case-sensitive |
| Per-entry scan depth | overrides the global default |
| Constant activation | always in context, no keyword needed |
| Cross-book group selection | one winner per group: sticky first → keyword scoring → `group_override` → weighted random |
| Trigger probability | 0–100, toggleable |
| Sticky / cooldown | stay active for N turns / cool down for N turns |
| Delayed activation | only eligible from turn N |
| Recursive scanning | activated content feeds further scans; `exclude` / `prevent` controls; numeric `delay_until_recursion` levels |
| Token budget | global budget % × context length, stops on overflow (optional alert), entries can be exempted |
| Match character fields | persona / description / personality / depth prompt / scenario / creator notes ×6 |
| Display order & generation filter | `display_index` / `display_position` / `triggers` |

The engine is an **official-style `checkWorldInfo` state machine**: INITIAL → RECURSION / MIN_ACTIVATIONS / delay-level loop, with the full budget, overflow, sticky and cooldown lifecycle. Serialization uses official enum names, so entries interoperate with Tavern.

The editor offers a global settings panel (scan depth, budget, minimum activations, max recursion steps, insertion strategy, overflow alert, group scoring), a per-entry editor, drag-to-reorder, and two-way sync between external and embedded lorebooks.

### 🧩 Programmable prompts: Macro Engine 2.0

Macros are handled by a small engine (`MacroEngine.kt`):

- **Variables**: `/setvar` `/getvar` `/incvar` … manage conversation variables; read them with `{{getvar::key}}` or the `.key` shorthand — a card can change how it speaks as the story state changes.
- **Conditionals**: `{{if}} / {{else}} / !`, comparison operators, `&&` / `||`, with nesting.
- **Random & time**: `{{pick::A|B|C}}` (stable within a turn, re-rollable via `/reroll-pick`), `{{roll::1d20}}`, `{{random}}`, `{{time}}`, `{{trim}}`, `{{comment}}`.
- **Conversation-aware**: `{{lastUserMessage}}`, `{{lastCharMessage}}`, `{{idleDuration}}`, `{{charFirstMessage::N}}`, `{{original}}`.
- **EJS template rendering**: `<% %>` templates in prompts, lorebooks and cards are evaluated before sending (ST-Prompt-Template semantics), with `getvar` / `setvar` and friends available in the render context.
- Unknown macros pass through untouched.

### ⌨️ Slash commands: the input box is a console

Type and send; `/help` lists everything with descriptions. Parameterless commands run on tap; parameterized ones fill the input box for completion. **33 built-in commands**:

- **Roleplay**: `/impersonate` (the AI drafts your reply), `/continue`, `/sendas`, `/sys`, `/send`
- **Generation control**: `/trigger` (reply without adding a message), `/sysgen` (AI narration), `/gen`
- **Cards**: `/char-update`, `/char-duplicate`, `/rename-char`
- **Variables & random**: `/listvar` `/setvar` `/getvar` `/addvar` `/incvar` `/decvar` `/flushvar` `/reroll-pick`
- **Messages & branching**: `/hide` `/unhide` `/swipe`, `/checkpoint-create` `/checkpoint-go` `/checkpoint-exit` `/checkpoint-parent` `/checkpoint-get` `/checkpoint-list`, `/branch-create`
- **Misc**: `/persona` (alias of official `/persona-set`), `/js` (run JS in the built-in runtime), `/tavern`

There is also an **STscript executor** (`StSlashParser` + `StSlashExecutor`, aligned with the core of official slash-commands syntax) supporting pipes `|`, named arguments, `/pass` `/return` `/echo` and more — shared by extension `triggerSlash()` calls and quick-reply auto-execution.

### 🎭 Personas · Author's Note · Group chats

These three systems follow official semantics as closely as I could manage:

- **Personas**: the official five injection positions (IN_PROMPT / TOP / BOTTOM / AT_DEPTH / NONE), per-character binding, standalone SYSTEM-message injection, disabled means not injected.
- **Author's Note**: official interval semantics (1 = every user message, N = every Nth), injection depth, injection role, master switch.
- **Group chats**: multiple characters in one conversation, each with its own prompt / persona / model; four speaker-selection strategies (natural / list / weighted random / manual); auto-reply (configurable rounds and delay, interrupted by user messages); live speaker status.

### 🔌 The Tavern extension ecosystem: install real extensions

Right now I've mainly adapted the two extensions below (plus the built-in MVU framework); others are still being adapted over time. The approach is not per-extension patching — it is **a runtime that runs Tavern extensions**:

- **Install**: Extensions hub → Third-party extensions → give it a zip, or an http(s) URL (GitHub / GitLab repos and bare `manifest.json` links all work; it probes `main` / `master`; source-style extensions get their relative imports fetched recursively).
- **Asset endpoint**: extension files are served at their real paths, `/scripts/extensions/third-party/<folder>/<path>`, and **loaded by the WebView exactly like real Tavern** — rather than the runtime interpreting extension code.
- **ST core module emulation**: 36 shims cover the ST internals extensions `import` (`script.js`, `extensions.js`, `world-info.js`, `preset-manager.js`, `PromptManager.js`, `power-user.js`, `slash-commands/*`, `tokenizers.js`, `openai.js`, `group-chats.js`, `reasoning.js`…).
- **Front-end API emulation**: `SillyTavern.getContext()`, `extension_settings`, `extension_prompts`, `eventSource` + `event_types`, and **55 `TavernHelper` API keys** (events / variables / messages / lorebooks / buttons / utilities).
- **DOM emulation**: `#extensions_settings`, `#extensions_settings2`, `#extensionsMenu`, `#send_form`, `#tavern_helper`, plus a hidden `#chat` message-node stub — extension settings panels really mount, and message nodes are really readable/writable.
- **Event bus**: all 82 official event constants are registered, and around 30 have real emit sites (the `GENERATE_*` generation-pipeline events, `CHAT_COMPLETION_PROMPT_READY`, `GENERATION_AFTER_COMMANDS`, the full message lifecycle, the preset family, the world-info family, streaming tokens…). **Registered ≠ emitted**: the remaining ~50 mean extensions that initialise on them silently do nothing.
- **Host endpoints**: Tavern's `/api/backends/chat-completions/{status,generate}` is implemented — which is what lets Tavern Helper's (JS-Slash-Runner) `generate()` / `generateRaw()` actually drive the app's generation pipeline (both SSE and `stream:false`).
- **Offline-friendly**: bare jsDelivr `import`s in card scripts are localised to the bundled `vendor/` — served locally when present, falling back to the real network otherwise.
- **Capability gate**: `/version` returns a deliberately chosen `pkgVersion` to unlock extension feature branches.

**Extensions adapted so far** (just these two, plus the built-in MVU framework):

| Extension | What works |
|---|---|
| **Tavern Helper (JS-Slash-Runner)** | script iframes, script buttons, events, variables, lorebooks, `triggerSlash`, `generate()` / `generateRaw()` end to end |
| **Prompt Template (ST-Prompt-Template)** | EJS rendering, `getCharacterDefine()`, PromptManager, variable scope semantics |

Other extensions are still being adapted; whether one runs mainly depends on whether it needs Tavern's server API — see the full analysis and gap list in **[docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md)**.

### 🧠 MVU variable framework

The MVU (MagVarUpdate, MIT) runtime bundle is built in, usable directly by card scripts; the host parses variable-update blocks out of model output into conversation variables, with a panel UI mounted in the local runtime. The `#tavern_helper` container gets the script-identity nodes MVU's `unique_check` contract expects, avoiding multi-implementation conflicts.

### ⚙️ Regex scripts · Quick replies · Preset import

- **Regex scripts**: four sources (global / scoped / preset / character card); `find_regex`, `replace_string`, placements (user input / AI output / slash command / world info / reasoning), depth, macro substitution. Ships an `extensions/regex/engine.js` compatibility layer.
- **Quick replies**: data model aligned with Tavern QR, with "execute on click" — slash-prefixed entries run through STscript, plain text is sent directly.
- **Preset & world-info import**: reads Tavern preset files and backup archives directly (`StSettingsImport` / `StArchiveImporter`), extracting embedded Tavern Helper scripts from presets.
- **Three-source card scripts**: character-card scripts (`extensions.tavern_helper.scripts`) + global scripts + preset scripts, each independently toggleable.

### 🛠 Skills & tools

**Skills**: on top of loading a skill when the model calls `use_skill`, this fork adds keyword-based **automatic triggering**, a public skills directory `/Rikkahub/skills` (drop files in via a file manager), GitHub one-click install, whole-repo batch download, update detection (repo source + directory hash), install-source tracking, and a skill registry.

**Tools**: alongside time, clipboard, calendar, JavaScript, screen time, TTS, ask-user, memory, search, skills and workspace, this fork adds file operations, shell, task tools, calculator, database query, a Python engine, and web scraping — plus a **Python / JS dual bridge** (read/write conversations, assistant settings, group chats; run Python / JS engines) and a system-prompt assembler. 17 local-tool options in total.

**Workspace**: a sandboxed directory with a terminal, where the agent can run commands and edit files.

### 🧠 Knowledge base: give your character long-term memory (RAG)

Chat isn't the only input — you can turn a folder, a stack of documents, or even past conversations into a character's long-term memory:

- **Import**: files / whole folders / chat history / plain-text notes; managed as "knowledge sources" you can bind to a specific assistant or share globally. PDF, EPUB, DOCX and PPTX are parsed by the built-in `document` module.
- **Two-level chunking**: parent chunks of 1024 tokens and child chunks of 256 tokens (with overlap). Only child chunks are embedded; a hit is expanded back to its parent — so the model gets fuller context, not one orphan sentence.
- **Hybrid retrieval**: SQLite **FTS5** full-text search and **vector search** run in parallel, then merge via **RRF (Reciprocal Rank Fusion)**; the query side also generates multiple rewrites to improve recall.
- **Injection budget**: results are trimmed to a token budget before entering the generation chain, so they never blow up your context.
- **Embeddings**: uses whichever embedding model you configure, with automatic embedding, progress reporting and an LRU vector cache; it also works purely on FTS5 if you don't configure one.

### ⚡ Stability

- **Background keep-alive**: foreground service + async start + 600 ms debounce + failure fallback — switching apps doesn't interrupt generation.
- **Runtime log**: extension-runtime logs persist to `Android/data/<pkg>/files/tavern-runtime.log`, the first place to look when debugging an extension.
- **Trimmed**: removed the GitHub tool, sleep tool and log-debug pages that I don't use (see [DIVERGENCE.md](DIVERGENCE.md) §5).

---

## Tavern compatibility: the honest version

| Layer | Coverage | Notes |
|---|---|---|
| **Data** (cards / lorebooks / presets / regex / variables / chats) | ✅ Essentially complete | Lossless round-trip; import then export loses nothing |
| **Engines** (world-info scan / macros / STscript / regex / MVU parsing / EJS) | ✅ Essentially complete | Aligned with official semantics, unit-tested |
| **Extension runtime** | 🟡 Works, with boundaries | Front-end API + DOM + events + 2 host endpoints; the server API is the big gap |

**Main gaps** (evidence for each in [docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md) §5):

> Two deeper documents:
> [docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md) — how the **extension runtime** works, where its boundaries are, which extensions are adapted;
> [docs/ST-FEATURE-MATRIX.md](docs/ST-FEATURE-MATRIX.md) — a **feature-by-feature comparison** with official Tavern: cards / lorebooks / presets / macros / STscript / regex / variables / media, every row with `path:line`.

1. Only 2 of Tavern's server API endpoints are implemented (`/api/backends/chat-completions/{status,generate}`); everything else 404s.
2. Zero official built-in Tavern extensions implemented (only a `regex/engine.js` shim): vectors / expressions / caption / summarize / tts / sd / gallery / assets are all missing.
3. Of 82 event constants, ~30 have real emit sites; the other ~50 are registered but never fired.
4. Three iframe events (`GENERATION_REQUESTED` and two reasoning-token events) are registered but not wired.
5. No extension store / online index; `manifest.requires` is parsed but not installed.

---

## Relationship to upstream RikkaHub

- **Direct upstream**: `github.com/rikkahub/rikkahub` (RikkaHub), AGPL-3.0.
- **Preserved**: Material You theming, multi-provider support, streaming, conversation forking & regeneration, message edit / delete / translate, full-text search (jieba), favourites, image generation, TTS / ASR, MCP, workspace sandbox, backup (S3 / WebDAV / reminders), web server, chat export — all working as before.
- **Removed**: GitHub tool, sleep tool, log-debug pages (shipped upstream, not used here; see [DIVERGENCE.md](DIVERGENCE.md) §5).
- **Divergence map**: which upstream files were modified, which files are unique to this fork, and how to resolve conflicts — all recorded file by file in [DIVERGENCE.md](DIVERGENCE.md). When upstream updates, I compare and merge the changes by hand.

---

## Build and install

Full instructions in [BUILDING.md](BUILDING.md). Shortest path:

```bash
# Requirements: JDK 17+, Android SDK, NDK (Chaquopy needs it)
# Release signing and Chaquopy need local.properties (not tracked by git)
./gradlew :app:assembleRelease

# Unit tests only
./gradlew :app:testDebugUnitTest
```

Artifacts land in `app/build/outputs/apk/`. Prebuilt APKs are on GitHub Releases.

> After editing `app/src/main/assets/st-runtime/*.js`, run an ES-module syntax check before building.

---

## Known limitations

- **Extension compatibility is not 100%** — see the coverage table above and the compatibility report. The rule of thumb is simple: does the extension touch Tavern's server API?
- **Tavern's Image Generation / TTS extensions don't work yet**: they need the unimplemented `/api/sd` and `/api/tts` endpoints (the app's built-in image generation and TTS are a different path, not the Tavern extension one).
- **Signing**: the key was rotated, so this cannot be installed over earlier builds with the same package name without uninstalling first.
- **Platform**: prebuilt APKs are `arm64-v8a` only.

---

## Credits

- [**RikkaHub**](https://github.com/rikkahub/rikkahub): the upstream project.
- [**SillyTavern**](https://github.com/SillyTavern/SillyTavern): the compatibility target and event-contract reference. This project's Tavern layer is a **compatible implementation** of its data formats and extension contracts.
- [**Kelivo**](https://github.com/Chevey339/kelivo) (Flutter / AGPL-3.0): **UI and interaction design reference**. It's a great project, and this project's visual and interaction direction takes inspiration from it (no Kelivo Dart code was copied).
- Other third-party components, fonts and full license texts: see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

---

## License

Released under the **GNU Affero General Public License v3.0 (AGPL-3.0)** — see [LICENSE](LICENSE).

The AGPL choice isn't arbitrary: upstream [RikkaHub](https://github.com/rikkahub/rikkahub) is AGPL-3.0,
[SillyTavern](https://github.com/SillyTavern/SillyTavern) is AGPL-3.0, and
[MuPDF](https://github.com/ArtifexSoftware/mupdf) (a PDF-parsing dependency) is AGPL-3.0 too — **all four share the same license, so they're naturally compatible**.

> If you distribute binaries of this project, ship `LICENSE` and `THIRD-PARTY-NOTICES.md` alongside them.

### Trademarks

"SillyTavern" is a trademark of its respective owner. This project is *compatible with* its data formats and extension contracts but is **not affiliated with or endorsed by** it — nor by Artifex Software, Khan Academy, OpenAI, Anthropic or Google.

---

If this fork is useful to you, feel free to open an issue about anything that doesn't work well; a ⭐ is also very welcome ✨
