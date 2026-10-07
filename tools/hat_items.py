# -*- coding: utf-8 -*-
"""
hat_items.py — 为瑞科德饰品帽子生成物品模型 + 维护 lang/zh_cn.json 条目。

用法：python tools/hat_items.py
幂等：已存在的条目会先移除再按当前清单重写，不会重复累积。
"""
import io
import json
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_MODEL_DIR = os.path.join(ROOT, "src/main/resources/assets/noellesroles/models/item")
LANG = os.path.join(ROOT, "src/main/resources/assets/noellesroles/lang/zh_cn.json")

# (物品id, 皮肤名, 中文名)  —— 顺序即文件中的插入顺序
HATS = [
    ("ricord_cat_headset", "hat_ricord_cat_headset", "小猫耳机"),
    ("ricord_chushimao", "hat_ricord_chushimao", "厨师帽"),
    ("ricord_matongchou", "hat_ricord_matongchou", "马桶抽"),
    ("ricord_kuanggongmao", "hat_ricord_kuanggongmao", "矿工帽"),
    ("ricord_limao", "hat_ricord_limao", "礼帽"),
    ("ricord_xiaodangao", "hat_ricord_xiaodangao", "小蛋糕"),
    ("ricord_xiaoji", "hat_ricord_xiaoji", "小鸡"),
    ("ricord_helmet", "hat_ricord_helmet", "钢盔"),
    ("ricord_helmet_t", "hat_ricord_helmet_t", "钢盔+T"),
    ("ricord_shengdanmao", "hat_ricord_shengdanmao", "圣诞帽"),
    ("ricord_caidao", "hat_ricord_caidao", "菜刀"),
    ("ricord_jidan", "hat_ricord_jidan", "鸡蛋"),
    ("ricord_beleimao", "hat_ricord_beleimao", "贝雷帽"),
    ("ricord_nainao", "hat_ricord_nainao", "奶闹"),
    ("ricord_xiaohonghua", "hat_ricord_xiaohonghua", "小红花"),
    ("ricord_xiaoji_taotao", "hat_ricord_xiaoji_taotao", "小鸡头套"),
    ("ricord_xiaoqie_taotao", "hat_ricord_xiaoqie_taotao", "小企鹅头套"),
    ("ricord_maidangdang", "hat_ricord_maidangdang", "麦当当员工帽"),
    ("ricord_t7", "hat_ricord_t7", "T7 头盔"),
    # 帽子第三期：行军帽 / 小天使光环（uncommon）
    ("ricord_xingjunmao", "hat_ricord_xingjunmao", "行军帽"),
    ("ricord_tianshiguanghuan", "hat_ricord_tianshiguanghuan", "小天使光环"),
    # 帽子第三期：其余（epic）
    ("ricord_dajitui", "hat_ricord_dajitui", "大鸡腿头套"),
    ("ricord_wangguan", "hat_ricord_wangguan", "王冠"),
    ("ricord_jiaohuang", "hat_ricord_jiaohuang", "教皇冠冕"),
    ("ricord_sharenkuang", "hat_ricord_sharenkuang", "杀人狂面具"),
]

# 已废弃、需要从 lang 中清掉的旧条目
OBSOLETE_ITEMS = [
    "ricord_green_camo_helmet",
    "ricord_green_camo_base_helmet",
    "ricord_green_camo_t_helmet",
]

DISPLAY = {
    "gui": {"rotation": [30, 45, -60], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1, 1], "scale": [0.4, 0.4, 0.4]},
}

def detect_eol():
    with open(LANG, "rb") as f:
        head = f.read(4096)
    return "\r\n" if b"\r\n" in head else "\n"


# ── 1. 物品模型 ──────────────────────────────────────────────────────────
for item_id, skin, _cn in HATS:
    model = {"parent": "starrailexpress:item/skins/hat/" + skin, "display": DISPLAY}
    p = os.path.join(ITEM_MODEL_DIR, item_id + ".json")
    with open(p, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=4)
        f.write("\n")
print("[ITEM MODEL] 生成 %d 个物品模型" % len(HATS))

