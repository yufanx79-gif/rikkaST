# rikkaST 发版前核查清单（Release Checklist）

1. **包名与签名一致性**：
   - 确认 `app/build.gradle.kts` 中 `applicationId = "me.rerere.rikkahub.st"`，保证与官方原版 RikkaHub 可共存安装且老用户可平滑覆盖升级。
   - 确认使用同一正式签名密钥签名 APK，禁止混用 Debug 签名发布正式版。
2. **版本号与 `update.json` 对齐**：
   - 确认 `app/build.gradle.kts` 的 `versionCode` / `versionName` 与根目录 `update.json` 中的 `version`、`versionCode`、`packageName` 完全一致。
   - 确认 `update.json` 中 `downloads[].url` 全部为有效的 `https://.../xxx.apk` 直链，并在构建出正式包后填入 64 位十六进制 `sha256`。
   - 运行 `pwsh scripts/release/check-release.ps1` 验证通过。
3. **契约与回归测试门禁**：
   - 运行 `node --test scripts/st119-migration-and-5plugins.test.mjs` 全部通过。
   - 运行 `node tools/st-sync/verify-lock.mjs` 验证上游锁文件完整性。
   - 运行 `./gradlew testDebugUnitTest assembleDebug` 零编译错误、零单测失败。
4. **真机冒烟测试（First-Success Smoke Test）**：
   - 按照 `docs/testing/FIRST-SUCCESS-TEST.md` 执行新装首启向导、2 步配置模型、一键导入 `.png/.charx` 角色卡、状态栏头像渲染、小手机浮窗交互与停止生成验证。
