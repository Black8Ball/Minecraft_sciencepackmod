// 科技包配方生成器（1.21 格式：result 用 id/count，目录为 data/<ns>/recipe）
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'sciencepacks');
const OUT = path.join(ROOT, 'recipe');
const OLD = path.join(ROOT, 'recipes');

const shapeless = (ingredients, result, count) => ({
  type: 'minecraft:crafting_shapeless',
  ingredients: ingredients.map((id) => ({ item: id })),
  result: { id: result, count },
});

const shaped = (pattern, key, result, count) => ({
  type: 'minecraft:crafting_shaped',
  pattern,
  key: Object.fromEntries(Object.entries(key).map(([k, id]) => [k, { item: id }])),
  result: { id: result, count },
});

const RECIPES = {
  // 红：3 种材料，最简单，且不需要铁镐（红石要铁镐，会和研究进度互相死锁）
  'pack_red': shapeless(
    ['minecraft:glass_bottle', 'minecraft:red_dye', 'minecraft:sugar'],
    'sciencepacks:pack_red', 2),
  // 绿：4 种材料，加入铁与萤石
  'pack_green': shapeless(
    ['minecraft:glass_bottle', 'minecraft:iron_ingot', 'minecraft:glowstone_dust', 'minecraft:sugar'],
    'sciencepacks:pack_green', 2),
  // 蓝：5 种材料，需要钻石/青金石/红石块
  'pack_blue': shapeless(
    ['minecraft:glass_bottle', 'minecraft:lapis_lazuli', 'minecraft:diamond',
      'minecraft:redstone_block', 'minecraft:sugar'],
    'sciencepacks:pack_blue', 2),
  // 紫：5 种材料，需要末影珍珠与烈焰粉
  'pack_purple': shapeless(
    ['minecraft:glass_bottle', 'minecraft:ender_pearl', 'minecraft:blaze_powder',
      'minecraft:amethyst_shard', 'minecraft:sugar'],
    'sciencepacks:pack_purple', 2),
  // 军备：6 种材料，铁块 + 火药 + 燧石 + 煤
  'pack_gray': shapeless(
    ['minecraft:glass_bottle', 'minecraft:iron_block', 'minecraft:gunpowder',
      'minecraft:flint', 'minecraft:coal', 'minecraft:sugar'],
    'sciencepacks:pack_gray', 2),
  // 效能：3×3 有序配方，9 种材料，最复杂
  'pack_yellow': shaped(
    ['GAG', 'BDB', 'CEF'],
    {
      G: 'minecraft:gold_block',
      A: 'minecraft:amethyst_shard',
      B: 'minecraft:glowstone',
      D: 'minecraft:glass_bottle',
      C: 'minecraft:lapis_lazuli',
      E: 'minecraft:redstone',
      F: 'minecraft:blaze_powder',
    },
    'sciencepacks:pack_yellow', 2),
};

fs.mkdirSync(OUT, { recursive: true });
for (const [name, data] of Object.entries(RECIPES)) {
  fs.writeFileSync(path.join(OUT, `${name}.json`), JSON.stringify(data, null, 2) + '\n', 'utf8');
}
if (fs.existsSync(OLD)) {
  for (const f of fs.readdirSync(OLD)) fs.unlinkSync(path.join(OLD, f));
  fs.rmdirSync(OLD);
  console.log('removed legacy recipes/ folder');
}
console.log('wrote', Object.keys(RECIPES).length, 'recipes to', OUT);
