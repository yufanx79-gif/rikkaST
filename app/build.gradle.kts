import com.android.build.api.dsl.Packaging
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
    id("com.chaquo.python")
}
// Python 引擎配置 — Chaquopy 新 DSL
chaquopy {
    defaultConfig {
        version = "3.12"
        // PC 构建用的 Python 解释器：优先 local.properties 的 chaquopy.python，其次环境变量 CHAQUOPY_PYTHON，
        // 都没有则交给 Chaquopy 用 PATH 上的 python。
        // （原先这里写死了本机绝对路径，已移除以避免泄露个人环境；本机可写入 local.properties：）
        //   chaquopy.python=C:/Users/<you>/AppData/Roaming/uv/python/cpython-3.12.13-windows-x86_64-none/python.exe
        val chaquopyPython = (project.findProperty("chaquopy.python") as String?)
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("CHAQUOPY_PYTHON")?.takeIf { it.isNotBlank() }
        if (chaquopyPython != null) buildPython(chaquopyPython)
        pip {
            install("requests")
            install("beautifulsoup4")
            install("markdown")
            install("pypdf")
            install("openpyxl")
            install("markdownify")
            install("tabulate")
            install("python-dateutil")
            install("pytz")
            install("setuptools")
        }
    }
}
android {
    namespace = "me.rerere.rikkahub"
    compileSdk = 37
    defaultConfig {
        applicationId = "me.rerere.rikkahub.st"  // 独立包名：可与官方 RikkaHub 共存，互不覆盖（debug 变体自动加 .debug 后缀）
        minSdk = 26
        targetSdk = 37
                versionCode = 237 // 2026-10-05: v235 - S3 depth 注入对齐 ST(同深度角色序修复)+S4 charJailbreak 别名; R2 KaTeX 0.16.45 离线(60 字体+mhchem, stMessageFormatting 接入); I3 eventOn per-iframe 隔离行为测试; I4 MVU owner-guard 修复; I5 EJS 边界收口; 单测 530/0; ESM 48/0
        versionName = "2.4.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
    splits {
        abi {
            // AppBundle tasks usually contain "bundle" in their name
            //noinspection WrongGradleMethod
            val isBuildingBundle = gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
            isEnable = !isBuildingBundle
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(FileInputStream(localPropertiesFile))
                val storeFilePath = localProperties.getProperty("storeFile")
                val storePasswordValue = localProperties.getProperty("storePassword")
                val keyAliasValue = localProperties.getProperty("keyAlias")
                val keyPasswordValue = localProperties.getProperty("keyPassword")
                if (storeFilePath != null && storePasswordValue != null &&
                    keyAliasValue != null && keyPasswordValue != null
                ) {
                    storeFile = file(storeFilePath)
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
    androidResources {
        generateLocaleConfig = false
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "lib/*/libtermux.so"
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        compilerOptions.optIn.add("androidx.navigation3.runtime.ExperimentalNavigation3Api")
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        project.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

tasks.register("buildAll") {
    dependsOn("assembleRelease", "bundleRelease")
    description = "Build both APK and AAB"
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.profileinstaller)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation3)
    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)


    // DataStore
    implementation(libs.androidx.datastore.preferences)
    // Image metadata extractor
    // https://github.com/drewnoakes/metadata-extractor
    implementation(libs.metadata.extractor)
    // Haze (background blur)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.material3)

    // koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.diffutils)
    implementation(libs.termux.terminal.view)
    implementation(libs.guava.listenablefuture)
    // jetbrains markdown parser
    implementation(libs.jetbrains.markdown)
    // okhttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.json)
    // ktor client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // ucrop
    implementation(libs.ucrop)
    // pebble (template engine)
    implementation(libs.pebble)

    // coil
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.coil.cache.control)
    // serialization
    implementation(libs.kotlinx.serialization.json)
    // QuickJS (JS 引擎执行; 原由 highlight 模块 api 传递, 上游重写 highlight 后需显式声明)
    implementation(libs.quickjs)
    // zxing
    implementation(libs.zxing.core)
    // 相机扫码三方库已全部移除（Quickie / ML Kit barcode-scanning / CameraX）：
    // ML Kit 的 MlKitInitProvider 会在应用启动时自动初始化，部分设备上初始化失败会崩溃；
    // 相册导入二维码走 zxing（MultiFormatReader），无需这些库。
    // exifinterface（读取图片方向信息；原为 camera-core 的传递依赖，现显式声明）
    implementation(libs.androidx.exifinterface)
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    baselineProfile(project(":app:baselineprofile"))
    ksp(libs.androidx.room.compiler)
    // Paging3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    // Apache Commons Text
    implementation(libs.commons.text)
    // Toast (Sonner)
    implementation(libs.sonner)
    // Reorderable (https://github.com/Calvin-LL/Reorderable/)
    implementation(libs.reorderable)
    // lucide icons
    implementation(libs.lucide.icons)
    implementation(libs.huge.icons)
    // image viewer
    implementation(libs.image.viewer)
    // JLatexMath
    // https://github.com/rikkahub/jlatexmath-android
    implementation(libs.jlatexmath)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)
    // mcp
    implementation(libs.modelcontextprotocol.kotlin.sdk)
    // jmDNS (mDNS/Bonjour for .local hostname)
    implementation(libs.jmdns)
    // SLF4J Android binding — routes Ktor/SLF4J logs to logcat
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)
    // sqlite-android (requery SQLite for Android)
    implementation(libs.sqlite.android)
    // modules
    implementation(project(":ai"))
    implementation(project(":web"))
    implementation(project(":document"))
    implementation(project(":highlight"))
    implementation(project(":search"))
    implementation(project(":speech"))
    implementation(project(":common"))
    implementation(project(":material3"))
    implementation(project(":workspace"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(kotlin("reflect"))
    // Leak Canary
    // debugImplementation(libs.leakcanary.android)
    // tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
