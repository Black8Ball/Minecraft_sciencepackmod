package com.sciencepacks.research;

import com.sciencepacks.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 多方块结构锁：科技 JSON 里写 "structures": ["nether_portal"]，
 * 在这项研究解锁之前，对应的搭建 / 点燃 / 进入动作会被拦截。
 *
 * <p>为了兼容其它模组，除了内置类型还支持通用写法（{@code 核心id|图标物品|显示名称}）：</p>
 * <ul>
 *     <li>{@code nether_portal / end_portal / wither / iron_golem / snow_golem} —— 内置拦截点</li>
 *     <li>{@code dimension:othermod:some_dim} —— 未解锁前无法通过任何方式进入该维度</li>
 *     <li>{@code entity:othermod:boss} —— 未解锁前该生物无法生成 / 进入世界</li>
 *     <li>{@code item_on_block:othermod:igniter=othermod:portal_block} —— 未解锁前不能用该物品右键该方块</li>
 * </ul>
 *
 * <p>每个条目还可以在后面用 {@code |} 追加图标物品与显示名称，例如：<br>
 * {@code "dimension:othermod:some_dim|othermod:portal_igniter|允许进入某维度"}。</p>
 */
public final class Structures {

    /** 下界传送门：打火石/火焰弹点燃黑曜石框 + 穿过传送门去下界。 */
    public static final String NETHER_PORTAL = "nether_portal";
    /** 末地传送门：往末地传送门框里插末影之眼 + 进入末地。 */
    public static final String END_PORTAL = "end_portal";
    /** 凋灵：灵魂沙 + 凋灵头颅的搭建。 */
    public static final String WITHER = "wither";
    /** 铁傀儡：铁块 + 南瓜的搭建。 */
    public static final String IRON_GOLEM = "iron_golem";
    /** 雪傀儡：雪块 + 南瓜的搭建。 */
    public static final String SNOW_GOLEM = "snow_golem";
    /** 维度锁前缀：未解锁前无法进入该维度。 */
    public static final String PREFIX_DIMENSION = "dimension:";
    /** 生物锁前缀：未解锁前该生物无法生成 / 进入世界。 */
    public static final String PREFIX_ENTITY = "entity:";
    /** 交互锁前缀：未解锁前不能用某物品右键某方块。 */
    public static final String PREFIX_ITEM_ON_BLOCK = "item_on_block:";

    private Structures() {
    }

    /* ---------------- 解析 ---------------- */

    /** 条目里真正参与匹配的部分（去掉 |图标|名称 后缀）。 */
    public static String rawKey(String entry) {
        if (entry == null) {
            return "";
        }
        int cut = entry.indexOf('|');
        return (cut < 0 ? entry : entry.substring(0, cut)).trim();
    }

