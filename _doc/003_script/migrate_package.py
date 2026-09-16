#!/usr/bin/env python3
"""
z-mist 包名迁移脚本:
  com.zifang.z.mist.*  →  io.github.yuku123.z.mist.*

步骤:
  1. 移动 .java 文件目录
  2. 替换文件内容中的 package 声明和 import 语句
  3. 不动 application*.yml / pom.xml 中的 com.zifang 引用
     (那部分由后续 step 处理,例如 application.yml type-aliases-package)
"""
import os
import re
import shutil
from pathlib import Path

ROOT = Path("/Users/zifang/workplace/ceo_workplace/z-opc-foundation/z-mist")
OLD_PKG_DIR = "com/zifang/z/mist"
NEW_PKG_DIR = "io/github/yuku123/z/mist"
OLD_PKG_DOT = "com.zifang.z.mist"
NEW_PKG_DOT = "io.github.yuku123.z.mist"

# ---------- 1. 移动 java 文件 ----------
moved = 0
for java_file in ROOT.rglob("*.java"):
    parts = java_file.parts
    try:
        idx = parts.index(OLD_PKG_DIR.split("/")[0])
    except ValueError:
        continue
    # 验证完整路径包含 OLD_PKG_DIR
    rel = java_file.relative_to(ROOT)
    rel_str = str(rel)
    if OLD_PKG_DIR not in rel_str:
        continue
    new_rel_str = rel_str.replace(OLD_PKG_DIR, NEW_PKG_DIR)
    new_path = ROOT / new_rel_str
    new_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(java_file), str(new_path))
    moved += 1

print(f"✅ 移动 {moved} 个 .java 文件")

# ---------- 2. 替换 package + import ----------
changed = 0
for java_file in ROOT.rglob("*.java"):
    text = java_file.read_text(encoding="utf-8")
    new_text = re.sub(re.escape(OLD_PKG_DOT), NEW_PKG_DOT, text)
    if new_text != text:
        java_file.write_text(new_text, encoding="utf-8")
        changed += 1

print(f"✅ 替换 {changed} 个 .java 文件内容")

# ---------- 3. 清理空目录 ----------
removed_dirs = 0
for dirpath, dirnames, filenames in os.walk(ROOT, topdown=False):
    full = Path(dirpath)
    if OLD_PKG_DIR in str(full) and not any(full.iterdir()):
        full.rmdir()
        removed_dirs += 1
print(f"✅ 清理 {removed_dirs} 个空目录")

# ---------- 4. 校验:不应再有 com/zifang/z/mist 路径 ----------
remaining = list(ROOT.rglob(f"{OLD_PKG_DIR.split('/')[0]}/{OLD_PKG_DIR.split('/')[1]}/{OLD_PKG_DIR.split('/')[2]}/{OLD_PKG_DIR.split('/')[3]}"))
print(f"❌ 剩余 com/zifang/z/mist 路径: {len(remaining)}")
if remaining:
    for r in remaining[:5]:
        print(f"   {r}")