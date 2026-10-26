# rikkaST — Bringing SillyTavern to Android

["简体中文"](README.md) | ["English"](README_EN.md)

> Latest version: v2.5.10-public-20261026

rikkaST is a customized native Android LLM chat client based on RikkaHub, with a built-in SillyTavern compatibility layer. Its goal is to bring SillyTavern's character cards, World Info, presets, prompt processing, and third-party extension ecosystem to Android as naturally as possible.

["Download Latest APK"](https://github.com/yufanx79-gif/rikkaST/releases/tag/v2.5.10-public-20261026) · ["All Releases"](https://github.com/yufanx79-gif/rikkaST/releases) · ["Changelog"](RELEASE_NOTES_2.5.10.md)

---

## A Note from the Developer

This is a personal open-source project under continuous development with the assistance of AI. I am still learning, fixing issues, and filling in missing features. The project has benefited greatly from the code, documentation, and design references provided by excellent open-source projects such as RikkaHub, SillyTavern, and Kelivo.

My goal is not simply to give a chat application a different interface. I want to bring as much of the SillyTavern experience as possible to Android: importing character cards, managing World Info and prompts, running scripts, and gradually supporting SillyTavern's third-party extensions.

However, rikkaST is not a complete implementation of SillyTavern, and not every SillyTavern extension is guaranteed to work. Native features, compatibility implementations, and known gaps will be documented separately wherever possible.

If you encounter a problem, feel free to open an Issue or suggest an improvement. I will continue iterating on features that are incomplete.

## Table of Contents

- [Project Overview](#project-overview)
- [Feature Overview](#feature-overview)
- [SillyTavern Compatibility](#sillytavern-compatibility)
- [Installation and Updates](#installation-and-updates)
- [Building the Project](#building-the-project)
- [Relationship with Upstream Projects](#relationship-with-upstream-projects)
- [Privacy and Networking](#privacy-and-networking)
- [Known Limitations](#known-limitations)
- [Acknowledgments](#acknowledgments)
- [License](#license)

---

## Project Overview

SillyTavern's character cards, World Info, presets, macros, regex scripts, and third-party extensions form an interconnected ecosystem. However, this ecosystem is primarily designed around a web environment. On mobile devices, browser performance, interface interactions, and extension compatibility can all become obstacles.

rikkaST builds on RikkaHub's native Android application and progressively implements SillyTavern-compatible data formats, prompt-processing logic, and an extension runtime.

The project follows two approaches:

- **Native implementation:** Features suited to native implementation, such as character cards, World Info, macros, preset management, prompt injection, and slash commands, are integrated directly into the Android application.
- **WebView compatibility runtime:** A compatibility environment provides selected SillyTavern JavaScript extensions with the frontend APIs, modules, DOM containers, and event mechanisms they require. The aim is to reuse existing extensions where possible instead of rewriting each one individually.

The goal is to continuously improve compatibility with the SillyTavern ecosystem while retaining RikkaHub's existing model integrations, chat functionality, tools, and knowledge base.

Related technical documentation:

- [SillyTavern Compatibility Report](docs/ST-COMPATIBILITY.md)
- [SillyTavern Feature Matrix](docs/ST-FEATURE-MATRIX.md)
- [Code Differences from Upstream](UPSTREAM-COMPATIBILITY.md)
- [Build Instructions](BUILDING.md)
- [Privacy and Networking](docs/PRIVACY-NETWORK.md)

## Feature Overview

### 1. Character Cards: Import, Edit, and Export

rikkaST includes dedicated compatibility handling for SillyTavern character cards.

- Supports the primary data structures used by SillyTavern character cards, including fields from V2/V3 cards such as description, personality, scenario, first message, example dialogue, creator's notes, alternate greetings, and depth prompts.
- Attempts to preserve extension fields and original data to reduce information loss during import and export.
- Supports processing related data embedded in character cards, including World Info, regex scripts, and scripts.
- Provides a native visual editing interface.
- Supports character card export in PNG and JSON formats.
- Supports `.charx` character cards and embedded resources, attempting to convert resource references such as `embeded://`, `embedded://`, and `ccdefault:` into locally accessible resource addresses.

Complex character cards may still encounter compatibility issues involving individual fields or special resources. Keep the original files for important character cards.

### 2. World Info: Keywords, Recursive Scanning, and Injection

World Info is an important component of character settings and narrative memory in SillyTavern. rikkaST provides independent World Info management and prompt-injection capabilities.

- Supports primary keywords, secondary keywords, and multiple matching logic options.
- Supports whole-word matching, regular-expression matching, and case-sensitive settings.
- Supports constant entries, trigger probabilities, delayed activation, sticky entries, cooldowns, and group logic.
- Supports recursive scanning, scan-depth settings, and token budgets.
- Supports controlling entry insertion positions, ordering, and related generation behavior.
- Supports managing and synchronizing external and embedded World Info.
- Supports exporting World Info as SillyTavern World Info JSON.

The World Info engine aims to follow SillyTavern's relevant semantics, but it should not be considered an exact reproduction of every edge case in the official implementation.

### 3. Separate Preset Management and Prompt Injection

rikkaST provides separate entry points for preset management and prompt injection, reducing the maintenance burden caused by mixing different configuration responsibilities.

- Preset management handles preset configurations and related imported content.
- Prompt injection manages injection settings associated with the generation context.
- Supports importing SillyTavern preset files and related backup data.
- Parses and applies supported SillyTavern preset parameters.
- Processes stop sequences, sampling parameters, forced reply prefixes, and selected message post-processing strategies according to configuration.

Whether a specific parameter takes effect also depends on the model provider, generation API, and compatibility of the corresponding parameter.

### 4. Macros and Prompt Templates

rikkaST includes macro-processing logic for handling dynamic content before messages are sent.

- Supports reading, writing, and modifying conversation variables.
- Supports conditional expressions, comparison operators, and nested conditions.
- Supports random selection, dice, time, and selected conversation-state-related macros.
- Supports template processing for relevant content in prompts, character cards, and World Info.
- Supports EJS-style template rendering to provide partial compatibility with ST-Prompt-Template workflows.
- Attempts to preserve the original text of unrecognized macros to avoid damaging user content.

### 5. Slash Commands and STscript

rikkaST provides a native slash-command interface and implements part of SillyTavern STscript syntax and execution logic.

Supported areas include:

- Conversation control and message operations.
- Character card, persona, and variable-related operations.
- Message branches, Swipe operations, and selected checkpoint operations.
- Pipelines, parameter passing, and selected script-control commands.
- Native dialog interactions corresponding to commands such as `/popup`, `/input`, and `/buttons`.
- Quick Reply automatic execution and related paths for extensions to invoke slash commands.

STscript remains an evolving compatibility implementation. Not all official commands, parameters, or execution details are supported.

### 6. Persona, Author's Note, and Group Chats

- **Persona:** Supports persona management, character binding, and multiple injection positions.
- **Author's Note:** Supports settings such as injection interval, depth, character association, and enable/disable controls.
- **Group chats:** Supports conversations involving multiple characters, with character-selection strategies and automatic turn-taking.

These modules use SillyTavern's relevant usage semantics as a reference. Their behavior may differ from the official implementation.

### 7. Regex, Quick Reply, and Script Management

- **Regex scripts:** Supports multiple scopes and processing positions for input, output, and related prompt-processing workflows.
- **Quick Reply:** Supports quick-reply sets, command execution, and selected automatic-trigger mechanisms.
- **Script source management:** Supports managing scripts from sources such as character cards, global scripts, and presets.
- **Automatic triggers:** Integrates selected lifecycle events and related workflows, including startup, conversation switching, user message submission, AI response completion, and associated interactions.

The actual behavior of each trigger depends on whether the corresponding event has been integrated and which APIs the script uses.

### 8. Third-Party Extension Runtime

rikkaST includes a WebView runtime that attempts to provide a compatibility environment for SillyTavern third-party JavaScript extensions.

Current work includes:

- Installing third-party extensions from ZIP files or supported HTTP(S) URLs.
- Loading extension scripts, dependencies, and related resources.
- Providing compatibility implementations for selected SillyTavern core modules.
- Providing compatibility entry points for frontend APIs such as `SillyTavern.getContext()`, `eventSource`, and `TavernHelper`.
- Providing selected DOM containers required by extension settings interfaces.
- Connecting selected SillyTavern events to native generation, message, and World Info processing workflows.
- Providing chat-generation interfaces for selected extensions so they can invoke the application's generation workflow.
- Localizing selected third-party JavaScript dependencies where possible to improve repeated loading and usability in network-restricted environments.

Currently targeted extensions and capabilities:

| Extension or Module | Current Focus |
| --- | --- |
| Tavern Helper (JS-Slash-Runner) | Scripts, buttons, variables, World Info, slash commands, and selected generation interfaces |
| ST-Prompt-Template | EJS template rendering, PromptManager, and related variable semantics |
| MVU (MagVarUpdate) | Variable-update parsing, runtime support, and related panel functionality |
| Other third-party extensions | Progressive adaptation based on their API, event, and DOM dependencies |

This list indicates areas with targeted compatibility work. It does not mean every feature has been fully tested across all versions of each extension.

### 9. Native Tools, Skills, and Knowledge Base

rikkaST also retains and extends RikkaHub's native capabilities.

- **Models and chat:** Multiple model providers, streaming output, conversation branches, message editing, regeneration, and more.
- **Tool calling:** Selected tools such as file operations, Shell, calculator, database queries, Python, and web scraping.
- **Skills:** Supports skill loading, management, and related installation workflows.
- **Workspace:** Provides a working directory and terminal capabilities for Agents.
- **Knowledge base (RAG):** Supports importing documents, folders, text, and chat histories, combining full-text and vector retrieval to provide contextual information.
- **Native multimedia:** Retains the application's own image generation, TTS, and ASR capabilities. Availability depends on the relevant configuration and services.

Note that the application's native image-generation and voice features do not use the same interfaces as SillyTavern's official Image Generation and TTS extensions. Their availability does not imply that the corresponding SillyTavern extensions will work.

### 10. WebView Panels and Stability Improvements

rikkaST continues to improve WebView resource loading and rendering for HTML panels, status bars, and interactive content embedded in SillyTavern character cards.

- Adds MIME-type mappings for common images, fonts, audio, and video resources.
- Improves compatibility for loading selected external images.
- Improves the detection and mounting logic for selected HTML/CSS panels.
- Reduces full-page reloads and flickering during selected streaming updates and variable refreshes.
- Persists runtime logs to help diagnose extension loading and execution problems.
- Improves background-generation process retention and exception handling.

Different panels have different structures and dependencies. Complex third-party pages may still require targeted compatibility work.

---

## SillyTavern Compatibility

rikkaST aims to support SillyTavern workflows as closely as possible, but clear technical limitations remain.

| Area | Current Status | Description |
| --- | --- | --- |
| Character cards and basic data | Under continuous development | Supports primary fields and related import, editing, and export workflows |
| World Info and prompt processing | Under continuous development | Many core behaviors have been implemented, but some edge cases still require verification |
| Macros, regex, and STscript | Partial compatibility | Multiple common capabilities are implemented, but not all official behaviors are covered |
| Third-party extension runtime | Partial compatibility | Provides a runtime environment and targeted adaptations, but extensions have widely varying dependencies |
| SillyTavern server APIs | Limited coverage | Currently integrates only selected chat-generation-related endpoints |
| Official SillyTavern built-in extensions | Incomplete | Some required server APIs and runtime modules have not yet been implemented |

### Known Compatibility Gaps

- SillyTavern server APIs are not fully implemented. Some extension requests to `/api/*` endpoints will fail.
- Some official events have been integrated, but not every defined event is emitted at the correct time.
- Third-party extensions that depend on unimplemented events, server APIs, or official built-in extensions may not work correctly.
- Some extensions depend on complex iframe or DOM structures, network proxies, or specific runtime environments and still require individual adaptation.
- Successful extension installation does not mean every feature of that extension is functional.

For implementation details, known gaps, and corresponding code evidence, see:

- [ST-COMPATIBILITY.md: Compatibility Report](docs/ST-COMPATIBILITY.md)
- [ST-FEATURE-MATRIX.md: Feature Matrix](docs/ST-FEATURE-MATRIX.md)

I would rather clearly distinguish what has been implemented from what remains incomplete than conceal limitations behind claims of "full compatibility."

---

## Installation and Updates

### Download

Visit [GitHub Releases](https://github.com/yufanx79-gif/rikkaST/releases) to download the latest published version.

Current release: **v2.5.10-public-20261026**

- Package: `rikkaST-2.5.10-public-20261026.apk`
- SHA-256: `01573d4f07e12a8aef02bcc6413f0292f5829f4b87d87b9ddb24f9afc7e6f0cc`
- Prebuilt architecture: `arm64-v8a`
- Database: Room v33 (upgrades from v29 / v30 / v31 / v32)
- Upgrade: install over the existing app; do not uninstall or clear app data.
- APK signature: This project has changed signing keys in the past, so older versions may not support direct in-place upgrades.

### Update Notes

If installation reports a signature mismatch, the old and current versions may have been signed with different keys. Uninstalling the old version and installing the new one may resolve the issue, but uninstalling can result in the loss of local data. Make sure your chats, configurations, and other important data are backed up before proceeding.

After a new version is released, use the version and APK provided on the GitHub Releases page.

---

## Building the Project

For detailed build procedures, environment requirements, and configuration instructions, see [BUILDING.md](BUILDING.md).

Basic build commands:

```bash
# Build the Release APK
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest
```

The build requires a compatible JDK, Android SDK, and other environment components appropriate for the project configuration. Some features also involve the NDK, Chaquopy, and local configuration.

Refer to the build documentation for Release signing and related local configuration. Do not commit signing keys, tokens, or personal configuration files to a public repository.

---

## Relationship with Upstream Projects

rikkaST is a derivative project that modifies and extends existing open-source projects.

- [RikkaHub](https://github.com/rikkahub/rikkahub): Provides the foundation and related functionality for the native Android chat client.
- [SillyTavern](https://github.com/SillyTavern/SillyTavern): Serves as a reference for SillyTavern compatibility targets, data formats, and extension contracts.
- [Kelivo](https://github.com/Chevey339/kelivo): Provides references for selected UI and interaction designs.

This project retains substantial upstream functionality while adding a SillyTavern compatibility layer and other native implementations. For details about modified files, newly added files, and upstream migration records, see [UPSTREAM-COMPATIBILITY.md](UPSTREAM-COMPATIBILITY.md).

The project will continue maintaining its differences from upstream and evaluating future updates according to its needs.

---

## Privacy and Networking

The project's privacy and networking behavior should be understood through its code and related documentation.

- The project states that it does not integrate analytics or crash-reporting SDKs in the Analytics / Crashlytics category.
- Model requests are sent to the corresponding services according to the provider and configuration selected by the user.
- Third-party extensions, skills, web scraping, and network features explicitly invoked by users may access external services.
- For details about network requests, permissions, and triggering conditions associated with different features, see [Privacy and Networking](docs/PRIVACY-NETWORK.md).

When using third-party model services or extensions, also review the privacy policies and data-handling practices of the relevant services.

---

## Known Limitations

- SillyTavern compatibility is still under development. Some character cards, macros, scripts, and third-party extensions may have compatibility issues.
- Not all SillyTavern server APIs have been implemented. Extensions that depend on unavailable endpoints may not work correctly.
- Official SillyTavern image-generation and TTS extensions are not guaranteed to work. The application's native image-generation and speech-synthesis features use a separate implementation.
- Prebuilt APKs currently support only the `arm64-v8a` architecture.
- Signing keys may differ between versions. Back up local data before upgrading from an older version.
- The project is under continuous development. Implemented features, compatibility levels, and known issues may change between releases.

If you encounter a problem, please provide reproduction steps, the application version, and relevant logs. For extension compatibility issues, including the extension name and version, relevant settings, and error logs will make diagnosis easier.

---

## Acknowledgments

Thanks to the following projects and their contributors:

- [RikkaHub](https://github.com/rikkahub/rikkahub): The upstream project for the native Android LLM chat client.
- [SillyTavern](https://github.com/SillyTavern/SillyTavern): A reference for SillyTavern data formats, feature semantics, and the extension ecosystem.
- [Kelivo](https://github.com/Chevey339/kelivo): A reference for UI and interaction design.
- All third-party library and tool developers and maintainers whose work is used by this project.

For third-party components and related license information, see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

---

## License

This project is released under the GNU Affero General Public License v3.0 (AGPL-3.0). See [LICENSE](LICENSE) for details.

This project modifies and extends upstream open-source code. When distributing binaries or modified versions, comply with the applicable license requirements and include the necessary license files and third-party notices.

### Trademarks and Project Relationship

"SillyTavern" and the names and trademarks of other projects belong to their respective owners. rikkaST is an independent compatibility implementation. It is not an official product of SillyTavern, RikkaHub, or any other related project, and it does not imply endorsement by those projects.

---

If rikkaST is useful to you, feel free to use it, report issues, suggest improvements, or give the repository a ⭐.

The project is still evolving. Thank you to everyone who tries it, points out problems, and helps make it better.
