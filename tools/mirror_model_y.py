import json, sys

C = 7.9  # 镜像常数 y' = C - y，保持模型整体中心不变

def mirror_element(el):
    f, t = el["from"], el["to"]
    el["from"] = [f[0], round(C - t[1], 4), f[2]]
    el["to"] = [t[0], round(C - f[1], 4), t[2]]
    rot = el.get("rotation")
    if rot:
        o = rot["origin"]
        rot["origin"] = [o[0], round(C - o[1], 4), o[2]]
        if rot.get("axis") == "y":
            rot["angle"] = -rot.get("angle", 0)
    faces = el.get("faces", {})
    if "up" in faces and "down" in faces:
        faces["up"], faces["down"] = faces["down"], faces["up"]

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as fp:
    data = json.load(fp)

for el in data.get("elements", []):
    mirror_element(el)

disp = data.setdefault("display", {})
for side, tx in (("firstperson_righthand", 0.5), ("firstperson_lefthand", -0.5)):
    d = disp.setdefault(side, {})
    d["rotation"] = [0, -90, -90]
    d["translation"] = [tx, 1.5, 0]
    d["scale"] = [0.55, 0.55, 0.55]

with open(path, "w", encoding="utf-8") as fp:
    json.dump(data, fp, ensure_ascii=False, indent=1)
print("done, elements:", len(data.get("elements", [])))