package com.abyss.plugin.appbuilder.base

/**
 * 构建底座三级降级链（N-APP-002）—— 纯 JVM 状态机，零 Android 依赖可单测。
 *
 * 决策表（规格 FR-003 ~ FR-005，`docs/ARCHITECTURE.md` §1.2 同源）：
 *  1. Shizuku 运行中且已授权 → [BaseLevel.READY]（shell 上下文 uid 2000
 *     拉起 PRoot，推荐档 —— 厂商针对「普通应用 ptrace」的策略不直接作用于
 *     这一层）；
 *  2. 环境探测通过但 Shizuku 不可用 → [BaseLevel.DEGRADED]（App 沙箱内
 *     纯 PRoot；部分设备可行，构建前仍需再次实测）；
 *  3. 环境探测不通过 / 已知受限 ROM → [BaseLevel.UNAVAILABLE]（人话告知
 *     系统限制与已知 ROM 清单 —— 绝不让用户在注定失败的环境里反复尝试）；
 *  4. Shizuku 运行中但本插件未授权 → [BaseLevel.NEEDS_AUTH]（展示授权
 *     引导而非崩溃 —— N-APP-003 FR-001；宿主授权不能继承，per-app 授权）。
 *
 * 诚实边界（N-APP-002 §5）：Shizuku 是 uid 2000 的 shell 上下文不是 root，
 * 不提供越权能力；ColorOS 15/16 ptrace 约 1 分钟被杀、MagicOS 内核拒绝
 * 属平台限制而非代码缺陷 —— 降级链不给「绕过厂商安全策略」的暗示。
 */
object DegradeChain {

    /** 底座档位（N-APP-002 FR-006：持久化并展示在插件首页）。 */
    enum class BaseLevel {
        /** 未初始化（首启 / 探测未执行）。 */
        NOT_INITIALIZED,
        /** Shizuku shell 上下文可用（推荐档）。 */
        READY,
        /** Shizuku 运行中但本插件未获独立授权 —— 需引导。 */
        NEEDS_AUTH,
        /** App 沙箱纯 PRoot（Shizuku 不可用时的降级档）。 */
        DEGRADED,
        /** 环境探测不通过 —— 内置构建环境不可用（不进入构建）。 */
        UNAVAILABLE,
    }

    /** 环境探测结果（探测协议见 [BaseProbe]）。 */
    enum class ProbeOutcome { OK, DENIED, TIMEOUT, NOT_RUN }

    /** 探测输入快照（Shizuku 状态 + 环境探测）。 */
    data class Environment(
        val shizukuRunning: Boolean,
        val shizukuAuthorized: Boolean,
        val probe: ProbeOutcome,
    ) {
        companion object {
            val INITIAL = Environment(
                shizukuRunning = false,
                shizukuAuthorized = false,
                probe = ProbeOutcome.NOT_RUN,
            )
        }
    }

    fun resolve(env: Environment): BaseLevel = when {
        env.probe == ProbeOutcome.DENIED || env.probe == ProbeOutcome.TIMEOUT -> BaseLevel.UNAVAILABLE
        !env.shizukuRunning && env.probe == ProbeOutcome.OK -> BaseLevel.DEGRADED
        env.shizukuRunning && !env.shizukuAuthorized -> BaseLevel.NEEDS_AUTH
        env.shizukuRunning && env.shizukuAuthorized -> BaseLevel.READY
        else -> BaseLevel.NOT_INITIALIZED
    }

    /** 档位对应的人话说明（首页展示，诚实边界写进 UI）。 */
    fun humanMessage(level: BaseLevel): String = when (level) {
        BaseLevel.NOT_INITIALIZED -> "底座未初始化：点击「探测环境」开始"
        BaseLevel.READY -> "就绪（Shizuku shell 上下文 uid 2000，推荐档）—— 底座随 v0.2 底座资产拉起"
        BaseLevel.NEEDS_AUTH ->
            "Shizuku 已运行，但本插件尚未获得独立授权。Shizuku 授权严格 per-app：宿主「深渊工具」的授权不能继承给本插件，请在本插件内单独授权一次"
        BaseLevel.DEGRADED ->
            "降级（App 沙箱纯 PRoot）：Shizuku 未运行。沙箱模式受厂商 ptrace 策略影响，ColorOS / MagicOS 等受限 ROM 可能不可用；建议启动 Shizuku（无线调试或 ADB）获得更稳的 shell 上下文"
        BaseLevel.UNAVAILABLE ->
            "内置构建环境不可用：当前设备系统限制（ptrace 被平台策略否决）。已知受限 ROM：ColorOS 15/16（OPPO/OnePlus/Realme，约 1 分钟后被杀）、荣耀 MagicOS（内核拒绝）—— 属系统限制而非本插件缺陷，构建不会启动"
    }
}
