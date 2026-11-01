package com.sciencepacks.research;

import com.sciencepacks.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 数值型研究效果：把科技 JSON 的 effects 换算成实际数值。
 *
 * <p>服务端（配方、放置、触及距离）与客户端（挖掘速度预览）共用同一套算法，
 * 区别只是数据来源：服务端用 {@link PlayerResearch}，客户端用 ClientResearchState。</p>
 */
public final class ResearchEffects {

    /** 触及距离属性的修饰符名（固定 id，重复应用不会叠加）。 */
    public static final String ATTRIBUTE_NAME = "sciencepacks_reach";
    private static final ResourceLocation ATTRIBUTE_ID =
            ResourceLocation.fromNamespaceAndPath("sciencepacks", "reach");

    private ResearchEffects() {
    }

    /**
     * 研究对玩家生效的「层数」：普通研究解锁后为 1；无限研究为已完成等级。
     * 未完成返回 0。
     */
    public static int levelOf(Technology tech, Set<ResourceLocation> unlocked,
                              Map<ResourceLocation, Integer> levels) {
        if (tech.infinite()) {
            return levels.getOrDefault(tech.id(), 0);
        }
        return unlocked.contains(tech.id()) ? 1 : 0;
    }

    /** 所有研究在指定类型上的加成总和（amount × 层数）。 */
    public static double bonus(Collection<Technology> techs, Set<ResourceLocation> unlocked,
                               Map<ResourceLocation, Integer> levels, String type) {
        double sum = 0;
        for (Technology tech : techs) {
            int level = levelOf(tech, unlocked, levels);
            if (level <= 0) {
                continue;
            }
            for (Technology.Effect effect : tech.effects()) {
                if (effect.type().equals(type)) {
                    sum += effect.amount() * level;
                }
            }
        }
        return sum;
    }

    /** 指定方块因研究增加的放置数量。 */
    public static int placeBonus(Collection<Technology> techs, Set<ResourceLocation> unlocked,
                                 Map<ResourceLocation, Integer> levels, ResourceLocation block) {
        if (block == null) {
            return 0;
        }
        double sum = 0;
        for (Technology tech : techs) {
            int level = levelOf(tech, unlocked, levels);
            if (level <= 0) {
                continue;
            }
            for (Technology.Effect effect : tech.effects()) {
                if (Technology.Effect.PLACE_LIMIT.equals(effect.type())
                        && block.equals(effect.block())) {
                    sum += effect.amount() * level;
                }
            }
        }
        return (int) Math.floor(sum);
    }

    /** 配置里的方块放置基础上限，0 表示不限制。 */
    public static int basePlaceLimit(ResourceLocation block) {
        if (block == null) {
            return 0;
        }
        String id = block.toString();
        for (String entry : Config.COMMON.blockPlaceLimits.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!key.equals(id)) {
                continue;
            }
            try {
                return Math.max(0, Integer.parseInt(entry.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** 玩家对指定方块的放置上限（基础值 + 研究加成），0 表示不限制。 */
    public static int placeLimit(PlayerResearch research, ResourceLocation block) {
        int base = basePlaceLimit(block);
        int bonus = placeBonus(TechLibrary.ordered(), research.unlocked(), research.levels(), block);
        if (base <= 0 && bonus <= 0) {
            return 0;
        }
        return base + bonus;
    }

    /** 把触及距离等属性效果应用到玩家身上（重复调用会先移除旧修饰符）。 */
    public static void applyAttributes(Player player) {
        // 1.21 把触及距离拆成「方块交互距离」与「实体交互距离」两个属性，两边都要加
        AttributeInstance blockReach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        AttributeInstance entityReach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (blockReach == null && entityReach == null) {
            return;
        }
        if (blockReach != null) {
            blockReach.removeModifier(ATTRIBUTE_ID);
        }
        if (entityReach != null) {
            entityReach.removeModifier(ATTRIBUTE_ID);
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        PlayerResearch research = Research.of(serverPlayer);
        double bonus = bonus(TechLibrary.ordered(), research.unlocked(), research.levels(),
                Technology.Effect.REACH);
        if (bonus > 0) {
            AttributeModifier modifier = new AttributeModifier(ATTRIBUTE_ID, bonus,
                    AttributeModifier.Operation.ADD_VALUE);
            if (blockReach != null) {
                blockReach.addTransientModifier(modifier);
            }
            if (entityReach != null) {
                entityReach.addTransientModifier(modifier);
            }
        }
    }

    /** 调试用：把配置解析结果转成 map。 */
    public static Map<ResourceLocation, Integer> parsedBaseLimits() {
        Map<ResourceLocation, Integer> map = new LinkedHashMap<>();
        for (String entry : Config.COMMON.blockPlaceLimits.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, eq).trim());
            if (id == null) {
                continue;
            }
            try {
                map.put(id, Math.max(0, Integer.parseInt(entry.substring(eq + 1).trim())));
            } catch (NumberFormatException ignored) {
                // 忽略无效条目
            }
        }
        return map;
    }
}
