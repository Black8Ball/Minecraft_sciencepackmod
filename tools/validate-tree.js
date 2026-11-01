// 研究树校验：不生成文件，只检查现有 tech JSON。
// 用法：node tools/validate-tree.js
const fs = require('fs');
const path = require('path');

const DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'sciencepacks', 'sciencepacks', 'technologies');
const ITEMS = path.join(__dirname, '..', 'run', 'config', 'sciencepacks-items.json');
const RECIPES = path.join(__dirname, '..', 'run', 'config', 'sciencepacks-recipes.json');

const BASE_NS = new Set(['minecraft', 'sciencepacks']);
const own = new Set();
const files = fs.readdirSync(DIR).filter(f => f.endsWith('.json'));
for (const f of files) own.add('sciencepacks:' + f.replace(/\.json$/, ''));

let items = null;
if (fs.existsSync(ITEMS)) {
  const raw = JSON.parse(fs.readFileSync(ITEMS, 'utf8'));
  const arr = Array.isArray(raw) ? raw : (raw.items || raw.ids || []);
  items = new Set(arr);
}
let recipes = null;
if (fs.existsSync(RECIPES)) {
  const raw = JSON.parse(fs.readFileSync(RECIPES, 'utf8'));
  const arr = Array.isArray(raw) ? raw : (raw.recipes || raw.ids || []);
  recipes = new Set(arr);
}

const errors = [];
const warnings = [];
const unlocks = new Map();
const cells = new Map();
const parsed = [];

const clean = (id) => String(id || '').replace(/^(item|recipe):/, '').trim();
const knownItem = (id) => {
  const c = clean(id);
  if (!c.includes(':')) return false;
  const ns = c.split(':')[0];
  if (!BASE_NS.has(ns)) return true; // 模组物品无法离线核对
  if (items && items.size) return items.has(c);
  return true;
};

for (const f of files) {
  const id = 'sciencepacks:' + f.replace(/\.json$/, '');
  let j;
  try { j = JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8')); }
  catch (e) { errors.push(`${f}: JSON 解析失败 ${e.message}`); continue; }
  parsed.push({ f, id, j });

  if (j.x === undefined || j.y === undefined) errors.push(`${f}: 缺少 x/y 坐标`);
  const cell = `${j.x},${j.y}`;
  if (cells.has(cell)) errors.push(`${f}: 坐标 (${cell}) 与 ${cells.get(cell)} 冲突`);
  else cells.set(cell, f);
  if (j.root && (j.prerequisites || []).length) {
    warnings.push(`${f}: 已标记为根研究，前置 ${j.prerequisites.join('、')} 会被忽略`);
  }

  for (const pid of (j.root ? [] : (j.prerequisites || []))) {
    if (!own.has(pid)) errors.push(`${f}: 前置 ${pid} 不存在`);
  }
  for (const eid of (j.excludes || [])) {
    if (!own.has(eid)) warnings.push(`${f}: 互斥目标 ${eid} 当前不存在（安装对应模组后才出现，可忽略）`);
  }
  if (j.icon && !knownItem(j.icon)) errors.push(`${f}: 图标物品 ${j.icon} 不在 1.21.1 物品表`);
  for (const u of (j.unlocks || [])) {
    const c = clean(u);
    if (c.startsWith('recipe:')) { if (recipes && !recipes.has(c.slice(7))) errors.push(`${f}: 配方 ${c} 不存在`); continue; }
    if (!knownItem(c)) { errors.push(`${f}: 解锁物品 ${u} 不在 1.21.1 物品表`); continue; }
    if (unlocks.has(c)) errors.push(`${f}: 解锁 ${u} 与 ${unlocks.get(c)} 重复`);
    else unlocks.set(c, f);
  }
  for (const cost of (j.cost || [])) {
    const c = clean(cost.item);
    if (!c.includes(':')) { errors.push(`${f}: 研究材料 "${cost.item}" 不是合法物品 id`); continue; }
    if (!knownItem(c)) errors.push(`${f}: 研究材料 ${c} 不在 1.21.1 物品表`);
  }
  for (const trigger of (j.triggers || [])) {
    const item = String(trigger).split('*')[0];
    const c = clean(item);
    if (!c.includes(':')) { errors.push(`${f}: 触发条件 "${trigger}" 不是合法物品 id`); continue; }
    if (!knownItem(c)) errors.push(`${f}: 触发物品 ${c} 不在 1.21.1 物品表`);
  }
}

const targets = new Set();
for (const p of parsed) for (const x of (p.j.prerequisites || [])) targets.add(x);
const reachable = parsed.filter(p => (p.j.prerequisites || []).length === 0).map(p => p.id);
const seen = new Set(reachable);
let frontier = [...reachable];
while (frontier.length) {
  const next = [];
  for (const p of parsed) {
    if (seen.has(p.id)) continue;
    const pre = p.j.prerequisites || [];
    if (pre.some(x => seen.has(x))) { seen.add(p.id); next.push(p.id); }
  }
  frontier = next;
}
for (const p of parsed) if (!seen.has(p.id)) warnings.push(`${p.f}: 从根节点不可达（前置链可能互锁）`);

const nameDup = new Map();
for (const p of parsed) {
  const n = String(p.j.name || '');
  if (!n) { warnings.push(`${p.f}: 名称为空`); continue; }
  if (nameDup.has(n)) warnings.push(`${p.f}: 名称「${n}」与 ${nameDup.get(n)} 重复`);
  else nameDup.set(n, p.f);
}

console.log(`files: ${files.length}`);
if (items) console.log(`item table: ${items.size} ids`);
if (recipes) console.log(`recipe table: ${recipes.size} ids`);
if (errors.length) {
  console.log(`\nERRORS (${errors.length}):`);
  for (const e of errors) console.log('  ' + e);
} else {
  console.log('\nOK: no blocking errors (ids/preqs/coords/duplicates)');
}
const shown = warnings.filter((w, i, a) => a.indexOf(w) === i);
if (shown.length) {
  console.log(`\nWARNINGS (${shown.length}):`);
  for (const w of shown) console.log('  ' + w);
}
process.exit(errors.length ? 1 : 0);
