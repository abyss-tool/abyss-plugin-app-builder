package com.abyss.plugin.appbuilder

/**
 * HOST_API v2 宿主入口（N-APP-001 FR-002 · 瘦客户端形态）。
 *
 * 宿主经 PackageManager meta-data 发现本类（v0.2 AIDL 桥接入前宿主不直接
 * 实例化，本类承担自启动入口与能力自述）。**v0.2.0 起底座已迁出**
 * （ADR-100 D-3 / ARC-103）：Linux 运行环境由 abyss-plugin-linux 提供
 * （经宿主 PluginBroker 中介拿受限会话代理），本插件零引擎、零授权、
 * 零资产管线 —— 能力切片边界诚实声明（ARC-101 FR-001：零引擎字符串常量）。
 */
class AppBuilderPlugin {

    val id: String = "abyss-plugin-app-builder"

    /** 宿主 API 需求（与 plugin.json minHost 一致，CI 门禁校验）。 */
    val minHostApi: Int = PluginContract.HOST_API_VERSION

    /** 运行环境依赖声明（与 plugin.json depends 一致；宿主侧依赖解析消费）。 */
    val dependsOn: List<String> = listOf(PluginContract.CAPABILITY_LINUX_RUNTIME_V1)

    /**
     * 宿主详情页能力自述（诚实边界：瘦客户端只交付编排，运行环境依赖
     * Linux 插件 —— M2 起逐路径落地）。
     */
    fun describeCapabilities(): List<String> = listOf(
        "依赖握手与会话编排（APP-100：四类失败引导 + SessionGrant 消费）",
        "静态能力预检与三选一交互（Anpyra / aapt2+固定壳 / Gradle）",
        "构建产物签名、校验与安装闭环（APP-102）",
        "Linux 运行环境（双执行域）由 abyss-plugin-linux 经宿主中介提供 —— 本插件零底座实现",
    )
}
