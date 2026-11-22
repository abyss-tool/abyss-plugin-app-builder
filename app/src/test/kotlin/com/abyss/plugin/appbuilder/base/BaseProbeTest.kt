package com.abyss.plugin.appbuilder.base

import com.abyss.plugin.appbuilder.base.DegradeChain.ProbeOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** N-APP-002 FR-002/FR-005 探测协议（纯 JVM：解析 + 受限 ROM 清单）。 */
class BaseProbeTest {

    private val probeOutput = """
        --- getenforce
        Permissive
        --- grep TracerPid /proc/self/status
        TracerPid:	0
    """.trimIndent()

    @Test
    fun `parses permissive environment with no tracer as OK`() {
        assertEquals(ProbeOutcome.OK, BaseProbe.parseOutcome(probeOutput, "google"))
    }

    @Test
    fun `parses attached tracer as DENIED`() {
        val attached = probeOutput.replace("TracerPid:\t0", "TracerPid:\t4242")
        assertEquals(ProbeOutcome.DENIED, BaseProbe.parseOutcome(attached, "google"))
    }

    @Test
    fun `blank output is fail-closed DENIED`() {
        assertEquals(ProbeOutcome.DENIED, BaseProbe.parseOutcome("", "google"))
    }

    @Test
    fun `known restricted rom is denied before generic probe`() {
        // ColorOS（OPPO）——清单权威，先于通用探测
        assertEquals(ProbeOutcome.DENIED, BaseProbe.parseOutcome(probeOutput, "OPPO"))
        assertEquals(ProbeOutcome.DENIED, BaseProbe.parseOutcome(probeOutput, "OnePlus"))
        assertEquals(ProbeOutcome.DENIED, BaseProbe.parseOutcome(probeOutput, "HONOR"))
    }

    @Test
    fun `restricted rom summary carries rom name and behavior`() {
        val summary = BaseProbe.restrictedRomSummary("oppo")
        assertNotNull(summary)
        assertTrue(summary!!.contains("ColorOS"))
        assertNull(BaseProbe.restrictedRomSummary("samsung"))
        assertNull(BaseProbe.restrictedRomSummary(""))
    }

    @Test
    fun `probe script for shizuku shell includes getenforce`() {
        assertTrue(BaseProbe.buildProbeScript(viaShizukuShell = true).contains("getenforce"))
        // App 沙箱降级：只做 TracerPid 自查
        val sandbox = BaseProbe.buildProbeScript(viaShizukuShell = false)
        assertTrue(sandbox.contains("TracerPid"))
        assertTrue(!sandbox.contains("getenforce"))
    }
}
