package com.sciencepacks.mixin;

import com.sciencepacks.research.LockBridge;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 切石机：未解锁的产物结果为空。目标 slotsChanged（1.21.1 官方名）。 */
@Mixin(StonecutterMenu.class)
public class StonecutterMenuMixin {

    @Inject(method = "slotsChanged", at = @At("TAIL"), remap = false)
    private void sciencepacks$lockStonecuttingResult(Container container, CallbackInfo callback) {
        LockBridge.enforceMenuResult((AbstractContainerMenu) (Object) this, 1,
                LockBridge.MenuKind.STONECUTTING);
    }
}
