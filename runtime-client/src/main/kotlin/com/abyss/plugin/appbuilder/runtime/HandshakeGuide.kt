package com.abyss.plugin.appbuilder.runtime

/**
 * 启动握手编排（APP-100 §4 —— 七步握手 + 四类失败引导，纯 JVM 可单测）。
 *
 * 纪律：
 *  - 引导文案人话，禁暴露错误码原文（FR-007）；
 *  - 远端状态走三态（FR-005）：加载中禁假数据；
 *  - 四类引导各配「一键动作」：装 / 升 / 授权 / 等资产；宿主过旧为第五类。
 */
object HandshakeGuide {

    /** 运行时快照（客户端消费视图 —— 镜像 :runtime-api RuntimeInfo 首帧）。 */
    data class RuntimeSnapshot(
        val pluginVersion: String = "",
        val assetsReady: Boolean = false,
        /** NOT_INSTALLED / NOT_RUNNING / NOT_GRANTED / GRANTED / REVOKED / STALE。 */
        val shizukuState: String = "NOT_INSTALLED",
        val probeSummary: String = "",
    )

    /** 握手失败类型（每类一个引导出口）。 */
    enum class FailureKind(val id: String) {
        /** 依赖插件未安装（一键一并安装 / 跳转插件中心）。 */
        DEP_MISSING("dep.missing"),
        /** 依赖插件版本过低（跳转补丁 / Release 升级）。 */
        DEP_VERSION("dep.version"),
        /** 未授权（引导为 Linux 插件单独授权一次 —— 全生态唯一授权动作）。 */
        AUTH_NEEDED("auth.needed"),
        /** 资产未就绪（进入 Linux 插件资产页等待真实进度）。 */
        ASSETS_NOT_READY("assets.not_ready"),
        /** 宿主过旧（升级主壳）。 */
        HOST_TOO_OLD("host.too_old"),
        /** 探测被平台拦截（切域选项 + ROM 清单）。 */
        PROBE_BLOCKED("probe.blocked"),
    }

    /** 引导结果：类型 + 人话 + 一键动作语义。 */
    data class Guidance(val kind: FailureKind, val humanMessage: String, val action: String)

    /**
     * 按 RuntimeInfo 快照分流（APP-100 §4 步 ④）：
     * 全绿 → null（进入项目导入 → 预检 → 三选一 → openSession）。
     */
    fun route(snapshot: RuntimeSnapshot): Guidance? {
        // 授权优先于资产（E2E-04：已装未授权 → 授权引导，授权次数 1）
        if (snapshot.shizukuState in setOf("NOT_INSTALLED", "NOT_RUNNING")) {
            return Guidance(
                FailureKind.AUTH_NEEDED,
                "Shizuku 尚未就绪（未安装或未运行）—— 引擎无法获得 shell 上下文；请先启动 Shizuku，再为「Linux 运行环境」授权一次",
                "启动 Shizuku 引导",
            )
        }
        if (snapshot.shizukuState in setOf("NOT_GRANTED", "REVOKED", "STALE")) {
            return Guidance(
                FailureKind.AUTH_NEEDED,
                "请为「Linux 运行环境」单独授权一次（全生态唯一授权动作；宿主授权不继承）",
                "跳转 Shizuku 授权列表",
            )
        }
        if (!snapshot.assetsReady) {
            return Guidance(
                FailureKind.ASSETS_NOT_READY,
                "运行环境组件尚未下载完成 —— 请等待真实下载进度（rootfs / 工具链经大资产通道），期间不会启动构建",
                "进入运行环境资产页",
            )
        }
        if (snapshot.probeSummary == "PROOT_BLOCKED" || snapshot.probeSummary == "PLATFORM_BLOCKED") {
            return Guidance(
                FailureKind.PROBE_BLOCKED,
                "当前系统限制探测（已知 ROM：ColorOS 15/16 约 1 分钟拦截 / 荣耀 MagicOS 内核拒绝）。可切换「完整系统域」（冷启动约 1–3 分钟、构建耗时约为当前 3–8 倍、需 X GB 存储）",
                "切换执行域（需确认）",
            )
        }
        if (snapshot.pluginVersion.isBlank()) {
            return Guidance(
                FailureKind.DEP_MISSING,
                "应用构建器需要「Linux 运行环境」插件提供运行环境，请先安装（支持一并安装）",
                "一键安装 Linux 插件",
            )
        }
        return null
    }

