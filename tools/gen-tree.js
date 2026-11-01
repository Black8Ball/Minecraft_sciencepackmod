// 研究树生成器：按「越后期越贵」的思路统一生成科技 JSON，并用服务端导出的物品清单校验 id
const fs = require('fs');
const path = require('path');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'sciencepacks', 'sciencepacks', 'technologies');
const ITEMS = path.join(__dirname, '..', 'run', 'config', 'sciencepacks-items.json');

const R = 'sciencepacks:pack_red';
const G = 'sciencepacks:pack_green';
const B = 'sciencepacks:pack_blue';
const P = 'sciencepacks:pack_purple';
const A = 'sciencepacks:pack_gray';
const Y = 'sciencepacks:pack_yellow';

const cost = (pairs) => pairs.map((p) => ({ item: p[0], count: p[1] }));

const TECHS = [
  /* ---------- 生存与建造 ---------- */
  {
    id: 'stone_age', name: '石器时代',
    description: '第一块圆石落地时，世界第一次有了棱角。石镐凿开洞穴，火把照亮前路。',
    icon: 'minecraft:stone_pickaxe', category: 'material', x: 0, y: 0,
    triggers: ['minecraft:cobblestone*16'],
    unlocks: ['minecraft:stone_pickaxe', 'minecraft:stone_axe', 'minecraft:stone_shovel',
      'minecraft:stone_hoe', 'minecraft:stone_sword', 'minecraft:furnace',
      'minecraft:stonecutter', 'minecraft:campfire', 'minecraft:torch'],
  },
  {
    id: 'farming', name: '耕作',
    description: '有人在洞穴口种下第一排小麦。石磨与木犁都不值钱，但它们让「明天」有了着落。',
    icon: 'minecraft:bread', category: 'material', x: -3, y: 0, rounds: 10,
    prerequisites: ['sciencepacks:stone_age'], cost: cost([[R, 1]]),
    unlocks: ['minecraft:bread', 'minecraft:hay_block', 'minecraft:sugar',
      'minecraft:composter', 'minecraft:carved_pumpkin', 'minecraft:jack_o_lantern'],
  },
  {
    id: 'cooking', name: '烹饪',
    description: '蛋糕、炖菜与南瓜派摆上长桌。矿洞里第一次有了节日的味道。',
    icon: 'minecraft:cake', category: 'material', x: -3, y: 1, rounds: 12,
    prerequisites: ['sciencepacks:farming'], cost: cost([[R, 1]]),
    unlocks: ['minecraft:cookie', 'minecraft:cake', 'minecraft:pumpkin_pie',
      'minecraft:mushroom_stew', 'minecraft:rabbit_stew', 'minecraft:beetroot_soup',
      'minecraft:golden_carrot'],
  },
  {
    id: 'librarian', name: '书卷',
    description: '纸张堆到齐腰高，书架一格格立起来。知识开始被留下，而不是被记住。',
    icon: 'minecraft:bookshelf', category: 'material', x: -3, y: 2, rounds: 14,
    prerequisites: ['sciencepacks:farming'], cost: cost([[R, 2]]),
    unlocks: ['minecraft:paper', 'minecraft:book', 'minecraft:bookshelf',
      'minecraft:lectern', 'minecraft:chiseled_bookshelf'],
  },
  {
    id: 'honey', name: '养蜂',
    description: '木箱挂在花丛边，蜜蜂把它当作家。蜂蜜缓慢滴落，甜得像一种奢侈。',
    icon: 'minecraft:beehive', category: 'material', x: -3, y: 3, rounds: 12,
    prerequisites: ['sciencepacks:farming'], cost: cost([[R, 1]]),
    unlocks: ['minecraft:beehive', 'minecraft:honey_block', 'minecraft:honeycomb_block'],
  },
  {
    id: 'cartography', name: '制图与计时',
    description: '地图铺满桌面，罗盘指向远方。有人开始记录这个世界到底有多大。',
    icon: 'minecraft:compass', category: 'material', x: -3, y: 4, rounds: 16,
    prerequisites: ['sciencepacks:librarian'], cost: cost([[R, 2], [G, 1]]),
    unlocks: ['minecraft:map', 'minecraft:compass', 'minecraft:clock',
      'minecraft:recovery_compass'],
  },
  {
    id: 'masonry', name: '石作',
    description: '石砖在锤下咬合，砂浆填平最后一处缝隙。洞窟开始有墙、有门、有街。',
    icon: 'minecraft:stone_bricks', category: 'building', x: -2, y: 0, rounds: 10,
    prerequisites: ['sciencepacks:stone_age'], cost: cost([[R, 1]]),
    unlocks: ['minecraft:bricks', 'minecraft:stone_bricks', 'minecraft:stone_brick_stairs',
      'minecraft:mossy_stone_bricks', 'minecraft:smooth_stone'],
  },
  {
    id: 'glassworks', name: '玻璃工坊',
    description: '沙子熔成透明的薄片。第一次，墙外的风景可以透过墙照进来。',
    icon: 'minecraft:glass', category: 'building', x: -2, y: 1, rounds: 12,
    prerequisites: ['sciencepacks:masonry'], cost: cost([[R, 1]]),
    unlocks: ['minecraft:glass', 'minecraft:glass_pane', 'minecraft:tinted_glass'],
  },
  {
    id: 'smithy', name: '铁匠铺',
    description: '铁砧立起来，风箱呼哧作响。锻造台旁堆着第一批被烧红的失败品。',
    icon: 'minecraft:anvil', category: 'material', x: -2, y: 4, rounds: 20,
    prerequisites: ['sciencepacks:gold_craft'], cost: cost([[R, 2], [G, 1]]),
    unlocks: ['minecraft:anvil', 'minecraft:smithing_table', 'minecraft:grindstone',
      'minecraft:blast_furnace', 'minecraft:smoker'],
  },
  /* ---------- 科技包 ---------- */
  {
    id: 'red_science', name: '红色科技包',
    description: '把花瓣捣成红色染料，混进糖浆装进玻璃瓶——第一支红色研究包在微光中轻轻震颤。',
    icon: 'sciencepacks:pack_red', category: 'science', x: -1, y: 0,
    triggers: ['minecraft:red_dye*2'], unlocks: ['sciencepacks:pack_red'],
  },
  {
    id: 'green_science', name: '绿色科技包',
    description: '铁屑与萤石粉在瓶中相遇，绿色的微光像植物的汁液，缓慢地爬上瓶壁。',
    icon: 'sciencepacks:pack_green', category: 'science', x: -1, y: 1, rounds: 30,
    prerequisites: ['sciencepacks:red_science'], cost: cost([[R, 1]]),
    unlocks: ['sciencepacks:pack_green'],
  },
  {
    id: 'blue_science', name: '蓝色科技包',
    description: '钻石的冷光透过瓶壁，青金石沉淀成深蓝的星屑，在液面下缓慢旋转。',
    icon: 'sciencepacks:pack_blue', category: 'science', x: -1, y: 2, rounds: 20, researchTime: 40,
    prerequisites: ['sciencepacks:green_science'], cost: cost([[R, 3], [G, 2]]),
    unlocks: ['sciencepacks:pack_blue'],
  },
  {
    id: 'purple_science', name: '紫色科技包',
    description: '末影珍珠的裂隙里渗出紫色的雾，烈焰粉在雾中明明灭灭——像一颗不肯熄灭的星。',
    icon: 'sciencepacks:pack_purple', category: 'science', x: -1, y: 3, rounds: 20, researchTime: 60,
    prerequisites: ['sciencepacks:blue_science'], cost: cost([[G, 5], [B, 3]]),
    unlocks: ['sciencepacks:pack_purple'],
  },
  {
    id: 'gray_science', name: '灰色科技包',
    description: '铁块与火药压成暗灰的一层，燧石与煤在瓶底磨出细响——军备的味道总是带着硝烟。',
    icon: 'sciencepacks:pack_gray', category: 'science', x: -1, y: 4, rounds: 20, researchTime: 80,
    prerequisites: ['sciencepacks:purple_science'], cost: cost([[G, 5], [B, 3], [P, 1]]),
    unlocks: ['sciencepacks:pack_gray'],
  },
  {
    id: 'yellow_science', name: '黄色科技包',
    description: '金块、紫水晶与萤石在高脚杯里分层，最上面浮着一层不肯熄灭的金色光。',
    icon: 'sciencepacks:pack_yellow', category: 'science', x: -1, y: 5, rounds: 20, researchTime: 100,
    prerequisites: ['sciencepacks:gray_science'], cost: cost([[G, 8], [B, 5], [P, 4], [A, 3]]),
    unlocks: ['sciencepacks:pack_yellow'],
  },
  /* ---------- 金属与装备 ---------- */
  {
    id: 'iron_tools', name: '铁工具',
    description: '铁镐咬进岩层的声音比木头沉得多。矿脉终于不再是墙，而是一扇半开的门。',
    icon: 'minecraft:iron_pickaxe', category: 'material', x: 0, y: 1, rounds: 12,
    prerequisites: ['sciencepacks:stone_age'], cost: cost([[R, 1]]),
    unlocks: ['minecraft:iron_pickaxe', 'minecraft:iron_axe', 'minecraft:iron_shovel',
      'minecraft:iron_hoe', 'minecraft:shears', 'minecraft:bucket',
      'minecraft:flint_and_steel', 'minecraft:iron_bars', 'minecraft:iron_block',
      'minecraft:iron_door', 'minecraft:iron_trapdoor', 'minecraft:cauldron'],
  },
  {
    id: 'gold_craft', name: '金工技艺',
    description: '把黄金敲成薄片，贴满整副甲胄。它不结实，但火光下的确好看。',
    icon: 'minecraft:golden_sword', category: 'material', x: 1, y: 0, rounds: 16,
    triggers: ['minecraft:gold_ingot*4'], cost: cost([[R, 2]]),
    unlocks: ['minecraft:golden_pickaxe', 'minecraft:golden_axe', 'minecraft:golden_shovel',
      'minecraft:golden_hoe', 'minecraft:golden_sword', 'minecraft:golden_helmet',
      'minecraft:golden_chestplate', 'minecraft:golden_leggings', 'minecraft:golden_boots',
      'minecraft:gold_block'],
  },
  {
    id: 'military_1', name: '军工学',
    description: '军需官把剑与胸甲并排摆上工作台，旁边是一桶黑色火药：先活下来，再谈文明。',
    icon: 'minecraft:iron_sword', category: 'military', x: 0, y: 2, rounds: 14,
    prerequisites: ['sciencepacks:iron_tools'], cost: cost([[R, 2]]),
    unlocks: ['minecraft:iron_sword', 'minecraft:iron_helmet', 'minecraft:iron_chestplate',
      'minecraft:iron_leggings', 'minecraft:iron_boots', 'minecraft:shield',
      'minecraft:leather_helmet', 'minecraft:leather_chestplate',
      'minecraft:leather_leggings', 'minecraft:leather_boots'],
  },
  {
    id: 'archery', name: '弓弩',
    description: '弓弦绷紧又松开，箭簇在暮色里划出直线。战争第一次有了距离。',
    icon: 'minecraft:bow', category: 'military', x: 1, y: 2, rounds: 16,
    prerequisites: ['sciencepacks:military_1'], cost: cost([[R, 2], [G, 1]]),
    unlocks: ['minecraft:bow', 'minecraft:arrow', 'minecraft:crossbow',
      'minecraft:tipped_arrow', 'minecraft:spectral_arrow'],
  },
  {
    id: 'explosives', name: '爆破工程',
    description: '火药压实、引线点着——不是每一种工程都需要小心翼翼。',
    icon: 'minecraft:tnt', category: 'military', x: 1, y: 3, rounds: 20,
    prerequisites: ['sciencepacks:military_1', 'sciencepacks:nether'], cost: cost([[R, 3], [G, 1]]),
    unlocks: ['minecraft:tnt', 'minecraft:tnt_minecart', 'minecraft:fire_charge'],
  },
  {
    id: 'diamond_tools', name: '钻石工具',
    description: '钻石在熔炉里不肯融化，于是你把它磨成镐与斧——蓝色、冰冷、几乎永不磨损。',
    icon: 'minecraft:diamond_pickaxe', category: 'equipment', x: 0, y: 3, rounds: 18,
    prerequisites: ['sciencepacks:iron_tools'], cost: cost([[R, 3], [G, 1]]),
    unlocks: ['minecraft:diamond_pickaxe', 'minecraft:diamond_axe', 'minecraft:diamond_shovel',
      'minecraft:diamond_hoe', 'minecraft:diamond_block'],
  },
  {
    id: 'diamond_armor', name: '钻石甲胄',
    description: '甲片一片片扣上，发出细碎的咔嗒声。你终于敢站进最深的黑暗里。',
    icon: 'minecraft:diamond_chestplate', category: 'military', x: 1, y: 4, rounds: 20,
    prerequisites: ['sciencepacks:military_1'], cost: cost([[R, 3], [G, 2]]),
    unlocks: ['minecraft:diamond_sword', 'minecraft:diamond_helmet',
      'minecraft:diamond_chestplate', 'minecraft:diamond_leggings',
      'minecraft:diamond_boots'],
  },
  {
    id: 'redstone_basics', name: '基础红石电路',
    description: '红石火把亮起的一瞬，电路学会了呼吸；中继器把脉搏一站一站传得更远。',
    icon: 'minecraft:repeater', category: 'redstone', x: 2, y: 3, rounds: 14,
    prerequisites: ['sciencepacks:red_science'], cost: cost([[R, 2]]),
    unlocks: ['minecraft:redstone_torch', 'minecraft:repeater', 'minecraft:lever',
      'minecraft:stone_button', 'minecraft:stone_pressure_plate', 'minecraft:piston',
      'minecraft:sticky_piston', 'minecraft:redstone_block'],
  },
  {
    id: 'redstone_advanced', name: '高级电路',
    description: '比较器开始比较，侦测器开始注视，红石灯在无人时亮起。电路学会了思考。',
    icon: 'minecraft:comparator', category: 'redstone', x: 3, y: 2, rounds: 20, researchTime: 60,
    prerequisites: ['sciencepacks:redstone_basics'], cost: cost([[R, 3], [G, 1]]),
    unlocks: ['minecraft:comparator', 'minecraft:observer', 'minecraft:daylight_detector',
      'minecraft:tripwire_hook', 'minecraft:heavy_weighted_pressure_plate',
      'minecraft:light_weighted_pressure_plate', 'minecraft:redstone_lamp',
      'minecraft:note_block'],
  },
  {
    id: 'copper_engineering', name: '铜与光学',
    description: '铜在雨里慢慢变绿，磨成镜片却能望见远处的山脊。铜灯在矿道尽头亮起。',
    icon: 'minecraft:copper_block', category: 'building', x: 3, y: 1, rounds: 16,
    prerequisites: ['sciencepacks:masonry', 'sciencepacks:redstone_basics'],
    cost: cost([[R, 2], [G, 1]]),
    unlocks: ['minecraft:copper_block', 'minecraft:cut_copper', 'minecraft:chiseled_copper',
      'minecraft:copper_grate', 'minecraft:cut_copper_stairs', 'minecraft:copper_bulb',
      'minecraft:lightning_rod', 'minecraft:spyglass'],
  },
  /* ---------- 物流 ---------- */
  {
    id: 'logistics_1', name: '基础物流',
    description: '箱子摞成墙，矿车在铁轨上吱呀作响——仓库第一次有了仓库的样子。',
    icon: 'minecraft:chest', category: 'logistics', x: 2, y: 1, rounds: 16,
    prerequisites: ['sciencepacks:iron_tools'], cost: cost([[R, 2]]),
    unlocks: ['minecraft:chest', 'minecraft:barrel', 'minecraft:trapped_chest',
      'minecraft:ladder', 'minecraft:scaffolding', 'minecraft:minecart',
      'minecraft:rail', 'minecraft:oak_boat', 'minecraft:oak_chest_boat'],
  },
  {
    id: 'logistics_2', name: '自动物流',
    description: '漏斗开始自己吞吐，动力铁轨把整座矿井的产出送进同一只箱子。',
    icon: 'minecraft:hopper', category: 'logistics', x: 2, y: 2, rounds: 20, researchTime: 60,
    prerequisites: ['sciencepacks:logistics_1'], cost: cost([[R, 3], [G, 1]]),
    // 装了 Create 时，物流由 create_logistics 接管，这条原版物流研究自动隐藏
    excludes: ['sciencepacks:create_logistics'],
    unlocks: ['minecraft:hopper', 'minecraft:chest_minecart', 'minecraft:hopper_minecart',
      'minecraft:furnace_minecart', 'minecraft:powered_rail', 'minecraft:detector_rail',
      'minecraft:activator_rail'],
  },
  {
    id: 'automation', name: '红石自动化',
    description: '自动合成器咔哒一声合上，红石信号替你把这一页翻了过去。之后，机器替你醒着。',
    icon: 'minecraft:crafter', category: 'logistics', x: 2, y: 4, rounds: 30, researchTime: 100,
    prerequisites: ['sciencepacks:logistics_1', 'sciencepacks:redstone_advanced'],
    cost: cost([[R, 5], [G, 3], [B, 1]]),
    unlocks: ['minecraft:crafter', 'minecraft:dispenser', 'minecraft:dropper',
      'minecraft:target'],
  },
  /* ---------- 下界与酿造 ---------- */
  {
    id: 'nether', name: '下界通行',
    description: '黑曜石框里腾起紫烟。门后不是矿洞，是另一个世界的呼吸声——热、腥、带着硫磺味。',
    icon: 'minecraft:flint_and_steel', category: 'dimension', x: 1, y: 1, rounds: 24, researchTime: 80,
    prerequisites: ['sciencepacks:iron_tools'], cost: cost([[R, 1]]),
    structures: ['nether_portal'],
    unlocks: ['minecraft:nether_bricks', 'minecraft:nether_brick_fence', 'minecraft:quartz_block',
      'minecraft:glowstone', 'minecraft:soul_torch', 'minecraft:soul_lantern',
      'minecraft:lantern', 'minecraft:chain', 'minecraft:magma_block'],
  },
  {
    id: 'potions', name: '酿造工艺',
    description: '酿造台冒着泡，烈焰粉让药水翻滚出橙色。痛苦、失明与剧毒，都可以被调配。',
    icon: 'minecraft:brewing_stand', category: 'magic', x: -2, y: 2, rounds: 18,
    prerequisites: ['sciencepacks:cooking', 'sciencepacks:nether'], cost: cost([[R, 2], [G, 1]]),
    unlocks: ['minecraft:brewing_stand', 'minecraft:blaze_powder', 'minecraft:magma_cream',
      'minecraft:glistering_melon_slice', 'minecraft:golden_apple'],
  },
  {
    id: 'enchanting', name: '附魔学',
    description: '附魔台的低语顺着书架爬上来，青金石在符文间沉下去。铁砧在旁静静等待。',
    icon: 'minecraft:enchanting_table', category: 'magic', x: -2, y: 3, rounds: 24, researchTime: 80,
    prerequisites: ['sciencepacks:librarian', 'sciencepacks:potions'],
    cost: cost([[G, 2], [B, 1]]),
    unlocks: ['minecraft:enchanting_table', 'minecraft:lapis_block', 'minecraft:amethyst_block'],
  },
  {
    id: 'netherite_gear', name: '下界合金',
    description: '下界合金在锻造台上渗出黑色的光。旧装备被投入，新的归来，边缘还带着余温。',
    icon: 'minecraft:netherite_ingot', category: 'equipment', x: 0, y: 4, rounds: 30, researchTime: 90,
    prerequisites: ['sciencepacks:diamond_tools', 'sciencepacks:diamond_armor', 'sciencepacks:smithy'],
    cost: cost([[G, 3], [B, 2], [P, 1]]),
    unlocks: ['minecraft:netherite_pickaxe', 'minecraft:netherite_axe',
      'minecraft:netherite_shovel', 'minecraft:netherite_hoe', 'minecraft:netherite_sword',
      'minecraft:netherite_helmet', 'minecraft:netherite_chestplate',
      'minecraft:netherite_leggings', 'minecraft:netherite_boots',
      'minecraft:netherite_block', 'minecraft:lodestone', 'minecraft:crying_obsidian'],
  },
  /* ---------- 终局 ---------- */
  {
    id: 'endgame', name: '终局科技',
    description: '信标的光柱刺穿云层，潮涌核心在水下睁开眼。图纸的最后一角，终于补全了。',
    icon: 'minecraft:beacon', category: 'endgame', x: 0, y: 5, rounds: 40, researchTime: 120,
    prerequisites: ['sciencepacks:automation', 'sciencepacks:purple_science'],
    cost: cost([[R, 6], [G, 4], [B, 2], [P, 1]]),
    unlocks: ['minecraft:beacon', 'minecraft:conduit', 'minecraft:respawn_anchor',
      'minecraft:mace', 'minecraft:heavy_core', 'minecraft:ender_eye'],
  },
  {
    id: 'the_end', name: '末地之门',
    description: '末影之眼一颗颗嵌进门框。虚空在门后安静地等待被跨过。',
    icon: 'minecraft:ender_eye', category: 'dimension', x: 1, y: 6, rounds: 32, researchTime: 100,
    prerequisites: ['sciencepacks:endgame'], cost: cost([[P, 2]]),
    structures: ['end_portal'],
    unlocks: ['minecraft:ender_chest', 'minecraft:end_crystal', 'minecraft:shulker_box',
      'minecraft:purpur_block', 'minecraft:purpur_pillar', 'minecraft:end_stone_bricks',
      'minecraft:end_rod'],
  },
  {
    id: 'mining_speed', name: '终极研究 I：挖掘效率',
    description: '新镐芯的每一次落点都在变准，岩层开始主动让路。每完成一次，消耗翻倍。',
    icon: 'minecraft:iron_pickaxe', category: 'endgame', x: 2, y: 5, rounds: 5, researchTime: 120,
    infinite: true, maxLevel: 5, costMultiplier: 2.0,
    prerequisites: ['sciencepacks:endgame'], cost: cost([[R, 8], [G, 6], [B, 6], [P, 4], [A, 6], [Y, 6]]),
    effects: [{ type: 'mining_speed', amount: 0.15 }],
  },
  {
    id: 'reach_upgrade', name: '终极研究 II：触及距离',
    description: '末影珍珠把指尖拉长了一寸。世界忽然近得触手可及，连远处的水晶也不再遥远。',
    icon: 'minecraft:ender_pearl', category: 'endgame', x: 3, y: 5, rounds: 5, researchTime: 120,
    infinite: true, maxLevel: 3, costMultiplier: 2.0,
    prerequisites: ['sciencepacks:mining_speed'], cost: cost([[R, 10], [G, 8], [B, 8], [P, 5], [A, 8], [Y, 8]]),
    effects: [{ type: 'reach', amount: 1.0 }],
  },

  /* ---------- 模组联动：Create（装了 Create 才加载） ---------- */
  {
    id: 'create_kinetics', name: '动力工程',
    description: '安山合金被压成第一根传动轴。水车转动、齿轮咬合，风与流水第一次被写进图纸的动力链。',
    icon: 'create:shaft', category: 'logistics', x: 4, y: 1, rounds: 6, researchTime: 60,
    requiredMods: ['create'],
    prerequisites: ['sciencepacks:iron_tools'],
    cost: cost([[R, 2], [G, 2]]),
    triggers: ['create:andesite_alloy*2'],
    unlocks: ['create:andesite_alloy', 'create:shaft', 'create:cogwheel', 'create:large_cogwheel',
      'create:gearbox', 'create:hand_crank', 'create:water_wheel', 'create:windmill_bearing',
      'create:andesite_casing'],
  },
  {
    id: 'create_processing', name: '机械加工',
    description: '压力机把矿石压成板，搅拌机在盆里翻出新的合金。人力被机器接管的那一刻，工坊安静了下来。',
    icon: 'create:mechanical_press', category: 'logistics', x: 4, y: 2, rounds: 10, researchTime: 90,
    requiredMods: ['create'],
    prerequisites: ['sciencepacks:create_kinetics'],
    cost: cost([[R, 3], [G, 3], [B, 1]]),
    unlocks: ['create:mechanical_press', 'create:millstone', 'create:mechanical_mixer', 'create:basin',
      'create:mechanical_saw', 'create:mechanical_drill', 'create:encased_fan', 'create:depot'],
  },
  {
    id: 'create_logistics', name: '机械物流',
    description: '机械臂把箱子里的货一件件摆上带子，漏斗与滑道接力，整座工坊开始自己搬运自己。',
    icon: 'create:mechanical_arm', category: 'logistics', x: 4, y: 3, rounds: 12, researchTime: 110,
    requiredMods: ['create'],
    prerequisites: ['sciencepacks:create_processing'],
    cost: cost([[R, 4], [G, 4], [B, 2]]),
    unlocks: ['create:chute', 'create:andesite_funnel', 'create:brass_funnel', 'create:mechanical_arm',
      'create:deployer', 'create:belt_connector', 'create:copper_casing', 'create:brass_casing',
      'create:clutch', 'create:nozzle'],
  },
];

