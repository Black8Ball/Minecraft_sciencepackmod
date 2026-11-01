package com.sciencepacks.events;

import com.sciencepacks.research.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 多方块结构锁的拦截点。
 *
 * <p>只在服务端处理；客户端看到的粒子/音效由服务端取消事件后自然消失。</p>
 */
public final class StructureEvents {

    /** 传送门里站着时每 tick 都会尝试传送，提示要限流。 */
    private static final int MESSAGE_COOLDOWN = 100;

    private StructureEvents() {
    }

    /* ---------------- 下界传送门 ---------------- */

    /** 用打火石/火焰弹点黑曜石：未研究时直接取消（不点火、不消耗）。 */
    @SubscribeEvent
    public static void onIgnitePortal(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        // 内置下界门
        if ((stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) && state.is(Blocks.OBSIDIAN)
                && Structures.locked(player, Structures.NETHER_PORTAL)) {
            event.setCanceled(true);
            Structures.notifyLocked(player, Structures.NETHER_PORTAL);
            return;
        }
        // 通用写法：item_on_block:<物品>=<方块>，兼容其它模组的点燃器与传送门框架
        String entry = Structures.matchingItemOnBlock(stack, state);
        if (entry != null && Structures.locked(player, entry)) {
            event.setCanceled(true);
            Structures.notifyLocked(player, entry);
        }
    }

    /** 兜底：框架被点燃时再检查一次（例如发射器点火）。 */
    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8.0, entity -> entity instanceof ServerPlayer);
        if (nearest instanceof ServerPlayer player && Structures.locked(player, Structures.NETHER_PORTAL)) {
            event.setCanceled(true);
            Structures.notifyLocked(player, Structures.NETHER_PORTAL);
        }
    }

    /* ---------------- 末地传送门 ---------------- */

    /** 往末地传送门框里插末影之眼。 */
    @SubscribeEvent
    public static void onInsertEye(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getItemStack().is(Items.ENDER_EYE)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(Blocks.END_PORTAL_FRAME) || state.getValue(EndPortalFrameBlock.HAS_EYE)) {
            return;
        }
        if (Structures.locked(player, Structures.END_PORTAL)) {
            event.setCanceled(true);
            Structures.notifyLocked(player, Structures.END_PORTAL);
        }
    }

    /* ---------------- 维度传送 ---------------- */

    /** 穿过传送门（含已有的门）进入下界/末地，或任何被研究锁住的模组维度。 */
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String structure = null;
        if (Level.NETHER.equals(event.getDimension())) {
            structure = Structures.NETHER_PORTAL;
        } else if (Level.END.equals(event.getDimension())) {
            structure = Structures.END_PORTAL;
        }
        // 通用写法：dimension:ns:id，兼容其它模组的维度
        String generic = Structures.PREFIX_DIMENSION + event.getDimension().location();
        if (structure != null && Structures.locked(player, structure)) {
            blockTravel(event, player, structure);
            return;
        }
        if (Structures.locked(player, generic)) {
            blockTravel(event, player, generic);
        }
    }

    private static void blockTravel(EntityTravelToDimensionEvent event, ServerPlayer player, String structure) {
        event.setCanceled(true);
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong("sciencepacks.structureMsg");
        if (now - last >= MESSAGE_COOLDOWN) {
            player.getPersistentData().putLong("sciencepacks.structureMsg", now);
            Structures.notifyLocked(player, structure);
        }
    }

    /* ---------------- 凋灵 / 傀儡 ---------------- */

    /** 新生成（非读档）的凋灵、铁傀儡、雪傀儡：搭建者未研究时取消。 */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        String structure = structureOf(event.getEntity());
        if (structure == null) {
            // 通用写法：entity:ns:id，兼容其它模组的 Boss / 召唤物
            net.minecraft.resources.ResourceLocation typeId =
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (typeId == null) {
                return;
            }
            structure = Structures.PREFIX_ENTITY + typeId;
        }
        Player nearest = nearestPlayer(event.getEntity(), 12.0);
        if (nearest instanceof ServerPlayer player && Structures.locked(player, structure)) {
            event.setCanceled(true);
            Structures.notifyLocked(player, structure);
        }
    }

    private static String structureOf(Entity entity) {
        if (entity instanceof WitherBoss) {
            return Structures.WITHER;
        }
        if (entity instanceof IronGolem) {
            return Structures.IRON_GOLEM;
        }
        if (entity instanceof SnowGolem) {
            return Structures.SNOW_GOLEM;
        }
        return null;
    }

    private static Player nearestPlayer(Entity entity, double radius) {
        if (entity.level() instanceof ServerLevel level) {
            return level.getNearestPlayer(entity, radius);
        }
        return null;
    }
}
