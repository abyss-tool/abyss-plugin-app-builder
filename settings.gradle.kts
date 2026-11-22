// 应用构建器插件（abyss-plugin-app-builder）—— 独立 Android Gradle 工程。
// 工具链与主壳 Abyss-Tools 整组对齐（AGENTS.md 纪律：勿单独升降）：
//   Kotlin 2.3.21 / AGP 8.13.2 / Gradle 8.14 / JDK 17 / compileSdk 36 /
//   minSdk 26 / targetSdk 28（钉 28，组织统一红线，同 ADR-003）。
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "abyss-plugin-app-builder"
include(":app")
// ── 瘦客户端模块（ARC-103 §5）────────────────────────────────────────
// :runtime-client  AIDL 客户端 / SessionGrant 消费 / 握手引导 / DeathRecipient 语义
// :precheck        静态能力预检 / 路径推荐 / 硬性预检标注
// :paths / :delivery 三路径编排与产物闭环随 v0.3~v0.6 逐路径落地
include(":runtime-client")
include(":precheck")
