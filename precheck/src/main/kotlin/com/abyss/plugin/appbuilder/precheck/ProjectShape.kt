package com.abyss.plugin.appbuilder.precheck

/**
 * 静态能力预检（APP-101 §6 —— 只分析不拦截；路径一硬性标注例外）。
 *
 * 预检特征集来自项目文件形态（导入即分析，零构建动作）：
 *  - 路径一（Anpyra）：仅支持「单 Activity + TextView 子集」—— 含
 *    `res/` / `build.gradle` / 任何 Java/Kotlin 源码 → **必然失败特征，
 *    硬性标注**（自由选择 ≠ 放任反复试错，APP-101 §6）；
 *  - 路径二（aapt2+固定壳）：标准资源结构 / HTML·Web；自定义源码仅
 *    「不编译，仅打包资源与壳」提示；
 *  - 路径三（Gradle）：多模块 / Kotlin / 依赖多 —— 首选展示资源约束
 *    （内存上限 / 预计耗时区间 / 依赖下载量）。
 */
object ProjectShape {

    /** 项目形态特征（导入时静态分析产物）。 */
    data class Features(
        val hasResDir: Boolean = false,
        val hasBuildGradle: Boolean = false,
        val hasJavaSources: Boolean = false,
        val hasKotlinSources: Boolean = false,
        val hasMultiModule: Boolean = false,
        val hasAssetsHtml: Boolean = false,
        /** Anpyra 子集兼容（单 Activity + TextView 声明式子集）。 */
        val isAnpyraSubset: Boolean = false,
    )

    /** 三条构建路径（与执行域正交 —— APP-101 §1）。 */
    enum class Path(val id: String) {
        ANPYRA("path.anpyra"),
        AAPT2_SHELL("path.aapt2-shell"),
        GRADLE("path.gradle"),
    }

    /** 预检结论：推荐 + 风险 + 硬性标注 + 二次确认需求。 */
    data class Verdict(
        val recommended: Path,
        val hardFails: Set<Path>,
        val warnings: Map<Path, String>,
        /** 选了「看起来不匹配」的路径 → 二次确认（不拦截）。 */
        val confirmations: Set<Path>,
    )

    /** 预检决策（推荐标记真实来自预检结果 —— 不自动选中）。 */
    fun analyze(features: Features): Verdict {
        val hardFails = mutableSetOf<Path>()
        val warnings = mutableMapOf<Path, String>()
        val confirmations = mutableSetOf<Path>()

        // 路径一硬性预检（APP-101 §6 例外：必然失败特征必须标注）
        if (features.hasResDir || features.hasBuildGradle || features.hasJavaSources || features.hasKotlinSources) {
            hardFails += Path.ANPYRA
        }
        // 路径二：自定义源码不编译提示
        if (features.hasJavaSources || features.hasKotlinSources) {
            warnings[Path.AAPT2_SHELL] = "固定壳 DEX 不编译 src/main/java|kotlin 源码 —— 仅打包资源与壳"
            confirmations += Path.AAPT2_SHELL
        }
        // 路径三：重构建资源约束提示（首次选择时展示）
        if (features.hasMultiModule || features.hasKotlinSources) {
            warnings[Path.GRADLE] = "标准 Gradle 构建：预计 10–30 分钟 · JVM 堆上限 2048m · 依赖按需下载"
        }

        val recommended = when {
            features.isAnpyraSubset && Path.ANPYRA !in hardFails -> Path.ANPYRA
            features.hasResDir || features.hasAssetsHtml -> Path.AAPT2_SHELL
            else -> Path.GRADLE
        }
        // 推荐之外的选择（非硬拒）→ 二次确认
        Path.entries.filter { it != recommended && it !in hardFails }.forEach { confirmations += it }
        return Verdict(recommended, hardFails, warnings, confirmations)
    }

    /** 路径三资源约束文案（首次选择展示，APP-101 §3）。 */
    fun gradleConstraintsNotice(): String =
        "首次使用标准 Gradle 构建建议了解：内存上限（JVM 堆 ≤2048m）· " +
            "预计耗时 10–30 分钟（多模块偏上限）· 依赖首次下载量较大（可复用缓存）"

    /** 耗时预期（三选一界面直出）。 */
    fun durationExpectation(path: Path): String = when (path) {
        Path.ANPYRA -> "秒级 ~ 1 分钟"
        Path.AAPT2_SHELL -> "1 – 3 分钟"
        Path.GRADLE -> "10 – 30 分钟"
    }
}

/**
 * 三选一交互层数据（K-2：推荐标记 / 风险提示 / 自由选择 / 二次确认）。
 */
object PathSelection {

    data class Option(
        val path: ProjectShape.Path,
        val title: String,
        val durationExpectation: String,
        val recommended: Boolean,
        /** 硬性标注：构建将无法完成（仍可展示但不推荐选择）。 */
        val hardBlocked: Boolean,
        val warning: String?,
    )

    fun options(verdict: ProjectShape.Verdict): List<Option> = ProjectShape.Path.entries.map { p ->
        Option(
            path = p,
            title = titleOf(p),
            durationExpectation = ProjectShape.durationExpectation(p),
            recommended = verdict.recommended == p,
            hardBlocked = p in verdict.hardFails,
            warning = verdict.warnings[p],
        )
    }

    private fun titleOf(p: ProjectShape.Path): String = when (p) {
        ProjectShape.Path.ANPYRA -> "路径一 · Anpyra 纯 Python 构建（轻量项目）"
        ProjectShape.Path.AAPT2_SHELL -> "路径二 · aapt2 + 固定壳 DEX（资源 / HTML 打包）"
        ProjectShape.Path.GRADLE -> "路径三 · 标准 Gradle 构建（复杂标准项目）"
    }

    /**
     * 用户确认流：选了「看起来不匹配」的路径 → 返回确认文案（不拦截）；
     * 选了硬拒路径 → 返回硬性标注文案（仍允许用户知情选择）。
     */
    fun confirmMessage(verdict: ProjectShape.Verdict, selected: ProjectShape.Path): String? {
        if (selected in verdict.hardFails) {
            return "该路径含必然不支持的构造（构建将无法完成）—— 请确认仍要尝试"
        }
        if (selected in verdict.confirmations) {
            return "与预检推荐（${titleShort(verdict.recommended)}）不匹配，继续尝试吗？（失败可低成本切换，产物保留）"
        }
        return null
    }

    private fun titleShort(p: ProjectShape.Path): String = when (p) {
        ProjectShape.Path.ANPYRA -> "路径一"
        ProjectShape.Path.AAPT2_SHELL -> "路径二"
        ProjectShape.Path.GRADLE -> "路径三"
    }
}
