package com.sciencepacks.client;

import com.sciencepacks.menu.ResearchStationMenu;
import com.sciencepacks.research.Technology;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 研究台界面：输入槽按每行 9 个自动换行，槽位多时窗口自动变高。
 * 显示当前科技、每轮材料剩余、本轮消耗耐久条，以及玩家背包网格。
 */
@OnlyIn(Dist.CLIENT)
public class ResearchStationScreen extends AbstractContainerScreen<ResearchStationMenu> {

    private static final int PANEL = 0xFF1E1E24;
    private static final int HEAD = 0xFF26262E;
    private static final int BORDER = 0xFF3D3D48;
    private static final int SLOT_BG = 0xFF0E0E12;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFF9A9AA6;
    private static final int GREEN = 0xFF3E8E41;
    private static final int CYAN = 0xFF42C7C7;

    private float uiScale = 1f;
    private float uiTx;
    private float uiTy;

    public ResearchStationScreen(ResearchStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        // 背包 3 行 + 快捷栏 1 行，再留 6 像素底边，避免面板比物品栏短
        this.imageHeight = menu.playerInvY() + 58 + 18 + 6;
        this.inventoryLabelY = menu.playerInvY() - 11;
    }

    @Override
    protected void init() {
        super.init();
        // 窗口装不下时整体等比缩小，并居中显示
        uiScale = Math.min(1f, Math.min((this.height - 8f) / this.imageHeight,
                (this.width - 8f) / this.imageWidth));
        uiTx = (this.width - this.imageWidth * uiScale) / 2f;
        uiTy = (this.height - this.imageHeight * uiScale) / 2f;
        this.leftPos = 0;
        this.topPos = 0;
    }

    // 1.20.1 的 renderBg 由 AbstractContainerScreen.render 直接调用；
    // 1.21.1 改成了 renderBackground -> renderBg 的链路，所以这里必须让
    // renderBackground 调到 renderBg，否则整个面板/槽位/文字都不绘制。
    // 同时不能调用原版的 renderTransparentBackground：它在缩放后的 pose 里
    // 执行，会把满屏模糊遮罩一起缩放，出现「遮罩只盖住一部分/错位」。
    // 全屏暗化改由 render() 在缩放前统一填充。
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        int vx = (int) ((mouseX - uiTx) / uiScale);
        int vy = (int) ((mouseY - uiTy) / uiScale);
        graphics.pose().pushPose();
        graphics.pose().translate(uiTx, uiTy, 0);
        graphics.pose().scale(uiScale, uiScale, 1f);
        super.render(graphics, vx, vy, partialTick);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double vx = (mouseX - uiTx) / uiScale;
        double vy = (mouseY - uiTy) / uiScale;
        // 点在面板空白处（不是槽位）时，不要把手里拿着的东西丢到地上。
        // 研究台中间的信息区很大，误点一下就把物品丢出去会很恼人。
        if (this.hoveredSlot(vx, vy) == null && !this.menu.getCarried().isEmpty()) {
            boolean inside = vx >= leftPos && vx < leftPos + imageWidth
                    && vy >= topPos && vy < topPos + imageHeight;
            if (inside) {
                return true;
            }
        }
        return super.mouseClicked(vx, vy, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased((mouseX - uiTx) / uiScale, (mouseY - uiTy) / uiScale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged((mouseX - uiTx) / uiScale, (mouseY - uiTy) / uiScale,
                button, dragX / uiScale, dragY / uiScale);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, ClientSettings.tint(PANEL));
        graphics.fill(x, y, x + imageWidth, y + 16, ClientSettings.tint(HEAD));
        border(graphics, x, y, imageWidth, imageHeight);
        // 标题只在 renderBg 里画一次；renderLabels 已清空，避免两处叠字
        graphics.drawString(this.font, this.title, x + 7, y + 4, TEXT, false);

        // 输入槽（按菜单坐标，自动换行）
        int slots = this.menu.stationSlots();
        for (int i = 0; i < slots; i++) {
            int sx = x + this.menu.slotX(i) - 1;
            int sy = y + this.menu.slotY(i) - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_BG);
            border(graphics, sx, sy, 18, 18);
        }

