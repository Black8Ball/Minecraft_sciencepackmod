# 科技瓶 Science Packs

Factorio 风格的科技树模组（**NeoForge 1.21.1**）：**配方被科技锁定**，通过「获得指定物品」或「研究台消耗材料」解锁。
界面参考 Factorio：左侧研究队列 / 科技详情 / 科技列表（约 30%），右侧可拖拽缩放的科技树（约 70%）。

> 关于这个项目的来历：作者 **Black8Ball** 闲来无事，把想法丢给 DeepSeek（AI）说「给我做个 Factorio 风格的研究树模组」，
> 需求、方向、验收、贴图取舍由作者拍板，代码、研究树生成脚本、可视化编辑器与文档由 AI 写出来。详见文末「关于这个项目」。
连这个仓库也是我让DS帮我推的
## 已实现功能

| 功能 | 说明 |
| --- | --- |
| 配方锁（合成） | 工作台 3x3 与背包 2x2 的合成结果在未解锁时为空 |
| 配方锁（熔炉族） | 熔炉/高炉/烟熏炉/营火：输入配方未解锁时整个 tick 暂停（不烧、不耗燃料），产物也取不走 |
| 配方锁（锻造/切石） | 锻造台、切石机的产物在未解锁时结果槽为空 |
| 触发解锁 | 科技可配置 `triggers`：拿到对应物品即自动解锁（铁锭/铁矿/红石…） |
| 免费科技 | 没有 `cost` 也没有 `triggers` 的科技，前置满足后自动解锁（可配置关闭） |
| 研究台 | 放置 → 放入材料 → 按每份材料的耗时消耗 → 进度满解锁 |
| 研究台可配置 | 槽位数量（1-9）、允许放入的物品列表都可以在配置里改 |
| 每份材料独立耗时 | `cost` 条目可用 `"ticks"` 单独指定，未写则用全局 `ticksPerPack` |
| 材料不限于瓶子 | `cost.item` 可以是任意物品 id（矿物、锭、食物都行） |
| 研究共享 | `personal` / `team`（用户名正则分组）/ `global` |
| 配方书同步 | 未解锁配方在配方书中隐藏 |
| 全数据驱动 | `data/<ns>/sciencepacks/technologies/*.json`，`/reload` 热重载 |
| 轮次制研究 | 每轮消耗一组材料、跑 `researchTime`，完成 `rounds` 轮解锁（v1.10） |
| 白名单 / 多配方 | `whitelist` 放行指定配方；`recipe:` / `item:` 前缀精确控制解锁粒度（v1.11） |
| 隐藏研究 | `hidden` + `showWhen`，满足条件才出现在研究列表与研究树（v1.11） |
| 研究树总览 | 树页面「总览 / 关联」切换，选择会记住（v1.11） |
| 研究台可自动化 | 漏斗等方块可以从研究台槽位抽出材料（v1.11） |
| 无限研究 | `infinite` + `maxLevel` + `costMultiplier`：每升一级消耗翻倍，可反复研究（v1.12） |
| 数值型效果 | `effects`：每级挖掘速度、触及距离、指定方块放置上限；可叠加到等级上限（v1.12） |
| 酿造台锁 | 玩家放置的酿造台在原料未解锁时整体暂停，可在配置里关闭（v1.12） |
| 六色科技包 | 铁质 / 物流 / 红石 / 末影 / 军备 / 效能 科技包，槽位表默认六项（v1.12） |
| 队列批量排程 | 前置在队列中即可排后置；取消前置会递归取消依赖它的排队研究（v1.13） |
| 队列播报与完成提示 | 开始/取消/调序/清空向组内播报；完成时播放可配置音效 + 右上角提示框（v1.13） |
| 命令 | `/sciencepacks` 系列 |

## 安装

1. 装 **Minecraft 1.20.1 + Forge 47.4.28**。
2. 把 `sciencepacks-1.0.0.jar` 放进 `mods/`。
3. 进游戏：创造模式「科技瓶」页签；按 **K** 打开科技树；放下「研究台」右键打开。

## 更新须知（重要）

替换 `mods/sciencepacks-1.0.0.jar` 之前请**完全退出游戏**（不是退回主菜单，而是关掉游戏进程），再启动。

- 游戏运行时覆盖 jar，正在读取的资源包会失效，典型表现是：所有本模组贴图变成紫黑块、界面显示 `gui.sciencepacks.xxx` 原始键名、研究树变空。
- 这种情况不是语言文件或数据包坏了，重启游戏即可恢复；切语言本身是安全的。
- 仓库里的 `tools/deploy.ps1` 会检测游戏是否在运行，运行中自动跳过客户端 mods 目录，避免踩这个坑。

## 玩法循环

1. 按 K 打开科技树 → 选中科技 → 「开始研究」入队。
2. 放置**研究台**，把科技要求的材料放进槽位。
3. 研究台按每份材料的耗时逐份消耗，进度走满即解锁，队列自动推进下一项。
4. 带 `triggers` 的科技（如铁器时代）不需要研究台，拿到物品即解锁。

## 科技 JSON 格式

```json
{
  "name": "显示名",
  "description": "说明文本",
  "icon": "minecraft:iron_ingot",
  "category": "material",
  "x": 0, "y": 1,
  "researchTime": 100,
  "rounds": 30,
  "prerequisites": ["sciencepacks:stone_age"],
  "triggers": ["minecraft:iron_ingot*4", "block_break:minecraft:stone*64"],
  "cost": [
    { "item": "sciencepacks:pack_red", "count": 1 },
    { "item": "sciencepacks:pack_green", "count": 2 }
  ],
  "unlocks": ["minecraft:iron_pickaxe", "sciencepacks:pack_red"],
  "structures": ["nether_portal", "end_portal"]
}
```

**无限研究 / 数值效果（v1.12）**

```json
{
  "name": "效能 I：挖掘速度",
  "icon": "minecraft:iron_pickaxe",
  "rounds": 3,
  "infinite": true,
  "maxLevel": 5,
  "costMultiplier": 2.0,
  "cost": [{ "item": "sciencepacks:pack_yellow", "count": 1 }],
  "effects": [{ "type": "mining_speed", "amount": 0.15 }],
  "prerequisites": ["sciencepacks:green_science"]
}
```

- `infinite`：设为无限研究，可反复研究升级；每升一级，下一级的每轮消耗 = 原倍率 × `costMultiplier^等级`（四舍五入向上取整）
- `maxLevel`：最高等级，0 或省略 = 不限；到达上限后自动解锁（不再出现在队列）
- `costMultiplier`：每级消耗倍率，默认 2.0，最小 1.0
- `effects`：数值型效果数组，可多个：
  - `mining_speed`：每级挖掘速度 +`amount`（0.15 = +15%）
  - `reach`：每级方块/实体触及距离 +`amount` 格
  - `place_limit`：每级 `block` 的放置上限 +`amount`（必须带 `block`，例如 `"minecraft:torch"`）；基础上限在 `config/sciencepacks-common.toml` 的 `[limits] blockPlaceLimits` 里配（条目 `modid:block=数量`）
