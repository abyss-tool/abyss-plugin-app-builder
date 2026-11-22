# 🏗 应用构建器 路线图

> 插件本体实现排期（与深渊工具总体 SRS §6 里程碑对齐）。
> **v0.2.0 起为瘦客户端**（ADR-100 D-3 / ARC-103）：运行环境依赖
> `abyss-plugin-linux`（linux-runtime-v1），本插件只做「预检 / 三路径
> 编排 / 产物闭环」—— 落地节奏从「先底座」改为「先依赖握手」。

- [ ] v0.2 依赖握手与会话编排（APP-100）：manifest v2 / 四类失败引导 / SessionGrant 消费与续期 / 一并安装拓扑序 / requestAssets 工具链请求
- [ ] v0.3 路径一：Anpyra 纯 Python 构建（轻量项目：单 Activity + TextView 子集；硬性预检标注）
- [ ] v0.4 路径二：aapt2 + 固定壳 DEX（标准资源结构 + HTML/Web 内容打包；ldd 验证门禁由 Linux 插件把守）
- [ ] v0.5 路径三：标准 Gradle 构建（ARM64 工具链资产请求 + 内存/并发约束 + 离线依赖缓存）
- [ ] v0.6 三选一交互层：静态预检提示 / 推荐标记 / 用户自由选择 / 失败后切换（复用同一会话）/ 会话状态隔离

## 已知风险清单（落地时逐项验证 · 已迁往 Linux 插件仓的项标注）

- [x] ~~PRoot ptrace 平台限制 / Shizuku 降级链~~ → 迁至 `abyss-plugin-linux`（LIN-001 C-8 / LIN-005）
- [x] ~~rootfs / 工具链下载与校验~~ → 迁至 `abyss-plugin-linux`（LIN-003 abyss-asset-v1）
- [ ] 依赖插件缺失 / 版本低 / 未授权 / 资产未就绪四类引导的用户可达性（APP-100 AC-002，各状态截图留痕）
- [ ] Anpyra 能力边界（不支持 lambda / 集合 / 多模块 / 签名）→ 路径一硬性预检而非仅提示
- [ ] 手机端 Gradle 构建耗时（10–30 分钟）与 OOM → 前台服务 + WakeLock + 阶段进度 + 耗时预期管理（引擎侧资源约束由 Linux 插件 ResourceLimits 把守）
- [ ] 失败切路径复用同一 sessionId（产物保留、无需重导）→ LIN-004 FR-009 两侧联动验证

## 工程接入清单（v0.2 起逐项落地）

- [x] plugin.json v2（minHost=2 / depends / 删 shizuku）+ validate_plugin 反转断言（v0.2.0 ✅）
- [x] docs/ARCHITECTURE.md 瘦客户端化（底座章节迁至 Linux 插件仓）（v0.2.0 ✅）
- [ ] 插件工程脚手架（Gradle 模块 `:runtime-client` / `:precheck` / `:paths` / `:delivery` + HOST_API v2 契约接入）
- [ ] 首个可用功能切片 + 单元测试
- [ ] APK 构建流水线（GitHub Actions，固定签名）
- [ ] 补丁通道首条记录（数据补丁全链路演练）
