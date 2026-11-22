package com.abyss.plugin.appbuilder

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.abyss.plugin.appbuilder.base.BaseProbe
import com.abyss.plugin.appbuilder.base.BaseStateStore
import com.abyss.plugin.appbuilder.base.DegradeChain

/**
 * 插件首页（M1 切片最小 UI，零第三方依赖）：
 *  - 展示底座档位 + 人话说明（FR-006：状态持久化并展示）；
 *  - 探测按钮：环境级探测（M1）→ 档位落盘；
 *  - NEEDS_AUTH 档位展示 Shizuku 独立授权引导（per-app，宿主授权不继承）。
 */
class BuilderActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = "🏗 应用构建器（M1 · 底座脚手架）"
            textSize = 20f
        }
        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }
        val probeButton = Button(this).apply {
            text = "探测构建环境"
            setOnClickListener { runProbe() }
        }
        root.addView(title)
        root.addView(statusText)
        root.addView(probeButton)
        setContentView(ScrollView(this).apply { addView(root) })
        renderPersisted()
    }

    private fun renderPersisted() {
        val (level, ts, manufacturer) = BaseStateStore.load(this)
        val probedAt = if (ts > 0) "（探测于 ${ts}）" else ""
        statusText.text = buildString {
            appendLine("底座档位：${level.name}$probedAt")
            appendLine()
            appendLine(DegradeChain.humanMessage(level))
            BaseProbe.restrictedRomSummary(manufacturer)?.let {
                appendLine()
                appendLine("已识别设备限制 —— $it")
            }
            appendLine()
            appendLine("能力边界：M1 交付底座探测与降级链；三条构建路径随 v0.2+ 底座资产落地。")
        }
    }

    private fun runProbe() {
        val manufacturer = Build.MANUFACTURER ?: ""
        // M1 环境级探测：已知受限 ROM 先行判定（清单权威，fail-closed）；
        // ptrace 实测 binary 随 v0.2 底座资产接入（ProbeExecutor 契约已定）。
        val probe = if (BaseProbe.restrictedRomOf(manufacturer) != null) {
            DegradeChain.ProbeOutcome.DENIED
        } else {
            DegradeChain.ProbeOutcome.NOT_RUN
        }
        // Shizuku 状态接入随 N-APP-003 授权链路接线（当前恒 false=未接线）
        val level = DegradeChain.resolve(
            DegradeChain.Environment(
                shizukuRunning = false,
                shizukuAuthorized = false,
                probe = probe,
            )
        )
        BaseStateStore.save(this, level, manufacturer)
        renderPersisted()
    }
}