    /** 条目自定义的图标物品 id（没有时返回 null）。 */
    public static ResourceLocation customIcon(String entry) {
        String[] parts = split(entry);
        if (parts.length < 2 || parts[1].isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(parts[1]);
    }

    /** 条目自定义的显示名称（没有时返回 null）。 */
    public static String customName(String entry) {
        String[] parts = split(entry);
        if (parts.length < 3 || parts[2].isEmpty()) {
            return null;
        }
        return parts[2];
    }

    /**
     * 条目自定义的右下角小角标物品 id（没有时返回 null）。
     *
     * <p>写法：{@code 核心id|大图标物品|显示名称|小角标物品}。
     * 大图标 + 小角标的组合可以让「末地门 / 地狱门」这类结构看起来更直观。</p>
     */
    public static ResourceLocation customBadge(String entry) {
        String[] parts = split(entry);
        if (parts.length < 4 || parts[3].isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(parts[3]);
    }

    private static String[] split(String entry) {
        return entry == null ? new String[0] : entry.split("\\|", -1);
    }

    public static boolean isDimension(String key) {
        return key.startsWith(PREFIX_DIMENSION);
    }

    public static boolean isEntity(String key) {
        return key.startsWith(PREFIX_ENTITY);
    }

    public static boolean isItemOnBlock(String key) {
        return key.startsWith(PREFIX_ITEM_ON_BLOCK);
    }

    /** dimension:ns:id -> ns:id */
    public static String dimensionId(String key) {
        return key.substring(PREFIX_DIMENSION.length());
    }

    /** entity:ns:id -> ns:id */
    public static String entityId(String key) {
        return key.substring(PREFIX_ENTITY.length());
    }

    /** item_on_block:item=block -> [item, block] */
    public static String[] itemOnBlock(String key) {
        String body = key.substring(PREFIX_ITEM_ON_BLOCK.length());
        int eq = body.indexOf('=');
        if (eq < 0) {
            return new String[]{body, ""};
        }
        return new String[]{body.substring(0, eq), body.substring(eq + 1)};
    }

    /* ---------------- 查询 ---------------- */

    /** 该结构由哪项研究解锁；没有科技声明时返回 null（=不拦截）。 */
    public static ResourceLocation techOf(String structure) {
        return TechLibrary.ownerOfStructure(rawKey(structure));
    }

    /** 该结构对应的研究定义。 */
    public static Technology tech(String structure) {
        ResourceLocation id = techOf(structure);
        return id == null ? null : TechLibrary.get(id);
    }

    /** 玩家（按研究组进度）是否还没解锁这个结构。 */
    public static boolean locked(PlayerResearch research, String structure) {
        if (!Config.COMMON.lockStructures.get()) {
            return false;
        }
        ResourceLocation tech = techOf(structure);
        // 无限研究升过一级就算完成，配方/结构都放行
        return tech != null && !Research.completedFor(research, tech);
    }

    public static boolean locked(ServerPlayer player, String structure) {
        return player != null && locked(Research.of(player), structure);
    }

    /** 任意一个条目解锁即可放行：传 null 表示该条目不存在。 */
    public static boolean anyLocked(ServerPlayer player, String... structures) {
        for (String structure : structures) {
            if (structure != null && locked(player, structure)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出「用这个物品右键这个方块」对应的配置条目（支持其它模组的点燃器 / 传送门框架）。
     *
     * @return 匹配到的条目原文（可能带 |图标|名称 后缀），没有匹配时返回 null
     */
    public static String matchingItemOnBlock(net.minecraft.world.item.ItemStack held,
                                             net.minecraft.world.level.block.state.BlockState clicked) {
        if (held == null || held.isEmpty() || clicked == null) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(clicked.getBlock());
        if (itemId == null || blockId == null) {
            return null;
        }
        String itemKey = itemId.toString();
        String blockKey = blockId.toString();
        for (Technology tech : TechLibrary.ordered()) {
            for (String entry : tech.structures()) {
                String key = rawKey(entry);
                if (!isItemOnBlock(key)) {
                    continue;
                }
                String[] pair = itemOnBlock(key);
                if (itemKey.equals(pair[0]) && blockKey.equals(pair[1])) {
                    return entry;
                }
            }
        }
        return null;
    }

    /* ---------------- 显示 ---------------- */

    /** 结构名：优先用条目里自定义的名称，否则查语言文件。 */
    public static Component name(String structure) {
        String custom = customName(structure);
        if (custom != null) {
            return Component.literal(custom);
        }
        String key = rawKey(structure);
        if (isDimension(key)) {
            ResourceLocation dim = ResourceLocation.tryParse(dimensionId(key));
            if (dim != null) {
                return Component.translatable("structure.sciencepacks.dimension." + dim.getNamespace() + "." + dim.getPath());
            }
        }
        if (isEntity(key)) {
            ResourceLocation entity = ResourceLocation.tryParse(entityId(key));
            if (entity != null) {
                return Component.translatable("entity." + entity.getNamespace() + "." + entity.getPath());
            }
        }
        return Component.translatable("structure.sciencepacks." + key);
    }

    /** 效果栏里显示的图标物品。 */
    public static ItemStack icon(String structure) {
        ResourceLocation custom = customIcon(structure);
        if (custom != null) {
            Item item = BuiltInRegistries.ITEM.get(custom);
            if (item != null) {
                return new ItemStack(item);
            }
        }
        String key = rawKey(structure);
        Item item = switch (key) {
            case NETHER_PORTAL -> Items.OBSIDIAN;
            case END_PORTAL -> Items.END_PORTAL_FRAME;
            case WITHER -> Items.WITHER_SKELETON_SKULL;
            case IRON_GOLEM, SNOW_GOLEM -> Items.CARVED_PUMPKIN;
            default -> {
                if (isDimension(key)) {
                    yield Items.OBSIDIAN;
                }
                if (isEntity(key)) {
                    yield Items.WITHER_SKELETON_SKULL;
                }
                if (isItemOnBlock(key)) {
                    yield Items.FLINT_AND_STEEL;
                }
                yield Items.BARRIER;
            }
        };
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name(structure));
        return stack;
    }

    /**
     * 效果栏右下角的小角标：优先用条目第 4 段自定义物品，
     * 否则按结构类型给一个默认角标（如地狱门 → 打火石、末地门 → 末影之眼）。
     */
    public static ItemStack badge(String structure) {
        ResourceLocation custom = customBadge(structure);
        if (custom != null) {
            Item item = BuiltInRegistries.ITEM.get(custom);
            if (item != null) {
                return new ItemStack(item);
            }
        }
        String key = rawKey(structure);
        Item item = switch (key) {
            case NETHER_PORTAL -> Items.FLINT_AND_STEEL;
            case END_PORTAL -> Items.ENDER_EYE;
            case WITHER -> Items.SOUL_SAND;
            case IRON_GOLEM -> Items.IRON_BLOCK;
            case SNOW_GOLEM -> Items.SNOW_BLOCK;
            default -> {
                if (isDimension(key)) {
                    yield Items.ENDER_EYE;
                }
                if (isEntity(key)) {
                    yield Items.ROTTEN_FLESH;
                }
                if (isItemOnBlock(key)) {
                    yield Items.FLINT_AND_STEEL;
                }
                yield Items.AIR;
            }
        };
        return item == Items.AIR ? null : new ItemStack(item);
    }

    /** 效果栏悬停时显示的说明行，例如「允许进入下界」。 */
    public static Component effectLine(String structure) {
        String key = rawKey(structure);
        String tail = switch (key) {
            case NETHER_PORTAL -> NETHER_PORTAL;
            case END_PORTAL -> END_PORTAL;
            case WITHER -> WITHER;
            case IRON_GOLEM -> IRON_GOLEM;
            case SNOW_GOLEM -> SNOW_GOLEM;
            default -> null;
        };
        if (tail != null) {
            return Component.translatable("structure.sciencepacks." + tail + ".effect")
                    .withStyle(ChatFormatting.GREEN);
        }
        if (isDimension(key)) {
            return Component.translatable("structure.effect.dimension", name(structure))
                    .withStyle(ChatFormatting.GREEN);
        }
        if (isEntity(key)) {
            return Component.translatable("structure.effect.entity", name(structure))
                    .withStyle(ChatFormatting.GREEN);
        }
        if (isItemOnBlock(key)) {
            String[] pair = itemOnBlock(key);
            ResourceLocation blockId = ResourceLocation.tryParse(pair[1]);
            net.minecraft.world.level.block.Block block = blockId == null ? null
                    : BuiltInRegistries.BLOCK.get(blockId);
            Component blockName = block == null ? Component.literal(pair[1])
                    : block.getName();
            return Component.translatable("structure.effect.item_on_block", blockName)
                    .withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("structure.effect.custom", name(structure))
                .withStyle(ChatFormatting.GREEN);
    }

    /** 给玩家发「无法构建」的提示（快捷栏上方，不刷屏）。 */
    public static void notifyLocked(ServerPlayer player, String structure) {
        Technology tech = tech(structure);
        Component techName = tech == null ? Component.empty() : Component.literal(tech.name());
        player.displayClientMessage(Component.translatable(
                        "message.sciencepacks.structure.locked", name(structure), techName)
                .withStyle(ChatFormatting.RED), true);
    }
}