- 无限研究升级时给全组提示 `message.sciencepacks.levelup`，等级存在玩家 NBT 的 `Levels`；`/sciencepacks dumpresearch` 导出的 JSON 会包含这些字段
- 示例数据包已带 `sciencepacks:mining_speed`（效能 I：挖掘速度）、`sciencepacks:reach_upgrade`（效能 II：触及距离）、`sciencepacks:armament_place_limit`（军备 I：火把上限）

- `prerequisites`：前置科技（必须全部解锁）
- `triggers`：触发条件，**全部满足**才自动解锁（详见下一节）
- `researchTime`：这项研究每轮的耗时（tick），省略则用全局 `ticksPerPack`
- `rounds`：需要完成的研究轮数（默认 1）
- `cost`：**每轮**同时在研究台里扣掉的清单，每一项都必须凑齐
  - `item`：任意物品 id（不限于科技瓶，可填模组物品）
  - `count`：每轮消耗的数量（例：`2` = 每轮 2 个绿瓶；一轮 30 轮总共 60 个）
  - `ticks`：兼容旧字段，现由 `researchTime` 决定，可忽略
- `unlocks`：被锁定的配方 id **或物品 id**（两者都会匹配，模组配方写配方 id 即可）
- `structures`：被锁定的多方块结构（详见「多方块结构锁」）
- `x` / `y`：科技树坐标（y 向下）

总需求 = 每轮倍率 × `rounds`。例如上例消耗 30 红瓶 + 60 绿瓶，共 30 轮。

**触发条件写法（v1.9）**

| 写法 | 含义 |
| --- | --- |
| `minecraft:iron_ingot` | 背包里持有 ≥1 个铁锭（实时检查） |
| `minecraft:iron_ingot*4` | 持有 ≥4 个 |
| `block_break:minecraft:stone*64` | 累计挖掘 64 个石头 |
| `kill:minecraft:zombie*10` | 累计击杀 10 只僵尸 |
| `craft:minecraft:iron_pickaxe*1` | 累计合成 1 次 |
| `smelt:minecraft:iron_ingot*16` | 累计烧炼 16 个 |
| `pickup:minecraft:diamond*1` | 累计拾取 1 颗钻石 |
| `dimension:minecraft:the_nether` | 到达某个维度 |

也可以写成对象：`{ "type": "block_break", "id": "minecraft:stone", "count": 64 }`。
类型标签可在语言文件里改（`gui.sciencepacks.trigger.*`）。

**三种科技类型**

| 类型 | 条件 | 行为 |
| --- | --- | --- |
| 触发式 | 有 `triggers` | 满足条件后自动解锁；不能手动开始研究 |
| 研究式 | 有 `cost` | 需要研究台供料 |
| 免费式 | 无 `cost` 且无 `triggers` | 前置满足后自动解锁（`autoUnlockFree`） |

## 配置（`config/sciencepacks-common.toml`）

```toml
[research]
lockCrafting = true          # 合成台
lockSmelting = true          # 熔炉族（彻底暂停 + 取不走）
lockSmithing = true          # 锻造台
lockStonecutting = true      # 切石机
lockBrewing = true           # 酿造台（原料未解锁时整体暂停）
ticksPerPack = 20            # 每份材料的默认耗时（tick），可被 JSON 覆盖
announceUnlocks = true
announceQueue = true         # 开始/取消/调序/清空队列时向组内播报
completeSound = "minecraft:entity.player.levelup"   # 研究完成音效，留空则不播放
completeToast = true         # 研究完成时右上角弹提示框
triggerScanInterval = 20
hideLockedRecipeBook = true
autoUnlockTriggered = true
autoUnlockFree = true

[station]
# 固定槽位表：一个条目 = 一个槽位，顺序就是界面里从左到右的顺序。
# 槽位数量不再单独配置（没有 stationSlots 了），完全由这个列表的长度决定，上限 54。
stationAllowedItems = [
  "sciencepacks:pack_red", "sciencepacks:pack_green",
  "sciencepacks:pack_blue", "sciencepacks:pack_purple",
  "sciencepacks:pack_gray", "sciencepacks:pack_yellow"
]

[limits]
# 方块放置上限的基础值，条目写法 modid:block=数量；研究里的 place_limit 效果在其上叠加
blockPlaceLimits = []

[sharing]
shareMode = "personal"       # personal / team / global
teamPattern = "^(.+)$"       # team 模式下的用户名正则
teamRegexGroup = 1

[structures]
lockStructures = true        # 多方块结构锁（下界门 / 末地门 / 凋灵 / 傀儡）
```

`stationAllowedItems` 示例（槽位会按这个顺序排成一行 9 个）：

```toml
stationAllowedItems = [
  "sciencepacks:pack_red", "sciencepacks:pack_green",
  "sciencepacks:pack_blue", "sciencepacks:pack_purple",
  "minecraft:iron_ingot"
]
```

留空（`[]`）等同于默认的六种科技包：red / green / blue / purple / gray / yellow。

## 命令（OP）

```
/sciencepacks techs                  列出所有科技
/sciencepacks status [player]        查看进度
/sciencepacks unlock <tech> [player] 解锁
/sciencepacks lock <tech>            重新锁上
/sciencepacks reset [player]         清空进度
/sciencepacks queue <tech>           加入队列
/sciencepacks debug <recipe>         查询配方归属（同时显示「按配方 id」与「按产物物品」两种结果）
/sciencepacks dumprecipes            把所有配方的归属导出到 config/sciencepacks-recipes.json
/sciencepacks dumpresearch           把当前研究树导出到 config/sciencepacks-research-export.json（可直接改）
/sciencepacks reload                 重载科技定义
```

## 轮次制研究（v1.10）

- 每个研究用 `"rounds"` 配置**需要多少轮**，用 `cost[].count` 配置**每轮消耗几个**；总需求 = 倍率 × 轮数。
- 每个研究可以用 `"researchTime"` 单独配置**每轮耗时**（tick，20 tick = 1 秒）；不写则用全局 `ticksPerPack`。
- 一轮 = 当前研究需要的**所有材料按各自倍率同时扣掉**（不是一种一种轮流扣）。材料不齐时整轮跳过：不扣任何东西、进度不动。
- 研究台界面显示：当前研究图标与名称、总进度（已完成轮数 / 总轮数）、每轮消耗倍率、剩余需求（倍率 × 剩余轮数）。
- 研究详情里成本行右侧显示「轮次：已完成 / 总轮数」，悬停可见每轮消耗、需要数量、剩余数量。

```json
{
  "name": "终局研究",
  "x": 0, "y": 5,
  "rounds": 50,
  "researchTime": 120,
  "cost": [
    { "item": "sciencepacks:pack_red", "count": 6 },
    { "item": "sciencepacks:pack_green", "count": 4 },
    { "item": "sciencepacks:pack_purple", "count": 1 }
  ]
}
```

上例每轮扣 6 红 + 4 绿 + 1 紫，共 50 轮（总需求 300 / 200 / 50）。

## 队列规则、播报与完成提示（v1.13）

- **前置在队列里就能排后置**：只要前置「已完成」或者「已经在队列里排队」，后置研究就可以直接排进队列，
  不用等前置真正研究完；整条科技线可以一次排好，队列会按顺序依次完成。
