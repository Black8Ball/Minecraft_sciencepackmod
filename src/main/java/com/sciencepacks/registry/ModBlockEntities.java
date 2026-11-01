package com.sciencepacks.registry;

import com.sciencepacks.SciencePacks;
import com.sciencepacks.block.ResearchStationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块实体注册。 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SciencePacks.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchStationBlockEntity>> RESEARCH_STATION =
            BLOCK_ENTITIES.register("research_station", () -> BlockEntityType.Builder
                    .of(ResearchStationBlockEntity::new, ModBlocks.RESEARCH_STATION.get())
                    .build(null));

    public static void init() {
    }

    private ModBlockEntities() {
    }
}
