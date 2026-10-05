# -*- coding: utf-8 -*-
"""
hat_convert.py — 把 Blockbench 基岩版帽子模型转换成 Java 物品模型 + 拷贝贴图。

用法：
    python tools/hat_convert.py            # 全部任务
    python tools/hat_convert.py helmet     # 只跑 SKIN_NAME 含该关键字的任务

脚本会：
    - 把 textures 里的占位（#0 / block/texture / 中文名）换成
      starrailexpress:item/skins/hat/<皮肤名> 命名空间；
    - 把模型里所有旋转（骨骼组旋转 + 方块自带旋转）烘焙进 from/to 轴对齐包围盒，
      避免 Java 只允许 -45/-22.5/0/22.5/45° 的限制；
    - 保留模型自带的 display（含 head 变换）；
    - 把真实贴图复制到 src/main/resources/assets/starrailexpress/textures/item/skins/hat/。

注意：本脚本放在 tools/ 而不是 build/ —— build/ 被 .gitignore 忽略且会被 Gradle 清理。
"""
import json
import math
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODEL_OUT_DIR = os.path.join(
    ROOT, "src", "main", "resources", "assets", "starrailexpress", "models", "item", "skins", "hat")
TEX_OUT_DIR = os.path.join(
    ROOT, "src", "main", "resources", "assets", "starrailexpress", "textures", "item", "skins", "hat")

HAT1 = os.path.join(ROOT, "帽子")
HAT2 = os.path.join(ROOT, "帽子第二期")


def tex(skin, *suffix):
    """starrailexpress:item/skins/hat/<skin>[_suffix]"""
    return "starrailexpress:item/skins/hat/" + "_".join((skin,) + suffix)


def task(group, folder, json_name, png_name, skin, extra_tex=(), particle_skin=None):
    """构造一条转换任务。

    extra_tex 用于「一个模型里多个贴图槽指向同一张合并图集」的情况，
    形如 (("0", "hat_ricord_helmet"),)：把槽位 "0" 指向皮肤 hat_ricord_helmet，
    贴图来源仍是本任务的 png_name。
    particle_skin 用于指定 particle 槽指向哪个皮肤（默认与自己相同）；
    当多个皮肤共用同一张贴图时可指向那个实际存在的贴图。
    """
    src = os.path.join(group, folder, json_name)
    png = os.path.join(group, folder, png_name)
    tex_map = {"0": tex(skin), "particle": tex(particle_skin or skin)}
    tex_copy = {"0": png}
    for slot, sub in extra_tex:
        tex_map[slot] = tex(sub)
        tex_copy[slot] = png
    return {"SRC": src, "SKIN_NAME": skin, "TEX_MAP": tex_map, "TEX_COPY": tex_copy}


# ════════════════════════════════════════════════════════════════════════════
# 第一期：覆盖原有帽子（模型/贴图为当前 帽子/ 目录下的最新版）
# ════════════════════════════════════════════════════════════════════════════
TASKS = [
    task(HAT1, "厨师帽", "厨师帽.json", "厨师帽.png", "hat_ricord_chushimao"),
    task(HAT1, "马桶抽", "马桶抽.json", "马桶抽.png", "hat_ricord_matongchou"),
    task(HAT1, "矿工帽", "矿工帽.json", "矿工帽.png", "hat_ricord_kuanggongmao"),
    task(HAT1, "礼帽", "礼帽.json", "礼帽.png", "hat_ricord_limao"),
    task(HAT1, "小蛋糕", "小蛋糕.json", "小蛋糕.png", "hat_ricord_xiaodangao"),
    task(HAT1, "小鸡", "小鸡.json", "小鸡.png", "hat_ricord_xiaoji"),
    # 头盔：新版文件夹里只有「基础头盔+T」一个模型，且 T.png 是一张
    # 32×32 合并图集（迷彩 + 面罩 + T 三部分都在里面），所以两个变体共用同一张贴图。
    task(HAT1, "头盔", "基础头盔+T.json", "T.png", "hat_ricord_helmet_t",
         extra_tex=(("0", "hat_ricord_helmet"),),
         particle_skin="hat_ricord_helmet"),
    # 新增帽子
    task(HAT1, "圣诞帽", "圣诞帽.json", "圣诞帽.png", "hat_ricord_shengdanmao"),
    task(HAT1, "菜刀", "菜刀.json", "菜刀.png", "hat_ricord_caidao"),
    task(HAT1, "鸡蛋", "鸡蛋.json", "鸡蛋.png", "hat_ricord_jidan"),
    # 第二期（全部 rare）
    task(HAT2, "贝雷帽2", "贝雷帽.json", "贝雷帽.png", "hat_ricord_beleimao"),
    task(HAT2, "奶闹2", "奶闹.json", "奶闹.png", "hat_ricord_nainao"),
    task(HAT2, "小红花2", "小红花.json", "小红花.png", "hat_ricord_xiaohonghua"),
    task(HAT2, "小鸡头套2", "小鸡头套.json", "小鸡头套.png", "hat_ricord_xiaoji_taotao"),
    task(HAT2, "小企鹅头套2", "小企鹅头套.json", "小企鹅头套.png", "hat_ricord_xiaoqie_taotao"),
    task(HAT2, "麦当当员工帽2", "麦当当员工帽.json", "麦当当员工帽.png", "hat_ricord_maidangdang"),
]

