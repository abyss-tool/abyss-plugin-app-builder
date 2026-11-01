# 应用构建器架构（docs/ARCHITECTURE.md）

> 本文档是应用构建器的技术方案单一事实源，整合自架构评审结论。
> 深渊工具主壳（abyss-tool/Abyss-Tools）提供插件框架；本插件自带全部
> 构建能力，宿主只负责安装入口与补丁更新。

## 总览

```
深渊工具主壳（插件框架 / 安装 / 补丁更新 / 独立 Shizuku 授权引导）
└── 应用构建器插件（本仓库，独立 APK）
    └── 共享底座：Alpine + PRoot（自包含，无 Termux 依赖）
        ├── 路径一：Anpyra 纯 Python 构建（轻量项目）
        ├── 路径二：aapt2 + 固定壳 DEX（资源 / HTML 打包）
        └── 路径三：标准 Gradle 构建（复杂标准项目，ARM64 工具链）
```

底座形态：**普通 App 进程 → PRoot → Alpine** 为主链路；受限设备上切换
**Shizuku（ADB shell 权限，uid 2000）→ PRoot → Alpine** 执行链。

## 一、底座层

### 1.1 自包含，告别 Termux

- proot-distro-nolib 等方案已把 PRoot 引擎、Alpine 解压与管理逻辑封装
  成可嵌入 AAR / 原生库，直接打包进本插件 APK；
- PRoot 经 ptrace 在用户态模拟 chroot，免 Root，仅用 App 私有目录，
  底座本体约 4MB；
- 先例：pr、Linuxify 等项目均以 "No Termux dependency" 为核心特性在
  App 内运行 Alpine / Ubuntu；
- 结论：用户只需安装「深渊工具 + 本插件」两个 APK。

### 1.2 PRoot 可用性探测与降级链（关键）

PRoot 依赖 `ptrace()`，**在部分国产定制系统上被平台级策略否决**：

| 系统 | 现象 | 根因 |
| --- | --- | --- |
| ColorOS 15/16（OPPO / OnePlus / Realme） | 运行约 1 分钟后安全警告并杀进程 | ptrace 被安全策略限制 |
| 荣耀 MagicOS | 内核拒绝 | 严格 SELinux 策略 |

这不是能靠代码「优化」解决的问题。因此底座启动必须走降级链：

```
启动底座
  ├─ ① Shizuku 模式：Shizuku 运行中且已授权 → 经 shell 上下文拉起 PRoot（推荐）
  ├─ ② 纯 PRoot 模式：App 沙箱内直接拉起（部分设备不可用）
  └─ ③ 两者皆失败 → 明确告知「当前设备系统限制，内置构建环境不可用」，
       绝不让用户在注定失败的环境里反复尝试
```

探测手段：启动前执行一次极轻量 ptrace 调用验证可用性。

### 1.3 Shizuku 执行层

Shizuku 把 PRoot 的宿主从受限的 App 沙箱换到 ADB shell 执行上下文：

- 链路变为 `Shizuku（uid 2000）→ PRoot → Alpine`，厂商针对「普通应用
  ptrace」的限制策略不直接作用于这一层；
- 路径三受益最大：Gradle 构建需要 aapt2 / zipalign 等执行大量系统
  调用，shell 上下文更接近「正常的 Linux 进程」；
- 路径一 / 路径二统一运行在 Shizuku 拉起的底座内，不为两种权限维护
  两套启动逻辑；
- ColorOS 16 上 Shizuku 本身也可能被「权限监控」拦截，绕过方式：
  系统语言临时切英语 → 开发者选项开启 Disable system optimization →
  重启 Shizuku（可能影响 LTPO 刷新率 / 续航，需在 UI 提示权衡）。

**必须接受的约束**：

- Shizuku 不是 root：uid 2000（shell 级），不能 `su`，不能任意突破
  SELinux —— 它解决「PRoot 被沙箱限制」，不提供越权能力；
- 需用户主动激活：无线调试或 ADB 启动，重启后通常要重新启动一次；
  插件启动时检测 Shizuku 状态，未运行给引导，不假定其常可用；
- **per-app 授权**：本插件是独立 APK，Shizuku 只认授权列表里的包名 ——
  宿主「深渊工具」的授权**不能继承**，用户需为本插件单独授权一次
  （插件详情页与安装引导均明示此点）。

### 1.4 底座状态隔离（三路径共享底座的前提）

- 工具链隔离：路径一装 Python 包 / 路径二放 aapt2 / 路径三装
  JDK+SDK+Gradle，各自独立子目录，互不覆盖；
- 构建工作区隔离：每次构建独立临时目录
  `/workspace/build-<timestamp>`，避免中间产物交叉污染；
- 缓存共享但只读优先：Maven / pip 缓存可跨构建复用，路径间不共享
  构建输出目录；
- 并发控制：同一时间只允许一个构建任务占用底座；切换路径时先取消
  在跑任务再启动。

## 二、三条构建路径

### 路径一：Anpyra 纯 Python（轻量项目）

能力边界（官方文档诚实列出）：不支持 lambda、捕获生命周期局部变量、
异步处理器、任意 import、集合、多模块应用、发布签名；通用布局 /
多子元素 / 回调尚未实现。适用面极窄：单 Activity + TextView + 基础
声明式属性。

**预检纪律**：对含 `res/`、`build.gradle` 或任何 Java/Kotlin 源码的
项目，预检不得只「提示」，应明确标注「包含路径一必然不支持的构造，
构建将无法完成」—— 自由选择 ≠ 放任反复试错。

