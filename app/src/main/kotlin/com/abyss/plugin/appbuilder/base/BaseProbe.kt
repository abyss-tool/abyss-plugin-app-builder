package com.abyss.plugin.appbuilder.base

/**
 * 底座可用性探测协议（N-APP-002 FR-002：只读、无副作用、5s 超时）。
 *
 * 探测分层：
 *  - **环境级探测（M1 可执行）**：SELinux enforce 状态 + 已有 tracer 检查
 *    （/proc/self/status 的 TracerPid）+ 已知受限 ROM 判定 —— 全部只读，
 *    由命令编排层构造 shell 指令，执行器（Shizuku shell / ProcessBuilder）
 *    由 Android 侧注入 —— 指令编排纯 JVM 可单测（对齐 core:shell 纪律）。
 *  - **ptrace 实测（v0.2）**：带 `ptrace(PTRACE_TRACEME)` 的最小探测
 *    binary 随底座资产（~4MB Alpine）分发；本协议的
 *    [ProbeExecutor] 签名与超时语义先行定死，v0.2 只换实现不换契约。
 */
object BaseProbe {

    /** 探测超时（规格硬约束：5 秒）。 */
    const val PROBE_TIMEOUT_SECONDS: Int = 5

    /** 已知 ptrace 受限 ROM 清单（N-APP-002 FR-005 / ARCHITECTURE §1.2）。 */
    val KNOWN_RESTRICTED_ROMS: List<KnownRom> = listOf(
        KnownRom(
            manufacturerPrefixes = listOf("oppo", "oneplus", "realme"),
            romName = "ColorOS 15/16",
            behavior = "运行约 1 分钟后安全警告并杀进程（ptrace 被安全策略限制）",
            workaround = "Shizuku shell 上下文可缓解但不保证 —— ColorOS 16 的「权限监控」也可能拦截 Shizuku 本身（临时切英语系统 → 开发者选项 Disable system optimization → 重启 Shizuku；可能影响 LTPO 刷新率/续航，需权衡）",
        ),
        KnownRom(
            manufacturerPrefixes = listOf("honhor", "honor"),
            romName = "荣耀 MagicOS",
            behavior = "内核拒绝（严格 SELinux 策略）",
            workaround = "无已知缓解路径 —— 底座不可用",
        ),
    )

    data class KnownRom(
        val manufacturerPrefixes: List<String>,
        val romName: String,
        val behavior: String,
        val workaround: String,
    )

    /**
     * 环境级探测指令组（只读；执行顺序即返回文本顺序）。
     *  - `getenforce`：SELinux 状态（Enforcing / Permissive / Disabled）
     *  - `cat /proc/self/status` 中的 TracerPid（已有 tracer 即异常环境）
     * 执行上下文由 [ProbeExecutor] 决定（Shizuku shell 优先）。
     */
    val PROBE_COMMANDS: List<String> = listOf(
        "getenforce",
        "grep TracerPid /proc/self/status",
    )

    /**
     * 探测指令在给定执行上下文下的构造（Shizuku shell 上下文 = 推荐）。
     * 纯字符串编排 —— 执行与超时由 [ProbeExecutor] 实现（Android 侧注入）。
     */
    fun buildProbeScript(viaShizukuShell: Boolean): String {
        val body = PROBE_COMMANDS.joinToString("\n") { "echo \"--- $it\"; $it 2>&1" }
        return if (viaShizukuShell) {
            // shell 上下文（uid 2000）：直接执行；超时语义由网关看门狗承担
            body
        } else {
            // App 沙箱：getenforce 通常不可执行（权限），降级为 TracerPid 自查
            "grep TracerPid /proc/self/status"
        }
    }

    /** 探测执行器接口（Android 侧注入：Shizuku 网关 / ProcessBuilder）。 */
    fun interface ProbeExecutor {
        /** 执行脚本返回输出；超时 / 失败抛异常。5s 看门狗语义在实现层保证。 */
        fun exec(script: String, timeoutSeconds: Int): String
    }

    /**
     * 解析探测输出 → [DegradeChain.ProbeOutcome]（纯函数，单测全覆盖）。
     * 解析纪律：任何输出缺失 / 语义不明 → DENIED（fail-closed，绝不猜测）。
     */
    fun parseOutcome(output: String, manufacturer: String): DegradeChain.ProbeOutcome {
        // ① 已知受限 ROM 直接判定（清单权威，先于通用探测）
        if (restrictedRomOf(manufacturer) != null) return DegradeChain.ProbeOutcome.DENIED
        // ② SELinux Enforcing + shell 上下文 = MagicOS 型拒绝的常见形态
        val enforcing = output.lineOf("getenforce") == "Enforcing"
        val tracerPid = output.lineOf("TracerPid")?.substringAfterLast(':')?.trim()
        return when {
            output.isBlank() -> DegradeChain.ProbeOutcome.DENIED
            enforcing && manufacturerBlank(manufacturer) -> DegradeChain.ProbeOutcome.DENIED
            tracerPid == null || tracerPid == "0" -> DegradeChain.ProbeOutcome.OK
            else -> DegradeChain.ProbeOutcome.DENIED
        }
    }

    /** 已知受限 ROM 匹配（manufacturer 小写前缀比对）。 */
    fun restrictedRomOf(manufacturer: String): KnownRom? {
        val m = manufacturer.trim().lowercase()
        if (m.isEmpty()) return null
        return KNOWN_RESTRICTED_ROMS.firstOrNull { rom ->
            rom.manufacturerPrefixes.any { m.startsWith(it) }
        }
    }

    /** 受限 ROM 的人话清单（UNAVAILABLE 档 UI 展示）。 */
    fun restrictedRomSummary(manufacturer: String): String? =
        restrictedRomOf(manufacturer)?.let { "${it.romName}：${it.behavior}" }

    private fun manufacturerBlank(manufacturer: String): Boolean = manufacturer.isBlank()

    private fun String.lineOf(key: String): String? =
        lineGroups().firstOrNull { it.first.contains(key, ignoreCase = true) }?.second

    /** 探测输出解析：`--- cmd` 分节 → (节名, 首行值)。 */
    private fun String.lineGroups(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var currentKey = ""
        for (raw in split("\n")) {
            val line = raw.trim()
            if (line.startsWith("--- ")) {
                currentKey = line.removePrefix("--- ").trim()
            } else if (line.isNotEmpty() && currentKey.isNotEmpty()) {
                out += currentKey to line
                currentKey = ""
            }
        }
        return out
    }
}
