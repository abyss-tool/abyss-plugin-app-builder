import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// :precheck —— 静态能力预检（ARC-103 §5 / APP-101 §6：只分析不拦截；
// 例外：路径一对必然失败特征硬性标注）。
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