- **取消前置会连带取消后置**：取消某项研究时，所有「依赖它、但它还没完成」的排队研究会被递归取消
  （进度仍按研究单独保存，重新排队可以接着研究）。
- **队列操作播报**：开始研究、取消研究、调整顺序、清空队列时，会向同一研究组的所有在线成员播报；
  可用 `announceQueue = false` 关闭。
- **黄色标记**：如果一项研究的前置还排在队列里（还没完成，但已经排上），它现在也能排进队列，
  但不会马上开始。这类研究在研究列表、研究树和队列里都会用黄色描边标出，详情状态显示「等待前置研究」，
  队列悬停提示「前置研究还在队列中，完成后自动开始」，和「前置已完成、可立即开始」的研究区分开。
- **完成提示**：研究完成（或无限研究升级）时，给组内成员播放音效并在右上角弹出提示框（原版提示框样式）：
  - `completeSound`：音效 id，默认 `minecraft:entity.player.levelup`（升级音效），留空则不播放；
  - `completeToast`：是否弹出右上角提示框，默认 `true`。

```toml
[research]
	announceQueue = true
	completeSound = "minecraft:entity.player.levelup"
	completeToast = true
```

## 研究台规则（v1.6）

- **一个槽位固定一种物品**：槽位表由 `stationAllowedItems` 的顺序决定，第 N 个物品就是第 N 个槽位，最多 54 个；空槽会显示该物品的淡色虚影（半透明渲染），一眼看出该放什么。
- **六色科技包**：默认槽位表是铁质 / 物流 / 红石 / 末影 / 军备 / 效能六种科技包，各占一格。
  从旧版本升级时，如果配置里还留着升级前默认的「红绿蓝紫」四项，研究台会自动补上灰（军备）、黄（效能）两格；
  玩家自己改过的槽位表不会被改动（想加就手动在 `stationAllowedItems` 里写 `sciencepacks:pack_gray` / `pack_yellow`）。
- **每格同类叠加**：每个槽位只能放对应的那一种物品，同种物品可以叠加到 64 个（一组上限）；不同物品不能混放，Shift / 漏斗都不会灌进别的物品。
- **材料不齐就不工作**：一轮需要当前研究所有材料按各自倍率同时齐全，缺任何一种时该研究台整轮跳过（不扣任何材料、进度不动）；补齐后自动继续。
- **支持自动化输入**：研究台实现了 `IItemHandler` 能力，漏斗/管道/其他模组的物流都能往里送；不允许自动化把材料抽走。
- **交互修复**：在面板空白处点击不会再把手里拿着的物品丢到地上；玩家背包里的物品可正常拿取、交换、放回。
- 界面显示：当前科技图标/名称、总进度（已消耗材料数 / 总需求数）、每种材料剩余数量、本轮消耗耐久条。

## 界面设置（v1.9）

- 所有界面相关配置都在 **MOD 列表 →「科技瓶 Science Packs」→「配置」→ 外观页**：
  研究树背景透明/不透明、面板不透明度（0.25–1.0）、界面明暗度（0.4–1.6）。
- 研究树标题栏右上角的「背景：透明/不透明」按钮已移除，避免和研究树内容抢位置。
- 研究队列里**每个研究方块都显示自己的进度**：调整队列顺序后，正在研究的那项即使被挪到后面，
  方块上的进度条也会显示它真实的进度，不会变成空条。
- 队列方块支持拖拽排序；详情面板右下角的 ▲ / ▼ 可以把研究直接前移到队首或后移到队尾。

## 与 KubeJS / 整合包魔改的兼容（v1.8）

研究的 `unlocks` 里写的是**物品 id**（例如 `minecraft:iron_pickaxe`），原版配方的 id 恰好等于产物物品 id，所以早期实现只按配方 id 匹配也能用；
但 KubeJS、其它模组生成的配方 id 往往是 `kubejs:xxx`、`modid:something`，与物品名不同，旧逻辑就会绕过锁定。

现在的归属判定顺序是：

1. 先按**配方 id** 精确匹配（兼容只改配方、不改产物的数据包）；
2. 匹配不到时，取该配方的**产物物品 id** 再匹配一次。

因此：

- 用 KubeJS `event.shaped(...).id('kubejs:xxx')` 新加的配方，只要产物是被锁物品，会自动被对应研究锁住，无需额外配置；
- 被 KubeJS 修改/替换但仍产出原物品的配方，同样保持锁定；
- 只改产物、不改 id 的配方以产物为准。

实测环境：Minecraft 1.20.1 Forge 47.4.28 + KubeJS 2001.6.5-build.26 + Architectury 9.2.14 + Rhino 2001.2.3。
服务端脚本新增一条 `kubejs:test_locked_pickaxe`（产物铁镐）后启动，`/sciencepacks dumprecipes` 导出 1179 条配方，
其中 `kubejs:test_locked_pickaxe => sciencepacks:iron_age`，与原版 `minecraft:iron_pickaxe => sciencepacks:iron_age` 一致，锁定生效。

用同样的思路适配其它魔改工具：只要其配方最终由 `RecipeManager` 管理（KubeJS、CraftTweaker、数据包、大部分模组都是），都会走上面的归属判定。
涉及非配方体系的自定义逻辑（例如某个模组自己实现的合成界面），需要该模组调用 `Research.isRecipeLocked` / `isOutputLocked` 才会生效。

## 多方块结构锁（v1.9）

给研究加 `structures` 字段后，对应结构在未解锁时无法被建造/使用。
结构条目支持三类写法，兼容其它模组：

| 写法 | 含义 |
| --- | --- |
| `nether_portal` / `end_portal` / `wither` / `iron_golem` / `snow_golem` | 内置结构 |
| `dimension:modid:dimension_id` | 禁止进入某个维度（任意模组维度） |
| `entity:modid:entity_id` | 禁止某类实体生成/被玩家生成（任意模组实体） |
| `item_on_block:modid:item=modid:block` | 手持该物品右键该方块时拦截（任意模组物品/方块交互） |

还可以加自定义图标与名称，格式 `核心id|大图标物品id|显示名称|小角标物品id`（后三段都可省略）：

```json
{ "structures": ["nether_portal|minecraft:obsidian|下界通行|minecraft:flint_and_steel",
                 "dimension:twilightforest:twilight_forest",
                 "item_on_block:minecraft:flint_and_steel=minecraft:obsidian"] }
```

| 内置结构 | 拦截点 |
| --- | --- |
| `nether_portal` | 打火石/火焰弹点黑曜石、传送门方块生成、进入下界 |
| `end_portal` | 往末地传送门框架里塞末影之眼、进入末地 |
| `wither` | 凋灵生成时取消（附近 12 格内最近玩家未解锁） |
| `iron_golem` / `snow_golem` | 傀儡生成时取消 |

