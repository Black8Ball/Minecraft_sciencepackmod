package com.sciencepacks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;

import javax.annotation.Nullable;
import java.util.List;

/** 科技包：研究消耗品，颜色对应 Factorio 的 science pack 分级。 */
public class SciencePackItem extends Item {

    private final String tier;

    public SciencePackItem(Properties properties, String tier) {
        super(properties);
        this.tier = tier;
    }

    public String tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sciencepacks.pack." + tier).withStyle(ChatFormatting.GRAY));
    }
}
