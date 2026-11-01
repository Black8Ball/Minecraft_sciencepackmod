const fs = require('fs');
const path = require('path');
const DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'sciencepacks', 'sciencepacks', 'technologies');
const NAME = {
  'sciencepacks:pack_red': '红', 'sciencepacks:pack_green': '绿', 'sciencepacks:pack_blue': '蓝',
  'sciencepacks:pack_purple': '紫', 'sciencepacks:pack_gray': '灰', 'sciencepacks:pack_yellow': '黄',
};
const list = fs.readdirSync(DIR).filter((f) => f.endsWith('.json')).map((f) => {
  const json = JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8'));
  return { id: f.slice(0, -5), ...json };
});
list.sort((a, b) => (a.y - b.y) || (a.x - b.x) || a.id.localeCompare(b.id));
for (const t of list) {
  const pre = [];
  for (const p of t.prerequisites || []) pre.push(p.replace('sciencepacks:', ''));
  for (const tr of t.triggers || []) pre.push('触发：' + tr.replace('minecraft:', ''));
  const cost = (t.cost || []).map((c) => (NAME[c.item] || c.item) + ' ×' + (c.count ?? 1)).join('、') || '—';
  const rounds = t.infinite ? (t.maxLevel ? `5（无限，最高 ${t.maxLevel} 级）` : '5（无限）') : (t.rounds || '—');
  console.log(`| ${t.id} | ${t.name} | ${pre.join('、') || '—'} | ${rounds} | ${cost} |`);
}
