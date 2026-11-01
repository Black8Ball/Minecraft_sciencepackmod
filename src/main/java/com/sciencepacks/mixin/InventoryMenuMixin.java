package com.sciencepacks.mixin;

import com.sciencepacks.research.LockBridge;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 背包 2x2 合成结果守卫。
 *
 * <p>1.21.1 官方字段名：craftSlots / resultSlots / owner（不再用 f_xxxx_）。</p>
 */
@Mixin(InventoryMenu.class)
public class InventoryMenuMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Shadow
    @Final
    private ResultContainer resultSlots;

    @Shadow
    @Final
    private Player owner;

    @Inject(method = "slotsChanged", at = @At("TAIL"), remap = false)
    private void sciencepacks$lockInventoryCrafting(Container container, CallbackInfo callback) {
        LockBridge.enforceCrafting(this.owner, this.craftSlots, this.resultSlots);
    }
}
