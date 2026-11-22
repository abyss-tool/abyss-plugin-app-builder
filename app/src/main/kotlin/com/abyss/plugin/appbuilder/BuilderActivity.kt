package com.abyss.plugin.appbuilder

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 插件首页（瘦客户端形态 · 零第三方依赖）。
 *
 * v0.2.0 起本插件不再自带底座（ADR-100 D-3 / ARC-103）：
 *  - 运行环境 = abyss-plugin-linux（双执行域 + 唯一授权）；
 *  - 本页展示**依赖握手清单**（APP-100 启动编排四阶段）与能力边界；
 *  - 探测 / 授权 / 资产下载全部发生在 Linux 插件内 —— 本插件握手时读
 *    运行时快照（资产就绪 / 授权状态 / 探测摘要）做引导，自身零探测
 *    实现（M2 接线宿主能力中介桥）。
 */
class BuilderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = "🏗 应用构建器（瘦客户端 · v0.2）"
            textSize = 20f
        }
        val body = TextView(this).apply {
            textSize = 14f
            setPadding(0, pad, 0, 0)
            text = buildString {
                appendLine("运行环境依赖：Linux 运行环境插件（linux-runtime-v1 ≥0.2.0）")
                appendLine()
                appendLine("启动握手四阶段（宿主 PluginBroker 中介，永不直连）：")
                appendLine("  ① 依赖解析 —— 未装 Linux 插件 → 一键一并安装（拓扑序）")
                appendLine("  ② 授权引导 —— 请为 Linux 插件单独授权一次（全生态唯一授权动作）")
                appendLine("  ③ 资产就绪 —— 运行环境组件（用户空间 / 工具链）经大资产通道下载")
                appendLine("  ④ 会话开启 —— openSession → 三路径构建（产物闭环 APP-102）")
                appendLine()
                appendLine("本插件零底座实现（ADR-100 D-3）：执行引擎、环境探测、授权与")
                appendLine("资产管线均在 abyss-plugin-linux 仓。")
            }
        }
        root.addView(title)
        root.addView(body)
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
