package com.sciencepacks.mixin;

import com.sciencepacks.research.LockBridge;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 锻造台：未解锁的产物结果为空。
 * 目标 createResult 为 SmithingMenu 自己的方法（slotsChanged 在父类 ItemCombinerMenu 中）。
 */
@Mixin(SmithingMenu.class)
public class SmithingMenuMixin {

    @Inject(method = "createResult", at = @At("TAIL"), remap = false)
    private void sciencepacks$lockSmithingResult(CallbackInfo callback) {
        LockBridge.enforceMenuResult((AbstractContainerMenu) (Object) this, 3,
                LockBridge.MenuKind.SMITHING);
    }
}