结构锁也会显示在研究详情「效果」栏：大图标 + 右下角小角标，悬停显示结构名与「允许……」的效果说明；
角标默认按结构类型给（下界门 = 打火石、末地门 = 末影之眼、凋灵 = 灵魂沙、傀儡 = 铁块/雪块），也可用第 4 段自定义。
默认 `nether_portal` 的图标是黑曜石、`end_portal` 是末地传送门框架。
被拦下时会有一条红色提示（`message.sciencepacks.structure.locked`），传送门提醒有 5 秒冷却防止刷屏。
总开关是配置里的 `[structures] lockStructures`，MOD 列表 →「配置」→ 玩法页也能直接关。

## 科技包与酿造台锁（v1.12）

科技瓶配方大部分都比较弱智，但我没什么时间改了-2026.11.1
六种科技包：

| 物品 id | 显示名 | 配方（对应研究未完成时锁住） |
| --- | --- | --- |
| `sciencepacks:pack_red` | 铁质科技包 | 玻璃瓶 + 红色染料 + 糖（无序，×2） |
| `sciencepacks:pack_green` | 物流科技包 | 玻璃瓶 + 铁锭 + 萤石粉 + 糖（×2） |
| `sciencepacks:pack_blue` | 红石科技包 | 玻璃瓶 + 青金石 + 钻石 + 红石块 + 糖（×2） |
| `sciencepacks:pack_purple` | 末影科技包 | 玻璃瓶 + 末影珍珠 + 烈焰粉 + 紫水晶碎片 + 糖（×2） |
| `sciencepacks:pack_gray` | 军备科技包 | 玻璃瓶 + 铁块 + 火药 + 燧石 + 煤 + 糖（×2） |
| `sciencepacks:pack_yellow` | 效能科技包 | 3×3 有序：金块 ×2 + 紫水晶碎片 + 萤石 ×2 + 青金石 + 红石 + 烈焰粉，中心玻璃瓶（×2） |

红色科技包特意用**红色染料**而不是红石：红石矿需要铁镐，而铁镐由「铁工具」解锁、铁工具又要红色科技包，会形成死循环。

配方位于 `data/sciencepacks/recipe/`（1.21 起目录是单数 `recipe`，不再是 `recipes`），由 `tools/gen-recipes.js` 生成。
每色科技包对应一项研究，未完成时该配方在配方书与工作台里都不可用：`pack_red ← red_science`、`green ← green_science`、`blue ← blue_science`、`purple ← purple_science`、`gray ← gray_science`、`yellow ← yellow_science`。

研究台默认槽位表是六个包各一格（`[station] stationAllowedItems`，顺序即界面顺序）。
**酿造台锁**：玩家放置的酿造台会记录归属组，槽位 3 的原料若属于未完成的研究，整个酿造 tick 暂停（不推进、不耗材料），
靠近的组员每 5 秒收到一次提示；原料命中白名单或研究已完成（无限研究等级 > 0 也算）时放行。
总开关 `[research] lockBrewing`，MOD 列表 →「配置」→ 玩法页可关。

## 魔改 API（v1.9）

除了数据包，还可以在 KubeJS 等脚本里直接改研究树。API 类不依赖任何 KubeJS 类型，
用 `Java.loadClass('com.sciencepacks.api.SciencePacksApi')` 拿到即可：

| 方法 | 说明 |
| --- | --- |
| `techIds()` | 当前生效的全部研究 id（逗号分隔） |
| `techJson()` | 当前研究树 JSON（可直接写进配置文件备份） |
| `addTech(json)` | 加/覆盖一项研究（同一 `id` 覆盖数据包里的定义），返回生效 id |
| `addTech(id, json)` | 同上，显式指定 id |
| `removeTech(id)` | 从研究树里隐藏一项研究 |
| `reset()` | 清空脚本改动，回到数据包定义 |
| `unlock(playerName, techId)` / `lock(...)` | 给某个玩家所在的组解锁/锁定 |
| `isUnlocked(playerName, techId)` | 查询 |
| `structureOwner(structureId)` | 查询某个结构被哪项研究锁着 |

KubeJS `server_scripts/` 示例：

```js
const SciencePacks = Java.loadClass('com.sciencepacks.api.SciencePacksApi')

ServerEvents.loaded(event => {
  // 覆盖/新增研究（字段与数据包 JSON 一致）
  SciencePacks.addTech('{"id":"steam_age","name":"蒸汽时代","icon":"minecraft:furnace",' +
    '"prerequisites":["sciencepacks:iron_age"],"cost":[{"item":"sciencepacks:pack_red","count":20}],' +
    '"x":2,"y":2,"researchTime":60}')

  // 隐藏原版研究
  SciencePacks.removeTech('sciencepacks:automation')
})
```

- 调用后会自动重建「配方/结构 → 研究」的归属表，并把新状态同步给在线玩家，不需要重启。
- 同一 id 的脚本定义优先于数据包；`removeTech` 之后再 `addTech` 同 id 可以恢复。
- 也可以用数据包方式覆盖：把 `kubejs/data/sciencepacks/sciencepacks/technologies/xxx.json` 放进去，
  用相同 `id` 即可覆盖内置定义（这个 id 由文件名决定，文件名即 id）。

## 用配置文件改研究树（v1.10）

不想动数据包时，可以直接在配置文件里改研究树，分**全局**与**单个世界**两层（后者覆盖前者）：

| 层级 | 路径 | 生效范围 |
| --- | --- | --- |
| 全局 | `config/sciencepacks-research.json` | 所有存档 |
| 单世界 | `<存档>/serverconfig/sciencepacks-research.json` | 只对当前存档 |

格式与数据包一致，外面包一层 `technologies` / `remove`：

```json
{
  "technologies": [
    {
      "id": "sciencepacks:stone_age",
      "name": "石器时代（改过）",
      "icon": "minecraft:cobblestone",
      "description": "捡 32 个圆石就解锁。",
      "x": 0, "y": 0,
      "triggers": ["minecraft:cobblestone*32"],
      "unlocks": ["minecraft:stone_pickaxe", "minecraft:stone_axe"]
    },
    {
      "id": "mypack:steam_age",
      "name": "蒸汽时代",
      "icon": "minecraft:furnace",
      "x": 3, "y": 2,
      "prerequisites": ["sciencepacks:iron_age"],
      "rounds": 20,
      "researchTime": 40,
      "cost": [
        { "item": "sciencepacks:pack_red", "count": 2 },
        { "item": "minecraft:iron_ingot", "count": 8 }
      ],
      "unlocks": ["create:mechanical_press", "minecraft:blast_furnace"],
      "structures": ["item_on_block:minecraft:flint_and_steel=minecraft:obsidian"]
    }
  ],
  "remove": ["sciencepacks:endgame"]
}
```

- 同一个 `id` 会覆盖数据包里的定义；新 `id` 就是新增研究。
- `remove` 里的 id 会从研究树里隐藏（不会删存档进度）。
- 文件缺失时模组会生成一份带示例的全局文件，照着改即可。
- 改完在游戏里执行 `/sciencepacks reload`（或重启服务器）生效。
- 加载顺序：数据包 → 全局文件 → 单世界文件 → 脚本 API（后加载的覆盖前面的）。

## 白名单与多配方（v1.11）

默认一条 `unlocks` 项会同时锁「配方 id」和「产物物品的所有配方」。想更精细时用前缀：

