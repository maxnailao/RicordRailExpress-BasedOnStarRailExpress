const fs = require('fs');
const path = process.argv[2];
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
// ring center (7.5, -0.8, 8.3) -> model center (8, 8, 8)
const d = [0.5, 8.8, -0.3];
const add = (v) => v.map((x, i) => +(x + d[i]).toFixed(4));
for (const el of data.elements) {
    el.from = add(el.from);
    el.to = add(el.to);
    if (el.rotation && el.rotation.origin) el.rotation.origin = add(el.rotation.origin);
}
fs.writeFileSync(path, JSON.stringify(data, null, 1));
console.log('ring center moved to pivot, offset', d);