### 路径二：内置工具链（aapt2 + 固定壳 DEX）

适用：标准资源结构 + HTML/Web 内容 + 固定壳 DEX 可覆盖的场景。

- 底座内预置 ARM64 aapt2 及其依赖；
- 项目含自定义 `src/main/java|kotlin` 源码时提示「固定壳 DEX 不编译
  这些源码，仅打包资源与壳」；
- 失败诊断区分资源编译失败 vs 签名 / 对齐失败。

### 路径三：标准 Gradle（复杂项目兜底）

在手机上跑标准构建链路 `Gradle + JDK + Android SDK`，必须解决四个
工程难题：

**① ARM64 工具链架构错位**（Google 官方工具几乎全是 x86_64）：

```properties
# gradle.properties 强制覆盖
android.aapt2FromMavenOverride=/path/to/arm64/aapt2
```

- NDK：社区 ARM64 编译版（如 lzhiyong/termux-ndk，clang / lld /
  ndk-build 全 ARM 原生）；
- build-tools 优先 ≥ 33.0.0（官方渠道开始含 ARM64 aapt2）。

**② aapt2 的 libc 依赖坑**：官方 / Termux 的 aapt2 可能链接 glibc，
而 Alpine 用 musl、Android 用 Bionic —— 混用会 `undefined symbol`
崩溃。选型必须是为 Android/Bionic 或 musl 明确编译的版本，底座内用
`ldd` 验证动态依赖全部可解析。已知限制：Target SDK 35 的
`android-35.jar` 与可用 aapt2 有兼容问题，compileSdk 暂锁 34（导入
提示中告知）。

**③ 内存与性能约束**：

```properties
org.gradle.jvmargs=-Xmx2048m     # JVM 堆上限，防挤占系统内存
org.gradle.vfs.watch=false        # PRoot 内 inotify 不可靠
org.gradle.workers.max=2          # 限制并发 Worker，防内存峰值
android.builder.sdkDownload=false # 防 Gradle 拉取 x86_64 包
```

**④ Maven 依赖与离线缓存**：底座内维护本地 Maven 缓存 / 私有仓库，
首次联网解析后复用；`~/.gradle` 缓存纳入 App 持久化管理，避免每次
构建重新下载。

**性能现实**：调试构建 1–2 分钟到数分钟甚至更长（设备内存 / 存储 /
依赖数量影响极大）。路径三定位是「没有电脑时的可行兜底」，不是桌面
替代品。

## 三、交互层：导入 → 提示 → 用户三选一

**核心原则：预检只提示，不禁止。** 工具给建议、用户做决定、失败可切换。

```
导入项目
   ↓
静态能力预检（只分析，不拦截）
   ↓
┌──────────────────────────────────────────────┐
│ 选择构建方式                                   │
│ ○ 路径一：Anpyra 纯 Python 构建                │
│   💡 推荐：检测到 Python 子集项目               │
│   ⚠️ 提示：项目含 res/ 资源，可能不支持          │
│ ○ 路径二：内置工具链（aapt2 + 固定壳）           │
│   💡 推荐：检测到标准资源结构                    │
│ ○ 路径三：标准 Gradle 构建                      │
│   💡 推荐：检测到多模块 / Kotlin 源码            │
│   ⚠️ 提示：构建耗时较长，需较多内存              │
└──────────────────────────────────────────────┘
   ↓
用户选定 → 执行构建 → 产出 APK 或失败诊断
```

- 选了「看起来不匹配」的路径 → 弹二次确认（匹配度低仍要继续吗？），
  但不直接拦截；
- 例外：路径一对必然失败的项目特征做**硬性预检标注**（见 2.1）；
- 首次选路径三：展示资源约束说明（内存上限 / 预计耗时区间 / 依赖
  下载量），让用户有预期；
- 构建过程显示阶段进度（依赖解析 → 资源编译 → DEX → 打包），
  前台服务 + WakeLock 对冲后台回收。

### 失败后的回退与切换

- 失败界面直接提供「切换到其他方式」按钮，无需重新导入项目；
- 切换不清理已有产物与底座状态，只切换执行路径；
- 记录每个项目上一次成功的方式，下次导入作为推荐标记（不自动选中）。

## 四、落地节奏

1. **v0.2 底座可用**：Alpine + PRoot 拉起 + 可用性探测 + Shizuku 降级链；
2. **v0.3 路径一贯通**（Anpyra + 硬性预检）；
3. **v0.4 路径二贯通**（ARM64 aapt2 + libc 验证）；
4. **v0.5 路径三贯通**（ARM64 工具链 + 资源约束 + 离线缓存）；
5. **v0.6 三选一交互层**（预检提示 / 推荐标记 / 选择界面 / 失败切换 /
   底座状态隔离）。

## 五、与主壳的契约

| 事项 | 约定 |
| --- | --- |
| 插件形态 | 独立 APK（`abyss-plugin-app-builder`） |
| HOST_API | v1（manifest 发现 + 补丁通道） |
| 安装 | 主壳插件中心：下载 → SHA-256 → PackageInstaller |
| 补丁 | 本仓库 `patches/index.json`（abyss-plugin-patch-v1 数据补丁） |
| Shizuku | **独立授权**（per-app）：`plugin.json` 声明 `shizuku.ownAuth=true`，主壳详情页展示引导，授权动作在插件内完成 |
| 底座位置 | 本插件私有目录（rootfs / 工具链 / 缓存），不污染主壳存储 |
