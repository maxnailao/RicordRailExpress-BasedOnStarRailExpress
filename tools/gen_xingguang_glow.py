# tools/gen_xingguang_glow.py
# 生成刀刃朦胧光晕用的软径向渐变小贴图（白心透明边，颜色由代码染色）
# 用法: python tools/gen_xingguang_glow.py
from PIL import Image
import math, os

OUT_DIR = os.path.join("src", "main", "resources", "assets", "starrailexpress", "textures", "particle")
OUT = os.path.join(OUT_DIR, "xingguang_blade_glow.png")
S = 64

os.makedirs(OUT_DIR, exist_ok=True)
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
px = img.load()
for y in range(S):
    for x in range(S):
        dx = (x + 0.5 - S / 2) / (S / 2)
        dy = (y + 0.5 - S / 2) / (S / 2)
        d = math.sqrt(dx * dx + dy * dy)
        a = max(0.0, 1.0 - d) ** 2.2
        px[x, y] = (255, 255, 255, int(a * 255))
img.save(OUT)
print("已生成:", OUT)