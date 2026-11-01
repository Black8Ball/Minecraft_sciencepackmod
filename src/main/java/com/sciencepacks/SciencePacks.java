package com.sciencepacks;

import com.sciencepacks.events.BlockLimitEvents;
import com.sciencepacks.events.ResearchEffectEvents;
import com.sciencepacks.events.ServerEvents;
import com.sciencepacks.events.StructureEvents;
import com.sciencepacks.events.TriggerEvents;
import com.sciencepacks.network.Network;
import com.sciencepacks.registry.ModBlockEntities;
import com.sciencepacks.registry.ModBlocks;
import com.sciencepacks.registry.ModItems;
import com.sciencepacks.registry.ModMenus;
import com.sciencepacks.research.ResearchDataLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 科技瓶 (Science Packs)：Factorio 风格科技树、研究台与配方解锁。 */
@Mod(SciencePacks.MOD_ID)
public final class SciencePacks {

    public static final String MOD_ID = "sciencepacks";

    public static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public SciencePacks(IEventBus modBus, ModContainer container) {
        ModBlocks.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModItems.init();

        // 注意：方块/方块实体/菜单的 DeferredRegister 定义在各自的 registry 类里，
        // 这里必须注册它们本人的实例，而不是在本类另建一个。
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 网络载荷注册
        modBus.addListener(Network::register);

        // 漏斗/管道访问研究台：暴露物品处理器能力
        modBus.addListener(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class, event ->
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        ModBlockEntities.RESEARCH_STATION.get(),
                        (be, side) -> new net.neoforged.neoforge.items.wrapper.InvWrapper(be)));

        // 客户端初始化必须在这里（构造函数阶段）挂监听，commonSetup 阶段已经太晚
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.sciencepacks.client.ClientSetup.init(modBus, container);
        }

        // 游戏总线事件
        NeoForge.EVENT_BUS.addListener(ResearchDataLoader::onAddReloadListener);
        // 模组联动：按模组条件替换配方
        NeoForge.EVENT_BUS.addListener(com.sciencepacks.research.RecipeOverrides::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(com.sciencepacks.command.ResearchCommand::register);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        // 挖掘/击杀/合成/烧炼/拾取/到达维度 等事件触发
        NeoForge.EVENT_BUS.register(TriggerEvents.class);
        // 多方块结构锁（下界门 / 末地门 / 凋灵 / 傀儡）
        NeoForge.EVENT_BUS.register(StructureEvents.class);
        // 数值型研究：挖掘速度 / 触及距离
        NeoForge.EVENT_BUS.register(ResearchEffectEvents.class);
        // 数值型研究：方块放置数量上限
        NeoForge.EVENT_BUS.register(BlockLimitEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