| 写法 | 含义 |
| --- | --- |
| `minecraft:iron_pickaxe` | 旧行为：锁该物品的所有配方，并锁 id 等于它的配方 |
| `item:minecraft:iron_pickaxe` | 只锁「产物是该物品」的配方 |
| `recipe:minecraft:iron_pickaxe` | 只锁 id 为 `minecraft:iron_pickaxe` 的那一个配方 |

多配方：同一种物品有常规配方与替代配方时，把两个配方分别用 `recipe:` 写进两项不同研究的
`unlocks`，就能做到「A 研究解锁常规配方、B 研究解锁替代配方」。

白名单 `whitelist` 是反向放行：列表里的配方在**任何研究没解锁时也能合成**。写法与 `unlocks` 相同，
不加前缀表示配方 id 与产物物品都放行：

```json
{
  "id": "sciencepacks:iron_age",
  "prerequisites": ["sciencepacks:red_science"],
  "unlocks": ["item:minecraft:iron_pickaxe"],
  "whitelist": ["item:minecraft:iron_pickaxe"]
}
```

上例中 `iron_age` 解锁前，玩家仍能用任意配方合成铁镐（方便整合包给替代配方留后路）。

## 隐藏研究（v1.11）

`hidden: true` 的研究默认不出现在研究列表和研究树里，满足 `showWhen`（写法与 `triggers` 相同，
空格子表示「前置解锁后就显示」）后才出现；服务端也会拒绝直接开始未显示的隐藏研究：

```json
{
  "id": "mypack:secret_reactor",
  "name": "秘密反应堆",
  "hidden": true,
  "showWhen": ["minecraft:diamond*1", "kill:minecraft:wither*1"],
  "prerequisites": ["sciencepacks:purple_science"],
  "rounds": 10,
  "cost": [{ "item": "sciencepacks:pack_purple", "count": 4 }]
}
```

被隐藏研究锁定的配方仍然锁定——玩家得先满足显示条件、再研究它。

## 总览模式与研究台自动化（v1.11）

- 研究树标题栏右上角有「总览 / 关联」按钮：总览显示整棵树，关联只显示当前研究前后各两级
  （悬停按钮有说明，选择会记住）。
- 研究台的每个槽位都允许漏斗/管道等自动化方块**抽出**材料（`canTakeItemThroughFace` 返回 true），
  因此可以把研究台接进物流网络，让材料自动回流。

## 可视化编辑器（tools/tech-editor.html）

不想手改 JSON 的话，用浏览器打开 `tools/tech-editor.html`（Chrome / Edge）：

1. 点「打开 technologies 文件夹」，选 `data/sciencepacks/sciencepacks/technologies`。
2. 左侧画布拖拽节点改位置，右侧填名称/图标/前置/触发/材料/解锁配方。
3. 点「自动布局（递进式）」按前置关系重排成一层一层的阶梯。
4. 「保存全部」直接写回文件（需要文件系统访问权限），或「导出全部」再手动放回。
5. 回到游戏执行 `/sciencepacks reload`。

编辑器会保留 JSON 里未在界面出现的字段，只改它认识的键。

**编辑操作**：

| 操作 | 方式 |
| --- | --- |
| 新建研究 | 顶部「新建研究」按钮、右键菜单，或按 `N`（通过文件夹打开时会立即写入新 JSON） |
| 复制研究 | 右键菜单「复制选中」 |
| 删除研究 | 顶部「删除选中」、右键菜单，或按 `Delete`（会删文件并从其它研究前置里移除） |
| 撤销 / 重做 | 顶部按钮或 `Ctrl+Z` / `Ctrl+Y`（含 `Ctrl+Shift+Z`） |
| 右键菜单 | 在画布上右键：新建 / 复制 / 删除 / 自动布局 / 撤销 / 重做 / 导入物品清单 |
| 导入物品清单 | 顶部「导入物品清单」，选 `config/sciencepacks-items.json` |
| 导入配方清单 | 顶部「导入配方清单」，选 `/sciencepacks dumprecipes` 导出的 JSON |
| 选择解锁配方 | 顶部「导入配方…」打开配方选择器：搜索 id、点选（可多选）、双击直接加入 |

撤销/重做只改内存里的数据：新建或删除文件后如果反悔，撤销不会自动还原磁盘，需要再「保存全部」。

**自动补全**：所有物品 id / 配方 id 输入都支持补全。

- 输入框（图标、研究材料）：直接输入片段，浏览器下拉列表会列出匹配的 id（输入 `iron` 提示 `minecraft:iron_ingot`）。
- 多行文本框（触发条件、解锁配方、白名单、多方块结构、数值效果）：边打字边弹候选列表，
  `↑`/`↓` 选择、`Enter`/`Tab` 填入、`Esc` 关闭；会识别 `recipe:` / `item:` / `block_break:` / `dimension:` 等前缀，
  结构行的 `|` 之后自动只提示物品 id。
- 前置科技有独立的搜索框，输入关键字实时过滤 chips，已选中的排在最前。

**研究材料**：面板底部「研究消耗」把研究时间、轮次和每轮材料放在一起；
六个科技包各有一个快捷按钮（铁质/物流/红石/末影/军备/效能），点一次加一种，
「+ 自定义」可以填任意物品 id（整合包里的模组物品也行）。

**连线**：连线只在有前置关系的两个研究之间绘制；如果直线路径会压到无关的研究节点，
编辑器会自动换一条不穿过节点的路线。

**物品库**：编辑器内嵌了 1.20.1 的物品 id 清单（从游戏 registry 导出），
图标、材料等输入框可以直接输入 id 并自动补全（输入 `iron` 会提示 `minecraft:iron_ingot`）。
要编辑整合包里的其他模组物品，先在游戏里执行：

```
/sciencepacks dumpitems
```

它会把**当前所有已注册物品**（原版 + 所有模组）写成一个 JSON 数组，
然后在编辑器里点「导入物品清单」选择该文件即可。导入的物品 id 会记在浏览器本地，
下次打开编辑器仍然可用；也支持 `{"items":[...]}` 格式。

配方同理，执行：

```
/sciencepacks dumprecipes
```

会导出 `config/sciencepacks-recipes.json`（内容为 `{ "配方id": "所属科技id" }`），
在编辑器里「导入配方清单」后，解锁配方输入框与配方选择器都能补全模组配方。

## 用 Gradle 构建（可选）

已安装 Gradle 8.8：`E:\DeepSeek文件\3-辅助工具\gradle\gradle-8.8`；
工程在 `projects/sciencepacks-gradle`，自带 wrapper：

```
cd projects\sciencepacks-gradle
gradlew.bat build
```

首次运行会从腾讯镜像下载 Gradle 发行版。任务链：`textures -> compileMod -> compileMixins -> packageMod`，
产物 `build/sciencepacks-1.0.0.jar`。

注意：该工程**不使用 ForgeGradle**（`maven.minecraftforge.net` 在本机不可达），
而是把原有的「javac + 重映射」流程包装成 Gradle 任务；因此 `runClient` / `runData` 暂不可用。
需要这两项时，开启可访问 GitHub/Forge 的代理后，可切换到标准 ForgeGradle 工程。
## 构建

