plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.abyss.plugin.builder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abyss.plugin.builder"
        minSdk = 26
        // targetSdk 钉 28：构建底座（PRoot/ptrace）依赖 W^X 宽松执行环境，
        // 与主壳同款红线（ADR-003）—— 升 target 属 breaking，另册评估。
        targetSdk = 28
        // 版本单一事实源 = 仓库根 plugin.json "version"（CI 门禁校验一致）；
        // 构建期由 gradle 从 plugin.json 读取，避免第三处手写漂移。
        val manifestJson = file("../plugin.json").readText()
        val version = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
            .find(manifestJson)?.groupValues[1] ?: "0.1.0"
        versionName = version
        versionCode = 1
    }

    signingConfigs {
        create("release") {
            // N-APP-001 FR-004：独立 keystore 经 secrets 注入（与宿主签名
            // 分离；宿主安装走 SHA-256 + 发布者白名单校验）。材料缺失时
            // 降级为 unsigned 产物 —— 编译门禁照常，发布由 Release 门禁兜底。
            val storeFile = System.getenv("ABYSS_PLUGIN_KEYSTORE_FILE")
            if (storeFile != null) {
                this.storeFile = file(storeFile)
                storePassword = System.getenv("ABYSS_PLUGIN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ABYSS_PLUGIN_KEY_ALIAS")
                keyPassword = System.getenv("ABYSS_PLUGIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("ABYSS_PLUGIN_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // M1 切片零外部依赖：契约以镜像副本内置（唯一事实源 = 主仓 plugin-sdk，
    // v0.2 AIDL 桥接入后改经发布工件）；UI 为最小 Compose 免依赖薄壳。
    testImplementation(kotlin("test"))
}
