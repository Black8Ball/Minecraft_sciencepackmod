package com.sciencepacks.block;

import com.sciencepacks.Config;
import com.sciencepacks.registry.ModBlockEntities;
import com.sciencepacks.research.PlayerResearch;
import com.sciencepacks.research.Research;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 研究台方块实体。
 *
 * <p>槽位表由配置按顺序给出：每个条目固定对应一个槽位，槽位里只能放那一种物品，且一次只放一个。
 * 手动放入与漏斗（IItemHandler 能力）都走同一套 {@link #canPlaceItem} 规则。</p>
 */
public class ResearchStationBlockEntity extends BlockEntity
        implements MenuProvider, WorldlyContainer {

    /** 配置上限：54 = 6 行（与箱子一致），再多 GUI 会很高。 */
    public static final int MAX_SLOTS = 54;
    /** 每个研究槽位最多叠多少个同种科技包。 */
    public static final int MAX_STACK = 64;

    public static final int DATA_COOLDOWN = 0;
    public static final int DATA_MAX_COOLDOWN = 1;

    /** 配置没写时的默认槽位：六种科技包，一个槽位一种。 */
    private static final List<ResourceLocation> DEFAULT_TYPES = List.of(
            ResourceLocation.fromNamespaceAndPath("sciencepacks", "pack_red"),
            ResourceLocation.fromNamespaceAndPath("sciencepacks", "pack_green"),
            ResourceLocation.fromNamespaceAndPath("sciencepacks", "pack_blue"),
            ResourceLocation.fromNamespaceAndPath("sciencepacks", "pack_purple"),
            ResourceLocation.fromNamespaceAndPath("sciencepacks", "pack_gray"),
            ResourceLocation.fromNamespaceAndPath("sciencepacks", "pack_yellow"));

    private static List<ResourceLocation> cachedTypes;
    private static String cachedTypesKey = "";

    private final SimpleContainer inventory;
    private final ContainerData data = new SimpleContainerData(2);
    private final int[] allSlots;
    private String groupKey = "";
    private int cooldown;

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_STATION.get(), pos, state);
        int slots = slotCount();
        this.inventory = new SimpleContainer(slots) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return ResearchStationBlockEntity.this.canPlaceItem(slot, stack);
            }

            @Override
            public int getMaxStackSize() {
                // 同一个槽位最多叠 64 个同种科技包
                return MAX_STACK;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
                // 兜底：任何来源（漏斗、命令行、旧存档）都不允许超过 64 个
                if (!stack.isEmpty() && stack.getCount() > MAX_STACK) {
                    ItemStack capped = stack.copy();
                    capped.setCount(MAX_STACK);
                    super.setItem(slot, capped);
                } else {
                    super.setItem(slot, stack);
                }
            }
        };
        this.allSlots = new int[slots];
        for (int i = 0; i < slots; i++) {
            allSlots[i] = i;
        }
        this.data.set(DATA_COOLDOWN, 0);
        this.data.set(DATA_MAX_COOLDOWN, 0);
    }

    /**
     * 槽位表：配置里按顺序列出的物品，一个物品固定一个槽位。
     *
     * <p>重复的 id 会被去掉；配置为空时回退到六种科技包；最多 {@link #MAX_SLOTS} 个。</p>
     *
     * <p>老存档/老配置里可能还留着升级前的四色槽位表（红绿蓝紫），
     * 这里识别出「正好是旧的四种默认值」时自动补上灰、黄两种新包，
     * 免得新包明明已经做好了却进不了研究台；玩家自己改过的槽位表不动。</p>
     */
    public static List<ResourceLocation> slotTypes() {
        List<? extends String> configured = Config.COMMON.stationAllowedItems.get();
        String key = String.join(",", configured);
        if (cachedTypes != null && key.equals(cachedTypesKey)) {
            return cachedTypes;
        }
        List<String> values = new ArrayList<>(configured);
        if (isLegacyFourDefaults(values)) {
            values.add("sciencepacks:pack_gray");
            values.add("sciencepacks:pack_yellow");
        }
        LinkedHashSet<ResourceLocation> set = new LinkedHashSet<>();
        for (String value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                set.add(id);
            }
        }
        if (set.isEmpty()) {
            set.addAll(DEFAULT_TYPES);
        }
        List<ResourceLocation> types = new ArrayList<>(set);
        if (types.size() > MAX_SLOTS) {
            types = new ArrayList<>(types.subList(0, MAX_SLOTS));
        }
        cachedTypes = List.copyOf(types);
        cachedTypesKey = key;
        return cachedTypes;
    }

    /** 是否是升级前默认的四色槽位表（顺序一致，允许省略命名空间）。 */
    private static boolean isLegacyFourDefaults(List<String> values) {
        if (values.size() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            ResourceLocation id = ResourceLocation.tryParse(values.get(i));
            if (id == null || !id.equals(DEFAULT_TYPES.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static int slotCount() {
        return Math.max(1, slotTypes().size());
    }

    /** 第 slot 个槽位应该放哪种物品；越界返回 null。 */
    public static ResourceLocation slotType(int slot) {
        List<ResourceLocation> types = slotTypes();
        return slot >= 0 && slot < types.size() ? types.get(slot) : null;
    }

    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && slotTypes().contains(id);
    }

    /* ---------------- 槽位规则：一个槽位固定一种物品，最多叠 64 个 ---------------- */

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.getContainerSize() || stack.isEmpty()) {
            return false;
        }
        ResourceLocation wanted = slotType(slot);
        if (wanted == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !id.equals(wanted)) {
            return false;
        }
        // 同一种物品可以继续叠加，上限 64；槽里是别的物品则拒绝
        ItemStack current = inventory.getItem(slot);
        return current.isEmpty()
                || (current.getItem() == stack.getItem() && current.getCount() < MAX_STACK);
    }

    /* ---------------- WorldlyContainer（漏斗等） ---------------- */

    @Override
    public int[] getSlotsForFace(Direction side) {
        return allSlots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // 允许漏斗/管道等自动化方块把研究台里的材料抽走
        return slot >= 0 && slot < slotCount();
    }

    /* ---------------- Container 委托 ---------------- */

    @Override
    public int getContainerSize() {
        return inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return inventory.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setItem(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
    }

    @Override
    public void startOpen(Player player) {
        inventory.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        inventory.stopOpen(player);
    }

    public Container getInventory() {
        return inventory;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
        setChanged();
    }

    public ContainerData getData() {
        return data;
    }

    public int cooldown() {
        return cooldown;
    }

    /* ---------------- tick ---------------- */

    public static void serverTick(Level level, BlockPos pos, BlockState state, ResearchStationBlockEntity station) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (station.groupKey.isBlank()) {
            station.syncData(0, 0);
            return;
        }
        if (station.cooldown > 0) {
            station.cooldown--;
            station.syncData(station.cooldown, station.data.get(DATA_MAX_COOLDOWN));
            return;
        }
        PlayerResearch research = Research.group(serverLevel.getServer(), station.groupKey);
        int ticks = Research.consumeCycle(serverLevel.getServer(), research, station);
        if (ticks > 0) {
            station.cooldown = ticks;
            station.syncData(ticks, ticks);
            station.setChanged();
        } else {
            station.syncData(0, 0);
        }
    }

    private void syncData(int cooldownValue, int maxValue) {
        if (data.get(DATA_COOLDOWN) != cooldownValue) {
            data.set(DATA_COOLDOWN, cooldownValue);
        }
        if (data.get(DATA_MAX_COOLDOWN) != maxValue) {
            data.set(DATA_MAX_COOLDOWN, maxValue);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.sciencepacks.research_station");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new com.sciencepacks.menu.ResearchStationMenu(id, playerInventory, this, this.data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Group", groupKey);
        tag.putInt("Cooldown", cooldown);
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, inventory.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        groupKey = tag.getString("Group");
        cooldown = tag.getInt("Cooldown");
        // 槽位布局以当前配置为准：旧存档里的物品按物品 id 对号入座，避免配置改了之后错位
        NonNullList<ItemStack> saved = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, saved, registries);
        inventory.clearContent();
        List<ResourceLocation> types = slotTypes();
        for (ItemStack stack : saved) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            int index = types.indexOf(id);
            if (index >= 0 && index < inventory.getContainerSize()) {
                // 同一物品分散在旧槽位时合并到对应槽，总量不超过上限
                int merged = inventory.getItem(index).getCount() + stack.getCount();
                ItemStack kept = stack.copy();
                kept.setCount(Math.min(merged, MAX_STACK));
                inventory.setItem(index, kept);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("Group", groupKey);
        return tag;
    }
}
