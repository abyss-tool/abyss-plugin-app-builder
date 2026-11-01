<div align="center">

# 🏗 应用构建器

### 手机端 APK 构建 —— Alpine + PRoot 底座 · 三条构建路径 · 用户自由选择 · 独立 Shizuku 授权

[![Plugin CI](https://github.com/abyss-tool/abyss-plugin-app-builder/actions/workflows/plugin-ci.yml/badge.svg?branch=main)](https://github.com/abyss-tool/abyss-plugin-app-builder/actions/workflows/plugin-ci.yml)
![Version](https://img.shields.io/badge/version-0.1.0-7EC8FF)
![Category](https://img.shields.io/badge/分类-开发与构建-4FD8EB)
![Status](https://img.shields.io/badge/状态-框架预览-1A222D)

</div>

---

> 深渊工具（[abyss-tool/Abyss-Tools](https://github.com/abyss-tool/Abyss-Tools)）的**旗舰大插件** —— 在手机上把项目源码构建成 APK。本仓库为**插件框架期骨架**：manifest（含独立 Shizuku 授权声明）/ 补丁通道 / CI 门禁已就绪，构建引擎按路线图实现。完整技术方案见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 为什么是插件？

构建器是深渊工具里**最重**的能力：自带 Alpine rootfs、ARM64 工具链、Gradle/JDK 发行版管理，运行独立的构建进程 —— 主壳保持轻量，构建器独立演进、独立补丁、独立授权。这正是「小功能内置，大功能插件化」分界线的插件侧代表。

## 插件信息

| 字段 | 值 |
| --- | --- |
| 插件 ID | `abyss-plugin-app-builder` |
| 分类 | 开发与构建（dev） |
| 版本 | v0.1.0（框架预览） |
| 最低宿主 | App v0.2.0 / HOST_API v1 |
| 仓库 | [abyss-tool/abyss-plugin-app-builder](https://github.com/abyss-tool/abyss-plugin-app-builder) |
| 补丁通道 | [`patches/index.json`](patches/index.json) |

## 权限需求

- `FOREGROUND_SERVICE`（长时间构建的前台保活）
- `WAKE_LOCK`（防止切后台构建被系统中断）
- `REQUEST_INSTALL_PACKAGES`（构建产物直接安装）

## Shizuku 独立授权（重要）

本插件为**独立 APK**，Shizuku 的授权按应用包名逐一发放 —— **宿主「深渊工具」的授权不能继承给本插件**，安装后需在 Shizuku 内单独授权一次。

为什么需要：构建底座（Alpine + PRoot）优先经 **Shizuku 的 ADB shell 上下文（uid 2000）** 启动。PRoot 依赖 `ptrace()`，在 ColorOS 15/16、荣耀 MagicOS 等系统上会被普通应用的沙箱策略拦截；运行在 shell 上下文中则不受此类限制。Shizuku 不是 root —— 它解决的是「PRoot 被沙箱限制」，不提供越权能力。

## 三条构建路径（用户自由选择）

```
共享底座：Alpine + PRoot（自包含，无 Termux 依赖）
├── 路径一：Anpyra 纯 Python 构建（轻量项目）
├── 路径二：aapt2 + 固定壳 DEX（资源 / HTML 打包）
└── 路径三：标准 Gradle 构建（复杂标准项目，ARM64 工具链）
```

导入项目后**静态预检只提示、不拦截**：每条路径给出推荐理由与风险提示，最终由用户三选一；构建失败可低成本切换路径再试（不清产物、不重导项目）。详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 安装

经深渊工具主壳「导航 → 开发与构建 → 应用构建器」进入（未安装时展示安装引导），或在「导航 → 插件 → 插件中心」直接安装：下载 → SHA-256 校验 → 安装网关 → Shizuku 单独授权。**APK 资产自 v0.2 发布流水线起发布**。

## 补丁更新

- 通道：本仓库 `patches/index.json`（abyss-plugin-patches-v1）
- 引擎：主壳 `abyss-patch-v1` 数据补丁（下载 → SHA-256 → 备份 → 应用 → 失败回滚）
- 插件详情页实时展示补丁时间线

## 路线图

- [ ] v0.2 底座拉起：Alpine rootfs + PRoot 可用性探测（Shizuku 优先，纯 PRoot 降级）
- [ ] v0.3 路径一：Anpyra 纯 Python 构建（轻量项目）
- [ ] v0.4 路径二：内置工具链 aapt2 + 固定壳 DEX（资源 / HTML 打包）
- [ ] v0.5 路径三：标准 Gradle 构建（ARM64 工具链 + 资源约束）
- [ ] v0.6 三选一交互层：预检提示 / 推荐标记 / 失败切换 / 底座隔离

## 许可

Ultra-Guru 受限使用许可协议（UGRL v1.0），见 [LICENSE](LICENSE)。
