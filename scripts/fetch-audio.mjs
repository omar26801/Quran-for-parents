// Downloads Abdullah Basfar's per-ayah recordings into the app's assets so the app works offline.
// Usage: node scripts/fetch-audio.mjs <everyayah folder name>
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const folder = process.argv[2] || 'Abdullah_Basfar_32kbps';
const base = `https://everyayah.com/data/${folder}/`;
const outDir = 'app/src/main/assets/www/audio';
const ctx = { window: {} };
vm.runInNewContext(fs.readFileSync('app/src/main/assets/www/quran.js', 'utf8'), ctx);

const pad3 = (n) => String(n).padStart(3, '0');
const files = [];
ctx.window.QURAN.forEach((s, i) => s[1].forEach((_, a) => files.push(pad3(i + 1) + pad3(a + 1) + '.mp3')));
fs.mkdirSync(outDir, { recursive: true });

async function get(name) {
  const dest = path.join(outDir, name);
  if (fs.existsSync(dest) && fs.statSync(dest).size > 0) return;
  for (let attempt = 1; attempt <= 5; attempt++) {
    try {
      const res = await fetch(base + name);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      fs.writeFileSync(dest, Buffer.from(await res.arrayBuffer()));
      return;
    } catch (e) {
      if (attempt === 5) throw new Error(`${name}: ${e.message}`);
      await new Promise((r) => setTimeout(r, attempt * 2000));
    }
  }
}

let next = 0, done = 0;
async function worker() {
  while (next < files.length) {
    await get(files[next++]);
    if (++done % 500 === 0) console.log(`${done}/${files.length}`);
  }
}
await Promise.all(Array.from({ length: 16 }, worker));
const bytes = files.reduce((a, f) => a + fs.statSync(path.join(outDir, f)).size, 0);
console.log(`${files.length} files, ${(bytes / 1e6).toFixed(0)} MB from ${folder}`);
