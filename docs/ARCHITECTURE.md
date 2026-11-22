# 应用构建器架构（docs/ARCHITECTURE.md）

> 本文档是应用构建器的技术方案单一事实源。**v0.2.0 起为瘦客户端**
> （ADR-100 D-3 / ARC-103）：底座层（Alpine + PRoot / 探测 / 授权 /
> 资产管线）整体迁至 `abyss-plugin-linux` —— 原第 1 章「底座层」已
> 迁入该仓 `docs/ARCHITECTURE.md` 并扩写为双执行域形态；本仓只保留
> 「会话消费架构」。
> 深渊工具主壳（abyss-tool/Abyss-Tools，HOST_API v2）提供插件框架与
> PluginBroker 中介；本插件自带构建编排能力，运行环境依赖 Linux 插件。

## 总览（三进程 · ARC-100 §2）

```
深渊工具主壳（PluginBroker 中介 · HOST_API v2）
└── 应用构建器插件（本仓库，独立 APK · 瘦客户端）
    └── 经宿主 getCapability("linux-runtime-v1") 中介依赖
        └── abyss-plugin-linux（LinuxRuntimeService · RuntimeSession）
            ├── 双执行域：PROOT_USERLAND（默认）/ QEMU_VM（兜底）
            ├── 资产通道 abyss-asset-v1（rootfs / 镜像 / 工具链）
            └── Shizuku 唯一授权（uid 2000）
```

关键单向性：**构建器 → 宿主 → Linux 插件**。构建器永不直接持有 Linux
插件的 Binder 全权（`ILinuxRuntime` 为宿主侧专属 —— 本仓静态扫描零
引用），也不持有 Shizuku 句柄；授权动作收敛为 Linux 插件一次。

## 一、依赖声明与启动编排（APP-100）

manifest（v2）：`minHost=2`、`depends=[{abyss-plugin-linux,
linux-runtime-v1, >=0.2.0 <1.0.0, optional=false}]`、**无 shizuku 声明**
（CI 门禁「出现即失败」）。

启动握手七步（每步失败给人话引导，不暴露错误码原文）：

```
1. 读自身 manifest（minHost=2 / depends 合法）
2. IAbyssPluginHost.getCapability("linux-runtime-v1")
   ├─ 失败：E_DEP_MISSING →「请先安装 Linux 插件」+ 一键一并安装（拓扑序）
   │        E_DEP_VERSION →「Linux 插件需升级到 ≥0.2.0」
   │        E_HOST_API_TOO_LOW →「请升级深渊工具主壳」
3. getRuntimeInfo()（第一帧快照：版本 / 域 / 资产 / 授权 / 探测摘要）
4. 按 RuntimeInfo 分流：
   ├─ assetsReady=false →「运行环境组件尚未下载完成」→ Linux 插件资产页
   ├─ shizukuState≠GRANTED →「请为 Linux 插件单独授权一次」（授权次数 1）
   └─ probeSummary=PROOT_BLOCKED → 切域选项（QEMU_VM + 耗时/存储预期）
5. openSession(spec: preferredDomain=PROOT_USERLAND)
6. requestAssets(toolchainIds) → 等待 assetsReady（真实进度，不启动构建）
7. 构建（阶段进度）→ 失败切路径（复用同一 sessionId，产物保留）
```

- SessionGrant 不缓存：每次 openSession 重新申请，过期自动重新
  `getCapability`（FR-002）；
- 一个构建 = 一次 `openSession`；切路径复用同一会话（N-3）。

## 二、三路径会话编排（APP-101 —— 与执行域正交）

| 维度 | 路径一 Anpyra | 路径二 aapt2+固定壳 | 路径三 Gradle |
| --- | --- | --- | --- |
| 适用 | 单 Activity + TextView 子集 | 标准资源 / HTML·Web | 多模块 / Kotlin / 依赖多 |
| 工具链资产 | anpyra-py-* | aapt2-arm64-* / shell-dex-* | jdk17-arm64 / sdk-34 / gradle-* |
| 默认域 | PROOT_USERLAND | PROOT_USERLAND | PROOT_USERLAND（QEMU 兜底） |
| 耗时预期 | 秒级~1 分钟 | 1–3 分钟 | 10–30 分钟 |

