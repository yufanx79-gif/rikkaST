# rikkaST — Native Android SillyTavern 1.19 Compatible Client

[中文文档 (README_ZH_CN.md)](./README_ZH_CN.md) | [Compatibility Matrix](./COMPATIBILITY_MATRIX.md) | [Known Issues](./KNOWN_LIMITATIONS.md)

**rikkaST** (`me.rerere.rikkahub.st`) is a native Android client built on top of **RikkaHub** (migrated to 3.1.0 core architecture) with full **SillyTavern 1.19.0** feature parity.

## Key Highlights
- **1-Tap Character Card Import**: Supports V1 / V2 / CCv3 `.png`, `.json`, `.charx`, and `.byaf` archives with automatic lorebook binding, asset extraction, and `/thumbnail` + `/characters/<id>.png` avatar alias resolution.
- **Accurate HTML Panel & Status Bar Rendering**: Multi-`<style>` container grouping (`findTopLevelHtmlNodes` + `groupTopLevelHtmlNodes`), static HTML card detection (`post-card`), visual-only unmatched `〔`/`〕` template wrapper cleanup, `[出图]...[/出图]` collapsible draw prompt cards, and a per-panel reload button below assistant bubbles.
- **Universal Floating Window & ST-iPhonie Host**: Resident floating dock for `ST-iPhonie`, character card floating stages, `st-memory-enhancement`, and `LittleWhiteBox`, with file chooser (`<input type="file">`), JS dialog (`alert`/`confirm`/`prompt`), `window.open` sub-navigation, and per-chat "Don't show again" state.
- **VPN-Independent Fast WebView Boot**: Local vendor caching with `Cache-Control` + `Content-Length`, explicit `boot_complete` readiness gating, and a 5-second per-extension dynamic import timeout.
- **End-to-End Generation Cancellation**: OkHttp `Call.await()` cancellation propagation, JSR `chatCompletionCancel` job termination, bounded 2.5s `stopGeneration`, and a dedicated Stop button visible even when draft text or attachments are present.
