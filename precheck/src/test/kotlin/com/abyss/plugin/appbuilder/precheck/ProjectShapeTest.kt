package com.abyss.plugin.appbuilder.precheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 静态能力预检（APP-101 §6：推荐 / 硬性标注 / 二次确认）。 */
class ProjectShapeTest {

    @Test
    fun `Anpyra 子集项目 - 路径一推荐且零硬拒`() {
        val v = ProjectShape.analyze(
            ProjectShape.Features(isAnpyraSubset = true),
        )
        assertEquals(ProjectShape.Path.ANPYRA, v.recommended)
        assertTrue(v.hardFails.isEmpty())
    }

    @Test
    fun `含 res 或 build_gradle 或源码 - 路径一硬性标注（必然失败）`() {
        listOf(
            ProjectShape.Features(hasResDir = true, isAnpyraSubset = true),
            ProjectShape.Features(hasBuildGradle = true, isAnpyraSubset = true),
            ProjectShape.Features(hasJavaSources = true, isAnpyraSubset = true),
            ProjectShape.Features(hasKotlinSources = true, isAnpyraSubset = true),
        ).forEach { f ->
            val v = ProjectShape.analyze(f)
            assertTrue("路径一必然失败特征必须硬性标注", ProjectShape.Path.ANPYRA in v.hardFails)
        }
    }

    @Test
    fun `资源结构项目 - 路径二推荐`() {
        val v = ProjectShape.analyze(ProjectShape.Features(hasResDir = true))
        assertEquals(ProjectShape.Path.AAPT2_SHELL, v.recommended)
        assertTrue(ProjectShape.Path.ANPYRA in v.hardFails)
    }

    @Test
    fun `多模块 Kotlin 项目 - 路径三推荐 + 资源约束提示`() {
        val v = ProjectShape.analyze(
            ProjectShape.Features(hasBuildGradle = true, hasKotlinSources = true, hasMultiModule = true),
        )
        assertEquals(ProjectShape.Path.GRADLE, v.recommended)
        assertTrue(v.warnings[ProjectShape.Path.GRADLE]!!.contains("10–30"))
        assertTrue(ProjectShape.Path.AAPT2_SHELL in v.confirmations)
    }

    @Test
    fun `自定义源码 + 路径二 - 不编译提示`() {
        val v = ProjectShape.analyze(ProjectShape.Features(hasResDir = true, hasJavaSources = true))
        assertEquals(ProjectShape.Path.AAPT2_SHELL, v.recommended)
        assertTrue(v.warnings[ProjectShape.Path.AAPT2_SHELL]!!.contains("不编译"))
    }

    @Test
    fun `选不匹配路径 - 二次确认（不拦截）`() {
        val v = ProjectShape.analyze(ProjectShape.Features(hasResDir = true)) // 推荐路径二
        val msg = PathSelection.confirmMessage(v, ProjectShape.Path.GRADLE)
        assertTrue(msg!!.contains("不匹配"))
    }

    @Test
    fun `选硬拒路径 - 硬性标注文案（知情选择）`() {
        val v = ProjectShape.analyze(ProjectShape.Features(hasResDir = true))
        val msg = PathSelection.confirmMessage(v, ProjectShape.Path.ANPYRA)
        assertTrue(msg!!.contains("必然不支持"))
    }

    @Test
    fun `选推荐路径 - 无确认`() {
        val v = ProjectShape.analyze(ProjectShape.Features(hasResDir = true))
        assertNull(PathSelection.confirmMessage(v, ProjectShape.Path.AAPT2_SHELL))
    }

    @Test
    fun `三选一选项带耗时预期`() {
        val v = ProjectShape.analyze(ProjectShape.Features(isAnpyraSubset = true))
        val options = PathSelection.options(v)
        assertEquals(3, options.size)
        assertEquals("秒级 ~ 1 分钟", options.first { it.path == ProjectShape.Path.ANPYRA }.durationExpectation)
        assertEquals("1 – 3 分钟", options.first { it.path == ProjectShape.Path.AAPT2_SHELL }.durationExpectation)
        assertEquals("10 – 30 分钟", options.first { it.path == ProjectShape.Path.GRADLE }.durationExpectation)
        assertTrue(options.first { it.path == ProjectShape.Path.ANPYRA }.recommended)
    }

    @Test
    fun `路径三资源约束文案（首次展示）`() {
        assertTrue(ProjectShape.gradleConstraintsNotice().contains("2048m"))
        assertTrue(ProjectShape.gradleConstraintsNotice().contains("10–30"))
    }
}
