#!/usr/bin/env python3
"""插件仓 CI 门禁：plugin.json / CHANGELOG / 补丁通道三处一致性校验。

应用构建器 v0.2.0 起为**瘦客户端**（ADR-100 D-3 / ARC-103）—— 底座
（Alpine + PRoot）与 Shizuku 授权均已迁至 abyss-plugin-linux。校验项：
  - category = dev；minHost >= 2（依赖解析与能力绑定需 v2 宿主）
  - **不得出现 shizuku 声明** —— 出现即失败（APP-100 FR-001 /
    LIN-005 FR-002：授权收敛为 Linux 插件唯一持有方）
  - depends 逐条校验（pluginId kebab-case / capability 形态 /
    versionRange 可解析 / optional=false 时 reason 非空 ≥10 字符）
  - 三处一致性（version / CHANGELOG / patches.latest）
  - 静态扫描：仓库内 0 文件含底座实现关键字（proot / rootfs / qemu /
    ptrace / Shizuku / ILinuxRuntime —— SRS-100 S-001，文档说明性
    引用与 CI 脚本自身不计）
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import minisign_py

# N-SEC-001：公钥唯一事实源 = 主仓 ChannelSignatureVerifier.kt TRUSTED_KEYS
#（轮换走双公钥：新增不替换）。
_TRUSTED_PUBKEYS = [
    """
    untrusted comment: Abyss-Tools manifest signing public key (meng4117222)
    RWRx16ORC4gsdxs6DsnctDYagiRptPX1NgFcVpQNYqh/BXGhPAHebh3B
    """,
]

SEMVER = re.compile(r"^\d+\.\d+\.\d+$")
REQUIRED = ("id", "name", "repo", "category", "version", "minApp", "summary")
CATEGORIES = {"terminal", "system", "sandbox", "disguise", "media",
              "automation", "desktop", "network", "data", "tools", "dev"}
CAPABILITY_ID = re.compile(r"^[a-z0-9-]+-v[0-9]+$")
HOST_API_VERSION = 2  # 主仓 PluginContract.HOST_API_VERSION（v2）

# 底座实现关键字黑名单（ARC-103 迁移后扫描断言；说明性文档与 CI 自身不计）
# 注：不扫描裸 "shizuku" —— 瘦客户端合法消费 RuntimeInfo.shizukuState /
# SessionGrant 等字段名做授权引导；要拦的是 Shizuku AAR 的实现引用
# （dev.rikka.shizuku 坐标）与 ILinuxRuntime 全权接口持有（AC-001）。
BASE_IMPL_KEYWORDS = ("ILinuxRuntime", "proot", "rootfs", "qemu", "ptrace", "rikka.shizuku")
SCAN_EXEMPT = (
    "tools/validate_plugin.py",   # CI 脚本自身
    "patches/",                   # 补丁通道载荷（小数据）
)

errors = []


def fail(msg):
    errors.append(msg)


manifest = json.loads(Path("plugin.json").read_text(encoding="utf-8"))
repo_full = Path(".git/config").read_text(encoding="utf-8")
expected_repo = None
for line in repo_full.splitlines():
    if "url = https://github.com/" in line:
        expected_repo = line.split("github.com/")[1].removesuffix(".git").strip()
        break
if expected_repo is None:
    expected_repo = __import__("os").environ.get("GITHUB_REPOSITORY", "")

for field in REQUIRED:
    if not manifest.get(field):
        fail(f"plugin.json 缺少必填字段 {field}")

pid = manifest.get("id", "")
if not re.match(r"^abyss-plugin-[a-z0-9]+(-[a-z0-9]+)*$", pid):
    fail(f"插件 id 形态非法 → {pid!r}")
if expected_repo and manifest.get("repo") != expected_repo:
    fail(f"plugin.json.repo({manifest.get('repo')}) 与仓库({expected_repo})不一致")
if expected_repo and not expected_repo.endswith(pid):
    fail("仓库名末段应等于插件 id")
if manifest.get("category") not in CATEGORIES:
    fail(f"category 非法 → {manifest.get('category')!r}")
for key in ("version", "minApp"):
    if not SEMVER.match(manifest.get(key, "")):
        fail(f"plugin.json.{key} 非法 SemVer → {manifest.get(key)!r}")
min_host = manifest.get("minHost", 1)
if not isinstance(min_host, int) or min_host < 1:
    fail("plugin.json.minHost 应为正整数")
elif min_host < 2:
    fail("plugin.json.minHost 必须 >= 2（depends 声明依赖 HOST_API v2 宿主的依赖解析与能力绑定，ARC-102 §2）")

# ── 瘦客户端断言（反转）：shizuku 出现即失败 ─────────────────────────────
if "shizuku" in manifest:
    fail("plugin.json.shizuku 不得出现 —— 底座与授权已迁至 abyss-plugin-linux（APP-100 FR-001 / LIN-005 FR-002），本插件为瘦客户端，不持有 Shizuku 授权")

# ── depends 逐条校验（DC-100 §3）────────────────────────────────────────
depends = manifest.get("depends")
if not isinstance(depends, list) or not depends:
    fail("plugin.json.depends 缺失或为空 —— 瘦客户端必须声明运行环境依赖（APP-100 FR-001）")
else:
    if not isinstance(depends, list) or len(depends) != 1 or not isinstance(depends[0], dict):
        fail("depends 应恰有一条（当前实现支持单层依赖）")
    for item in depends:
        if not isinstance(item, dict):
            fail(f"depends 条目应为对象 → {item!r}")
            continue
        dep_id = item.get("pluginId", "")
        if not re.match(r"^[a-z0-9]+(-[a-z0-9]+)+$", dep_id):
            fail(f"depends[].pluginId 形态非法 → {dep_id!r}")
        elif dep_id != "abyss-plugin-linux":
            fail(f"depends[].pluginId 当前唯一注册能力方为 abyss-plugin-linux → {dep_id!r}")
        if item.get("capability") != "linux-runtime-v1":
            fail(f"depends[{dep_id}].capability 必须为 linux-runtime-v1 → {item.get('capability')!r}")
        vr = item.get("versionRange", "")
        if not re.match(r"^>=\d+\.\d+\.\d+(\s+<\d+\.\d+\.\d+)?$", vr):
            fail(f"depends[{dep_id}].versionRange 应为 SemVer 区间形态（如 >=0.2.0 <1.0.0）→ {vr!r}")
        if item.get("optional") is not False:
            fail(f"depends[{dep_id}].optional 必须为 false（宿主 fail-closed，APP-100 FR-001）")
        reason = item.get("reason", "")
        if not reason or len(reason) < 10:
            fail(f"depends[{dep_id}].reason 必填且 >=10 字符（DC-100 §3）")

changelog = Path("CHANGELOG.md").read_text(encoding="utf-8")
if f"## v{manifest.get('version')}" not in changelog:
    fail(f"CHANGELOG.md 缺少当前版本条目 ## v{manifest.get('version')}")

channel = json.loads(Path("patches/index.json").read_text(encoding="utf-8"))
if channel.get("schema") != "abyss-plugin-patches-v1":
    fail("patches/index.json schema 应为 abyss-plugin-patches-v1")
if channel.get("plugin") != pid:
    fail(f"patches/index.json.plugin({channel.get('plugin')}) 与 id 不一致")
if channel.get("latest") != manifest.get("version"):
    fail(f"patches/index.json.latest({channel.get('latest')}) 与 version 不一致")
seen = set()
for p in channel.get("patches", []):
    key = (p.get("from"), p.get("to"))
    if key in seen:
        fail(f"补丁段重复 {key}")
    seen.add(key)
    if not p.get("url"):
        fail(f"补丁 {key} 缺 url")

# ── 静态扫描：实现代码零底座关键字（ARC-103 AC-001 / SRS-100 S-001）────
hits = []
for path in Path(".").rglob("*"):
    if not path.is_file() or any(path.as_posix().startswith(e) for e in SCAN_EXEMPT):
        continue
    if path.suffix not in (".kt", ".java", ".gradle", ".kts"):
        continue  # 实现代码扫描范围：src 与构建脚本（文档 md 的说明性引用不计）
    try:
        text = path.read_text(encoding="utf-8")
    except Exception:
        continue
    for kw in BASE_IMPL_KEYWORDS:
        if kw.lower() in text.lower():
            hits.append(f"{path.as_posix()}: {kw}")
for hit in hits:
    fail(f"底座实现关键字残留 → {hit}（底座已迁至 abyss-plugin-linux，本仓为瘦客户端）")

# ── N-SEC-001：补丁通道 detached 签名断言（存在必可验，缺失即拒）────
_sig_path = Path("patches/index.json.minisig")
if not _sig_path.exists():
    fail("patches/index.json.minisig 缺失 —— N-SEC-001 起补丁通道签名强制"
         "（由 sign-channel.yml / patch-release.yml 以 RELEASE_SIGNING_KEY "
         "secret 原子产出；客户端对补丁通道先验签再解析，无签名 = 不信任）")
else:
    _raw = Path("patches/index.json").read_bytes()
    if not any(minisign_py.verify(pk, _sig_path.read_text(encoding="utf-8"), _raw)
               for pk in _TRUSTED_PUBKEYS):
        fail("patches/index.json.minisig 验签失败 —— 通道字节与签名不配对（升版后需经 Sign Patch Channel 重签）")
    else:
        print("[plugin-ci] ✓ 补丁通道 detached 签名验签通过")

if errors:
    print(f"[plugin-ci] ✗ {len(errors)} 处校验失败：", file=sys.stderr)
    for e in errors:
        print(f"  - {e}", file=sys.stderr)
    sys.exit(1)
print(f"[plugin-ci] ✓ {pid} v{manifest.get('version')} 校验通过"
      f"（瘦客户端 · minHost={min_host} · depends abyss-plugin-linux/linux-runtime-v1 · "
      f"shizuku 已收敛至 Linux 插件 · 底座扫描零残留 · "
      f"{len(channel.get('patches', []))} 条补丁记录 · 通道签名已验）")
