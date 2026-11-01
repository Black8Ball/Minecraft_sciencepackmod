package com.sciencepacks.mixin;

import com.sciencepacks.research.LockBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 酿造台：原料对应研究未解锁时暂停整个 tick（不推进、不耗材料、不耗燃料）。
 * serverTick 为 1.21.1 官方名。
 */
@Mixin(BrewingStandBlockEntity.class)
public class BrewingStandBlockEntityMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sciencepacks$pauseLockedBrewing(Level level, BlockPos pos, BlockState state,
                                                        BrewingStandBlockEntity stand, CallbackInfo callback) {
        if (LockBridge.shouldPauseBrewing(level, pos, stand)) {
            callback.cancel();
        }
    }
}
