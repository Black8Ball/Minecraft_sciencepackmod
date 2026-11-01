package com.sciencepacks.mixin;

import com.sciencepacks.research.LockBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 熔炉族：未解锁的熔炼产物拿不出来。目标 onTake（1.21.1 官方名）。 */
@Mixin(FurnaceResultSlot.class)
public class FurnaceResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true, remap = false)
    private void sciencepacks$blockTake(Player player, ItemStack stack, CallbackInfo callback) {
        if (LockBridge.blockSmeltTake(player, stack)) {
            callback.cancel();
        }
    }
}