function build(t) {
  const out = { name: t.name, description: t.description, icon: t.icon, category: t.category, x: t.x, y: t.y };
  if (t.researchTime) out.researchTime = t.researchTime;
  if (t.rounds) out.rounds = t.rounds;
  if (t.infinite) out.infinite = true;
  if (t.maxLevel) out.maxLevel = t.maxLevel;
  if (t.costMultiplier && t.costMultiplier !== 2.0) out.costMultiplier = t.costMultiplier;
  if (t.prerequisites && t.prerequisites.length) out.prerequisites = t.prerequisites;
  if (t.triggers && t.triggers.length) out.triggers = t.triggers;
  if (t.cost && t.cost.length) out.cost = t.cost;
  if (t.unlocks && t.unlocks.length) out.unlocks = t.unlocks;
  if (t.structures && t.structures.length) out.structures = t.structures;
  if (t.effects && t.effects.length) out.effects = t.effects;
  if (t.requiredMods && t.requiredMods.length) out.requiredMods = t.requiredMods;
  if (t.excludes && t.excludes.length) out.excludes = t.excludes;
  return out;
}

const known = new Set(JSON.parse(fs.readFileSync(ITEMS, 'utf8')));
const bad = [];
const unverified = [];
const seen = new Map();
const hasMod = (t) => t.requiredMods && t.requiredMods.length > 0;
for (const t of TECHS) {
  // 联动科技的物品来自外部模组，原版物品清单里没有，跳过校验但记录下来
  for (const u of t.unlocks || []) {
    if (hasMod(t)) { unverified.push(`${t.id}: ${u}`); }
    else if (!known.has(u)) bad.push(`${t.id}: 未知物品 ${u}`);
    if (seen.has(u)) bad.push(`${t.id}: 重复解锁 ${u}（已在 ${seen.get(u)}）`);
    else seen.set(u, t.id);
  }
  if (!hasMod(t) && !known.has(t.icon)) bad.push(`${t.id} (icon): ${t.icon}`);
  for (const c of t.cost || []) if (!hasMod(t) && !known.has(c.item)) bad.push(`${t.id} (cost): ${c.item}`);
  for (const p of t.prerequisites || []) {
    if (!TECHS.some((x) => 'sciencepacks:' + x.id === p)) bad.push(`${t.id}: 未知前置 ${p}`);
  }
  for (const e of t.excludes || []) {
    if (!TECHS.some((x) => 'sciencepacks:' + x.id === e)) bad.push(`${t.id}: 未知互斥研究 ${e}`);
    if (e === 'sciencepacks:' + t.id) bad.push(`${t.id}: 不能与自己互斥`);
  }
}
// 坐标冲突检查：同一格子只能有一个研究
const cells = new Map();
for (const t of TECHS) {
  const key = t.x + ',' + t.y;
  if (cells.has(key)) bad.push(`坐标冲突 (${key}): ${cells.get(key)} / ${t.id}`);
  else cells.set(key, t.id);
}

fs.mkdirSync(OUT, { recursive: true });
const ids = new Set(TECHS.map((t) => t.id));
for (const t of TECHS) {
  fs.writeFileSync(path.join(OUT, `${t.id}.json`), JSON.stringify(build(t), null, 2) + '\n', 'utf8');
}
for (const f of fs.readdirSync(OUT)) {
  if (f.endsWith('.json') && !ids.has(f.slice(0, -5))) {
    fs.unlinkSync(path.join(OUT, f));
    console.log('removed', f);
  }
}
console.log(`wrote ${TECHS.length} technologies`);
if (unverified.length) {
  console.log(`skip-checked ${unverified.length} modded ids (need the mod installed to verify):`);
  for (const u of unverified) console.log('  ' + u);
}
if (bad.length) {
  console.log('PROBLEMS:');
  for (const b of bad) console.log('  ' + b);
} else {
  console.log('OK: ids valid, no duplicate unlocks, prerequisites exist, no coordinate conflicts');
}
