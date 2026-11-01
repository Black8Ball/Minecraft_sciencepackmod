package com.sciencepacks.research;

import com.sciencepacks.Config;
import com.sciencepacks.events.MenuTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 供 Mixin 调用的桥接类。Mixin 以 SRG 名编译，所以这里只暴露「我们的类型 + 类名稳定的 MC 类型」，
 * 所有对 MC 成员的调用都发生在这个用官方名编译并重映射过的类里。
 */
public final class LockBridge {

    public enum MenuKind {
        SMITHING,
        STONECUTTING
    }

    private static final RecipeType<?>[] COOKING_TYPES = {
            RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING
    };

    /** 熔炉是否暂停的短缓存：位置 -> (到期 tick, 是否锁定)。 */
    private static final Map<Long, long[]> FURNACE_CACHE = new ConcurrentHashMap<>();
    /** 酿造台提示的限流缓存：位置 -> 下次可以提示的 tick。 */
    private static final Map<Long, Long> BREWING_MESSAGE_CACHE = new ConcurrentHashMap<>();

    private LockBridge() {
    }

    /* ---------------- 合成 ---------------- */

    public static void enforceCrafting(Player player, CraftingContainer craftSlots, ResultContainer resultSlots) {
        if (!(player instanceof ServerPlayer serverPlayer) || !Config.COMMON.lockCrafting.get()) {
            return;
        }
        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) {
            return;
        }
        Level level = player.level();
        List<ItemStack> grid = new ArrayList<>(craftSlots.getContainerSize());
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            grid.add(craftSlots.getItem(i));
        }
        CraftingInput input = CraftingInput.of(craftSlots.getWidth(), craftSlots.getHeight(), grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) {
            return;
        }
        if (Research.isRecipeLocked(serverPlayer, recipe.get())) {
            resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }

    /* ---------------- 熔炉族 ---------------- */

    /** 炉子归属的研究组是否还没解锁当前输入对应的熔炼配方；是则整个 tick 暂停（不烧、不耗燃料）。 */
    public static boolean shouldPauseFurnace(Level level, BlockPos pos, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !Config.COMMON.lockSmelting.get()) {
            return false;
        }
        if (!(blockEntity instanceof Container container)) {
            return false;
        }
        ItemStack input = container.getItem(0);
        if (input.isEmpty()) {
            return false;
        }
        String groupKey = FurnaceOwnership.get(serverLevel.getServer()).get(level, pos);
        if (groupKey == null) {
            // 没有归属的炉子（村庄/遗留）不处理，避免误伤
            return false;
        }
        long now = serverLevel.getGameTime();
        long[] cached = FURNACE_CACHE.get(pos.asLong());
        if (cached != null && cached[0] > now) {
            return cached[1] == 1L;
        }
        boolean locked = isCookingLocked(serverLevel, groupKey, input);
        FURNACE_CACHE.put(pos.asLong(), new long[]{now + 20, locked ? 1L : 0L});
        return locked;
    }

    private static boolean isCookingLocked(ServerLevel level, String groupKey, ItemStack input) {
        PlayerResearch research = Research.group(level.getServer(), groupKey);
        SingleRecipeInput probe = new SingleRecipeInput(input.copy());
        for (RecipeType<?> type : COOKING_TYPES) {
            @SuppressWarnings("unchecked")
            Optional<RecipeHolder<?>> recipe = (Optional<RecipeHolder<?>>) (Optional<?>)
                    level.getRecipeManager().getRecipeFor((RecipeType) type, probe, level);
            if (recipe.isPresent()) {
                return Research.isLockedFor(research, level.getServer(), recipe.get());
            }
        }
        return false;
    }

    public static void clearFurnaceCache() {
        FURNACE_CACHE.clear();
        BREWING_MESSAGE_CACHE.clear();
    }

    /* ---------------- 酿造台 ---------------- */

    /**
     * 酿造台是否暂停：归属研究组还没解锁原料对应的研究时，整个 tick 取消
     * （不推进、不耗材料、不耗燃料）。天然酿造台没有归属记录，不处理。
     */
    public static boolean shouldPauseBrewing(Level level, BlockPos pos, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !Config.COMMON.lockBrewing.get()) {
            return false;
        }
        if (!(blockEntity instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand)) {
            return false;
        }
        String groupKey = FurnaceOwnership.get(serverLevel.getServer()).get(level, pos);
        if (groupKey == null) {
            return false;
        }
        ItemStack ingredient = stand.getItem(3);
        if (ingredient.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ingredient.getItem());
        if (itemId == null || TechLibrary.whitelistedItem(itemId)) {
            return false;
        }
        ResourceLocation techId = TechLibrary.ownerOfItem(itemId);
        if (techId == null) {
            return false;
        }
        PlayerResearch research = Research.group(serverLevel.getServer(), groupKey);
        if (Research.completedFor(research, techId)) {
            return false;
        }
        long now = serverLevel.getGameTime();
        Long until = BREWING_MESSAGE_CACHE.get(pos.asLong());
        if (until == null || until < now) {
            Technology tech = TechLibrary.get(techId);
            net.minecraft.network.chat.Component techName = tech != null
                    ? net.minecraft.network.chat.Component.literal(tech.name())
                    : net.minecraft.network.chat.Component.literal(techId.toString());
            for (ServerPlayer member : Research.members(serverLevel.getServer(), groupKey)) {
                if (member.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 64 * 64) {
                    member.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "message.sciencepacks.brewing_locked", techName), true);
                }
            }
            BREWING_MESSAGE_CACHE.put(pos.asLong(), now + 100);
        }
        return true;
    }

    /* ---------------- 熔炉取产物 ---------------- */

    public static boolean blockSmeltTake(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || !Config.COMMON.lockSmelting.get()) {
            return false;
        }
        return Research.isOutputLocked(serverPlayer, stack, COOKING_TYPES);
    }

    /* ---------------- 锻造台 / 切石机 ---------------- */

    public static void enforceMenuResult(AbstractContainerMenu menu, int resultSlotIndex, MenuKind kind) {
        ServerPlayer player = MenuTracker.playerOf(menu);
        if (player == null) {
            return;
        }
        if (kind == MenuKind.SMITHING && !Config.COMMON.lockSmithing.get()) {
            return;
        }
        if (kind == MenuKind.STONECUTTING && !Config.COMMON.lockStonecutting.get()) {
            return;
        }
        if (resultSlotIndex < 0 || resultSlotIndex >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.getSlot(resultSlotIndex);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return;
        }
        boolean locked = kind == MenuKind.SMITHING
                ? Research.isOutputLocked(player, stack, RecipeType.SMITHING)
                : Research.isOutputLocked(player, stack, RecipeType.STONECUTTING);
        if (locked) {
            slot.set(ItemStack.EMPTY);
            menu.broadcastChanges();
        }
    }

    public static ResourceLocation recipeIdOf(ServerPlayer player, ItemStack stack) {
        return null;
    }
}
