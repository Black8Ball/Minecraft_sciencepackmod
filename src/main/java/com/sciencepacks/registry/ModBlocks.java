package com.sciencepacks.registry;

import com.sciencepacks.SciencePacks;
import com.sciencepacks.block.ResearchStationBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块注册。 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, SciencePacks.MOD_ID);

    public static final DeferredHolder<Block, Block> RESEARCH_STATION = BLOCKS.register("research_station",
            () -> new ResearchStationBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .requiresCorrectToolForDrops()));

    public static void init() {
    }

    private ModBlocks() {
    }
}
