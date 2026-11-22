import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// :runtime-client —— AIDL 客户端逻辑（ARC-103 §5：不含任何底座实现）。
// 纯 JVM 面：握手引导 / grant 消费策略 / 会话客户端状态机；
// AIDL stub 绑定与 DeathRecipient Android 侧接线随宿主桥（T-022 后续批）。
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
