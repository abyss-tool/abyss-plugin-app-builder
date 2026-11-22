package com.abyss.plugin.appbuilder

/**
 * HOST_API v2 契约镜像副本（自 main 仓 plugin-sdk 同步 —— 2026-11-22）。
 *
 * 唯一事实源 = 主仓 `Abyss-Tools/plugin-sdk/plugin-api/.../PluginContract.kt`；
 * 本副本供独立 APK 插件编译（契约以镜像方式随主仓 spec 演进，变更必须
 * 回主仓修改后同步 —— SDK-001 ABI 纪律：HOST_API_VERSION 只增不减）。
 *
 * v2 增量（ADR-100 D-5）：新增依赖解析与能力绑定（PluginBroker）；
 * minHost=2 插件在 v1 宿主 fail-closed。本插件经 `depends` 声明依赖
 * abyss-plugin-linux 的 linux-runtime-v1 能力，永不直连。
 */
object PluginContract {
    const val HOST_API_VERSION: Int = 2
    const val MANIFEST_FILE: String = "plugin.json"
    const val PLUGINS_DIR: String = "plugins"
    const val PATCHES_INDEX: String = "patches/index.json"
    val ID_PATTERN: Regex = Regex("^[a-z0-9]+(-[a-z0-9]+)+$")
    const val META_HOST_ENTRY: String = "com.abyss.tools.plugin.HOST_ENTRY"
    const val META_UI_ENTRIES: String = "com.abyss.tools.plugin.UI_ENTRIES"

    // ── v2 增量：能力发现与中介动作串 ─────────────────────────────────
    const val META_CAPABILITIES: String = "com.abyss.tools.plugin.CAPABILITIES"
    const val ACTION_PLUGIN: String = "com.abyss.tools.plugin.action.PLUGIN"
    const val ACTION_LINUX_RUNTIME: String = "com.abyss.tools.plugin.action.LINUX_RUNTIME"

    /** 当前组织内唯一注册能力 id（abyss-plugin-linux 提供）。 */
    const val CAPABILITY_LINUX_RUNTIME_V1: String = "linux-runtime-v1"

    /** 跨插件令牌有效期上限（ARC-102 FR-005；能力描述可收紧不可放宽）。 */
    const val GRANT_TTL_MAX_SEC: Int = 300
}
