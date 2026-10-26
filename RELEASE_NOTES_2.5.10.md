# rikkaST 2.5.10 Public Release

## ⚠️ 升级前必读：请务必备份数据

本次版本涉及数据库兼容性调整及多项功能优化。为避免升级过程中出现闪退、启动失败或其他意外情况，**请务必在升级前完成一次完整的数据备份，并将备份文件保存到手机以外的安全位置。**

- 请优先使用应用内的备份功能，确认备份文件已成功生成并可以找到。
- 请勿在升级前卸载应用或清除应用数据。
- 首次启动时可能需要迁移旧数据，请耐心等待。
- 如果升级后出现闪退或无法启动，请先保留备份文件，不要反复卸载、清除数据或降级安装，以免增加数据恢复难度。
- 新版本的数据库结构与旧版不同。**升级到 2.5.10 后，不要再覆盖安装旧版 2.5.6**，否则可能因为数据库降级导致启动失败。

## 更新内容

### 1. 酒馆助手脚本显示优化

优化酒馆助手（TavernHelper）相关脚本的显示与使用体验，改善部分脚本内容的展示问题。

### 2. 闪退与稳定性优化

针对应用运行过程中出现的闪退及异常退出问题进行了优化，提升整体运行稳定性。

### 3. 上游 UI 与部分功能更新

吸收上游 RikkaHub 的部分 UI 调整与功能更新，改善应用的整体使用体验。

今后，rikkaST 将不再刻意紧跟上游的每一次版本更新，而是优先考虑现有功能的稳定性、兼容性与实际使用体验。对于确实有价值的新功能，会在评估后择优跟进。

### 4. 小手机（ST-iPhonie）出图问题优化

针对小手机相关的出图流程问题进行了修复与优化，改善相关功能的使用体验。

### 5. 大量聊天楼层时的性能与稳定性优化

针对聊天记录楼层不断增加后，助手输出可能出现卡顿、应用异常退出等情况进行了优化，改善长对话场景下的使用体验。

### 6. 小手机 WebView 二级页面交互优化

优化小手机相关 WebView 二级页面的交互体验，改善页面操作及跳转过程中的使用问题。

### 7. 扩展预设解析字段，兼容更多预设

增加可解析的预设字段，扩展对不同预设配置的兼容能力，让更多预设有机会正常导入和使用。

### 8. 数据库迁移与旧版备份兼容性修复

修复旧版本升级过程中可能出现的数据迁移兼容问题，并改善旧版备份的导入与恢复能力。相关修复涵盖旧版设置、供应商、助手、预设及正则脚本等数据。

### 9. 延续此前的兼容性改进

保留此前版本在预设兼容、酒馆助手脚本导入、备份恢复、消息显示，以及 SillyTavern 正则、世界书和运行时兼容方面的改进。

## 版本信息

- 版本号：2.5.10
- Version Code：242
- 最低系统要求：Android 8.0（API 26）
- 支持架构：arm64-v8a

---

# rikkaST 2.5.10 Public Release

## ⚠️ Important: Back Up Your Data Before Updating

This release includes database compatibility changes and several functional improvements. To reduce the risk of data loss or recovery difficulties caused by crashes, startup failures, or other unexpected issues, **please make a complete backup before updating and store a copy somewhere safe outside the app.**

- Use the in-app backup feature and confirm that the backup file has been created successfully.
- Do not uninstall the app or clear its data before updating.
- The first launch may take longer than usual while existing data is migrated.
- If the app crashes or fails to start after updating, keep your backup file safe. Avoid repeatedly uninstalling the app, clearing its data, or installing an older version.
- Version 2.5.10 uses a newer database structure. **Do not install the older 2.5.6 APK over version 2.5.10**, as a database downgrade may prevent the app from starting.

## What's New

### 1. TavernHelper Script Display Improvements

Improved the display and usability of TavernHelper-related scripts, addressing certain script presentation issues.

### 2. Stability and Crash-Related Improvements

Improved application stability and addressed issues related to crashes and unexpected exits.

### 3. Selected Upstream UI and Feature Updates

Adopted selected UI changes and functional updates from upstream RikkaHub to improve the overall user experience.

Going forward, rikkaST will not aim to follow every upstream release immediately. Development will prioritize stability, compatibility, and practical usability. Valuable upstream features may be adopted selectively after evaluation.

### 4. ST-iPhonie Image Generation Improvements

Made fixes and improvements to image-generation workflows related to ST-iPhonie.

### 5. Performance Improvements for Long Conversations

Improved behavior in conversations with a large number of messages, targeting slow assistant responses, lag, and unexpected application exits.

### 6. ST-iPhonie WebView Navigation and Interaction Improvements

Improved interactions within secondary WebView pages related to ST-iPhonie, including page navigation and usability.

### 7. Expanded Preset Parsing and Compatibility

Added support for more preset fields to improve compatibility with a wider range of preset configurations and make more presets usable.

### 8. Database Migration and Legacy Backup Compatibility

Fixed database migration compatibility issues that could occur when upgrading from older versions and improved the import and recovery of legacy backups, including settings, providers, assistants, presets, and regex scripts.

### 9. Previous Compatibility Improvements Retained

Retains earlier improvements for preset compatibility, TavernHelper script imports, backup recovery, message rendering, and SillyTavern regex, World Info, and runtime compatibility.

## Version Information

- Version: 2.5.10
- Version Code: 242
- Minimum Android Version: Android 8.0 (API 26)
- Supported ABI: arm64-v8a
