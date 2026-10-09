plugins {
    id("rikkahub.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.workspace"

    defaultConfig {
        // ============================================================
        // [ARM64 开发构建适配 · Operit 移植工程]
        // 官方 NDK/CMake 工具链仅提供 x86_64 主机版本，无法在 ARM64 设备上运行。
        // 开发构建改用官方 APK 提取的预编译原生库（src/main/jniLibs）：
        //   - libtermux.so    : termux_pty.cpp 的编译产物（与官方包内版本一致）
        //   - libworkspace.so : 官方模板空库（保留以对齐官方 APK 结构）
        // 功能与官方发行版完全一致。
        // 如需从源码重新构建原生库，请在 x86_64 主机上恢复以下配置：
        // externalNativeBuild {
        //     cmake {
        //         cppFlags += ""
        //     }
        // }
        // ============================================================
    }
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xz)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
