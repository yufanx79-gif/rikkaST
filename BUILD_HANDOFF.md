# Windows 本机编译与验收交接指南（BUILD_HANDOFF）

> **当前源码包状态**：已完成静态审查，待本机编译验收。

## 1. 本机环境前置要求
- **操作系统**：Windows 10 / 11（64-bit）
- **JDK 版本**：JDK 17 或 JDK 21（项目 `app/build.gradle.kts` 配置 `jvmTarget = "17"`，`compileOptions` 为 `VERSION_17`，推荐使用 Android Studio 自带的 JBR 21 或 Eclipse Temurin JDK 17/21）
- **Android SDK 组件**：
  - `compileSdk = 36`（Android SDK Platform 36）
  - `minSdk = 26`，`targetSdk = 36`
  - Android SDK Build-Tools `36.0.0`（或兼容版本）
- **`local.properties` 配置**：
  解压 `rikkaST-2.5.6-ai-reviewed-source.zip` 后，进入 `rikkaST/` 目录，在根目录下创建 `local.properties`（切勿提交到 Git）：
  ```properties
  sdk.dir=C:\:\Users\<您的用户名>\AppData\Local\Android\Sdk
  ```

## 2. 签名配置说明（仅使用占位符，请勿提交真实密钥）
- **Debug 构建**：无需配置任何密钥，Gradle 会自动使用本机 `~/.android/debug.keystore`。
- **Release 构建**：`app/build.gradle.kts` 会读取环境变量或 `local.properties` 中的签名配置。若需编译正式签名包，可在环境变量或本机未追踪的 `local.properties` 中填入您自己的签名占位配置（若不配置，Release 构建会自动跳过签名配置）：
  ```properties
  KEYSTORE_FILE_PATH=C:\:\path\to\your_release_key.jks
  KEYSTORE_PASSWORD=<YOUR_KEYSTORE_PASSWORD>
  KEY_ALIAS=<YOUR_KEY_ALIAS>
  KEY_PASSWORD=<YOUR_KEY_PASSWORD>
  ```

## 3. 推荐本机编译与验证命令顺序（PowerShell）

请在解压后的 `rikkaST` 根目录下按顺序执行以下命令：

### 第一步：主源码与单测源码 Kotlin 编译门禁（最快验证全部符号与类型）
```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --stacktrace
```
- **预期结果**：`BUILD SUCCESSFUL`，确认本轮修复的 12 个 P0 阻塞点及新增改动零编译错误。

### 第二步：运行 JVM 单元测试套件
```powershell
.\gradlew.bat :app:testDebugUnitTest --info
```
- **如果只想快速运行本轮核心回归测试类**：
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.st.script.StSlashExecutorTest" --tests "me.rerere.rikkahub.data.export.SillyTavernPresetImportTest" --tests "me.rerere.rikkahub.data.ai.transformers.WorldInfoDecoratorTest" --tests "me.rerere.rikkahub.ui.components.message.MessageHtmlDetectionTest"
  ```
- **测试报告路径**：`appuildeports	ests	estDebugUnitTest\index.html`

### 第三步：组装 Debug APK
```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
```
- **产物路径**：`appuild\outputspk\debug\`（按 ABI 分包：`app-arm64-v8a-debug.apk`、`app-x86_64-debug.apk`、`app-universal-debug.apk`）

### 第四步（可选）：组装 Release APK
```powershell
.\gradlew.bat :app:assembleRelease --stacktrace
```
- **产物路径**：`appuild\outputspkelease\`

## 4. APK 完整性与真机冒烟自检清单
1. **校验证书与 Native 库包含情况**：
   ```powershell
   jar tf appuild\outputspk\debugpp-arm64-v8a-debug.apk | Select-String "lib/.*\.so|assets/st-runtime/runtime\.js"
   ```
   确认 `assets/st-runtime/runtime.js`、`assets/st-runtime/panel.html` 及 `lib/arm64-v8a/` 下的 `.so` 均存在。
2. **真机核心链路冒烟建议**：
   - 导入一张 `.png` 角色卡与一张包含贴图的 `.charx` 角色卡，验证开场白与内嵌图片正常渲染；
   - 导入一份 SillyTavern OpenAI 预设 JSON，启用后发送消息，验证生成正常且角色合并/采样参数无异常；
   - 在聊天框执行 `/echo "Hello" | /popup result=true` 与 `/buttons labels=["选项A","选项B"] 请选择`，验证弹出原生对话框且返回预期值；
   - 测试含状态栏 HTML/CSS 的角色卡，验证流式回复或变量更新时状态栏原地刷新且不闪烁。
