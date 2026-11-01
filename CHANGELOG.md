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
