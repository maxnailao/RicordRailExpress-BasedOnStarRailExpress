# -*- coding: utf-8 -*-
"""把内部项目语言文件中缺失的 replay.* / sre.replay.* 键（原始行）合并到外部项目对应语言文件。"""
import json
import re
import sys

LANG = sys.argv[1] if len(sys.argv) > 1 else "zh_cn"
SRC = f"StarRailExpress/src/main/resources/assets/starrailexpress/lang/{LANG}.json"
DST = f"src/main/resources/assets/starrailexpress/lang/{LANG}.json"

with open(SRC, encoding="utf-8") as f:
    src_text = f.read()
with open(DST, encoding="utf-8") as f:
    dst_text = f.read()

dst_keys = set(json.loads(dst_text).keys())

key_re = re.compile(r'^\s*"((?:sre\.)?replay[^"]+)"\s*:')
missing_lines = []
for line in src_text.splitlines():
    m = key_re.match(line)
    if m and m.group(1) not in dst_keys:
        missing_lines.append(line.rstrip("\r\n"))
        dst_keys.add(m.group(1))

if not missing_lines:
    print("no missing keys")
    sys.exit(0)

# 保持原换行风格
nl = "\r\n" if "\r\n" in dst_text else "\n"
dst_lines = dst_text.splitlines()

anchor = -1
for i in range(len(dst_lines) - 1, -1, -1):
    if key_re.match(dst_lines[i]):
        anchor = i
        break
assert anchor >= 0, "no replay key anchor in dst"

if not dst_lines[anchor].rstrip().endswith(","):
    dst_lines[anchor] = dst_lines[anchor].rstrip() + ","

# 插入块：全部带逗号（锚点后还有其他键），最后一行若原本无逗号也补上
to_insert = []
for ln in missing_lines:
    ln = ln.rstrip()
    if not ln.endswith(","):
        ln += ","
    to_insert.append(ln)

for j, ln in enumerate(to_insert):
    dst_lines.insert(anchor + 1 + j, ln)

out = nl.join(dst_lines) + nl
# JSON 合法性校验
json.loads(out)
with open(DST, "w", encoding="utf-8", newline="") as f:
    f.write(out)
print(f"inserted {len(to_insert)} keys after line {anchor + 1}")
