package com.abyss.plugin.appbuilder.base

import com.abyss.plugin.appbuilder.base.DegradeChain.BaseLevel
import com.abyss.plugin.appbuilder.base.DegradeChain.Environment
import com.abyss.plugin.appbuilder.base.DegradeChain.ProbeOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** N-APP-002 FR-003~005 降级链决策表（纯 JVM 全档位覆盖）。 */
class DegradeChainTest {

    @Test
    fun `shizuku running and authorized with probe ok resolves to READY`() {
        assertEquals(
            BaseLevel.READY,
            DegradeChain.resolve(Environment(true, true, ProbeOutcome.OK)),
        )
    }

    @Test
    fun `shizuku running but not authorized resolves to NEEDS_AUTH`() {
        assertEquals(
            BaseLevel.NEEDS_AUTH,
            DegradeChain.resolve(Environment(true, false, ProbeOutcome.OK)),
        )
    }

    @Test
    fun `no shizuku with probe ok degrades to sandbox PRoot`() {
        assertEquals(
            BaseLevel.DEGRADED,
            DegradeChain.resolve(Environment(false, false, ProbeOutcome.OK)),
        )
    }

    @Test
    fun `probe denied or timeout means unavailable regardless of shizuku`() {
        assertEquals(
            BaseLevel.UNAVAILABLE,
            DegradeChain.resolve(Environment(true, true, ProbeOutcome.DENIED)),
        )
        assertEquals(
            BaseLevel.UNAVAILABLE,
            DegradeChain.resolve(Environment(false, false, ProbeOutcome.TIMEOUT)),
        )
    }

    @Test
    fun `initial state resolves to NOT_INITIALIZED`() {
        assertEquals(BaseLevel.NOT_INITIALIZED, DegradeChain.resolve(Environment.INITIAL))
    }

    @Test
    fun `unavailable human message names known restricted roms`() {
        val msg = DegradeChain.humanMessage(BaseLevel.UNAVAILABLE)
        assert(msg.contains("ColorOS")) { msg }
        assert(msg.contains("MagicOS")) { msg }
        assert(msg.contains("系统限制")) { msg }
    }

    @Test
    fun `needs-auth message states host grant is not inherited`() {
        assert(DegradeChain.humanMessage(BaseLevel.NEEDS_AUTH).contains("不能继承"))
    }
}