        // 空槽显示对应物品的虚影：每个位置固定一种物品，放什么一目了然
        for (int i = 0; i < slots; i++) {
            ResourceLocation wanted = this.menu.slotType(i);
            if (wanted == null || !this.menu.getSlot(i).getItem().isEmpty()) {
                continue;
            }
            ItemStack ghost = iconOf(wanted);
            int gx = x + this.menu.slotX(i);
            int gy = y + this.menu.slotY(i);
            // 真正的半透明虚影：渲染时把顶点 alpha 压到 0.32，而不是盖一层暗色块
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 0.32f);
            graphics.renderItem(ghost, gx, gy);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }

        // 玩家背包（槽位内容由菜单按 8 + col*18 绘制，背景左移 1 像素对齐原版）
        int invY = this.menu.playerInvY();
        graphics.drawString(this.font, this.playerInventoryTitle, x + 7, y + invY - 11, MUTED, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 7 + col * 18;
                int sy = y + invY - 1 + row * 18;
                graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_BG);
                border(graphics, sx, sy, 18, 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            int sx = x + 7 + col * 18;
            int sy = y + invY + 57;
            graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_BG);
            border(graphics, sx, sy, 18, 18);
        }

        int bottom = this.menu.slotsBottom();

        // 本轮消耗耐久条
        int barX = x + 7;
        int barW = imageWidth - 14;
        int barY = y + bottom + 2;
        int cooldown = this.menu.getData().get(0);
        int maxCooldown = this.menu.getData().get(1);
        graphics.fill(barX, barY, barX + barW, barY + 4, 0xFF0C0C10);
        if (maxCooldown > 0) {
            float remain = Math.max(0f, Math.min(1f, cooldown / (float) maxCooldown));
            graphics.fill(barX, barY, barX + (int) (barW * (1f - remain)), barY + 4, CYAN);
        }
        border(graphics, barX, barY, barW, 4);

        // 当前科技
        ResourceLocation currentId = ClientResearchState.queue().isEmpty() ? null : ClientResearchState.queue().get(0);
        Technology tech = currentId == null ? null : ClientResearchState.get(currentId);
        int techY = bottom + 10;
        if (tech == null) {
            graphics.drawString(this.font, Component.translatable("gui.sciencepacks.station.idle"),
                    x + 8, techY + 4, MUTED, false);
            return;
        }
        ItemStack icon = iconOf(tech.icon());
        graphics.renderItem(icon, x + 8, techY);
        graphics.drawString(this.font, tech.name(), x + 28, techY + 4, TEXT, false);

        int progressY = techY + 20;
        int barW2 = imageWidth - 16;
        graphics.fill(x + 8, progressY, x + 8 + barW2, progressY + 7, 0xFF0C0C10);
        int total = ClientResearchState.totalOf(tech);
        int done = ClientResearchState.progressOf(tech);
        if (total > 0) {
            float ratio = Math.min(1.0f, done / (float) total);
            graphics.fill(x + 8, progressY, x + 8 + (int) (barW2 * ratio), progressY + 7, GREEN);
        }
        border(graphics, x + 8, progressY, barW2, 7);
        String text = done + " / " + total;
        graphics.drawString(this.font, text, x + imageWidth - 8 - this.font.width(text),
                progressY + 9, MUTED, false);

        // 每轮消耗（按当前等级换算的倍率）+ 剩余总需求
        int level = ClientResearchState.levelOf(tech);
        int costY = progressY + 22;
        graphics.drawString(this.font, Component.translatable("gui.sciencepacks.station.cost"),
                x + 8, costY, MUTED, false);
        int cx = x + 46;
        for (int i = 0; i < tech.costs().size(); i++) {
            Technology.PackCost cost = tech.costs().get(i);
            int perRound = tech.packsForLevel(cost, level);
            if (perRound <= 0) {
                continue;
            }
            // 每轮按倍率消耗，这里显示倍率
            ItemStack stack = iconOf(cost.item());
            graphics.renderItem(stack, cx, costY - 4);
            graphics.renderItemDecorations(this.font, stack, cx, costY - 4);
            graphics.drawString(this.font, String.valueOf(perRound), cx + 18, costY, TEXT, false);
            cx += 34;
            if (cx > x + imageWidth - 34) {
                break;
            }
        }
        if (cx == x + 46) {
            graphics.drawString(this.font, Component.translatable("gui.sciencepacks.station.none"),
                    cx, costY, MUTED, false);
        }

        int remainY = costY + 18;
        graphics.drawString(this.font, Component.translatable("gui.sciencepacks.station.remaining"),
                x + 8, remainY, MUTED, false);
        int rx = x + 46;
        for (int i = 0; i < tech.costs().size(); i++) {
            Technology.PackCost cost = tech.costs().get(i);
            int left = Math.max(0, tech.packsForLevel(cost, level) * (total - done));
            ItemStack stack = iconOf(cost.item());
            graphics.renderItem(stack, rx, remainY - 4);
            graphics.renderItemDecorations(this.font, stack, rx, remainY - 4);
            graphics.drawString(this.font, String.valueOf(left), rx + 18, remainY,
                    left > 0 ? TEXT : GREEN, false);
            rx += 34;
            if (rx > x + imageWidth - 34) {
                break;
            }
        }
    }

    /** 自己算鼠标下的槽位：1.20.1 的 AbstractContainerScreen 没有公开的 getSlotAt。 */
    private Slot hoveredSlot(double vx, double vy) {
        for (Slot slot : this.menu.slots) {
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (vx >= sx && vx < sx + 16 && vy >= sy && vy < sy + 16) {
                return slot;
            }
        }
        return null;
    }

    private void border(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, BORDER);
        graphics.fill(x, y, x + 1, y + h, BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    private ItemStack iconOf(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            item = net.minecraft.world.item.Items.PAPER;
        }
        return new ItemStack(item);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题与“物品栏”都在 renderBg 里按自定义坐标绘制，这里保持空白，
        // 否则 AbstractContainerScreen 会再画一遍标题，导致左上角叠字。
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
