package com.sciencepacks.mixin;

import com.sciencepacks.research.LockBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 熔炉族：输入配方未解锁时直接暂停整个 tick（不烧、不耗燃料）。
 * serverTick 为 1.21.1 官方名。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntityMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sciencepacks$pauseLockedFurnace(Level level, BlockPos pos, BlockState state,
                                                        AbstractFurnaceBlockEntity furnace, CallbackInfo callback) {
        if (LockBridge.shouldPauseFurnace(level, pos, furnace)) {
            callback.cancel();
        }
    }
}
