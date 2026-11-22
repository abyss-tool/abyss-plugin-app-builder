package com.abyss.plugin.appbuilder

import com.abyss.plugin.appbuilder.base.DegradeChain

/**
 * HOST_API v1 宿主入口（N-APP-001 FR-002）。
 *
 * M1 切片：宿主经 PackageManager meta-data 发现本类（v0.2 AIDL 桥接入前
 * 宿主不直接实例化，本类承担自启动入口与能力自述）。能力切片边界诚实
 * 声明：脚手架 = 工程底座 + 探测降级链，三条构建路径随 N-APP-004~006。
 */
class AppBuilderPlugin {

    val id: String = "abyss-plugin-app-builder"

    /** 宿主 API 需求（与 plugin.json minHost 一致，CI 门禁校验）。 */
    val minHostApi: Int = PluginContract.HOST_API_VERSION

    /**
     * 宿主详情页能力自述（诚实边界：M1 只交付底座探测，不承诺构建能力）。
     */
    fun describeCapabilities(): List<String> = listOf(
        "底座可用性探测与三级降级链（Shizuku shell / 纯 PRoot / 明确不可用）",
        "Shizuku 独立授权引导（per-app，宿主授权不继承）",
        "构建路径一（Anpyra 纯 Python）/ 二（aapt2+固定壳）/ 三（标准 Gradle）：随 v0.2+ 底座资产逐步落地",
    )

    /** 底座档位查询（首页展示；DegradeChain 决策表唯一实现）。 */
    fun resolveLevel(env: DegradeChain.Environment): DegradeChain.BaseLevel =
        DegradeChain.resolve(env)
}
