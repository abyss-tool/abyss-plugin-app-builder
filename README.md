<div align="center">

# 🏗 应用构建器

### 手机端 APK 构建 —— 三条构建路径 · 用户自由选择 · 依赖 Linux 插件运行环境

[![Plugin CI](https://github.com/abyss-tool/abyss-plugin-app-builder/actions/workflows/plugin-ci.yml/badge.svg?branch=main)](https://github.com/abyss-tool/abyss-plugin-app-builder/actions/workflows/plugin-ci.yml)
![Version](https://img.shields.io/badge/version-0.2.0-7EC8FF)
![Category](https://img.shields.io/badge/分类-开发与构建-4FD8EB)
![Status](https://img.shields.io/badge/状态-框架预览-1A222D)

</div>

---

> 深渊工具（[abyss-tool/Abyss-Tools](https://github.com/abyss-tool/Abyss-Tools)）的**旗舰大插件** —— 在手机上把项目源码构建成 APK。**v0.2.0 起为瘦客户端**：Linux 运行环境由 [abyss-plugin-linux](https://github.com/abyss-tool/abyss-plugin-linux) 统一提供（PRoot 用户态 / QEMU 完整系统双执行域 + Shizuku 唯一授权），本插件只负责「项目预检 / 三路径编排 / 产物闭环」，经宿主中介依赖（永不直连）。完整技术方案见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 为什么是插件？

构建器是深渊工具里**最重**的编排能力：三条构建路径、产物签名与安装闭环，运行独立的构建编排进程 —— 主壳保持轻量，构建器独立演进、独立补丁。运行环境（rootfs / 工具链 / 引擎）不在本插件，而在 Linux 插件（ADR-100：底座唯一持有方），主壳以 HOST_API v2 的 PluginBroker 做依赖中介。

## 插件信息

| 字段 | 值 |
| --- | --- |
| 插件 ID | `abyss-plugin-app-builder` |
| 分类 | 开发与构建（dev） |
| 版本 | v0.2.0（瘦客户端 · 框架预览） |
| 最低宿主 | App v0.10.0 / HOST_API v2 |
| 依赖 | [abyss-plugin-linux](https://github.com/abyss-tool/abyss-plugin-linux) `linux-runtime-v1 >=0.2.0 <1.0.0`（非可选） |
| 仓库 | [abyss-tool/abyss-plugin-app-builder](https://github.com/abyss-tool/abyss-plugin-app-builder) |
| 补丁通道 | [`patches/index.json`](patches/index.json) |

## 权限需求

- `FOREGROUND_SERVICE`（长时间构建的前台保活）
- `WAKE_LOCK`（防止切后台构建被系统中断）
- `REQUEST_INSTALL_PACKAGES`（构建产物直接安装）
- `INTERNET`（仅依赖解析请求；rootfs/工具链大下载走 Linux 插件资产通道）

## Shizuku 授权（v0.2.0 起已收敛）

**本插件不再持有 Shizuku 授权** —— 运行环境引擎迁至 Linux 插件后，授权收敛为 Linux 插件**单独一次**（per-app，全生态唯一持有方）。用户装完三个 APK 只需一次授权动作：在 Shizuku 内为「Linux 运行环境」授权。本插件启动握手时读取 Linux 插件的 `RuntimeInfo.shizukuState`，未授权则给出一键引导（APP-100 四类失败引导之一）。

## 三条构建路径（用户自由选择）

```
Linux 运行环境（abyss-plugin-linux · PRoot 用户态 / QEMU 完整系统）
├── 路径一：Anpyra 纯 Python 构建（轻量项目）
├── 路径二：aapt2 + 固定壳 DEX（资源 / HTML 打包）
└── 路径三：标准 Gradle 构建（复杂标准项目，ARM64 工具链）
```

导入项目后**静态预检只提示、不拦截**：每条路径给出推荐理由与风险提示，最终由用户三选一；构建失败可低成本切换路径再试（不清产物、不重导项目）。详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 安装

经深渊工具主壳「导航 → 开发与构建 → 应用构建器」进入。**需先安装 Linux 运行环境插件**：依赖解析未满足时宿主展示「一键一并安装」（拓扑序 `[abyss-plugin-linux → abyss-plugin-app-builder]`），随后引导为 Linux 插件授权一次并下载运行环境资产。**APK 资产自发布流水线起上架**。

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