统一编排模板：

```
openSession(spec) → fileIn(project.zip → 工作区)
 → requestAssets(toolchainIds) → 阶段执行 session.exec(argv, sink)
 → 阶段1 依赖解析 / 资源编译 → 阶段2 DEX / 打包 → 阶段3 签名 / 对齐
 → fileOut(output.apk) → close() 或复用会话切路径
```

纪律：`ExecCommand` argv 数组下发（禁 shell 字符串拼接）；每次 exec 带
`stage` 标签（进度条一一对应）；`maxConcurrentExec=1`；切路径复用
sessionId 不清产物不重导；工具链**请求**（requestAssets）而非自行
下载/校验（K-8：工具链版本 + 资产 id 写构建记录）。

预检交互（K-1/K-2）：只提示不拦截（例外：路径一对必然失败特征硬性标注
「构建将无法完成」）；推荐标记真实来自预检结果；首次选路径三展示资源
约束（内存上限 / 预计耗时区间 / 依赖下载量）。

## 三、构建产物闭环（APP-102）

会话内 `app-unsigned.apk` → `fileOut()` → 构建器进程：

1. 落 App 私有缓存（临时名，canonical 断言）；
2. zipalign 对齐校验（4 字节 / 16KB 页兼容）；
3. 签名：v2+v3、v1 关闭（minSdk 26）；产物密钥独立于插件发布签名，
   首次使用生成并提示用户备份（禁用发布签名签用户产物）；
4. apksigner verify（签名集 + 指纹）；
5. SHA-256 写构建记录（工具链 id / sessionId / 阶段耗时 / 产物摘要）；
6. 安装网关：同签名门禁 + 确认页转发 + 结果广播 + 超时 abandon。

八项校验（fail-closed）：empty / format / align / sign / digest / meta /
path-traversal / signature —— 任一失败保留原始产物与日志，不删除、
不静默重试、不自动换密钥。

失败诊断七分类（可切路径性标注）：DEPS / RES（可，同会话）、DEX / PACK
（可，路径二↔路径三）、SIGN / INSTALL（否，产物有效仅修安装）、RUNTIME
（否，走 LIN-004 引导 / 切域）。

## 四、与主壳及 Linux 插件的契约

| 事项 | 约定 |
| --- | --- |
| 插件形态 | 独立 APK（`abyss-plugin-app-builder`） |
| HOST_API | v2（minHost=2；v1 宿主 fail-closed 并提示升级） |
| 依赖 | `depends: abyss-plugin-linux/linux-runtime-v1 >=0.2.0 <1.0.0`（非可选） |
| 安装序 | 宿主 PluginBroker 拓扑序：`[abyss-plugin-linux → abyss-plugin-app-builder]` |
| 会话 | 经宿主中介拿受限 `ILinuxSession`（exec/pty/fileIn/fileOut/close） |
| 补丁 | 本仓库 `patches/index.json`（abyss-plugin-patches-v1，仅小数据） |
| Shizuku | **不持有** —— Linux 插件唯一授权；本插件握手时读 RuntimeInfo.shizukuState 做引导 |
| 权限 | FOREGROUND_SERVICE / WAKE_LOCK / REQUEST_INSTALL_PACKAGES / INTERNET |
| 直连 | 禁止 —— `ILinuxRuntime` 零引用（CI 静态扫描）；直连即 E_DIRECT_BIND_DENIED |

## 五、落地节奏

1. **v0.2 依赖握手**（APP-100）：manifest v2 + 四类失败引导 +
   SessionGrant 消费 + 一并安装拓扑序；
2. **v0.3~v0.5 三路径贯通**（各路径独立演进，工具链经资产请求）；
3. **v0.6 三选一交互层**：预检提示 / 推荐标记 / 选择界面 / 失败切换 /
   会话状态隔离。

> 工程脚手架（Gradle 模块 / HOST_API v2 契约接入 / APK 流水线）随
> v0.2 起逐步落地：`:runtime-client`（AIDL 客户端 + grant 消费 +
> DeathRecipient）、`:precheck`、`:paths`、`:delivery`（ARC-103 §5）。
