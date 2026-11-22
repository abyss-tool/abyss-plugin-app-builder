package com.abyss.plugin.appbuilder.runtime

/**
 * Linux 运行时客户端契约（客户端侧镜像 —— 主仓 plugin-sdk HOST_API v2
 * 与 abyss-plugin-linux :runtime-api 契约的动作串/能力 id 消费视图）。
 *
 * 纪律（ARC-103 / DC-101 FR-006）：客户端不持有宿主专属的运行时全权
 * 接口；唯一可持有的会话句柄经宿主中介下发（受限代理）。
 */
object LinuxRuntimeContract {

    /** Linux 运行时服务发现动作串（镜像宿主 PluginContract.ACTION_LINUX_RUNTIME）。 */
    const val ACTION_LINUX_RUNTIME: String = "com.abyss.tools.plugin.action.LINUX_RUNTIME"

    /** 通用插件服务动作串（IAbyssPluginHost 桥）。 */
    const val ACTION_PLUGIN: String = "com.abyss.tools.plugin.action.PLUGIN"

    /** 当前唯一注册能力 id。 */
    const val CAPABILITY_LINUX_RUNTIME_V1: String = "linux-runtime-v1"

    /** 依赖版本区间（与 plugin.json depends 声明一致）。 */
    const val DEPENDS_VERSION_RANGE: String = ">=0.2.0 <1.0.0"

    /** grant TTL 上限（客户端刷新策略锚点）。 */
    const val GRANT_TTL_MAX_SEC: Int = 300

    /** 刷新提前量（expiry 前提前重申，避免在途过期）。 */
    const val REFRESH_LEAD_MS: Int = 30_000
}
