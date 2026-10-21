// Converts quran-align word timings (github.com/cpfair/quran-align, CC-BY-4.0) into
// app/src/main/assets/www/timings/<reciter>.js for word-by-word highlighting.
// Usage: node scripts/build-timings.mjs <folder with the quran-align .json files>
//
// quran-align numbers words by splitting Tanzil's Uthmani text on spaces, where the hizb sign ۞ and the
// sajda sign ۩ are words of their own. In the King Fahd text the app shows, ۞ is joined to the next word
// and ۩ to the previous one, so indices are mapped across before writing.
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const RECITERS = {
  basfar: (n) => /basfar/i.test(n) && !/mujawwad/i.test(n),
  husary: (n) => /husary/i.test(n) && !/muallim|mujawwad|warsh/i.test(n),
};
const NUM_RE = /[\s ]*[٠-٩]+$/;

const ctx = { window: {} };
vm.runInNewContext(fs.readFileSync('app/src/main/assets/www/quran.js', 'utf8'), ctx);
const QURAN = ctx.window.QURAN;

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
}
const jsons = walk(process.argv[2] || 'align').filter((f) => f.endsWith('.json'));
console.log('timing files:', jsons.map((f) => path.basename(f)).join(', ') || 'none');
if (!jsons.length) { console.error('no quran-align .json files found'); process.exit(1); }

const outDir = 'app/src/main/assets/www/timings';
fs.mkdirSync(outDir, { recursive: true });

for (const [rid, matches] of Object.entries(RECITERS)) {
  const file = jsons.filter((f) => matches(path.basename(f))).sort()[0];
  if (!file) { console.log(`${rid}: no timing file, highlighting off`); continue; }
  const data = JSON.parse(fs.readFileSync(file, 'utf8'));
  const out = QURAN.map((s) => s[1].map(() => 0));
  let kept = 0, dropped = 0;
  for (const entry of data) {
    const text = QURAN[entry.surah - 1]?.[1][entry.ayah - 1];
    if (!text || !Array.isArray(entry.segments)) continue;
    const words = text.replace(NUM_RE, '').split(' ');
    const map = [];
    words.forEach((w, i) => {
      if (i === 0 && w.startsWith('۞')) map.push(0);
      map.push(i);
      if (w.includes('۩')) map.push(i);
    });
    const flat = [];
    let ok = true;
    for (const [ws, we, start, end] of entry.segments) {
      if (ws < 0 || we <= ws || we > map.length) { ok = false; break; }
      flat.push(map[ws], map[we - 1] + 1, Math.round(start), Math.round(end));
    }
    if (ok && flat.length) { out[entry.surah - 1][entry.ayah - 1] = flat; kept++; } else dropped++;
  }
  fs.writeFileSync(path.join(outDir, rid + '.js'),
    `// Word timings for ${rid} from quran-align (${path.basename(file)}), CC-BY-4.0.\n` +
    `window.TIMINGS = window.TIMINGS || {};\nwindow.TIMINGS[${JSON.stringify(rid)}] = ${JSON.stringify(out)};\n`);
  console.log(`${rid}: ${path.basename(file)} -> ${kept} ayahs with timings, ${dropped} dropped`);
}
