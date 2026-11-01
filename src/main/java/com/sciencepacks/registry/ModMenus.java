package com.sciencepacks.registry;

import com.sciencepacks.SciencePacks;
import com.sciencepacks.menu.ResearchStationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 菜单注册。 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SciencePacks.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchStationMenu>> RESEARCH_STATION =
            MENUS.register("research_station", () -> IMenuTypeExtension.create(
                    (id, inventory, data) -> new ResearchStationMenu(id, inventory)));

    public static void init() {
    }

    private ModMenus() {
    }
}
