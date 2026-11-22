#!/usr/bin/env python3
"""插件补丁发布器：GitHub Release + 资产上传 + 补丁通道滚动。

职责：
  1. 读取 make_patch.py 的 meta（sha256 / size / notes）；
  2. 在本插件仓库创建 `v<to>` Release（幂等）并上传 zip 资产（同名先删）；
  3. 本地滚动 patches/index.json：新补丁条目置顶、latest → to，随后产出
     detached 签名 `patches/index.json.minisig`（N-SEC-001，M1 起 ——
     客户端对插件补丁通道先验签再解析；签名材料缺失 → fail-closed 拒绝滚动）
     （提交由 patch-release.yml 的 git 步骤完成）。

凭据：GITHUB_TOKEN 环境变量（Actions 默认令牌，contents:write 即可）。
签名材料：--seed-file（CI secret RELEASE_SIGNING_KEY 原文 base64 单行）+
--pubkey-file（与客户端 TRUSTED_KEYS 一致）。
仓库坐标：GITHUB_REPOSITORY 环境变量（Actions 自动注入），缺省回退
plugin.json.repo。纯 stdlib + pynacl（minisign_py 同构副本）。
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import minisign_py

TOKEN = os.environ.get("GITHUB_TOKEN", "")
API = "https://api.github.com"


def die(msg: str) -> None:
    print(f"[publish-patch] ✗ {msg}", file=sys.stderr)
    sys.exit(1)


def api(method: str, url: str, body: dict | None = None, raw: bytes | None = None,
        not_found_ok: bool = False, retries: int = 4) -> dict:
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, method=method, data=data)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    if raw is not None:
        req.add_header("Content-Type", "application/octet-stream")
    elif data is not None:
        req.add_header("Content-Type", "application/json")
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                payload = resp.read()
                return json.loads(payload.decode()) if payload else {}
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            if e.code == 404 and not_found_ok:
                return {}
            if e.code in (409, 422) and attempt < retries - 1:
                time.sleep(1.5 * (attempt + 1))
                continue
            die(f"GitHub API {method} {url} → HTTP {e.code}\n{detail[:500]}")
        except urllib.error.URLError as e:
            if attempt < retries - 1:
                time.sleep(2 * (attempt + 1))
                continue
            die(f"GitHub API {method} {url} → 网络错误 {e.reason}")
    return {}


def main() -> None:
    parser = argparse.ArgumentParser(description="abyss 插件补丁发布")
    parser.add_argument("--meta", required=True, help="make_patch.py 生成的 meta.json 路径")
    parser.add_argument("--seed-file", type=Path, default=None,
                        help="清单签名私钥（raw seed base64 单行；CI secret RELEASE_SIGNING_KEY）")
    parser.add_argument("--pubkey-file", type=Path, default=None,
                        help="清单签名公钥文本（与客户端 TRUSTED_KEYS 一致）")
    args = parser.parse_args()

    if not TOKEN:
        die("GITHUB_TOKEN 未设置")
    # N-SEC-001（M1 起）：补丁通道签名材料强制（fail-closed）
    if not args.seed_file or not args.pubkey_file:
        die("缺少清单签名材料：--seed-file / --pubkey-file 必须由 patch-release.yml 提供"
            "（RELEASE_SIGNING_KEY secret）—— N-SEC-001 起补丁通道签名强制")
    if not args.seed_file.is_file() or not args.pubkey_file.is_file():
        die("清单签名材料文件不存在（seed / pubkey）")
    try:
        seed, keynum, _ = minisign_py.load_seed(
            args.seed_file.read_text(encoding="utf-8"),
            args.pubkey_file.read_text(encoding="utf-8"),
        )
    except minisign_py.MinisignError as e:
        die(f"清单签名材料校验失败：{e}")
    pub_text = args.pubkey_file.read_text(encoding="utf-8")

    meta = json.loads(Path(args.meta).read_text(encoding="utf-8"))
    pid = meta["plugin"]
    to = meta["to"]
    frm = meta["from"]
    zip_path = Path(meta["zip"])
    if not zip_path.exists():
        die(f"补丁包不存在：{zip_path}")

    repo = os.environ.get("GITHUB_REPOSITORY") or json.loads(
        Path("plugin.json").read_text(encoding="utf-8")
    ).get("repo", "")
    if not repo:
        die("无法确定仓库坐标（GITHUB_REPOSITORY / plugin.json.repo）")

    tag = f"v{to}"
    asset_name = meta["assetName"]
    print(f"[publish-patch] {pid} {frm} → {to} · {repo}")

    # ── 1. Release（幂等；tag 未发布时 404 → 走创建分支）───────────────
    release = api("GET", f"{API}/repos/{repo}/releases/tags/{tag}", not_found_ok=True)
    if "id" in release:
        release_id = release["id"]
        print(f"[publish-patch] 复用既有 Release {tag}（id={release_id}）")
    else:
        created = api(
            "POST",
            f"{API}/repos/{repo}/releases",
            {
                "tag_name": tag,
                "target_commitish": "main",
                "name": f"{pid} {tag}",
                "body": "\n".join(f"- {n}" for n in meta.get("notes", [])) or tag,
            },
        )
        release_id = created.get("id")
        if not release_id:
            die("Release 创建失败")
        print(f"[publish-patch] ✓ Release {tag} 已创建（id={release_id}）")

    # ── 2. 资产上传（同名先删）─────────────────────────────────────────
    existing = {
        a["name"]: a for a in api("GET", f"{API}/repos/{repo}/releases/{release_id}/assets")
    }
    if asset_name in existing:
        api("DELETE", existing[asset_name]["url"])
        print(f"[publish-patch] 移除旧资产 {asset_name}，重新上传")
    with open(zip_path, "rb") as f:
        api(
            "POST",
            f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={asset_name}",
            raw=f.read(),
        )
    download_url = f"https://github.com/{repo}/releases/download/{tag}/{asset_name}"
    print(f"[publish-patch] ✓ 资产已上传 → {download_url}")

    # ── 3. 补丁通道滚动（本地文件；提交交给 workflow 的 git 步骤）───────
    channel_path = Path("patches/index.json")
    channel = json.loads(channel_path.read_text(encoding="utf-8"))
    if channel.get("schema") != "abyss-plugin-patches-v1":
        die(f"patches/index.json schema 非法：{channel.get('schema')!r}")
    if channel.get("plugin") != pid:
        die(f"patches/index.json.plugin({channel.get('plugin')}) 与 meta.plugin({pid}) 不一致")

    patches = [p for p in channel.get("patches", []) if not (p.get("from") == frm and p.get("to") == to)]
    patches.insert(
        0,
        {
            "from": frm,
            "to": to,
            "url": download_url,
            "sha256": meta["sha256"],
            "sizeBytes": meta["sizeBytes"],
            "notes": "；".join(meta.get("notes", [])),
        },
    )
    channel["latest"] = to
    channel["patches"] = patches[:20]
    channel["generatedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    channel_text = json.dumps(channel, ensure_ascii=False, indent=2) + "\n"
    channel_raw = channel_text.encode("utf-8")
    # ── N-SEC-001：通道滚动后立即签名（写入字节 = 验签字节，无归一化）──
    sig = minisign_py.sign_message(
        seed, keynum, channel_raw, "signature from abyss release key", "patches/index.json")
    if not minisign_py.verify(pub_text, sig, channel_raw):
        die("patches/index.json.minisig 签名自验失败 —— 拒绝滚动（fail-closed）")
    channel_path.write_text(channel_text, encoding="utf-8", newline="\n")
    Path("patches/index.json.minisig").write_text(sig, encoding="ascii")
    print(f"[publish-patch] ✓ 补丁通道已滚动并签名：latest=v{to} · {len(patches)} 条记录（.minisig 同 commit 上库）")
    print(f"[publish-patch] 元数据：sha256={meta['sha256']} · {meta['sizeBytes']} bytes")


if __name__ == "__main__":
    main()
