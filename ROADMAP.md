# 🏗 应用构建器 路线图

> 插件本体实现排期（与深渊工具总体 SRS §6 里程碑对齐）。
> 落地节奏遵循「先底座、再路径、最后交互层」—— 三条路径共享同一个
> Alpine + PRoot 底座，交互层（三选一）在三条路径全部贯通后收口。

- [ ] v0.2 底座拉起：Alpine rootfs + PRoot 可用性探测（Shizuku shell 上下文优先，纯 PRoot 降级，两者皆不可用时明确告知）
- [ ] v0.3 路径一：Anpyra 纯 Python 构建（轻量项目：单 Activity + TextView 子集）
- [ ] v0.4 路径二：内置工具链 aapt2 + 固定壳 DEX（标准资源结构 + HTML/Web 内容打包）
- [ ] v0.5 路径三：标准 Gradle 构建（ARM64 工具链 + 内存/并发约束 + 离线依赖缓存）
- [ ] v0.6 三选一交互层：静态预检提示 / 推荐标记 / 用户自由选择 / 失败后切换 / 底座状态隔离

## 已知风险清单（落地时逐项验证）

- [ ] PRoot 在 ColorOS 15/16（OPPO/OnePlus/Realme）被 ptrace 限制 → Shizuku 上下文启动 + 启动前可用性探测
- [ ] 荣耀 MagicOS 严格 SELinux 策略拒绝 ptrace → 同上（降级链）
- [ ] aapt2 动态链接 glibc vs musl/Bionic → 选型 Android/Bionic 或 musl 编译版，底座内 `ldd` 验证
- [ ] Target SDK 35 的 android-35.jar 与可用 aapt2 兼容性 → compileSdk 暂锁 34
- [ ] Anpyra 能力边界（不支持 lambda / 集合 / 多模块 / 签名）→ 路径一硬性预检而非仅提示
- [ ] 手机端 Gradle 构建耗时（10–30 分钟）与 OOM → 前台服务 + WakeLock + 阶段进度 + 耗时预期管理

## 工程接入清单（v0.2 起逐项落地）

- [ ] 插件工程脚手架（Gradle 模块 + HOST_API v1 契约接入）
- [ ] 首个可用功能切片 + 单元测试
- [ ] APK 构建流水线（GitHub Actions，固定签名）
- [ ] 补丁通道首条记录（数据补丁全链路演练）
