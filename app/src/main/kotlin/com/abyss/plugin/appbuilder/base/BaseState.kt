package com.abyss.plugin.appbuilder.base

/**
 * 底座状态持久化模型（N-APP-002 FR-006：持久化并展示在插件首页）。
 *
 * 单行文本格式（与主仓 RenderSafeMode 同款「三行文本」纪律：一切 IO
 * runCatching 静默兜底，状态文件自身绝不成为新崩溃源）：
 * ```
 * level|probedAtEpochMs|manufacturer
 * ```
 */
object BaseStateStore {

    private const val STATE_FILE = "base_state.txt"

    fun encode(level: DegradeChain.BaseLevel, probedAtEpochMs: Long, manufacturer: String): String =
        "${level.name}|$probedAtEpochMs|${manufacturer.replace("|", "_")}"

    /** 解析失败一律回退 NOT_INITIALIZED（fail-safe，状态文件不可信化）。 */
    fun decode(text: String): Triple<DegradeChain.BaseLevel, Long, String> {
        val parts = text.trim().split("|")
        if (parts.size != 3) return Triple(DegradeChain.BaseLevel.NOT_INITIALIZED, 0L, "")
        val level = runCatching { DegradeChain.BaseLevel.valueOf(parts[0]) }
            .getOrDefault(DegradeChain.BaseLevel.NOT_INITIALIZED)
        val ts = parts[1].toLongOrNull() ?: 0L
        return Triple(level, ts, parts[2])
    }

    /** Android 侧持久化入口（filesDir 状态文件；IO 全兜底）。 */
    fun load(context: android.content.Context): Triple<DegradeChain.BaseLevel, Long, String> =
        runCatching {
            val f = java.io.File(context.filesDir, STATE_FILE)
            decode(if (f.exists()) f.readText() else "")
        }.getOrDefault(Triple(DegradeChain.BaseLevel.NOT_INITIALIZED, 0L, ""))

    fun save(context: android.content.Context, level: DegradeChain.BaseLevel, manufacturer: String) {
        runCatching {
            java.io.File(context.filesDir, STATE_FILE)
                .writeText(encode(level, System.currentTimeMillis(), manufacturer))
        }
    }
}
