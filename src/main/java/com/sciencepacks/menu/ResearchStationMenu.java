package com.sciencepacks.menu;

import com.sciencepacks.block.ResearchStationBlockEntity;
import com.sciencepacks.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

/**
 * 研究台菜单。
 *
 * <p>槽位按每行 9 个自动换行；行数由槽位数量决定，玩家背包整体下移。
 * 菜单与界面共用这些坐标方法，保证两边一致。</p>
 */
public class ResearchStationMenu extends AbstractContainerMenu {

    private static final int SLOT_SIZE = 18;
    private static final int GUI_WIDTH = 176;
    private static final int SLOT_TOP = 19;
    /** 输入槽区域与玩家背包之间的信息区高度（耐久条 + 科技信息 + 每轮/剩余两行材料）。 */
    private static final int INFO_HEIGHT = 96;

    private final Container station;
    private final ContainerData data;
    private final int stationSlotCount;
    private final int rows;

    /** 客户端构造：用一个临时容器承接同步下来的物品。 */
    public ResearchStationMenu(int id, Inventory playerInventory) {
        this(id, playerInventory,
                new SimpleContainer(com.sciencepacks.block.ResearchStationBlockEntity.slotCount()),
                new SimpleContainerData(2));
    }

    public ResearchStationMenu(int id, Inventory playerInventory, Container station, ContainerData data) {
        super(ModMenus.RESEARCH_STATION.get(), id);
        this.station = station;
        this.data = data;
        this.stationSlotCount = Math.max(1, Math.min(54, station.getContainerSize()));
        this.rows = (stationSlotCount + 8) / 9;
        station.startOpen(playerInventory.player);

        for (int i = 0; i < stationSlotCount; i++) {
            addSlot(new PackSlot(station, i, slotX(i), slotY(i)));
        }
        int invY = playerInvY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, invY + 58));
        }
        addDataSlots(data);
    }

    public int stationSlots() {
        return stationSlotCount;
    }

    public int rows() {
        return rows;
    }

    /** 第 i 个输入槽的 X 坐标（同一行内居中）。 */
    public int slotX(int index) {
        int row = index / 9;
        int col = index % 9;
        int inRow = Math.min(9, stationSlotCount - row * 9);
        int start = (GUI_WIDTH - inRow * SLOT_SIZE) / 2 + 1;
        return start + col * SLOT_SIZE;
    }

    /** 第 i 个输入槽的 Y 坐标。 */
    public int slotY(int index) {
        return SLOT_TOP + (index / 9) * SLOT_SIZE;
    }

    /** 输入槽区域底部。 */
    public int slotsBottom() {
        return SLOT_TOP + rows * SLOT_SIZE;
    }

    /** 玩家背包第一行的 Y 坐标。 */
    public int playerInvY() {
        return slotsBottom() + INFO_HEIGHT;
    }

    /** 第 index 个输入槽应该放哪种物品；越界返回 null。 */
    public ResourceLocation slotType(int index) {
        List<ResourceLocation> types = ResearchStationBlockEntity.slotTypes();
        return index >= 0 && index < types.size() ? types.get(index) : null;
    }

    /**
     * 输入槽：只接受「这个槽位对应的那一种物品」，同种物品可以叠加到 64 个。
     *
     * <p>限制同时作用在手动点击与 shift 快速移动上，因此放不进别的物品，也不会超过上限。</p>
     */
    private class PackSlot extends Slot {
        private final int typeIndex;

        PackSlot(Container container, int typeIndex, int x, int y) {
            super(container, typeIndex, x, y);
            this.typeIndex = typeIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            ResourceLocation wanted = slotType(typeIndex);
            if (wanted == null) {
                return false;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || !id.equals(wanted)) {
                return false;
            }
            // 空槽或同种物品都能放，数量上限交给 getMaxStackSize
            return getItem().isEmpty() || ItemStack.isSameItemSameComponents(getItem(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return ResearchStationBlockEntity.MAX_STACK;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return station.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int inventoryStart = stationSlotCount;
        int hotbarStart = stationSlotCount + 27;
        int end = stationSlotCount + 36;
        if (index < stationSlotCount) {
            if (!this.moveItemStackTo(stack, inventoryStart, end, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(stack, 0, stationSlotCount, false)) {
                if (index < hotbarStart) {
                    if (!this.moveItemStackTo(stack, hotbarStart, end, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, inventoryStart, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        station.stopOpen(player);
    }

    public ContainerData getData() {
        return data;
    }
}
