# 基线环境与测试执行记录 (baseline-test-results)

- **可用工具**：OpenJDK `17.0.20.1`, Node.js `v18.20.4`, npm `9.2.0`, Python `3.11.2`。
- **受限工具**：当前容器无 Android SDK (`ANDROID_HOME` 未设置) 且无外部网络，因此无法执行 Gradle 在线依赖下载与 APK 打包（标记为“当前环境未构建”）。
- **基线测试结果**：`node --test littlewhitebox-server/tests/*.test.js` 共 106 个子测试，103 个通过，3 个因测试脚本自身直接 `require` 开发期包（`esbuild`、`@msgpack/msgpack`）失败，运行时预打包 bundle (`vendor/*.cjs`) 全部通过。
