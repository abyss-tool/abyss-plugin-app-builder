package com.abyss.plugin.appbuilder.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 启动握手四类引导（APP-100 AC-002 / §4 步④ 分流）。 */
class HandshakeGuideTest {

    private val green = HandshakeGuide.RuntimeSnapshot(
        pluginVersion = "0.2.0",
        assetsReady = true,
        shizukuState = "GRANTED",
        probeBlocked = false,
    )

    @Test
    fun `全绿 - 不产生引导（进入项目导入）`() {
        assertNull(HandshakeGuide.route(green))
    }

    @Test
    fun `授权引导优先于资产（E2E-04）`() {
        val g = HandshakeGuide.route(green.copy(assetsReady = false, shizukuState = "NOT_GRANTED"))
        assertNotNull(g)
        assertEquals(HandshakeGuide.FailureKind.AUTH_NEEDED, g!!.kind)
        assertTrue(g.humanMessage.contains("单独授权一次"))
        assertTrue(g.action.contains("授权"))
    }

    @Test
    fun `Shizuku 未安装或未运行 - 启动引导`() {
        listOf("NOT_INSTALLED", "NOT_RUNNING").forEach { state ->
            val g = HandshakeGuide.route(green.copy(shizukuState = state))
            assertEquals(HandshakeGuide.FailureKind.AUTH_NEEDED, g!!.kind)
            assertTrue(g.humanMessage.contains("Shizuku"))
        }
    }

    @Test
    fun `授权撤销与失效 - 重新授权引导`() {
        listOf("REVOKED", "STALE").forEach { state ->
            val g = HandshakeGuide.route(green.copy(shizukuState = state))
            assertEquals(HandshakeGuide.FailureKind.AUTH_NEEDED, g!!.kind)
            assertTrue(g.humanMessage.contains("重新授权") || g.humanMessage.contains("单独授权"))
        }
    }

    @Test
    fun `资产未就绪 - 资产页引导（不启动构建）`() {
        val g = HandshakeGuide.route(green.copy(assetsReady = false))
        assertEquals(HandshakeGuide.FailureKind.ASSETS_NOT_READY, g!!.kind)
        assertTrue(g.humanMessage.contains("尚未下载完成"))
    }

    @Test
    fun `探测被拦 - 切域选项（含耗时与存储预期，E2E-08）`() {
        // 探测被平台拦截（适配层折叠为 probeBlocked 布尔）
        val g = HandshakeGuide.route(green.copy(probeBlocked = true))
        assertEquals(HandshakeGuide.FailureKind.PROBE_BLOCKED, g!!.kind)
        assertTrue(g.humanMessage.contains("1–3 分钟"))
        assertTrue(g.humanMessage.contains("3–8 倍"))
    }

    @Test
    fun `版本缺失 - 安装引导（E2E-02）`() {
        val g = HandshakeGuide.route(green.copy(pluginVersion = ""))
        assertEquals(HandshakeGuide.FailureKind.DEP_MISSING, g!!.kind)
        assertTrue(g.humanMessage.contains("请先安装"))
    }

    @Test
    fun `宿主过旧与版本过低的第五类引导`() {
        val g = HandshakeGuide.hostTooOld(requiredHostApi = 2, currentHostApi = 1)
        assertEquals(HandshakeGuide.FailureKind.HOST_TOO_OLD, g.kind)
        assertTrue(g.humanMessage.contains("升级深渊工具"))
        val v = HandshakeGuide.dependencyTooLow("0.1.0", ">=0.2.0 <1.0.0")
        assertEquals(HandshakeGuide.FailureKind.DEP_VERSION, v.kind)
        assertTrue(v.humanMessage.contains("0.1.0"))
    }
}

/** Grant 消费策略（APP-100 FR-002：不缓存 / 提前刷新 / 心跳间隔）。 */
class GrantPolicyTest {

    private val token = GrantPolicy.GrantToken(
        sessionId = "s-1",
        nonce = "n",
        issuedAtEpochMs = 1_000L,
        expiryEpochMs = 1_000L + LinuxRuntimeContract.GRANT_TTL_MAX_SEC * 1000L,
    )

    @Test
    fun `未到期不刷新`() {
        assertEquals(1_000L + 270_000L, 1_000L + LinuxRuntimeContract.GRANT_TTL_MAX_SEC * 1000L - LinuxRuntimeContract.REFRESH_LEAD_MS)
        assertTrue(!GrantPolicy.shouldRefresh(token, nowEpochMs = 1_000L))
        assertTrue(!GrantPolicy.shouldRefresh(token, nowEpochMs = 200_000L))
    }

    @Test
    fun `提前量内刷新（expiry-30s 边界）`() {
        // 刷新线 = expiry - 30s = 271000；边界值恰达
        assertTrue(GrantPolicy.shouldRefresh(token, nowEpochMs = 271_000L))
        assertTrue(GrantPolicy.shouldRefresh(token, nowEpochMs = 301_000L))
    }

    @Test
    fun `心跳窗口为 TTL 半程`() {
        assertEquals(150_000L, GrantPolicy.heartbeatIntervalMs(token))
    }
}

/** 会话客户端状态机（ARC-103 N-3/N-6：死亡恢复 / 复用会话切路径）。 */
class SessionClientTest {

    @Test
    fun `正常流 - 打开到关闭`() {
        var s = SessionClient.State.IDLE
        s = SessionClient.transition(s, SessionClient.Event.OPEN)
        assertEquals(SessionClient.State.OPENING, s)
        s = SessionClient.transition(s, SessionClient.Event.OPENED)
        assertEquals(SessionClient.State.READY, s)
        s = SessionClient.transition(s, SessionClient.Event.EXEC_STARTED)
        assertEquals(SessionClient.State.BUSY, s)
        s = SessionClient.transition(s, SessionClient.Event.EXEC_DONE)
        assertEquals(SessionClient.State.READY, s)
        s = SessionClient.transition(s, SessionClient.Event.CLOSED)
        assertEquals(SessionClient.State.CLOSED, s)
    }

    @Test
    fun `Binder 死亡任一态转 ERROR（UI 恢复不崩溃，N-6）`() {
        listOf(SessionClient.State.OPENING, SessionClient.State.READY, SessionClient.State.BUSY).forEach { st ->
            assertEquals(SessionClient.State.ERROR, SessionClient.transition(st, SessionClient.Event.BINDER_DIED))
        }
        // ERROR 可重试（重开会话）
        assertEquals(SessionClient.State.OPENING, SessionClient.transition(SessionClient.State.ERROR, SessionClient.Event.OPEN))
    }

    @Test
    fun `非法迁移 fail-closed`() {
        try {
            SessionClient.transition(SessionClient.State.CLOSED, SessionClient.Event.EXEC_STARTED)
            fail("已关闭会话不允许构建动作")
        } catch (expected: IllegalStateException) {
        }
    }

    @Test
    fun `失败切路径复用同一 sessionId（APP-101 AC-003）`() {
        assertEquals("sess-42", SessionClient.retainSessionIdForPathSwitch("sess-42"))
    }
}
