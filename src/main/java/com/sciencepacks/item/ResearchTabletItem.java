package com.sciencepacks.item;

import com.sciencepacks.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 研究终端：右键打开科技树界面（与 Factorio 的研究面板对应）。 */
public class ResearchTabletItem extends Item {

    public ResearchTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // 只在客户端加载：专用服务端永远不会走到这里
            ClientHooks.openResearchScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