```
powershell -NoProfile -ExecutionPolicy Bypass -File projects\sciencepacks-mod\build.ps1
```

无 Gradle：javac 对 Mojang 官方名编译 → 自建 tiny 映射一步重映射到 SRG；Mixin 单独对 SRG 版 jar 编译；
构建脚本会给编译用客户端 jar 打访问补丁（`BlockEntitySupplier` 等 vanilla 包私有成员）。

## 已知限制

- 熔炉归属按「放置者」记录；无归属的旧炉子不拦截。
- 研究台的槽位数量在放置时读取配置；改配置后需要重新放置机器。
- 研究台界面在客户端通过反射注册（vanilla 的 `MenuScreens.register` 是包私有）。

---

## 1.21.1 迁移（v2.0）

本项目已从 **Forge 47.4.28 / MC 1.20.1** 迁移到 **Forge 52.0.1 / MC 1.21.1**（Java 21），代码位于 `projects/sciencepacks-1.21`。

### 构建

本机直连 `maven.minecraftforge.net` 会超时，构建走 BMCLAPI 镜像并在 `settings.gradle` / `build.gradle` 里声明；另外 ForgeGradle 6 在插件 apply 阶段会做「检查服务器连接」的自检（会挂死），必须关闭：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME = '<工作区>\.gradle-home'
gradle.bat build --console=plain --no-daemon --offline `
    -Dnet.minecraftforge.gradle.check.certs=false
```

产物：`build/libs/sciencepacks-1.0.0-1.21.jar`。

### API 迁移要点

| 1.20.1 写法 | 1.21.1 写法 |
| --- | --- |
| `new ResourceLocation(ns, path)` | `ResourceLocation.fromNamespaceAndPath(ns, path)` |
| `SimpleChannel` 手写 `registerMessage` | `ChannelBuilder.named(...).networkProtocolVersion(1).acceptedVersions(Channel.VersionTest.exact(1)).simpleChannel()` |
| `NetworkEvent.Context` | `net.minecraftforge.event.network.CustomPayloadEvent.Context` |
| `SavedData.save(CompoundTag)` | `save(CompoundTag, HolderLookup.Provider)`，配合 `SavedData.Factory` + `computeIfAbsent(factory, name)` |
| `ItemStack.setHoverName` | `stack.set(DataComponents.CUSTOM_NAME, ...)` |
| `isSameItemSameTags` | `isSameItemSameComponents` |
| `Operation.ADDITION` | `Operation.ADD_VALUE` |
| `BlockBehaviour.Properties.copy(...)` | `Properties.ofFullCopy(...)` |
| `Block#use` | `protected InteractionResult useWithoutItem(...)` + `codec()`（`simpleCodec(...)`） |
| `ContainerHelper.saveAllItems(tag, items)` | `saveAllItems(tag, items, registries)`；BlockEntity 的 save/load/getUpdateTag 都带 `HolderLookup.Provider` |
| `ForgeMod.REACH_DISTANCE` | `Attributes.BLOCK_INTERACTION_RANGE` / `ENTITY_INTERACTION_RANGE` |
| `Toast.TEXTURE` 自绘提示 | `SystemToast.add(..., SystemToast.SystemToastId.PERIODIC_NOTIFICATION, ...)` |
| `RecipeManager` 返回 `Recipe<?>` | 一律 `RecipeHolder<?>`（`holder.id()` / `holder.value()`） |

Mixin 目标改为官方方法名，`sciencepacks.mixins.json` 的 `compatibilityLevel` 为 `JAVA_21`。

## 研究树（v3.2，40 项）

研究树 JSON 位于 `src/main/resources/data/sciencepacks/sciencepacks/technologies/`，现在以 `tools/tech-editor.html` 编辑器维护（手改 JSON 也行）。校验用 `tools/validate-tree.js`：物品 id 对照 `run/config/sciencepacks-items.json`、前置存在、解锁不重复、坐标不冲突、模组物品跳过离线核对；`tools/md-tree.js` 可重新导出下表。

| id | 名称 | 前置 | 轮次 | 每轮消耗 |
| --- | --- | --- | --- | --- |
| stone_age | 石器时代 | 触发：cobblestone*16 | — | — |
| farming | 耕作 | stone_age、触发：wheat_seeds*16 | 1 | — |
| red_science | 红色科技包 | stone_age、触发：iron_ingot*4 | — | — |
| new_tech | 皮革护甲 | stone_age、触发：leather*16 | — | — |
| cooking | 烹饪 | farming、red_science | 12 | 红 ×1 |
| honey | 养蜂 | red_science、farming | 12 | 红 ×1 |
| iron_tools | 铁质工具 | red_science | 12 | 红 ×1 |
| librarian | 书与书架 | red_science | 14 | 红 ×2 |
| masonry | 石作 | red_science | 10 | 红 ×1 |
| gray_science | 灰色科技包 | red_science | 20 | 红 ×1 |
| glassworks | 玻璃工坊 | masonry | 12 | 红 ×1 |
| military_1 | 军工学 I | gray_science、new_tech | 20 | 红 ×1、灰 ×1 |
| smithy | 铁匠铺 | iron_tools | 20 | 红 ×2、绿 ×1 |
| nether | 下界通行 | iron_tools | 24 | 红 ×1 |
| redstone_basics | 基础红石电路 | iron_tools | 20 | 红 ×2 |
| diamond_tools | 钻石工具 | iron_tools | 32 | 红 ×1、绿 ×1 |
| archery | 军工学 II | military_1 | 20 | 红 ×2、灰 ×2 |
| netherite_gear | 下界合金 | diamond_tools、nether | 30 | 绿 ×3、蓝 ×2、紫 ×1 |
| gold_craft | 金工技艺 | nether、触发：gold_ingot*4 | 16 | 红 ×2 |
| potions | 酿造工艺 | nether | 18 | 红 ×2、绿 ×1 |
| green_science | 绿色科技包 | nether、redstone_basics | 30 | 红 ×1 |
| explosives | 爆破工程 | archery | 20 | 红 ×3、绿 ×1 |
| logistics_1 | 基础物流 | green_science | 16 | 红 ×2 |
| new_tech_2 | 矿车与铁轨 | green_science | 60 | 红 ×1、绿 ×1 |
| new_tech_3 | 军工学 III | archery、diamond_tools、green_science | 40 | 红 ×2、绿 ×2、灰 ×2 |
| cartography | 制图与计时 | gold_craft、librarian | 16 | 红 ×1、绿 ×1 |
| logistics_2 | 轨道物流 | logistics_1、new_tech_2 | 20 | 红 ×3、绿 ×1 |
| enchanting | 附魔学 | librarian、new_tech_3 | 24 | 绿 ×2、蓝 ×1 |
| blue_science | 蓝色科技包 | green_science | 20 | 红 ×3、绿 ×2 |
| new_tech_6 | 高级铁轨系统 | blue_science、new_tech_2、gold_craft | 30 | 红 ×3、绿 ×3、蓝 ×3 |
| new_tech_5 | 终极奉献 | blue_science、smithy | 120 | 红 ×1、绿 ×1、蓝 ×1 |
| redstone_advanced | 高级电路 | blue_science | 20 | 红 ×3、绿 ×1 |
| automation | 红石自动化 | blue_science | 30 | 红 ×2、绿 ×2、蓝 ×2 |
| purple_science | 紫色科技包 | blue_science | 20 | 绿 ×5、蓝 ×3 |
| new_tech_4 | 军工学 IV | new_tech_3、new_tech_5 | 100 | 红 ×2、绿 ×2、蓝 ×2、灰 ×2 |
| the_end | 末地之门 | purple_science | 32 | 红 ×1、绿 ×1、蓝 ×1、紫 ×1 |
| endgame | 终局科技 | new_tech_4、the_end | 40 | 红 ×4、绿 ×4、蓝 ×4、紫 ×4、灰 ×4 |
| yellow_science | 黄色科技包 | endgame | 100 | 红 ×5、绿 ×5、蓝 ×5、紫 ×5 |
| reach_upgrade | 终极研究 II：触及距离 | yellow_science | 5（无限） | 红 ×10、绿 ×8、蓝 ×8、紫 ×5、灰 ×8、黄 ×8 |
| mining_speed | 终极研究 I：挖掘效率 | yellow_science | 5（无限） | 红 ×8、绿 ×6、蓝 ×6、紫 ×4、灰 ×6、黄 ×6 |

