package com.abyss.plugin.appbuilder

/**
 * HOST_API v1 契约镜像副本。
 *
 * 唯一事实源 = 主仓 `Abyss-Tools/plugin-sdk/plugin-api/.../PluginContract.kt`；
 * 本副本供独立 APK 插件编译（契约以镜像方式随主仓 spec 演进，变更必须
 * 回主仓修改后同步 —— SDK-001 ABI 纪律：HOST_API_VERSION 只增不减）。
 */
object PluginContract {
    const val HOST_API_VERSION: Int = 1
    const val MANIFEST_FILE: String = "plugin.json"
    const val PLUGINS_DIR: String = "plugins"
    const val PATCHES_INDEX: String = "patches/index.json"
    val ID_PATTERN: Regex = Regex("^[a-z0-9]+(-[a-z0-9]+)+$")
    const val META_HOST_ENTRY: String = "com.abyss.tools.plugin.HOST_ENTRY"
    const val META_UI_ENTRIES: String = "com.abyss.tools.plugin.UI_ENTRIES"
}
