package com.sciencepacks.events;

import com.sciencepacks.research.Research;
import com.sciencepacks.research.Technology;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 事件类触发条件：挖掘方块、击败生物、合成、烧炼、拾取、到达维度。
 *
 * <p>所有计数都按研究组累加（见 {@link com.sciencepacks.research.PlayerResearch#addTriggerCount}），
 * 事件发生后立刻检查相关科技是否可以解锁。</p>
 */
public final class TriggerEvents {

    private TriggerEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (event.isCanceled()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        Research.onTriggerEvent(player, Technology.Trigger.BLOCK_BREAK, id, 1);
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Research.onTriggerEvent(player, Technology.Trigger.KILL, id, 1);
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            add(player, Technology.Trigger.CRAFT, event.getCrafting());
        }
    }

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            add(player, Technology.Trigger.SMELT, event.getSmelting());
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            add(player, Technology.Trigger.PICKUP, event.getItemEntity().getItem());
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Research.onTriggerEvent(player, Technology.Trigger.DIMENSION, event.getTo().location(), 1);
        }
    }

    private static void add(ServerPlayer player, String type, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Research.onTriggerEvent(player, type, id, Math.max(1, stack.getCount()));
    }
}