分类（界面左侧列表过滤）：material / science / military / equipment / redstone / logistics / building / magic / dimension / endgame。
装饰性方块（羊毛、地毯、旗帜、陶瓦、蜡烛等）不收录；开局必需项（工作台、木板、木棍、木镐、熔炉、火把）要么默认解锁，要么挂在触发式科技上，避免死锁。

## v2.1 界面与研究树调整

- 研究树节点放大到 64×64，横向间距 72、纵向间距 96（原来的 56/56/28/40 太挤），缩放后仍按比例布局。
- 连线重写：同一列直接竖直连；跨列时在两条竖线之间按端点错开「车道」，多条前置不再完全重叠。
- 研究台手持非方块物品右键也会打开界面（`useItemOn` 转调 `useWithoutItem`，方块物品仍可正常放置）。
- 科技包配方补齐并改为「越后期越复杂」，全部锁在对应科技包研究上。

服务端冒烟：`Loaded 36 technologies (189 locked recipes)`、`Done (4.540s)`。

## v2.2 修复（研究台遮罩 / 红色科技包死锁）

- **研究台界面空白**：1.21.1 的 `renderBg` 不再由 `AbstractContainerScreen.render` 直接调用，而是走 `renderBackground → renderBg`。
  之前把 `renderBackground` 覆盖成空方法（1.20 写法），导致面板/槽位/文字全都不绘制。现在 `renderBackground` 只转调 `renderBg`。
- **遮罩错位**：原版 `renderTransparentBackground` 会在缩放后的 pose 里执行，满屏模糊遮罩被一起缩放，只盖住一部分。
  现在它不再进入缩放 pose，全屏暗化由 `render()` 在 pushPose 之前用 `fill(0,0,width,height,0x90000000)` 统一完成。
- **红色科技包死锁**：红石矿需要铁镐 → 铁镐由「铁工具」解锁 → 铁工具要红色科技包 → 红色科技包要红石。
  现在红色科技包配方改为 **玻璃瓶 + 红色染料 + 糖**，`red_science` 触发也改为 `red_dye*2`（花就能做染料），
  这样「石器时代 → 红色科技包 → 铁工具 → 铁镐 → 红石」链路才成立。

### 客户端

PCL2 已有 `1.21.1-Forge_52.1.20` 实例，jar 已放入
`D:\PCL2\.minecraft\versions\1.21.1-Forge_52.1.20\mods\sciencepacks-1.0.0-1.21.jar`。
**替换 jar 前必须完全退出游戏**（否则资源包/数据包读不到，表现为紫黑块与原始键名）。

## v3.0 NeoForge 1.21.1 移植

本版本从 Forge 1.21.1 完整移植到 **NeoForge 21.1.80**，玩法/研究树/数据包内容与 v2.2 完全一致（36 项科技、189 个锁定配方、6 种科技包）。

### 构建环境

- JDK 21、Gradle 8.8，插件 **ModDevGradle `net.neoforged.moddev` 2.0.78**，`neo_version=21.1.80`。
- `maven.neoforged.net` 在本机被 DNS 污染无法直连，构建依赖走官方 CDN 镜像 **`https://neoforged.forgecdn.net/releases`**（已写入 `build.gradle` 的 repositories）。
- `net.neoforged:minecraft-dependencies:1.21.1` 在该 CDN 上不存在，工程改用本地 maven 仓库里的手工 POM：
  `C:\Users\reape\.m2\repository\net\neoforged\minecraft-dependencies\1.21.1\minecraft-dependencies-1.21.1.pom`
  （依赖清单从 NeoFormRuntime 缓存的 `minecraft_1.21.1_version_manifest.json` 生成，46 个主库；`build.gradle` 里已加 `mavenLocal()`）。
