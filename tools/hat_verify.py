# -*- coding: utf-8 -*-
"""
hat_verify.py — 端到端校验瑞科德饰品帽子在八个位置的完整性。

用法：
    python tools/hat_verify.py                          # 校验源码资源
    python tools/hat_verify.py --jar build/libs/xxx.jar # 额外校验产物 jar

校验项：帽子模型 / 贴图文件 / 皮肤注册(含品级) / 中文名 / 物品注册 /
物品模型 / 语言条目 / 帽子箱子(两处一致)。
"""
import argparse
import io
import json
import os
import re
import sys
import zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRE_ASSETS = os.path.join(ROOT, "src/main/resources/assets/starrailexpress")
NR_ASSETS = os.path.join(ROOT, "src/main/resources/assets/noellesroles")

# 皮肤名, 物品id, 中文名, 品级
HATS = [
    ("hat_ricord_chushimao", "ricord_chushimao", "厨师帽", "UNCOMMON"),
    ("hat_ricord_matongchou", "ricord_matongchou", "马桶抽", "UNCOMMON"),
    ("hat_ricord_kuanggongmao", "ricord_kuanggongmao", "矿工帽", "UNCOMMON"),
    ("hat_ricord_limao", "ricord_limao", "礼帽", "UNCOMMON"),
    ("hat_ricord_xiaodangao", "ricord_xiaodangao", "小蛋糕", "UNCOMMON"),
    ("hat_ricord_xiaoji", "ricord_xiaoji", "小鸡", "UNCOMMON"),
    ("hat_ricord_helmet", "ricord_helmet", "钢盔", "UNCOMMON"),
    ("hat_ricord_helmet_t", "ricord_helmet_t", "钢盔+T", "UNCOMMON"),
    ("hat_ricord_shengdanmao", "ricord_shengdanmao", "圣诞帽", "RARE"),
    ("hat_ricord_caidao", "ricord_caidao", "菜刀", "RARE"),
    ("hat_ricord_jidan", "ricord_jidan", "鸡蛋", "RARE"),
    ("hat_ricord_beleimao", "ricord_beleimao", "贝雷帽", "RARE"),
    ("hat_ricord_nainao", "ricord_nainao", "奶闹", "RARE"),
    ("hat_ricord_xiaohonghua", "ricord_xiaohonghua", "小红花", "RARE"),
    ("hat_ricord_xiaoji_taotao", "ricord_xiaoji_taotao", "小鸡头套", "RARE"),
    ("hat_ricord_xiaoqie_taotao", "ricord_xiaoqie_taotao", "小企鹅头套", "RARE"),
    ("hat_ricord_maidangdang", "ricord_maidangdang", "麦当当员工帽", "RARE"),
]
# 不在 帽子/ 文件夹里、按设计保留原样的皮肤：只校验注册没被破坏，不要求进箱子
KEEP_AS_IS = [("hat_ricord_cat_headset", "ricord_cat_headset", "小猫耳机", "LEGENDARY")]
OBSOLETE = ["hat_ricord_green_camo", "hat_ricord_green_camo_base", "hat_ricord_green_camo_t"]


