# -*- coding: utf-8 -*-
"""
【仅替换美术资源】把 临时/投稿/ 下的新帽子模型/贴图，覆盖到现有皮肤 id 上。

严格约束（本次任务要求）：
  · 只覆盖 models/item/skins/hat/<skin>.json 与 textures/item/skins/hat/<skin>.png
  · **不新增皮肤 id、不改品质、不改物品注册、不改箱子奖池、不改语言文件**
  · 因此服务端已有的物品 / 皮肤 / 品质 / 抽奖结果完全不变，玩家看到的只是"换了个外观"

用法：
    python tools/hat_replace.py            # 全部替换
    python tools/hat_replace.py 菜刀 王冠   # 只替换名称含这些关键字的
"""
import io
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import hat_convert as hc  # noqa: E402  复用它的 convert()/task()

ROOT = hc.ROOT
SUB = os.path.join(ROOT, "临时", "投稿")


def load_name2skin():
    """从 tools/hat_items.py 读「中文名 -> 现有皮肤 id」，保证 id 与服务端一致"""
    src = open(os.path.join(ROOT, "tools", "hat_items.py"), encoding="utf-8").read()
    m = re.search(r"HATS = \[(.*?)\n\]", src, re.S)
    out = {}
    for _item, skin, cn in re.findall(r'\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', m.group(1)):
        out[cn] = skin
    return out


NAME2SKIN = load_name2skin()


def resolve_skin(cn):
    """中文名 -> 皮肤 id；支持包含式匹配（如 T7热成像 -> T7 头盔）"""
    if cn in NAME2SKIN:
        return NAME2SKIN[cn]
    cand = [s for n, s in NAME2SKIN.items() if n and (n in cn or cn in n)]
    if len(cand) == 1:
        return cand[0]
    # 再退一步：去掉常见修饰词后按残留词匹配（"T7热成像" → "T7" → "T7 头盔"）
    for noise in ("热成像", "头套", "帽子", "头冠", "面具", "光环", "员工帽"):
        base = cn.replace(noise, "").strip()
        if not base or base == cn:
            continue
        for n, s in NAME2SKIN.items():
            if n and (base in n or n.startswith(base)):
                return s
    return None


def build_tasks():
    tasks = []
    for d in sorted(os.listdir(SUB)):
        p = os.path.join(SUB, d)
        if not os.path.isdir(p):
            continue
        cn = re.split(r"\s+品质", d)[0].strip()
        skin = resolve_skin(cn)
        if skin is None:
            print("[SKIP ] 目录 %-28s 无法匹配到现有皮肤 id" % d)
            continue
        jsons = [f for f in os.listdir(p) if f.endswith(".json") and not f.endswith(".bbmodel")]
        pngs = [f for f in os.listdir(p) if f.endswith(".png")]
        if len(jsons) != 1 or len(pngs) != 1:
            print("[SKIP ] 目录 %-28s json=%s png=%s（不唯一）" % (d, jsons, pngs))
            continue
        jf, pf = jsons[0], pngs[0]

        # ★ 贴图槽名必须从源模型里逐个读，**不能假定是 "0"**。
        #   hat_convert.task() 默认只映射 slot "0"，而投稿里「小鸡」的槽名是 "1"，
        #   会导致 tex_map 取不到值、输出贴图名变成 "null.png" 而被跳过（漏换贴图）。
        model = json.load(open(os.path.join(p, jf), encoding="utf-8"))
        slots = [s for s in (model.get("textures") or {}) if s != "particle"]
        tex_map = {s: hc.tex(skin) for s in slots}
        tex_map["particle"] = hc.tex(skin)
        tex_copy = {s: os.path.join(p, pf) for s in slots}
        spec = {"SRC": os.path.join(p, jf), "SKIN_NAME": skin,
                "TEX_MAP": tex_map, "TEX_COPY": tex_copy}
        tasks.append((spec, cn, d))
    return tasks


def main():
    only = sys.argv[1:] or None
    tasks = build_tasks()

    # 安全阀：先确认要覆盖的目标文件都已存在（避免误当"新增"来写）
    print("=== 将覆盖的现有资源 ===")
    missing = []
    for t, cn, d in tasks:
        mpath = os.path.join(hc.MODEL_OUT_DIR, t["SKIN_NAME"] + ".json")
        tpath = os.path.join(hc.TEX_OUT_DIR, t["SKIN_NAME"] + ".png")
        ok = os.path.isfile(mpath) and os.path.isfile(tpath)
        if not ok:
            missing.append((cn, t["SKIN_NAME"], os.path.isfile(mpath), os.path.isfile(tpath)))
    if missing:
        print("!! 以下目标资源不完整（会被当成新增写入，请确认）：")
        for cn, skin, m, tx in missing:
            print("   %-24s %-34s 模型存在=%s 贴图存在=%s" % (cn, skin, m, tx))
        print()

    ran = 0
    for t, cn, d in tasks:
        if only and not any(o in cn or o in t["SKIN_NAME"] for o in only):
            continue
        mpath = os.path.join(hc.MODEL_OUT_DIR, t["SKIN_NAME"] + ".json")
        print("--- %s（%s）-> %s ---" % (cn, d, t["SKIN_NAME"]))
        model, _ = hc.convert(t["SRC"], t["SKIN_NAME"], t["TEX_MAP"], t["TEX_COPY"])
        if model is None:
            print("   跳过（源缺失）")
            continue
        ran += 1

    print("\n完成替换 %d 个帽子（只覆盖模型与贴图，物品/品质/箱子未改动）。" % ran)


if __name__ == "__main__":
    main()
