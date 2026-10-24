const fs = require('fs');
const path = process.argv[2];
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
let n = 0;
for (const el of data.elements) {
    const r = el.rotation;
    if (r && r.axis === 'x') { r.angle = -r.angle; n++; }
}
fs.writeFileSync(path, JSON.stringify(data, null, 1));
console.log('flipped x-axis angles:', n);