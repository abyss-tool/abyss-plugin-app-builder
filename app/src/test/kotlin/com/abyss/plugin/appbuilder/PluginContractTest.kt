package com.abyss.plugin.appbuilder

import com.abyss.plugin.appbuilder.runtime.LinuxRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST_API v2 契约镜像钉扎（SDK-001 / ADR-100 D-5）。
 *
 * 镜像副本的唯一事实源在主仓 plugin-sdk；本测试把「镜像漂移」从
 * 运行期提前到 CI 编译期 —— 本仓内任何人改 PluginContract.kt，钉扎
 * 断言即刻红，强制回到主仓改完再同步。另与 runtime-client 的消费视图
 * （LinuxRuntimeContract）交叉校验，防止 app 镜像与客户端常量双头漂移。
 */
class PluginContractTest {

    @Test
    fun `HOST_API_VERSION 钉扎 v2 - 主仓升版须显式同步本仓`() {
        // SDK-001 ABI 纪律：只增不减。本插件按 v2 契约编译（minHost=2，
        // 在 v1 宿主 fail-closed）；主仓升 v3 时此处必须显式改钉并评估。
        assertEquals(2, PluginContract.HOST_API_VERSION)
    }

    @Test
    fun `ID_PATTERN 与宿主 PluginIndex 契约一致 - 小写连字符多段`() {
        assertTrue(PluginContract.ID_PATTERN.matches("abyss-plugin-app-builder"))
        assertTrue(PluginContract.ID_PATTERN.matches("abyss-plugin-linux"))
        assertFalse(PluginContract.ID_PATTERN.matches("singleword"))
        assertFalse(PluginContract.ID_PATTERN.matches("Upper-Case"))
        assertFalse(PluginContract.ID_PATTERN.matches("trailing-"))
    }

    @Test
    fun `路径与元数据常量与宿主消费侧字面量一致`() {
        assertEquals("plugin.json", PluginContract.MANIFEST_FILE)
        assertEquals("plugins", PluginContract.PLUGINS_DIR)
        assertEquals("patches/index.json", PluginContract.PATCHES_INDEX)
        assertTrue(PluginContract.META_HOST_ENTRY.startsWith("com.abyss.tools.plugin."))
        assertTrue(PluginContract.META_UI_ENTRIES.startsWith("com.abyss.tools.plugin."))
        assertTrue(PluginContract.META_CAPABILITIES.startsWith("com.abyss.tools.plugin."))
    }

    @Test
    fun `v2 动作串与 runtime-client 消费视图一致 - 双头镜像不漂移`() {
        // LinuxRuntimeContract 是 app 镜像的客户端侧消费视图（ARC-103）：
        // 两处常量必须同源，此处交叉钉扎防「改一处忘一处」。
        assertEquals(LinuxRuntimeContract.ACTION_LINUX_RUNTIME, PluginContract.ACTION_LINUX_RUNTIME)
        assertEquals(LinuxRuntimeContract.ACTION_PLUGIN, PluginContract.ACTION_PLUGIN)
    }

    @Test
    fun `Grant TTL 与刷新提前量与 runtime-client 消费视图一致`() {
        assertEquals(LinuxRuntimeContract.GRANT_TTL_MAX_SEC, PluginContract.GRANT_TTL_MAX_SEC)
        assertEquals(300, PluginContract.GRANT_TTL_MAX_SEC)
        // 能力 id 单一注册位：app 镜像与客户端消费视图同源
        assertEquals(
            LinuxRuntimeContract.CAPABILITY_LINUX_RUNTIME_V1,
            PluginContract.CAPABILITY_LINUX_RUNTIME_V1,
        )
    }
}
