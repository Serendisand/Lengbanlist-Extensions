#!/usr/bin/env python3
"""index.json 自动同步（push 时由 GitHub Action 调用）。

规则：
- 根目录的 <id>.lua → type=lua 条目；name / version 从脚本里的
  leng.name(...) / leng.version(...) 解析，sha256 每次重算。
- 存在 <id>/config.yml 时写入 config 字段（市场只在本地缺配置时释放它）。
- 含 pom.xml 的模块目录 → type=jar 条目；已发布的 version / url / sha256
  由 release 工作流回写，这里只为新模块补 0.0.0 占位。
- index.json 里手写过的 name / author / description 等字段保留。
"""
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INDEX = ROOT / "index.json"

NAME_RE = re.compile(r'leng\s*\.\s*name\s*\(\s*["\']([^"\']+)')
VER_RE = re.compile(r'leng\s*\.\s*version\s*\(\s*["\']([^"\']+)')


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    data = json.loads(INDEX.read_text(encoding="utf-8"))
    existing = {e["id"]: e for e in data.get("extensions", []) if e.get("id")}
    entries = []
    seen = set()

    for lua in sorted(ROOT.glob("*.lua")):
        ext_id = lua.stem
        if ext_id in seen:
            continue
        seen.add(ext_id)
        text = lua.read_text(encoding="utf-8")
        old = existing.get(ext_id, {})
        name = NAME_RE.search(text)
        version = VER_RE.search(text)
        entry = {
            "id": ext_id,
            "type": "lua",
            "name": name.group(1) if name else old.get("name", ext_id),
            "author": old.get("author", "Serendisand"),
            "version": version.group(1) if version else "0.0.0",
            "requiredApi": old.get("requiredApi", "[2.1,3.0)"),
            "minCore": old.get("minCore", "2.1.6"),
            "file": lua.name,
            "sha256": sha256(lua),
            "description": old.get("description", ""),
        }
        config = ROOT / ext_id / "config.yml"
        if config.is_file():
            entry["config"] = f"{ext_id}/config.yml"
        entries.append(entry)

    for pom in sorted(ROOT.glob("*/pom.xml")):
        ext_id = pom.parent.name
        if ext_id in seen:
            continue
        seen.add(ext_id)
        old = existing.get(ext_id, {})
        entries.append({
            "id": ext_id,
            "type": "jar",
            "name": old.get("name", ext_id),
            "author": old.get("author", "Serendisand"),
            "version": old.get("version", "0.0.0"),
            "requiredApi": old.get("requiredApi", "[2.1,3.0)"),
            "minCore": old.get("minCore", "2.1.6"),
            "file": old.get("file", ""),
            "url": old.get("url", ""),
            "sha256": old.get("sha256", ""),
            "description": old.get("description", ""),
        })

    data["schemaVersion"] = data.get("schemaVersion", 1)
    data["updated"] = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    data["extensions"] = entries
    INDEX.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("index.json synced: %d extensions" % len(entries))
    return 0


if __name__ == "__main__":
    sys.exit(main())
