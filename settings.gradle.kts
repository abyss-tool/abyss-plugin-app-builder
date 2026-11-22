// 应用构建器插件（abyss-plugin-app-builder）—— 独立 Android Gradle 工程。
// 工具链与主壳 Abyss-Tools 整组对齐（AGENTS.md 纪律：勿单独升降）：
//   Kotlin 2.3.21 / AGP 8.13.2 / Gradle 8.14 / JDK 17 / compileSdk 36 /
//   minSdk 26 / targetSdk 28（钉 28，构建器 W^X 依赖红线，同 ADR-003）。
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
