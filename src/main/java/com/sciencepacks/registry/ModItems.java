package com.sciencepacks.registry;

import com.sciencepacks.SciencePacks;
import com.sciencepacks.item.ResearchTabletItem;
import com.sciencepacks.item.SciencePackItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 物品注册：六色科技包 + 研究终端 + 研究台方块物品。 */
public final class ModItems {

    public static final DeferredHolder<Item, Item> PACK_RED = SciencePacks.ITEMS.register("pack_red",
            () -> new SciencePackItem(new Item.Properties(), "red"));
    public static final DeferredHolder<Item, Item> PACK_GREEN = SciencePacks.ITEMS.register("pack_green",
            () -> new SciencePackItem(new Item.Properties(), "green"));
    public static final DeferredHolder<Item, Item> PACK_BLUE = SciencePacks.ITEMS.register("pack_blue",
            () -> new SciencePackItem(new Item.Properties(), "blue"));
    public static final DeferredHolder<Item, Item> PACK_PURPLE = SciencePacks.ITEMS.register("pack_purple",
            () -> new SciencePackItem(new Item.Properties(), "purple"));
    /** 灰瓶：显示名「军备科技包」。 */
    public static final DeferredHolder<Item, Item> PACK_GRAY = SciencePacks.ITEMS.register("pack_gray",
            () -> new SciencePackItem(new Item.Properties(), "gray"));
    /** 黄瓶：显示名「效能科技包」。 */
    public static final DeferredHolder<Item, Item> PACK_YELLOW = SciencePacks.ITEMS.register("pack_yellow",
            () -> new SciencePackItem(new Item.Properties(), "yellow"));

    public static final DeferredHolder<Item, Item> RESEARCH_TABLET = SciencePacks.ITEMS.register("research_tablet",
            () -> new ResearchTabletItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> RESEARCH_STATION_ITEM = SciencePacks.ITEMS.register("research_station",
            () -> new BlockItem(ModBlocks.RESEARCH_STATION.get(), new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = SciencePacks.TABS.register("main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.sciencepacks"))
                    .icon(() -> new ItemStack(RESEARCH_TABLET.get()))
                    .displayItems((params, output) -> {
                        output.accept(PACK_RED.get());
                        output.accept(PACK_GREEN.get());
                        output.accept(PACK_BLUE.get());
                        output.accept(PACK_PURPLE.get());
                        output.accept(PACK_GRAY.get());
                        output.accept(PACK_YELLOW.get());
                        output.accept(RESEARCH_TABLET.get());
                        output.accept(RESEARCH_STATION_ITEM.get());
                    })
                    .build());

    /** 触发本类静态初始化。 */
    public static void init() {
    }

    private ModItems() {
    }
}
