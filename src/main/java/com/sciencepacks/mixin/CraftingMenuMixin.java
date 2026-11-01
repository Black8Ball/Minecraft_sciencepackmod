package com.sciencepacks.mixin;

import com.sciencepacks.research.LockBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 工作台 3x3 合成结果守卫。
 *
 * <p>1.21.1 的官方名是 slotChangedCraftingGrid，且多了一个 RecipeHolder 参数；
 * NeoForge 运行期直接用官方名（不再像 1.20.1 那样用 SRG 名），所以这里用官方名 +
 * remap = false，也不需要 refmap。</p>
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"), remap = false)
    private static void sciencepacks$lockCraftingResult(AbstractContainerMenu menu, Level level, Player player,
                                                        CraftingContainer craftSlots, ResultContainer resultSlots,
                                                        RecipeHolder<CraftingRecipe> recipe, CallbackInfo callback) {
        LockBridge.enforceCrafting(player, craftSlots, resultSlots);
    }
}