    /** 宿主门槛失败文案（v1 宿主 → 升级提示；PLG-001 FR-001）。 */
    fun hostTooOld(requiredHostApi: Int, currentHostApi: Int): Guidance =
        Guidance(
            FailureKind.HOST_TOO_OLD,
            "请升级深渊工具主壳：本插件需要 HOST_API ≥ $requiredHostApi（当前 $currentHostApi）",
            "跳转主壳更新",
        )

    /** 版本过低文案（依赖区间人话）。 */
    fun dependencyTooLow(installedVersion: String, requiredRange: String): Guidance =
        Guidance(
            FailureKind.DEP_VERSION,
            "「Linux 运行环境」当前版本 $installedVersion，不满足 $requiredRange —— 请先升级 Linux 插件",
            "跳转补丁 / Release 升级",
        )
}

/**
 * SessionGrant 消费策略（APP-100 FR-002 / ARC-103 N-2 —— 纯 JVM）。
 *
 * 纪律：**不缓存、不持久化**；过期自动重新 getCapability；心跳续期 =
 * 宿主重新签发（旧 grant 不延期）。
 */
object GrantPolicy {

    /** 客户端侧令牌视图（镜像宿主 SessionGrant 的消费字段）。 */
    data class GrantToken(
        val sessionId: String,
        val nonce: String,
        val issuedAtEpochMs: Long,
        val expiryEpochMs: Long,
    ) {
        fun isExpired(nowEpochMs: Long): Boolean = nowEpochMs >= expiryEpochMs
    }

    /**
     * 是否需要提前刷新（expiry 前 [LinuxRuntimeContract.REFRESH_LEAD_MS]）。
     * —— 刷新 = 重新走宿主 getCapability（不延旧令牌）。
     */
    fun shouldRefresh(token: GrantToken, nowEpochMs: Long): Boolean =
        nowEpochMs >= token.expiryEpochMs - LinuxRuntimeContract.REFRESH_LEAD_MS

    /** 长任务心跳窗口：exec 期间按此间隔重申（长构建不中断）。 */
    fun heartbeatIntervalMs(token: GrantToken): Long =
        (token.expiryEpochMs - token.issuedAtEpochMs) / 2
}

/**
 * 会话客户端状态机（ARC-103 N-3/N-6 —— 一个构建 = 一次会话；失败切换
 * 复用同一会话；Binder 死亡 → UI 恢复不崩溃可重试）。
 */
object SessionClient {

    enum class State { IDLE, OPENING, READY, BUSY, CLOSED, ERROR }

    enum class Event { OPEN, OPENED, EXEC_STARTED, EXEC_DONE, CLOSED, BINDER_DIED, FAILED }

    private val transitions: Map<Pair<State, Event>, State> = buildMap {
        put(State.IDLE to Event.OPEN, State.OPENING)
        put(State.OPENING to Event.OPENED, State.READY)
        put(State.OPENING to Event.FAILED, State.ERROR)
        put(State.READY to Event.EXEC_STARTED, State.BUSY)
        put(State.BUSY to Event.EXEC_DONE, State.READY)
        put(State.READY to Event.CLOSED, State.CLOSED)
        put(State.BUSY to Event.CLOSED, State.CLOSED)
        // Binder 死亡（Linux 插件被杀 / 宿主中介断链）→ ERROR（UI 转恢复态）
        put(State.OPENING to Event.BINDER_DIED, State.ERROR)
        put(State.READY to Event.BINDER_DIED, State.ERROR)
        put(State.BUSY to Event.BINDER_DIED, State.ERROR)
        put(State.ERROR to Event.OPEN, State.OPENING)
    }

    fun allowedEvents(state: State): Set<Event> =
        transitions.keys.filter { it.first == state }.map { it.second }.toSet()

    fun transition(state: State, event: Event): State =
        transitions[state to event]
            ?: throw IllegalStateException("非法客户端会话迁移：$state --$event--> ∅")

    /**
     * 失败切换路径复用判定（APP-101 AC-003 / LIN-004 FR-009）：
     * 路径切换不改 sessionId、不清产物、无需重导。
     */
    fun retainSessionIdForPathSwitch(currentSessionId: String): String = currentSessionId
}