def check_source():
    sre_reg = open(os.path.join(ROOT, "src/main/java/io/wifi/starrailexpress/index/SRESkinRegistry.java"),
                   encoding="utf-8").read()
    cs2_info = open(os.path.join(ROOT, "src/main/java/org/agmas/noellesroles/cs2/CS2SkinInfo.java"),
                    encoding="utf-8").read()
    mod_items = open(os.path.join(ROOT, "src/main/java/org/agmas/noellesroles/init/ModItems.java"),
                     encoding="utf-8").read()
    nr_lang = json.load(open(os.path.join(NR_ASSETS, "lang/zh_cn.json"), encoding="utf-8"))
    box_main = json.load(open(os.path.join(ROOT, "CS2_box/hat_box.json"), encoding="utf-8"))
    box_run = json.load(open(os.path.join(ROOT, "run/CS2_box/hat_box.json"), encoding="utf-8"))

    all_ok = True
    for skin, item_id, cn, quality in HATS + KEEP_AS_IS:
        checks = []
        mp = os.path.join(SRE_ASSETS, "models/item/skins/hat", skin + ".json")
        checks.append(("帽子模型", os.path.isfile(mp)))
        if os.path.isfile(mp):
            m = json.load(open(mp, encoding="utf-8"))
            miss = [ref for _s, ref in m["textures"].items()
                    if not os.path.isfile(os.path.join(SRE_ASSETS, "textures",
                                                       ref.split(":", 1)[1] + ".png"))]
            checks.append(("贴图", not miss))
            checks.append(("无残留rotation", not any("rotation" in e for e in m["elements"])))
        checks.append(("皮肤注册", bool(re.search(
            r'registerSkin\(SkinTypes\.HAT,\s*"%s",\s*QualityColor\.%s\)'
            % (re.escape(skin), quality), sre_reg))))
        checks.append(("中文名", ('register("hat/%s", "%s"' % (skin, cn)) in cs2_info))
        checks.append(("物品注册", ('"%s", RICORD_ACCESSORIES_GROUP' % item_id) in mod_items))
        checks.append(("物品模型", os.path.isfile(os.path.join(NR_ASSETS, "models/item", item_id + ".json"))))
        checks.append(("语言条目", nr_lang.get("item.noellesroles." + item_id) == cn))
        if skin not in {s for s, _i, _c, _q in KEEP_AS_IS}:
            sid = "hat/" + skin
            in_main = sid in box_main["uncommon_skins"] + box_main["rare_skins"]
            in_run = sid in box_run["uncommon_skins"] + box_run["rare_skins"]
            checks.append(("箱子CS2_box", in_main))
            checks.append(("箱子run", in_run))

        failed = [n for n, ok in checks if not ok]
        if failed:
            all_ok = False
        print("%-30s %-14s %-9s %s" % (skin, cn, quality, "OK" if not failed else "失败: " + ",".join(failed)))

    same = json.dumps(box_main, sort_keys=True, ensure_ascii=False) == \
           json.dumps(box_run, sort_keys=True, ensure_ascii=False)
    print("\n两处 hat_box.json 一致:", "OK" if same else "不一致")
    all_ok = all_ok and same

    leftover = []
    for o in OBSOLETE:
        for p in [os.path.join(SRE_ASSETS, "models/item/skins/hat", o + ".json"),
                  os.path.join(SRE_ASSETS, "textures/item/skins/hat", o + ".png"),
                  os.path.join(NR_ASSETS, "models/item", o.replace("hat_ricord_", "ricord_") + ".json"),
                  os.path.join(NR_ASSETS, "lang/zh_cn.json")]:
            if p.endswith(".json") and os.path.isfile(p) and "lang" in p:
                d = json.load(open(p, encoding="utf-8"))
                for k in d:
                    if o.replace("hat_ricord_", "ricord_") in k:
                        leftover.append(k)
            elif os.path.isfile(p):
                leftover.append(os.path.relpath(p, ROOT))
    print("废弃旧钢盔资源残留:", leftover or "无")
    all_ok = all_ok and not leftover

    print("\n源码校验:", "全部通过" if all_ok else "存在问题")
    return all_ok


def check_jar(path):
    z = zipfile.ZipFile(path)
    names = set(z.namelist())
    bad = []
    for skin, item_id, cn, _q in HATS + KEEP_AS_IS:
        m = "assets/starrailexpress/models/item/skins/hat/%s.json" % skin
        if m not in names:
            bad.append(m)
        if "assets/noellesroles/models/item/%s.json" % item_id not in names:
            bad.append(item_id)
    d = json.loads(z.read("assets/noellesroles/lang/zh_cn.json").decode("utf-8"))
    for _s, item_id, cn, _q in HATS + KEEP_AS_IS:
        if d.get("item.noellesroles." + item_id) != cn:
            bad.append("lang:" + item_id)
    for o in OBSOLETE:
        hits = [n for n in names if o in n]
        if hits:
            bad.append("废弃残留:" + str(hits))
    print("\njar 校验 (%s): %s" % (os.path.basename(path), "全部通过" if not bad else "问题: %s" % bad))
    return not bad


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", default=None)
    a = ap.parse_args()
    ok = check_source()
    if a.jar:
        ok = check_jar(a.jar) and ok
    sys.exit(0 if ok else 1)
