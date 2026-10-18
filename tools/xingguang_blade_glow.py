# tools/xingguang_blade_glow.py
# 给"星光"刀皮肤的刀刃部位烘焙从内到外的黑灰色微光
# 用法: python tools/xingguang_blade_glow.py
from PIL import Image
import shutil, os

TEX = os.path.join("src", "main", "resources", "assets", "starrailexpress",
                   "textures", "item", "skins", "knife", "knife_xingguang.png")
BACKUP = TEX.replace(".png", "_backup.png")

GLOW_COLOR = (46, 46, 52)   # 黑灰色
STRENGTH = 0.42             # 边缘最大混合强度（微微的），0.3~0.6 之间调
RADIUS_CAP = 3.0            # 光晕衰减最大半径（像素），想更"渗出来"就调大
FALLOFF = 1.4               # 衰减曲线，越大越集中在边缘

# 刃组（元素22-28）全部侧面 UV，Blockbench 16 单位制
BLADE_UVS = [
    (10, 10, 10.5, 11), (8, 5, 9, 6), (10.5, 0, 11, 1), (8, 8, 9, 9),
    (1.5, 9.5, 2, 11), (9.5, 2, 10, 3.5), (3.5, 9.5, 4, 11), (9.5, 3.5, 10, 5),
    (5.5, 0, 6, 4), (5.5, 4, 6, 8), (6, 0, 6.5, 4), (4, 6, 4.5, 10),
    (9.5, 8, 10, 9.5), (2.5, 6.5, 3.5, 8), (9, 9.5, 9.5, 11), (6.5, 4, 7.5, 5.5),
    (3.5, 6.5, 4, 9.5), (4, 0, 5, 3), (6.5, 5.5, 7, 8.5), (4, 3, 5, 6),
    (6, 4, 6.5, 8), (4.5, 6, 5, 10), (6.5, 0, 7, 4), (2, 6.5, 2.5, 10.5),
    (2, 0, 2.5, 6.5), (2.5, 0, 3, 6.5), (3, 0, 3.5, 6.5), (3.5, 0, 4, 6.5),
]

def main():
    if not os.path.exists(BACKUP):
        shutil.copy(TEX, BACKUP)
        print("已备份:", BACKUP)
    img = Image.open(TEX).convert("RGBA")
    W, H = img.size
    s = W / 16.0  # uv 单位 -> 像素
    px = img.load()
    for (u0, v0, u1, v1) in BLADE_UVS:
        x0, y0 = int(round(u0 * s)), int(round(v0 * s))
        x1, y1 = int(round(u1 * s)), int(round(v1 * s))
        w, h = x1 - x0, y1 - y0
        if w <= 0 or h <= 0:
            continue
        radius = min(RADIUS_CAP, max(1.0, min(w, h) / 2.0))
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                d = min(x - x0 + 0.5, x1 - x - 0.5, y - y0 + 0.5, y1 - y - 0.5)
                t = max(0.0, 1.0 - d / radius) ** FALLOFF * STRENGTH
                px[x, y] = (
                    int(r + (GLOW_COLOR[0] - r) * t),
                    int(g + (GLOW_COLOR[1] - g) * t),
                    int(b + (GLOW_COLOR[2] - b) * t),
                    a,
                )
    img.save(TEX)
    print("完成。回退: 把 _backup.png 覆盖回原文件即可。")

if __name__ == "__main__":
    main()