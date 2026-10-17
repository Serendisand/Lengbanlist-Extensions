#!/usr/bin/env python3
"""发布后回写 jar 条目的 version / url / sha256（由 release 工作流调用）。

扫描 */target/Lengbanlist-Ext-*.jar，模块目录名即扩展 id。
version 从 jar 内 plugin.yml 读取，url 指向本次 Release 资产。
"""
import hashlib
import json
import os
import re
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INDEX = ROOT / "index.json"
REPO = os.environ.get("GITHUB_REPOSITORY", "Serendisand/Lengbanlist-Extensions")
TAG = os.environ.get("GITHUB_REF_NAME", "")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()


def jar_version(jar: Path) -> str:
    try:
        with zipfile.ZipFile(jar) as zf:
            text = zf.read("plugin.yml").decode("utf-8", "replace")
    except Exception:
        return "0.0.0"
    match = re.search(r'(?m)^version:\s*"?([\w.+-]+)"?', text)
    return match.group(1) if match else "0.0.0"


def main() -> int:
    if not TAG:
        print("GITHUB_REF_NAME 为空，跳过", file=sys.stderr)
        return 1

    data = json.loads(INDEX.read_text(encoding="utf-8"))
    by_id = {e["id"]: e for e in data.get("extensions", []) if e.get("id")}
    updated = 0

    for jar in sorted(ROOT.glob("*/target/Lengbanlist-Ext-*.jar")):
        ext_id = jar.parent.parent.name
        entry = by_id.setdefault(ext_id, {
            "id": ext_id,
            "type": "jar",
            "name": ext_id,
            "author": "Serendisand",
            "requiredApi": "[2.1,3.0)",
            "minCore": "2.1.6",
            "description": "",
        })
        entry["type"] = "jar"
        entry["version"] = jar_version(jar)
        entry["file"] = jar.name
        entry["url"] = f"https://github.com/{REPO}/releases/download/{TAG}/{jar.name}"
        entry["sha256"] = sha256(jar)
        updated += 1
        print(f"updated {ext_id} -> {entry['version']} ({jar.name})")

    data["updated"] = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    data["extensions"] = list(by_id.values())
    INDEX.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("index.json updated: %d jar entries" % updated)
    return 0


if __name__ == "__main__":
    sys.exit(main())
