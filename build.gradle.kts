// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    // rikkaST 不启用官方 Firebase / Crashlytics，无需 google-services.json 即可离线构建
    // alias(libs.plugins.google.services) apply false
    // alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    id("com.chaquo.python") version "17.0.0" apply false
    alias(libs.plugins.spotless)
}

spotless {
    // 对齐 rikkaST 默认主分支 origin/main（替代上游 origin/master），避免无 master 分支时 CI 报错
    ratchetFrom("origin/main")

    val ktlintVersion = libs.versions.ktlint.get()
    val ktlintConfig = mapOf(
        "ktlint_code_style" to "intellij_idea",
        "max_line_length" to 120,
        "ktlint_standard_trailing-comma-on-call-site" to "disabled",
        "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
        "ktlint_standard_function-signature" to "disabled",
        "ktlint_standard_class-signature" to "disabled",
        "ktlint_standard_function-expression-body" to "disabled",
        "ktlint_standard_blank-line-between-when-conditions" to "disabled",
        "ktlint_standard_wrapping" to "disabled",
    )
    kotlin {
        target("**/*.kt")
        targetExclude("build-logic/build/**", "material3/material-color-utilities/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
        suppressLintsFor { step = "ktlint" }
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("build-logic/build/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
        suppressLintsFor { step = "ktlint" }
    }
}
