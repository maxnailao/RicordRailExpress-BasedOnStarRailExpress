# -*- coding: utf-8 -*-
"""把内部提交 36fc16698 中 noellesroles 语言文件新增的 replay.* 键合并到外层对应语言文件。"""
import json
import subprocess
import sys

INNER_REPO = "StarRailExpress"
COMMIT = "36fc16698"
KEYS = [
    "replay.noellesroles.yuyuko.eat",
    "replay.event.rabbit.restore",
    "replay.pelican.eat",
    "replay.pelican.spit",
    "replay.pelican.spit_desperado",
    "replay.pelican.spit_death",
]


def load_commit_lang(lang):
    path = f"src/main/resources/assets/noellesroles/lang/{lang}.json"
    text = subprocess.check_output(
        ["git", "show", f"{COMMIT}:{path}"], cwd=INNER_REPO
    ).decode("utf-8-sig")
    return json.loads(text)


for lang in ["zh_cn", "zh_tw", "en_us"]:
    inner = load_commit_lang(lang)
    dst_path = f"src/main/resources/assets/noellesroles/lang/{lang}.json"
    with open(dst_path, "rb") as f:
        raw_bytes = f.read()
    had_bom = raw_bytes.startswith(b"\xef\xbb\xbf")
    raw = raw_bytes.decode("utf-8-sig")
    outer = json.loads(raw)

    to_add = {}
    for k in KEYS:
        if k not in outer and k in inner:
            to_add[k] = inner[k]
    if not to_add:
        print(f"{lang}: nothing to add")
        continue

    # 找锚点行：最后一个 message.noellesroles.pelican 键行，之后插入
    lines = raw.splitlines()
    nl = "\r\n" if "\r\n" in raw else "\n"
    anchor = -1
    for i in range(len(lines) - 1, -1, -1):
        if '"message.noellesroles.pelican.spat_out_dead"' in lines[i]:
            anchor = i
            break
    if anchor < 0:
        # 退而求其次：最后一个以非 } 结尾的键行
        for i in range(len(lines) - 1, -1, -1):
            if '":' in lines[i]:
                anchor = i
                break
    assert anchor >= 0, f"no anchor in {lang}"

    # 检测缩进
    indent = lines[anchor][: len(lines[anchor]) - len(lines[anchor].lstrip())]
    if not lines[anchor].rstrip().endswith(","):
        lines[anchor] = lines[anchor].rstrip() + ","

    block = []
    items = list(to_add.items())
    # 锚点后还有其他键，因此插入的每一行都带逗号（锚点行已在上面补逗号）
    has_following = anchor + 1 < len(lines) and lines[anchor + 1].strip() not in ("", "}")
    for idx, (k, v) in enumerate(items):
        comma = "," if (idx < len(items) - 1 or has_following) else ""
        block.append(f"{indent}{json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}{comma}")

    for j, ln in enumerate(block):
        lines.insert(anchor + 1 + j, ln)

    out = nl.join(lines)
    out_check = out.lstrip("\ufeff")
    json.loads(out_check)  # 校验
    final = ("\ufeff" if had_bom else "") + out
    if raw.endswith("\n"):
        final += "\n" if not final.endswith("\n") else ""
    with open(dst_path, "w", encoding="utf-8", newline="") as f:
        f.write(final)
    print(f"{lang}: added {len(to_add)} keys: {list(to_add.keys())}")
