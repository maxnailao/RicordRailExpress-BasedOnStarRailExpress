const fs = require('fs');
const C = 7.9; // 镜像常数 y' = C - y，保持模型中心不变
const path = process.argv[2];
const data = JSON.parse(fs.readFileSync(path, 'utf8'));

for (const el of data.elements) {
    const [x1, y1, z1] = el.from;
    const [x2, y2, z2] = el.to;
    el.from = [x1, +(C - y2).toFixed(4), z1];
    el.to = [x2, +(C - y1).toFixed(4), z2];
    const r = el.rotation;
    if (r) {
        r.origin = [r.origin[0], +(C - r.origin[1]).toFixed(4), r.origin[2]];
        if (r.axis === 'y') r.angle = -r.angle;
    }
    const f = el.faces;
    if (f && f.up && f.down) { const t = f.up; f.up = f.down; f.down = t; }
}

for (const [side, tx] of [['firstperson_righthand', 0.5], ['firstperson_lefthand', -0.5]]) {
    const d = data.display[side];
    d.rotation = [0, -90, -90];
    d.translation = [tx, 1.5, 0];
    d.scale = [0.55, 0.55, 0.55];
}

fs.writeFileSync(path, JSON.stringify(data, null, 1));
console.log('done, elements:', data.elements.length);