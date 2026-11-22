# 更新日志

本文件遵循「一版一条」纪律：每个发布版本一个 `## v*` 条目；发布时由
插件 CI 校验当前版本必须有对应条目。

## v0.1.0

- 插件框架期骨架：plugin.json manifest（含 Shizuku 独立授权声明）/
  patches/index.json 补丁通道 / CI 清单门禁（validate_plugin.py，含
  shizuku 声明强制校验）全部就绪
- 技术方案定稿：Alpine + PRoot 底座 + 三条构建路径（Anpyra / 内置
  工具链 / 标准 Gradle）+ 用户自由选择交互层 + Shizuku shell 上下文
  执行，完整架构见 docs/ARCHITECTURE.md
- 插件本体按 ROADMAP.md 排期实现（APK 资产自 v0.2 发布流水线起发布）

## v0.2.0（2026-11-22）

**瘦客户端化（HOST_API v2 · 依赖 Linux 插件）—— ADR-100 D-3 / ARC-103。**

- **manifest v2 化**：`minHost` 1→2（依赖解析与能力绑定需 v2 宿主）；`minApp` 0.2.0→0.10.0；新增 `depends` 声明（`abyss-plugin-linux` · `linux-runtime-v1` · `>=0.2.0 <1.0.0` · 非可选）。
- **删除 `shizuku` 声明**：底座与 Shizuku 授权均已迁至 Linux 插件（唯一持有方）；本插件退化为「项目预检 / 三路径编排 / 产物闭环」的瘦客户端 —— CI 门禁同步反转为「出现即失败」（APP-100 FR-001 / LIN-005 FR-002）。
- **权限集**：新增 `INTERNET`（仅依赖解析请求；rootfs/工具链大下载走 Linux 插件资产通道）。
- **底座文档迁移**：`docs/ARCHITECTURE.md` 第 1 章「底座层」整体迁入 `abyss-plugin-linux/docs/ARCHITECTURE.md`（ARC-103 M-7），本仓改写为「会话消费架构」。
- **工程脚手架瘦化**：m1-scaffold 落地的底座探测三件套（`BaseProbe` / `DegradeChain` / `BaseStateStore` 及其单测）随 ADR-100 迁出 —— 逻辑承接至 `abyss-plugin-linux` 的 `:probe` 模块（ARC-103 M-2，设计署名保留）；`BuilderActivity` 改为依赖握手清单页；`PluginContract` 镜像同步 HOST_API v2。
- **CI 门禁扩展**：`validate_plugin.py` 反转 shizuku 断言 + depends 形态校验 + `minHost>=2` + 静态扫描（仓库 0 文件含 `proot`/`rootfs`/`qemu`/`ptrace`/`Shizuku`/`ILinuxRuntime` 底座实现）。

对齐规格：`ADR-100` / `ARC-103` / `APP-100` / `DC-100` / `SRS-102` / `PLAN-101 §1④`。