- 构建命令（Windows）：

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-21
set GRADLE_USER_HOME=E:\deepseek-harness\default-workspace\.gradle-home
gradle build --console=plain --no-daemon -Dnet.minecraftforge.gradle.check.certs=false
```

产物：`build/libs/sciencepacks-1.0.0-1.21.neo.jar`（229026 字节，SHA256 `451A29CE84E202C80C528C274E781ADBEBB6628D5EE4F46684C2339D75AEF32F`）。
注意 `-Dnet.minecraftforge.gradle.check.certs=false` 仍要带（ModDevGradle 复用同一套环境检查逻辑，缺它会在启动时连外网卡住）。



## 模组联动（v3.1）

不存在，但是我让DS写了一些自以为是的功能

### 1. 按模组启用研究：`requiredMods`

任何 `data/sciencepacks/sciencepacks/technologies/*.json` 都可以加一个字段：

```json
{
  "requiredMods": ["create"]
}
```

- 列出的模组全部存在时，这项研究才加载；否则整项隐藏。
- 写法 `!create` 表示「没有 create 时才加载」，可与普通条件混用；多个条件是「与」关系。
- 依赖它的后续研究若前置缺失，会自动一起隐藏（不会留下断链）。
- 启动日志会打印被隐藏的数量与 id：
  `Loaded 39 technologies (210 locked recipes, 3 disabled by mod conditions: [create_kinetics, create_processing, create_logistics])`

### 2. 互斥研究：`excludes`

同一个功能想用两套研究树时，让其中一项互斥掉另一项：

```json
{
  "excludes": ["sciencepacks:logistics_2"]
}
```

- 只要被列出的研究还在树里，本条就自动隐藏并不可研究；反过来也成立，所以两套树只在环境不同时各自出现。
- 当前示例：`create_logistics`（机械物流，需要 Create）互斥隐藏原版 `logistics_2`（自动物流）。

### 3. 按模组替换配方：`recipe_overrides`

新增数据目录 `data/sciencepacks/sciencepacks/recipe_overrides/`，一个文件描述一组覆盖：

```json
{
  "mods": ["create"],
  "remove": ["sciencepacks:pack_green"],
  "recipes": [
    {
      "id": "sciencepacks:pack_green",
      "recipe": { "...": "1.21 配方 JSON" }
    }
  ]
}
```

- `mods` 为空或不填则总是生效；否则要求列出的模组全部存在。
- `remove` 里的配方会被移除，`recipes` 里的同 id 配方随后写入。
- 文件自带 `version` 时，服务器只在版本变化时重新应用，避免重复替换。

### 4. 配方树编辑器：模组提示与物品图标

`tools/tech-editor.html`（用浏览器打开）新增：

- 表单里的「所需模组」「互斥研究」两项，直接写进 JSON 的 `requiredMods` / `excludes`。
- 顶部横幅汇总整棵树需要的模组（如「需要模组：create × 3」），并对「用了模组物品却没声明所需模组」的条目给出未声明警告。
- 画布节点在声明了 `requiredMods` 时，上方显示琥珀色标签「需要 create」。
- 节点与表单里的物品支持真实图标。图标来自 `tools/icons/<命名空间>/<路径>.png`，由脚本从本地 jar 导出：

```powershell
[scriptblock]::Create((Get-Content -Raw .\tools\gen-icons.ps1)) | Invoke-Expression
```

参数：`-Root`（工程根）、`-ClientJar`（默认 `D:\PCL2\.minecraft\versions\1.21.1\1.21.1.jar`）、`-ModsDir`（默认 `1.21.1-NeoForge_21.1.265\mods`）。
当前导出结果：索引 6 个 jar、4422 张纹理、1369 个物品 id，写入 **862** 个图标；其余（方块物品、动态生成图标）编辑器回退为文字标签。




## v3.4 关联模式的分层布局

点研究树右上角「关联」后，只显示所选研究附近的节点。以前这些节点沿用编辑器里的原始坐标，常常竖着排成一条线；现在关联模式会**临时重新排版**（不改 JSON）：

- 按「到最上游的距离」分层：上游前置在上、后续研究在下，**同一层级横向并排**。
- 排序参考父节点的平均列位置，子节点尽量落在父节点正下方；每一层还会整体平移，让本层中心对齐父节点中心，减少连线交叉。
- 范围内某个后续研究如果还有别的前置没出现在范围内，会**补入它的直接前置（一层）**，这样多前置的研究能看到它挂在哪些前置下面。
- 临时布局只用于绘制与点选；切回「总览」后仍使用编辑器里的 x/y 坐标，平移/缩放行为不变。

## v3.3 根研究（root）与更细的连线

### 根研究：`"root": true`

任何一项研究都可以声明自己是「根研究」：

```json
{
  "id": "new_tech_2",
  "root": true,
  "prerequisites": [],
  "triggers": ["minecraft:rail*16"]
}
```

- 根研究**忽略 `prerequisites`**（`Technology.effectivePrerequisites()` 恒为空），只要它的材料/触发条件满足就能开始研究，不需要先完成其它研究。
- 研究树里不会画入线；编辑器里节点左上角显示绿色「根」标记、边框变绿。
- `prerequisites` 字段可以留着当文档/布局参考，但不会生效——校验脚本会给一条提示。
- 编辑器：表单里勾选「根研究」即可，勾选时会自动清空当前前置（Ctrl+Z 可撤销）。

### 连线更细、跟随缩放

- 研究树连线宽度改为 `max(1, round(treeZoom))`：标准缩放下是 **1px** 细线，放大后才逐渐变粗，缩小到底也保留 1px，保证能看清连接关系。
- 颜色调暗为 `0xFF4C4C58`，1px 时不会糊成一片。

## v3.2 研究树同步（40 项）

这批研究树以 `E:\DeepSeek文件\我的世界Mod\02-模组项目\sciencepacks-1.21-neo\src\main\resources\data\sciencepacks\sciencepacks\technologies`（用编辑器直接改的那份）为准，同步回三处工程副本：

- 工作区 NeoForge 工程 `projects/sciencepacks-1.21-neo`、Forge 工程 `projects/sciencepacks-1.21`、以及 E 盘镜像的 Forge 工程，technology JSON 现在完全一致（40 个文件）。
- 新增 6 项：`new_tech` 皮革护甲、`new_tech_2` 矿车与铁轨、`new_tech_3` 军工学 III、`new_tech_4` 军工学 IV、`new_tech_5` 终极奉献、`new_tech_6` 高级铁轨系统。这些 id 由文件名决定，之后想改成 `leather_armor` 这类语义 id 的话，记得同步改掉引用它们的 `prerequisites`。
- 移除了 `copper_engineering`、`diamond_armor` 与三项 Create 联动研究（`create_kinetics` / `create_processing` / `create_logistics`）；模组联动能力本身仍然保留，需要时把 `requiredMods` 加回去即可。
- `logistics_2` 仍保留 `excludes: ["sciencepacks:create_logistics"]`。目标不存在时这条不生效，纯备忘；校验脚本会给一条 warning 而不是报错。
- 校验结果：`files: 40`、`OK: no blocking errors (ids/preqs/coords/duplicates)`，仅 1 条上述 warning。服务端加载日志：`Loaded 40 technologies (164 locked recipes)`、`Done (0.646s)`。

新增工具 `tools/validate-tree.js`（只读校验，不会覆盖 JSON）：

```powershell
node tools\validate-tree.js
```

会检查：JSON 语法、x/y 坐标冲突、前置是否存在、解锁物是否在 1.21.1 物品表（模组命名空间跳过）、解锁是否重复、研究材料/触发物品合法性、互斥目标是否存在，并从根节点做一次可达性扫描（互锁的研究会被标 warning）。

## 关于这个项目

- 作者 / 仓库所有者：**Black8Ball**。
- 这个模组是作者闲来无事，让 **DeepSeek（AI 助手）** 从零写出来的：需求、方向、验收、贴图与美术取舍由作者拍板；Java 代码、研究树 JSON、生成脚本（`tools/gen-tree.js` / `gen-recipes.js`）、可视化编辑器（`tools/tech-editor.html`）与文档由 AI 产出，作者试用后反馈问题、AI 迭代修复。
- 起因很简单：作者想要一个「Factorio 风格的科技树 + 科技包解锁」模组，于是把它们交给 AI 跑了一遍。整个过程中没有手写代码。
以下是我人手扣键盘写的:我没朋友，有些东西没有经过多人测试，我不知道有没有人愿意下这个，但还是希望有人能多人测试一下，再次重申:这个模组完全是我闲来无事浪费钱跑的！我希望有一个真正的factorio风格的科技瓶模组并有人做一个整合包，所以我搁这许愿呢。

## 开源协议

本项目以 **MIT License** 开源，详见 [LICENSE](LICENSE)。可自由使用、修改、分发，保留版权声明即可。

## 从源码构建（NeoForge 1.21.1）

`powershell
# 需要 JDK 21
.\gradlew.bat build
# 产物：build\libs\sciencepacks-1.0.0-1.21.neo.jar
`

研究树数据在 `src/main/resources/data/sciencepacks/sciencepacks/technologies/`，改动后可用 `node tools/validate-tree.js` 校验；
可视化编辑用浏览器打开 `tools/tech-editor.html`。