# ── 2. lang/zh_cn.json：整体重写 ricord 条目区 ───────────────────────────
# 逐行处理：只删除「ricord_*」条目所在的整行，再在锚点行后面插入新块。
# 不用跨行正则，避免 \s* 把下一行的缩进/换行一起吃掉。
with open(LANG, encoding="utf-8", newline="") as f:
    raw = f.read()

EOL = "\r\n" if "\r\n" in raw[:4096] else "\n"
lines = raw.split(EOL)
# 去掉 split 产生的尾随空串，改写完再补回行尾
trailing = ""
while lines and lines[-1] == "":
    lines.pop()
    trailing += EOL

ANCHOR = '  "item.noellesroles.rpg7_ammo": "RPG-7 火箭弹",'
anchor_idx = None
for i, ln in enumerate(lines):
    if ln.rstrip() == ANCHOR:
        anchor_idx = i
        break
if anchor_idx is None:
    raise SystemExit("!! 找不到插入锚点: " + ANCHOR.strip())

# 锚点后面连续的那一段 ricord 条目整体替换掉
end = anchor_idx + 1
while end < len(lines) and '"item.noellesroles.ricord_' in lines[end]:
    end += 1
removed = end - (anchor_idx + 1)

new_block = ['  "item.noellesroles.%s": "%s",' % (i, cn) for i, _s, cn in HATS]
lines[anchor_idx + 1:end] = new_block
with open(LANG, "w", encoding="utf-8", newline="") as f:
    f.write(EOL.join(lines) + trailing)

d = json.load(open(LANG, encoding="utf-8"))
missing = [i for i, _s, _c in HATS if ("item.noellesroles." + i) not in d]
leftover = sorted(k for k in d if k.startswith("item.noellesroles.ricord_")
                  and k[len("item.noellesroles."):] not in {i for i, _s, _c in HATS})
print("[LANG] 替换旧 ricord 条目 %d 行 -> %d 条，JSON 校验通过" % (removed, len(HATS)))
print("[LANG] 缺失=%s  残留旧条目=%s" % (missing or "无", leftover or "无"))


# ── 3. 帽子箱子：只改皮肤列表，保留用户自己调过的概率 ─────────────────────
BOX_UNCOMMON = ["hat_ricord_chushimao", "hat_ricord_matongchou", "hat_ricord_kuanggongmao",
                "hat_ricord_limao", "hat_ricord_xiaodangao", "hat_ricord_xiaoji",
                "hat_ricord_helmet", "hat_ricord_helmet_t",
                # 第三期（uncommon）
                "hat_ricord_xingjunmao", "hat_ricord_tianshiguanghuan"]
BOX_RARE = ["hat_ricord_shengdanmao", "hat_ricord_caidao", "hat_ricord_jidan",
            "hat_ricord_beleimao", "hat_ricord_nainao", "hat_ricord_xiaohonghua",
            "hat_ricord_xiaoji_taotao", "hat_ricord_xiaoqie_taotao", "hat_ricord_maidangdang",
            "hat_ricord_t7"]
BOX_EPIC = ["hat_ricord_dajitui", "hat_ricord_wangguan",
            "hat_ricord_jiaohuang", "hat_ricord_sharenkuang"]

for rel in ["CS2_box/hat_box.json", "run/CS2_box/hat_box.json"]:
    p = os.path.join(ROOT, rel)
    box = json.load(open(p, encoding="utf-8"))
    box["common_skins"] = []
    box["uncommon_skins"] = ["hat/" + s for s in BOX_UNCOMMON]
    box["rare_skins"] = ["hat/" + s for s in BOX_RARE]
    box["epic_skins"] = ["hat/" + s for s in BOX_EPIC]
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(box, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("[BOX] %-28s 概率=%s 罕见=%d 稀有=%d 史诗=%d"
          % (rel, {k: box[k] for k in ("common", "uncommon", "rare", "epic", "legendary", "unbelievable")},
             len(box["uncommon_skins"]), len(box["rare_skins"]), len(box["epic_skins"])))
