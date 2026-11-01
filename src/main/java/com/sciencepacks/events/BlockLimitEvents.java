package com.sciencepacks.events;

import com.sciencepacks.research.Research;
import com.sciencepacks.research.ResearchEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 方块放置数量限制：配置里给了基础上限、或研究里带 place_limit 效果时，
 * 玩家在附近区域放这种方块超过上限就会被拒绝。
 */
public final class BlockLimitEvents {

    /** 统计半径（区块）：上下左右各 8 个区块，约 128 格。 */
    private static final int RADIUS_CHUNKS = 8;

    private BlockLimitEvents() {
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Block block = event.getPlacedBlock().getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId == null) {
            return;
        }
        int limit = ResearchEffects.placeLimit(Research.of(player), blockId);
        if (limit <= 0) {
            return;
        }
        int count = countNearby(player.serverLevel(), event.getPos(), block);
        if (count >= limit) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable(
                    "message.sciencepacks.place_limit", block.getName(), limit), true);
        }
    }

    /** 统计中心点附近已放置的该方块数量（只扫已加载的区块）。 */
    private static int countNearby(ServerLevel level, BlockPos center, Block block) {
        int count = 0;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int x = cx - RADIUS_CHUNKS; x <= cx + RADIUS_CHUNKS; x++) {
            for (int z = cz - RADIUS_CHUNKS; z <= cz + RADIUS_CHUNKS; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (int i = 0; i < chunk.getSectionsCount(); i++) {
                    LevelChunkSection section = chunk.getSection(i);
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    if (!section.maybeHas(state -> state.is(block))) {
                        continue;
                    }
                    int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int lx = 0; lx < 16; lx++) {
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState state = section.getBlockState(lx, y, lz);
                                if (state.is(block)) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return count;
    }
}