# 非 T 版头盔是一个独立皮肤，但模型与 T 版只差「T 挂件」那几个元素，
# 这里直接从 T 版模型里删掉 T 元素生成，避免再维护一份源文件。
# T 挂件在骨骼组里是 group[12] name="t"，children=[16,17,18,19]。
HELMET_PLAIN = {
    "SKIN_NAME": "hat_ricord_helmet",
    "FROM_T_SKIN": "hat_ricord_helmet_t",
    "DROP_ELEMENTS": [16, 17, 18, 19],
    # 两个变体共用 T.png（一张合并图集），因此贴图直接用 T 版的产出
    "REUSE_TEX_FROM": "hat_ricord_helmet_t",
}


# ════════════════════════════════════════════════════════════════════════════
# 旋转工具
# ════════════════════════════════════════════════════════════════════════════
def mat_identity():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_apply(m, p):
    x, y, z = p
    return (
        m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
        m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
        m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3],
    )


def mat_translate(t):
    m = mat_identity()
    m[0][3], m[1][3], m[2][3] = t
    return m


def mat_rot_x(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def mat_rot_y(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def mat_rot_z(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def mat_rot_around(axis, deg, origin):
    """绕 origin 旋转 deg 度（绕指定轴）。"""
    if not deg:
        return mat_identity()
    rot = {"x": mat_rot_x, "y": mat_rot_y, "z": mat_rot_z}.get(axis)
    if rot is None:
        return mat_identity()
    return mat_mul(mat_translate(origin), mat_mul(rot(deg), mat_translate([-c for c in origin])))


def element_rotation_matrix(elem):
    """把元素的 rotation 字段转成矩阵。

    Blockbench 基岩版：{"angle": a, "axis": "y", "origin": [x,y,z]}
    Blockbench 旧格式： {"x": ax, "y": ay, "z": az, "origin": [x,y,z]}
    """
    r = elem.get("rotation")
    if not r:
        return mat_identity()
    origin = r.get("origin") or [0, 0, 0]
    if "axis" in r:
        return mat_rot_around(r.get("axis"), r.get("angle") or 0.0, origin)
    m = mat_identity()
    for axis in ("x", "y", "z"):
        m = mat_mul(m, mat_rot_around(axis, r.get(axis) or 0.0, origin))
    return m


def build_element_matrices(elements, groups):
    """返回 element_index -> 该元素继承的骨骼组变换矩阵。"""
    inherited = {i: mat_identity() for i in range(len(elements))}

    def walk(index, parent):
        g = groups[index]
        if isinstance(g, int):
            inherited[g] = parent
            return
        m = parent
        if g.get("rotation"):
            origin = g.get("origin") or [0, 0, 0]
            r = g["rotation"]
            for axis in ("x", "y", "z"):
                m = mat_mul(m, mat_rot_around(axis, r.get(axis) or 0.0, origin))
        for child in g.get("children", []):
            if 0 <= child < len(groups):
                walk(child, m)

    # 顶层骨骼 = 没有被任何骨骼当作 children 的骨骼
    referenced = set()
    for g in groups or []:
        if isinstance(g, dict):
            referenced.update(g.get("children", []))
    for i, g in enumerate(groups or []):
        if i not in referenced:
            walk(i, mat_identity())
    return inherited


def clean_number(v):
    """去掉浮点噪声：3.0000000000000004 -> 3"""
    if isinstance(v, float):
        r = round(v, 6)
        if abs(r - round(r)) < 1e-6:
            r = float(round(r))
        return r
    return v


def convert(src_path, skin_name, tex_map, tex_copy):
    with open(src_path, encoding="utf-8") as f:
        src = json.load(f)

    elements = src.get("elements") or []
    groups = src.get("groups") or []
    inherited = build_element_matrices(elements, groups)

    out_elements = []
    for i, e in enumerate(elements):
        m = mat_mul(inherited.get(i, mat_identity()), element_rotation_matrix(e))
        fx, fy, fz = e["from"]
        tx, ty, tz = e["to"]
        pts = [mat_apply(m, (x, y, z))
               for x in (fx, tx) for y in (fy, ty) for z in (fz, tz)]
        nf = [clean_number(min(p[k] for p in pts)) for k in range(3)]
        nt = [clean_number(max(p[k] for p in pts)) for k in range(3)]

        faces = {}
        for fname, fdata in (e.get("faces") or {}).items():
            fd = {"uv": [clean_number(u) for u in fdata["uv"]], "texture": fdata["texture"]}
            if fdata.get("rotation"):
                fd["rotation"] = fdata["rotation"]
            if fdata.get("tintindex") is not None:
                fd["tintindex"] = fdata["tintindex"]
            faces[fname] = fd

        el = {"from": nf, "to": nt, "faces": faces}
        if e.get("shade") is False:
            el["shade"] = False
        out_elements.append(el)

    textures = {}
    for slot in (src.get("textures") or {}):
        if slot == "particle":
            continue
        textures[slot] = tex_map.get(slot, tex_map.get("0"))
    textures["particle"] = tex_map.get("particle", tex_map.get("0"))

    model = {
        "credit": "Made with Blockbench",
        "texture_size": src.get("texture_size") or [16, 16],
        "textures": textures,
        "elements": out_elements,
    }
    if src.get("display"):
        model["display"] = src["display"]

    os.makedirs(MODEL_OUT_DIR, exist_ok=True)
    out_path = os.path.join(MODEL_OUT_DIR, skin_name + ".json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("[MODEL] %-34s (%d elements)" % (os.path.relpath(out_path, ROOT), len(out_elements)))

    # 贴图
    os.makedirs(TEX_OUT_DIR, exist_ok=True)
    for slot in (src.get("textures") or {}):
        if slot == "particle":
            continue
        target_name = textures[slot].split(":")[-1].split("/")[-1] + ".png"
        out_tex = os.path.join(TEX_OUT_DIR, target_name)
        real = tex_copy.get(slot)
        if real and os.path.isfile(real):
            shutil.copyfile(real, out_tex)
            print("[TEX  ] %-34s <- %s" % (target_name, os.path.relpath(real, ROOT)))
        elif os.path.isfile(out_tex):
            print("[TEX  ] %-34s (已存在，保留)" % target_name)
        else:
            raise SystemExit("!! 缺少贴图来源：skin=%s slot=%s" % (skin_name, slot))
    return model, out_path


def convert_helmet_plain(spec):
    """从 T 版模型派生非 T 版：删掉 T 挂件元素。"""
    src_model_path = os.path.join(MODEL_OUT_DIR, spec["FROM_T_SKIN"] + ".json")
    with open(src_model_path, encoding="utf-8") as f:
        base = json.load(f)
    elements = [e for i, e in enumerate(base["elements"]) if i not in spec["DROP_ELEMENTS"]]
    model = {
        "credit": base.get("credit", "Made with Blockbench"),
        "texture_size": base.get("texture_size", [16, 16]),
        "textures": {"0": tex(spec["SKIN_NAME"]), "particle": tex(spec["SKIN_NAME"])},
        "elements": elements,
    }
    if base.get("display"):
        model["display"] = base["display"]
    out_path = os.path.join(MODEL_OUT_DIR, spec["SKIN_NAME"] + ".json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("[MODEL] %-34s (%d elements, 派生自 %s)"
          % (os.path.relpath(out_path, ROOT), len(elements), spec["FROM_T_SKIN"]))
    # 贴图：两个头盔变体共用同一张合并图集，直接从 T 版产出拷一份
    src_tex = os.path.join(TEX_OUT_DIR, spec["REUSE_TEX_FROM"] + ".png")
    out_tex = os.path.join(TEX_OUT_DIR, spec["SKIN_NAME"] + ".png")
    if os.path.isfile(src_tex):
        shutil.copyfile(src_tex, out_tex)
        print("[TEX  ] %-34s <- %s.png" % (spec["SKIN_NAME"] + ".png", spec["REUSE_TEX_FROM"]))
    elif os.path.isfile(out_tex):
        print("[TEX  ] %-34s (已存在，保留)" % (spec["SKIN_NAME"] + ".png"))
    else:
        raise SystemExit("!! 缺少头盔贴图来源：%s" % src_tex)


def main():
    only = sys.argv[1:] or None
    ran = 0
    for t in TASKS:
        if only and not any(o in t["SKIN_NAME"] for o in only):
            continue
        convert(t["SRC"], t["SKIN_NAME"], t["TEX_MAP"], t["TEX_COPY"])
        ran += 1
    if not only or any("helmet" in o for o in only):
        convert_helmet_plain(HELMET_PLAIN)
        ran += 1
    print("\n完成 %d 个帽子。" % ran)


if __name__ == "__main__":
    main()
