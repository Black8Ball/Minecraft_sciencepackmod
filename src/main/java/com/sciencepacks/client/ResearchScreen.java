package com.sciencepacks.client;

import com.sciencepacks.network.ActionPacket;
import com.sciencepacks.network.Network;
import com.sciencepacks.network.QueueReorderPacket;
import com.sciencepacks.research.Structures;
import com.sciencepacks.research.Technology;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factorio 风格研究界面。
 *
 * <p>布局按窗口尺寸动态计算并铺满；所有坐标在「虚拟单位」里，渲染统一缩放。
 * 1.20.1 的 enableScissor 不跟随 pose 缩放，所以裁剪坐标手动换算成屏幕像素。</p>
 */
@OnlyIn(Dist.CLIENT)
public class ResearchScreen extends Screen {

    private static final int BG = 0xF2101014;
    private static final int PANEL = 0xFF1E1E24;
    private static final int PANEL_HEAD = 0xFF26262E;
    private static final int BORDER = 0xFF3D3D48;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFF9A9AA6;
    private static final int ACCENT = 0xFFE8B44A;
    private static final int GREEN = 0xFF3E8E41;
    private static final int RED = 0xFF8E2B2B;
    /** 「前置在队列里、可以排入但还不会开始」的高亮色。 */
    private static final int YELLOW = 0xFFFFD24A;
    /** 研究树底板颜色（不透明模式）。 */
    private static final int TREE_BG = 0xFF141418;

    private static final int NODE_W = 64;
    private static final int NODE_H = 64;
    private static final int NODE_GAP_X = 72;
    private static final int NODE_GAP_Y = 96;

    private static final float BASE_H = 400f;
    private static final float MIN_VIRT_W = 700f;

    /** 面板底色：跟随客户端设置里的明暗度/不透明度。 */
    private static int panelColor() {
        return ClientSettings.tint(PANEL);
    }

    /** 面板标题栏颜色：跟随客户端设置里的明暗度/不透明度。 */
    private static int panelHeadColor() {
        return ClientSettings.tint(PANEL_HEAD);
    }

    private float scale = 1f;
    private float virtW = 700f;
    private float virtH = 400f;

    private int leftX;
    private int leftW;
    private int queueY;
    private int queueH;
    private int detailY;
    private int detailH;
    private int listY;
    private int listH;
    private int treeX;
    private int treeY;
    private int treeW;
    private int treeH;
    /** 左下角操作提示文字的 y 坐标（研究列表的底边据此推算）。 */
    private int hintY;

    private Technology selected;
    private String selectedId;
    private final StringBuilder searchText = new StringBuilder();
    private boolean searchFocused;

    /** 研究列表的滚动量，单位是像素（平滑滚动，不再按整行翻页）。 */
    private float listScrollPx;
    private int effectScroll;
    /** 是否正在拖动研究列表的滚动条。 */
    private boolean listScrollDrag;
    private double treePanX;
    private double treePanY;
    private float treeZoom = 1.0f;
    private boolean treeCentered;
    /**
     * 关联模式下的临时布局：科技 id → {列, 层}。
     *
     * <p>关联模式只显示所选研究附近的一小片，节点按「依赖层级」重新排：
     * 同一层级横向并排，后续研究横向展开在各自前置下方，避免全挤成一条竖线。
     * 这个布局只影响绘制，不会写回 JSON（编辑坐标请用编辑器）。</p>
     */
    private Map<ResourceLocation, int[]> relatedLayout;
    private boolean draggingTree;
    private double lastMouseX;
    private double lastMouseY;

    /** 队列拖拽：按下时的下标、当前插入位置、是否已经真正拖动。 */
    private int queueDragIndex = -1;
    private int queueDragTarget = -1;
    private boolean queueDragging;
    private double queueDragStartX;

    private final Map<ResourceLocation, String> searchCache = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<Effect>> effectCache = new LinkedHashMap<>();

    /** 效果栏里的一个图标：解锁物品 / 配方产物，或「多方块结构」（带加号角标）。 */
    private record Effect(ItemStack stack, String structure, ItemStack badge) {
        boolean isStructure() {
            return structure != null;
        }
    }

    public ResearchScreen() {
        super(Component.translatable("gui.sciencepacks.title"));
    }

    @Override
    protected void init() {
        ClientHooks.requestSync();
        treeCentered = false;
        // 1) 本次界面里已经选过的研究 2) 上次打开界面时选中的研究 3) 队列首项 4) 列表第一项
        if (selectedId == null) {
            String last = ClientSettings.lastSelectedTech();
            if (last != null && !last.isEmpty()) {
                selectedId = last;
            }
        }
        if (selectedId != null) {
            selected = ClientResearchState.get(ResourceLocation.tryParse(selectedId));
        }
        if (selected == null) {
            for (ResourceLocation queued : ClientResearchState.queue()) {
                Technology tech = ClientResearchState.get(queued);
                if (tech != null) {
                    selected = tech;
                    break;
                }
            }
        }
        if (selected == null && !ClientResearchState.techs().isEmpty()) {
            selected = ClientResearchState.techs().get(0);
        }
        selectedId = selected == null ? null : selected.id().toString();
    }

    private void updateLayout() {
        scale = Math.max(0.4f, Math.min(this.height / BASE_H, this.width / MIN_VIRT_W));
        virtW = this.width / scale;
        virtH = this.height / scale;

        int margin = 6;
        int gap = 4;

        // 左侧栏：队列贴着屏幕顶端，研究列表一直延伸到左下角提示文字上方几个像素
        leftX = margin;
        leftW = Math.round(Math.max(196f, Math.min(340f, virtW * 0.33f)));
        queueY = margin;
        // 队列只是「一排方块」，高度压到最小，把纵向空间让给研究列表
        queueH = Math.round(Math.max(56f, Math.min(84f, virtH * 0.16f)));
        queueBlockH = Math.max(26, queueH - 30);
        queueBlockW = Math.max(24, Math.round(queueBlockH * 0.78f));
        detailY = queueY + queueH + gap;
        detailH = Math.round(Math.max(118f, Math.min(186f, virtH * 0.36f)));
        listY = detailY + detailH + gap;
        hintY = Math.round(virtH) - 12;
        // 列表高度自动铺满剩余空间，至少保证能放下三排
        listH = Math.max(60, hintY - 8 - listY);

        // 右侧研究树：带边框的独立面板，铺满整个右侧，与左侧栏隔开几个像素
        treeX = leftX + leftW + margin;
        treeY = margin;
        treeW = Math.round(virtW - treeX - margin);
        treeH = Math.round(virtH - treeY - margin);
    }

    private double mx(double mouseX) {
        return mouseX / scale;
    }

    private double my(double mouseY) {
        return mouseY / scale;
    }

    private void scissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(Math.round(x1 * scale), Math.round(y1 * scale),
                Math.round(x2 * scale), Math.round(y2 * scale));
    }

    /* ---------------- 主渲染 ---------------- */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        g.fill(0, 0, this.width, this.height, BG);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);

        double vx = mx(mouseX);
        double vy = my(mouseY);

        renderPanel(g, leftX, queueY, leftW, queueH, Component.translatable("gui.sciencepacks.queue").getString());
        renderQueue(g, vx, vy);

        renderPanel(g, leftX, detailY, leftW, detailH, Component.translatable("gui.sciencepacks.detail").getString());
        renderDetail(g, vx, vy);

        renderPanel(g, leftX, listY, leftW, listH, Component.translatable("gui.sciencepacks.list").getString());
        renderSearch(g);
        renderList(g, vx, vy);

        renderTreePanel(g, vx, vy);
        renderTree(g, vx, vy);

        g.pose().popPose();
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, panelColor());
        g.fill(x, y, x + w, y + 18, panelHeadColor());
        border(g, x, y, w, h);
    }

    private void renderPanel(GuiGraphics g, int x, int y, int w, int h, String title) {
        panel(g, x, y, w, h);
        g.drawString(this.font, title, x + 6, y + 5, TEXT, false);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
        g.fill(x, y, x + 1, y + h, BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    /** 黄色描边：标记「前置还在队列里、暂时排得进去但还不会开始」的研究。 */
    private void yellowBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, YELLOW);
        g.fill(x, y + h - 1, x + w, y + h, YELLOW);
        g.fill(x, y, x + 1, y + h, YELLOW);
        g.fill(x + w - 1, y, x + w, y + h, YELLOW);
    }

    /* ---------------- 研究队列：竖长方形方块，点击切换详情，悬停显示名称/进度 ---------------- */

    private static final int QB_GAP = 5;
    /** 队列方块尺寸：随队列面板高度变化，宽度按比例。 */
    private int queueBlockW = 36;
    private int queueBlockH = 46;

    private void renderQueue(GuiGraphics g, double mouseX, double mouseY) {
        List<ResourceLocation> queue = ClientResearchState.queue();
        if (queue.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.sciencepacks.queue.empty"),
                    leftX + 6, queueY + 24, MUTED, false);
            return;
        }
        int by = queueY + 22;
        Technology hoveredTech = null;
        int hoveredPercent = 0;
        for (int i = 0; i < queue.size(); i++) {
            int bx = leftX + 6 + i * (queueBlockW + QB_GAP);
            if (bx + queueBlockW > leftX + leftW - 6) {
                g.drawString(this.font, "+" + (queue.size() - i), leftX + leftW - 16, queueY + 30, MUTED, false);
                break;
            }
            Technology tech = ClientResearchState.get(queue.get(i));
            boolean active = i == 0;
            g.fill(bx, by, bx + queueBlockW, by + queueBlockH, active ? 0xFF243024 : 0xFF222228);
            border(g, bx, by, queueBlockW, queueBlockH);
            if (tech == null) {
                continue;
            }
            int barX = bx + 3;
            int barW = queueBlockW - 6;
            int barY = by + queueBlockH - 13;
            // 图标跟着方块一起放大，水平居中，垂直落在进度条上方约 3/4 处
            renderQueueIcon(g, tech.icon(), bx, by, barY);
            // 无限研究：左上角显示当前等级
            int level = ClientResearchState.levelOf(tech);
            if (tech.infinite() && level > 0) {
                String lv = "Lv" + level;
                g.pose().pushPose();
                // z 抬到 200：否则等级文字会被 z=150 的物品图标盖住
                g.pose().translate(bx + 2f, by + 2f, 200f);
                g.pose().scale(0.7f, 0.7f, 0.7f);
                g.drawString(this.font, lv, 0, 0, ACCENT, true);
                g.pose().popPose();
            }

            g.fill(barX, barY, barX + barW, barY + 6, 0xFF0C0C10);
            // 每个方块都画自己的进度：调整队列顺序后进度依然正确显示
            int total = ClientResearchState.totalOf(tech);
            int percent = total > 0 ? Math.min(100, ClientResearchState.progressOf(tech) * 100 / total) : 0;
            if (percent > 0) {
                g.fill(barX, barY, barX + barW * percent / 100, barY + 6, GREEN);
            }
            border(g, barX, barY, barW, 6);
            if (ClientResearchState.waitingOnQueuedPrereq(tech)) {
                yellowBorder(g, bx, by, queueBlockW, queueBlockH);
            }
            if (tech == selected) {
                g.renderOutline(bx - 1, by - 1, queueBlockW + 2, queueBlockH + 2, ACCENT);
            }
            if (mouseX >= bx && mouseX <= bx + queueBlockW && mouseY >= by && mouseY <= by + queueBlockH) {
                hoveredTech = tech;
                hoveredPercent = percent;
            }
        }
        if (queueDragging && queueDragIndex >= 0 && queueDragIndex < queue.size()) {
            // 插入位置指示线
            int gapX = queueBlockX(Math.min(queueDragTarget, queue.size() - 1))
                    + (queueDragTarget > queueDragIndex ? queueBlockW + QB_GAP : 0) - QB_GAP / 2;
            g.fill(gapX - 1, by - 2, gapX + 1, by + queueBlockH + 2, ACCENT);
            // 跟随鼠标的浮动方块
            Technology dragged = ClientResearchState.get(queue.get(queueDragIndex));
            if (dragged != null) {
                int fx = (int) Math.round(mouseX) - queueBlockW / 2;
                int fy = (int) Math.round(mouseY) - queueBlockH / 2;
                g.fill(fx, fy, fx + queueBlockW, fy + queueBlockH, 0xE6303038);
                border(g, fx, fy, queueBlockW, queueBlockH);
                renderQueueIcon(g, dragged.icon(), fx, fy, fy + queueBlockH - 13);
            }
            return;
        }
        if (hoveredTech != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hoveredTech.name()));
            tip.add(Component.translatable("gui.sciencepacks.queue.progress", hoveredPercent)
                    .withStyle(ChatFormatting.GRAY));
            if (ClientResearchState.waitingOnQueuedPrereq(hoveredTech)) {
                tip.add(Component.translatable("gui.sciencepacks.queue.waiting")
                        .withStyle(ChatFormatting.YELLOW));
            }
            g.renderComponentTooltip(this.font, tip, (int) mouseX, (int) mouseY);
        }
    }

    /** 队列方块里的图标：尺寸随方块缩放，水平居中，落在进度条上方约 3/4 处。 */
    private void renderQueueIcon(GuiGraphics g, ResourceLocation icon, int bx, int by, int barY) {
        int areaW = queueBlockW - 6;
        int areaH = Math.max(8, (barY - 2) - (by + 3));
        int size = Math.max(10, Math.min(40, Math.min(areaW, areaH)));
        float s = size / 16f;
        float cy = by + 3 + Math.max(size / 2f, areaH * 0.72f);
        cy = Math.min(cy, barY - 2 - size / 2f);
        g.pose().pushPose();
        g.pose().translate(bx + queueBlockW / 2f, cy, 0f);
        g.pose().scale(s, s, 1f);
        g.renderItem(iconOf(icon), -8, -8);
        g.pose().popPose();
    }

    /** 队列里第 i 个方块左边的 X 坐标。 */
    private int queueBlockX(int index) {
        return leftX + 6 + index * (queueBlockW + QB_GAP);
    }

    /** 鼠标横坐标落在队列的哪个插入位置（0..size-1）。 */
    private int queueTargetIndex(double vx) {
        int size = ClientResearchState.queue().size();
        if (size <= 0) {
            return -1;
        }
        int idx = (int) Math.floor((vx - (leftX + 6)) / (double) (queueBlockW + QB_GAP) + 0.5);
        return Math.max(0, Math.min(size - 1, idx));
    }

    /* ---------------- 科技详情 ---------------- */

    private void renderDetail(GuiGraphics g, double mouseX, double mouseY) {
        if (selected == null) {
            g.drawString(this.font, Component.translatable("gui.sciencepacks.none"),
                    leftX + 6, detailY + 24, MUTED, false);
            return;
        }
        Technology tech = selected;
        int x = leftX + 6;
        int y = detailY + 22;
        boolean unlocked = ClientResearchState.isUnlocked(tech.id());
        boolean available = ClientResearchState.canResearch(tech);
        boolean waiting = ClientResearchState.waitingOnQueuedPrereq(tech);
        boolean triggerOnly = tech.isTriggerOnly();

        g.renderItem(iconOf(tech.icon()), x, y);
        g.drawString(this.font, tech.name(), x + 20, y, TEXT, false);
        int level = ClientResearchState.levelOf(tech);
        if (tech.infinite()) {
            // 无限研究：名字右侧显示当前等级
            Component lv = Component.translatable("gui.sciencepacks.level", level);
            g.drawString(this.font, lv, leftX + leftW - 6 - this.font.width(lv), y, ACCENT, false);
        }
        g.drawString(this.font, Component.translatable(unlocked ? "gui.sciencepacks.state.unlocked"
                        : triggerOnly ? "gui.sciencepacks.state.trigger"
                        : tech.infinite() && level > 0 ? "gui.sciencepacks.state.level"
                        : waiting ? "gui.sciencepacks.state.waiting"
                        : available ? "gui.sciencepacks.state.available" : "gui.sciencepacks.state.locked"),
                x + 20, y + 10,
                unlocked || (tech.infinite() && level > 0) ? GREEN
                        : waiting ? YELLOW : available ? ACCENT : RED, false);

        int descY = y + 22;
        List<FormattedCharSequence> lines = this.font.split(
                Component.literal(tech.description()).withStyle(ChatFormatting.GRAY), leftW - 12);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(this.font, lines.get(i), x, descY + i * 10, MUTED, false);
        }

        int costY = descY + 24;
        g.drawString(this.font, Component.translatable("gui.sciencepacks.cost"), x, costY, TEXT, false);
        int cx = x + 42;
        if (!tech.costs().isEmpty()) {
            int shown = 0;
            ItemStack hoveredCost = null;
            int hoveredNeed = 0;
            int hoveredLeft = 0;
            int hoveredPerRound = 0;
            int rounds = ClientResearchState.totalOf(tech);
            int doneRounds = ClientResearchState.progressOf(tech);
            for (int i = 0; i < tech.costs().size(); i++) {
                Technology.PackCost cost = tech.costs().get(i);
                // 无限研究当前等级每轮消耗会按倍率增长，这里按等级换算
                int perRound = tech.packsForLevel(cost, level);
                int left = Math.max(0, perRound * (rounds - doneRounds));
                ItemStack stack = iconOf(cost.item());
                g.renderItem(stack, cx, costY - 4);
                g.drawString(this.font, String.valueOf(left), cx + 17, costY, left > 0 ? TEXT : GREEN, false);
                if (mouseX >= cx && mouseX <= cx + 16 && mouseY >= costY - 4 && mouseY <= costY + 13) {
                    hoveredCost = stack;
                    hoveredNeed = perRound * rounds;
                    hoveredLeft = left;
                    hoveredPerRound = perRound;
                }
                cx += 32;
                if (++shown >= 5) {
                    break;
                }
            }
            // 右侧显示轮次进度（研究台按轮消耗材料）
            Component roundText = Component.translatable("gui.sciencepacks.cost.rounds", doneRounds, rounds);
            g.drawString(this.font, roundText, leftX + leftW - 6 - this.font.width(roundText), costY,
                    doneRounds >= rounds ? GREEN : MUTED, false);
            if (hoveredCost != null) {
                List<Component> tip = new ArrayList<>();
                tip.add(hoveredCost.getHoverName());
                tip.add(Component.translatable("gui.sciencepacks.cost.per_round_n", hoveredPerRound)
                        .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("gui.sciencepacks.cost.rounds", doneRounds, rounds)
                        .withStyle(ChatFormatting.GRAY));
                if (tech.infinite()) {
                    tip.add(Component.translatable("gui.sciencepacks.cost.level_now", level, hoveredPerRound)
                            .withStyle(ChatFormatting.AQUA));
                }
                tip.add(Component.translatable("gui.sciencepacks.cost.need", hoveredNeed)
                        .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("gui.sciencepacks.cost.left", hoveredLeft)
                        .withStyle(hoveredLeft > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(hoveredCost.getItem());
                if (key != null) {
                    tip.add(Component.literal(key.toString()).withStyle(ChatFormatting.DARK_GRAY));
                }
                g.renderComponentTooltip(this.font, tip, (int) Math.round(mouseX), (int) Math.round(mouseY));
            }
        } else if (!tech.triggers().isEmpty()) {
            int shown = 0;
            for (Technology.Trigger trig : tech.triggers()) {
                if (trig.isItem()) {
                    Item trigItem = BuiltInRegistries.ITEM.get(trig.id());
                    if (trigItem == null) {
                        continue;
                    }
                    g.renderItem(new ItemStack(trigItem), cx, costY - 4);
                    String itemName = new ItemStack(trigItem).getHoverName().getString() + " ×" + trig.count();
                    int tx = cx + 17;
                    String trimmed = this.font.plainSubstrByWidth(itemName, leftX + leftW - 6 - tx);
                    g.drawString(this.font, trimmed, tx, costY, GREEN, false);
                    cx += 17 + this.font.width(trimmed) + 4;
                } else {
                    String label = triggerTypeLabel(trig) + " " + triggerName(trig) + " ×" + trig.count();
                    String trimmed = this.font.plainSubstrByWidth(label, leftX + leftW - 6 - cx);
                    g.drawString(this.font, trimmed, cx, costY, GREEN, false);
                    cx += this.font.width(trimmed) + 6;
                }
                if (++shown >= 2 || cx > leftX + leftW - 20) {
                    break;
                }
            }
        } else {
            g.drawString(this.font, Component.translatable("gui.sciencepacks.cost.free"), cx, costY, GREEN, false);
        }

        int effectsHeader = costY + 18;
        g.drawString(this.font, Component.translatable("gui.sciencepacks.effects"),
                x, effectsHeader, TEXT, false);
        int btnY = detailY + detailH - 20;
        int effectsY = effectsHeader + 10;
        int effectsH = Math.max(18, btnY - effectsY - 6);
        drawScrollableEffects(g, tech, x, effectsY, leftW - 14, effectsH, mouseX, mouseY);

        List<ResourceLocation> queue = ClientResearchState.queue();
        boolean inQueue = queue.contains(tech.id());
        int qIndex = queue.indexOf(tech.id());
        Component label = Component.translatable(unlocked ? "gui.sciencepacks.button.unlocked"
                : triggerOnly ? "gui.sciencepacks.state.trigger"
                : inQueue ? "gui.sciencepacks.button.cancel"
                : tech.infinite() && level > 0 ? "gui.sciencepacks.button.levelup"
                : "gui.sciencepacks.button.start");
        drawButton(g, x, btnY, 88, 16, label, unlocked || triggerOnly, mouseX, mouseY);
        drawButton(g, x + 92, btnY, 52, 16,
                Component.translatable("gui.sciencepacks.button.queue"),
                unlocked || triggerOnly || inQueue, mouseX, mouseY);
        // 已在队列里且队列有多项时，右侧给两个小按钮：▲ 提前到队首、▼ 后移到队尾
        if (qIndex >= 0 && queue.size() > 1) {
            int bx1 = x + leftW - 12 - 34;
            int bx2 = bx1 + 18;
            boolean hoverFront = mouseX >= bx1 && mouseX <= bx1 + 16 && mouseY >= btnY && mouseY <= btnY + 16;
            boolean hoverBack = mouseX >= bx2 && mouseX <= bx2 + 16 && mouseY >= btnY && mouseY <= btnY + 16;
            drawButton(g, bx1, btnY, 16, 16, Component.literal("▲"), qIndex <= 0, mouseX, mouseY);
            drawButton(g, bx2, btnY, 16, 16, Component.literal("▼"), qIndex >= queue.size() - 1, mouseX, mouseY);
            if (hoverFront && qIndex > 0) {
                g.renderComponentTooltip(this.font,
                        List.of(Component.translatable("gui.sciencepacks.queue.front")),
                        (int) Math.round(mouseX), (int) Math.round(mouseY));
            } else if (hoverBack && qIndex < queue.size() - 1) {
                g.renderComponentTooltip(this.font,
                        List.of(Component.translatable("gui.sciencepacks.queue.back")),
                        (int) Math.round(mouseX), (int) Math.round(mouseY));
            }
        }
    }

    private void drawScrollableEffects(GuiGraphics g, Technology tech, int x, int y, int w, int h,
                                       double mouseX, double mouseY) {
        List<Effect> effects = effects(tech);
        int cell = 18;
        int cols = Math.max(1, w / cell);
        int rows = Math.max(1, h / cell);
        int maxScroll = Math.max(0, (effects.size() + cols - 1) / cols - rows);
        effectScroll = Math.max(0, Math.min(effectScroll, maxScroll));

        g.fill(x, y, x + w, y + h, 0xFF17171B);
        border(g, x, y, w, h);
        Effect hovered = null;
        scissor(g, x + 1, y + 1, x + w - 1, y + h - 1);
        for (int i = effectScroll * cols; i < effects.size(); i++) {
            int visual = i - effectScroll * cols;
            int col = visual % cols;
            int row = visual / cols;
            if (row >= rows) {
                break;
            }
            int ix = x + 2 + col * cell;
            int iy = y + 2 + row * cell;
            Effect effect = effects.get(i);
            g.renderItem(effect.stack(), ix, iy);
            if (effect.isStructure()) {
                // 结构类效果：大图标 + 右下角小角标（可在科技 JSON 里用第 4 段自定义）
                // 角标必须画在大图标之后，并把 z 抬到 200（物品默认 150），
                // 否则方块模型类的物品（泥土、铁块等）会把角标盖住。
                ItemStack badge = effect.badge();
                if (badge != null && !badge.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(ix + 9f, iy + 9f, 200f);
                    g.pose().scale(0.55f, 0.55f, 1.0f);
                    g.renderItem(badge, 0, 0);
                    g.pose().popPose();
                } else {
                    // 绿色加号也用 z 抬到 200，避免被物品（z=150）压住
                    g.pose().pushPose();
                    g.pose().translate(0f, 0f, 200f);
                    g.fill(ix + 11, iy + 4, ix + 13, iy + 14, 0xFF55D98A);
                    g.fill(ix + 7, iy + 8, ix + 17, iy + 10, 0xFF55D98A);
                    g.pose().popPose();
                }
            }
            if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16) {
                hovered = effect;
            }
        }
        g.disableScissor();
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            if (hovered.isStructure()) {
                tip.add(Structures.name(hovered.structure()));
                tip.add(Structures.effectLine(hovered.structure()));

            } else {
                tip.add(hovered.stack().getHoverName());
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(hovered.stack().getItem());
                if (key != null) {
                    tip.add(Component.literal(key.toString()).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            g.renderComponentTooltip(this.font, tip, (int) mouseX, (int) mouseY);
        }
        if (maxScroll > 0) {
            int barX = x + w - 5;
            g.fill(barX, y + 2, barX + 3, y + h - 2, 0xFF0C0C10);
            float ratio = rows / (float) (rows + maxScroll);
            int barH = Math.max(10, (int) ((h - 4) * ratio));
            int barY = y + 2 + (int) ((h - 4 - barH) * (effectScroll / (float) maxScroll));
            g.fill(barX, barY, barX + 3, barY + barH, ACCENT);
        }
    }

    /* ---------------- 搜索 + 科技列表 ---------------- */

    private void renderSearch(GuiGraphics g) {
        int x = leftX + 6;
        int y = listY + 20;
        int w = leftW - 12;
        int h = 16;
        g.fill(x, y, x + w, y + h, 0xFF141418);
        border(g, x, y, w, h);
        if (searchFocused) {
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, ACCENT);
        }
        String text = searchText.toString();
        boolean empty = text.isEmpty() && !searchFocused;
        String shown = empty ? Component.translatable("gui.sciencepacks.search").getString() : text;
        if (!empty) {
            shown = this.font.plainSubstrByWidth(shown, w - 8);
        }
        g.drawString(this.font, shown, x + 4, y + 4, empty ? MUTED : TEXT, false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caret = x + 4 + this.font.width(this.font.plainSubstrByWidth(text, w - 8));
            g.fill(caret, y + 3, caret + 1, y + h - 3, TEXT);
        }
        g.fill(x, y + h + 4, x + w, y + h + 5, BORDER);
    }

    private void renderList(GuiGraphics g, double mouseX, double mouseY) {
        List<Technology> techs = filtered();
        int cell = 32;
        int cols = Math.max(1, (leftW - 12) / cell);
        int areaY = listY + 46;
        int areaH = Math.max(cell, listH - 50);
        int maxScroll = listMaxScrollPx(techs.size(), cols, areaH);
        listScrollPx = Math.max(0f, Math.min(listScrollPx, maxScroll));
        int offset = Math.round(listScrollPx);

        scissor(g, leftX + 4, areaY - 2, leftX + leftW - 4, areaY + areaH + 2);
        for (int i = 0; i < techs.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int y = areaY + row * cell - offset;
            if (y + cell < areaY - 2 || y > areaY + areaH + 2) {
                continue;
            }
            int x = leftX + 6 + col * cell;
            Technology tech = techs.get(i);
            boolean unlocked = ClientResearchState.isUnlocked(tech.id());
            boolean available = ClientResearchState.canResearch(tech);
            g.fill(x, y, x + cell - 3, y + cell - 3,
                    unlocked ? 0xFF1F3A22 : available ? 0xFF3A2224 : 0xFF252529);
            border(g, x, y, cell - 3, cell - 3);
            g.renderItem(iconOf(tech.icon()), x + 5, y + 5);
            if (ClientResearchState.waitingOnQueuedPrereq(tech)) {
                yellowBorder(g, x, y, cell - 3, cell - 3);
            }
            if (ClientResearchState.queue().contains(tech.id())) {
                g.fill(x + cell - 9, y + 2, x + cell - 5, y + 6, ACCENT);
            }
            if (tech == selected) {
                g.renderOutline(x - 1, y - 1, cell - 1, cell - 1, ACCENT);
            }
        }
        g.disableScissor();
        if (techs.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.sciencepacks.list.empty"),
                    leftX + 6, areaY + 4, MUTED, false);
        }
        renderListScrollbar(g, techs.size(), cols, areaH);
    }

    /* ---------------- 研究列表滚动条 ---------------- */

    /** 列表可视区域的高度（像素）。 */
    private int listAreaH() {
        return Math.max(32, listH - 50);
    }

    /** 列表按像素计算的最大滚动量，0 表示不需要滚动。 */
    private int listMaxScrollPx(int count, int cols, int areaH) {
        int totalRows = (count + cols - 1) / cols;
        return Math.max(0, totalRows * 32 - areaH);
    }

    private int listMaxScrollPx() {
        int cols = Math.max(1, (leftW - 12) / 32);
        return listMaxScrollPx(filtered().size(), cols, listAreaH());
    }

    private int listScrollTrackX() {
        return leftX + leftW - 8;
    }

    private int listScrollTrackTop() {
        return listY + 42;
    }

    private int listScrollTrackH() {
        return Math.max(0, listH - 48);
    }

    /** 按鼠标位置直接跳到对应滚动量（拖动滚动条时用），是连续像素而不是整行。 */
    private void dragListScroll(double vy) {
        int maxScroll = listMaxScrollPx();
        int trackH = listScrollTrackH();
        if (maxScroll <= 0 || trackH <= 0) {
            return;
        }
        float ratio = (float) ((vy - listScrollTrackTop()) / trackH);
        listScrollPx = Math.max(0f, Math.min(1f, ratio)) * maxScroll;
    }

    private void renderListScrollbar(GuiGraphics g, int count, int cols, int areaH) {
        int maxScroll = listMaxScrollPx(count, cols, areaH);
        if (maxScroll <= 0) {
            return;
        }
        int x = listScrollTrackX();
        int top = listScrollTrackTop();
        int trackH = listScrollTrackH();
        if (trackH <= 6) {
            return;
        }
        g.fill(x, top, x + 4, top + trackH, 0xFF15151A);
        int totalRows = (count + cols - 1) / cols;
        int contentH = totalRows * 32;
        int thumbH = Math.max(10, Math.round(trackH * Math.min(1f, areaH / (float) contentH)));
        int thumbY = top + Math.round((trackH - thumbH) * (listScrollPx / (float) maxScroll));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, listScrollDrag ? ACCENT : 0xFF7A7A88);
    }

    /* ---------------- 科技树 ---------------- */

    /**
     * 研究树面板：标题栏 + 边框始终绘制；
     * 面板本体在「不透明」模式下铺深色底，「透明」模式下不铺，露出后面的世界。
     */
    private static final int VIEW_BTN_W = 44;
    private static final int VIEW_BTN_H = 13;

    private int viewBtnX() {
        return treeX + treeW - VIEW_BTN_W - 4;
    }

    private int viewBtnY() {
        return treeY + 3;
    }

    private void renderTreePanel(GuiGraphics g, double mouseX, double mouseY) {
        if (!ClientSettings.treeTransparent()) {
            g.fill(treeX, treeY, treeX + treeW, treeY + treeH, panelColor());
        }
        g.fill(treeX, treeY, treeX + treeW, treeY + 18, panelHeadColor());
        border(g, treeX, treeY, treeW, treeH);
        g.drawString(this.font, Component.translatable("gui.sciencepacks.tree").getString(),
                treeX + 6, treeY + 5, TEXT, false);

        // 右上角「总览 / 关联」切换按钮
        boolean overview = ClientSettings.treeOverview();
        int bx = viewBtnX();
        int by = viewBtnY();
        boolean hover = mouseX >= bx && mouseX <= bx + VIEW_BTN_W && mouseY >= by && mouseY <= by + VIEW_BTN_H;
        g.fill(bx, by, bx + VIEW_BTN_W, by + VIEW_BTN_H, hover ? 0xFF3C3C46 : 0xFF2C2C34);
        border(g, bx, by, VIEW_BTN_W, VIEW_BTN_H);
        String label = Component.translatable(overview
                ? "gui.sciencepacks.tree.view.overview" : "gui.sciencepacks.tree.view.related").getString();
        g.drawString(this.font, label, bx + (VIEW_BTN_W - this.font.width(label)) / 2, by + 3,
                overview ? ACCENT : TEXT, false);
        if (hover) {
            g.renderComponentTooltip(this.font,
                    List.of(Component.translatable("gui.sciencepacks.tree.view.tip")),
                    (int) mouseX, (int) mouseY);
        }
    }

    private void renderTree(GuiGraphics g, double mouseX, double mouseY) {
        int canvasX = treeX + 2;
        int canvasY = treeY + 20;
        int canvasW = treeW - 4;
        int canvasH = treeH - 24;
        // 背景透明时什么都不画，露出后面的世界；不透明时铺深色底板
        if (!ClientSettings.treeTransparent()) {
            g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, ClientSettings.tint(TREE_BG));
        }

        List<Technology> techs = treeTechs();
        java.util.Set<ResourceLocation> visible = new java.util.HashSet<>();
        for (Technology t : techs) {
            visible.add(t.id());
        }
        if (!treeCentered && !techs.isEmpty()) {
            treeZoom = fitZoom(techs, canvasW, canvasH);
            centerTree(selected, techs, canvasW, canvasH);
            treeCentered = true;
        }

        int ox = canvasX + (int) treePanX;
        int oy = canvasY + (int) treePanY;
        int node = Math.max(12, Math.round(NODE_W * treeZoom));
        int stepX = Math.max(8, Math.round((NODE_W + NODE_GAP_X) * treeZoom));
        int stepY = Math.max(8, Math.round((NODE_H + NODE_GAP_Y) * treeZoom));

        scissor(g, canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        for (Technology tech : techs) {
            for (ResourceLocation prereqId : tech.effectivePrerequisites()) {
                Technology parent = ClientResearchState.get(prereqId);
                if (parent != null && visible.contains(prereqId)) {
                    drawConnector(g, nodeX(parent, ox, stepX), nodeY(parent, oy, stepY),
                            nodeX(tech, ox, stepX), nodeY(tech, oy, stepY), node,
                            layoutX(parent), layoutY(parent), layoutX(tech), layoutY(tech));
                }
            }
        }
        int hovered = -1;
        for (int i = 0; i < techs.size(); i++) {
            Technology tech = techs.get(i);
            int sx = nodeX(tech, ox, stepX);
            int sy = nodeY(tech, oy, stepY);
            if (sx + node < canvasX || sy + node < canvasY || sx > canvasX + canvasW || sy > canvasY + canvasH) {
                continue;
            }
            boolean unlocked = ClientResearchState.isUnlocked(tech.id());
            boolean available = ClientResearchState.canResearch(tech);
            boolean waiting = ClientResearchState.waitingOnQueuedPrereq(tech);
            g.fill(sx, sy, sx + node, sy + node,
                    unlocked ? 0xFF2A4A2E : waiting ? 0xFF4A4422 : available ? 0xFF4A2A2C : 0xFF2C2C32);
            border(g, sx, sy, node, node);
            if (waiting) {
                yellowBorder(g, sx, sy, node, node);
            }
            if (tech == selected) {
                g.renderOutline(sx - 1, sy - 1, node + 2, node + 2, ACCENT);
            }
            // 图标占节点上方大半空间（和编辑器里的观感一致），下方留出名字的位置。
            float iconSize = Math.max(8f, node * 0.62f);
            g.pose().pushPose();
            g.pose().translate(sx + node / 2.0f, sy + node * 0.40f, 0);
            g.pose().scale(iconSize / 16.0f, iconSize / 16.0f, 1.0f);
            g.renderItem(iconOf(tech.icon()), -8, -8);
            g.pose().popPose();
            if (node >= 40) {
                String label = tech.name();
                float textScale = 0.55f + 0.3f * treeZoom;
                while (label.length() > 1 && this.font.width(label) * textScale > node - 4) {
                    label = label.substring(0, label.length() - 1);
                }
                g.pose().pushPose();
                g.pose().translate(sx + node / 2.0f, sy + node * 0.80f, 0);
                g.pose().scale(textScale, textScale, 1.0f);
                g.drawString(this.font, label, -this.font.width(label) / 2, 0, 0xFFE6E6E6, true);
                g.pose().popPose();
            }
            if (ClientResearchState.queue().contains(tech.id())) {
                g.fill(sx + node - 7, sy + 3, sx + node - 3, sy + 7, ACCENT);
            }
            if (mouseX >= sx && mouseX <= sx + node && mouseY >= sy && mouseY <= sy + node) {
                hovered = i;
            }
        }
        g.disableScissor();

        if (hovered >= 0) {
            Technology tech = techs.get(hovered);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(tech.name()).withStyle(ChatFormatting.WHITE));
            if (!tech.description().isEmpty()) {
                tooltip.add(Component.literal(tech.description()).withStyle(ChatFormatting.GRAY));
            }
            if (!tech.triggers().isEmpty()) {
                tooltip.add(Component.translatable("gui.sciencepacks.tooltip.trigger_item",
                        triggerNames(tech)).withStyle(ChatFormatting.GREEN));
            }
            g.renderComponentTooltip(this.font, tooltip, (int) mouseX, (int) mouseY);
        }
    }

    /**
     * 研究树当前该显示的节点：
     * 选了研究时，只显示它的前置（向上最多两级）和它的后续（向下最多两级）以及它自己；
     * 没有选中时显示整棵树。
     */
    private List<Technology> treeTechs() {
        List<Technology> all = new ArrayList<>();
        for (Technology tech : ClientResearchState.techs()) {
            if (ClientResearchState.isVisible(tech)) {
                all.add(tech);
            }
        }
        if (ClientSettings.treeOverview() || selected == null) {
            relatedLayout = null;
            return all;
        }
        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        ids.add(selected.id());
        // 向上：前置依赖，最多追两级
        java.util.Set<ResourceLocation> frontier = new java.util.LinkedHashSet<>();
        frontier.add(selected.id());
        for (int depth = 0; depth < 2 && !frontier.isEmpty(); depth++) {
            java.util.Set<ResourceLocation> next = new java.util.LinkedHashSet<>();
            for (ResourceLocation id : frontier) {
                Technology t = ClientResearchState.get(id);
                if (t == null) {
                    continue;
                }
                for (ResourceLocation p : t.effectivePrerequisites()) {
                    if (ids.add(p)) {
                        next.add(p);
                    }
                }
            }
            frontier = next;
        }
        // 向下：依赖当前研究的后续研究，最多两级
        frontier = new java.util.LinkedHashSet<>();
        frontier.add(selected.id());
        for (int depth = 0; depth < 2 && !frontier.isEmpty(); depth++) {
            java.util.Set<ResourceLocation> next = new java.util.LinkedHashSet<>();
            for (ResourceLocation id : frontier) {
                for (Technology t : all) {
                    if (t.effectivePrerequisites().contains(id) && ids.add(t.id())) {
                        next.add(t.id());
                    }
                }
            }
            frontier = next;
        }
        // 补一级「旁支前置」：范围内的研究如果有别的前置没显示出来，也把它拉进来，
        // 这样后续研究需要多个前置时，能看到它挂在哪些前置下面（只补一层，避免越拉越大）。
        java.util.Set<ResourceLocation> extra = new java.util.LinkedHashSet<>();
        for (ResourceLocation id : new java.util.ArrayList<>(ids)) {
            Technology t = ClientResearchState.get(id);
            if (t == null) {
                continue;
            }
            for (ResourceLocation p : t.effectivePrerequisites()) {
                if (!ids.contains(p) && ClientResearchState.get(p) != null) {
                    extra.add(p);
                }
            }
        }
        ids.addAll(extra);

        List<Technology> result = new ArrayList<>();
        for (Technology t : all) {
            if (ids.contains(t.id())) {
                result.add(t);
            }
        }
        layoutRelated(result);
        return result;
    }

    /**
     * 关联模式的层级布局：先按「到最上游的距离」分层，再把同层节点横向并排，
     * 并按父节点的平均列位置做居中/错位，避免所有节点竖着排成一列。
     */
    private void layoutRelated(List<Technology> techs) {
        Map<ResourceLocation, int[]> layout = new HashMap<>();
        Map<ResourceLocation, Integer> depth = new HashMap<>();
        java.util.Set<ResourceLocation> present = new java.util.HashSet<>();
        for (Technology tech : techs) {
            present.add(tech.id());
            layout.put(tech.id(), new int[]{0, 0});
        }
        // 迭代求最长路径深度（有环时靠 guard 兜底）
        for (int guard = 0; guard < 64; guard++) {
            boolean changed = false;
            for (Technology tech : techs) {
                int d = 0;
                for (ResourceLocation p : tech.effectivePrerequisites()) {
                    if (present.contains(p)) {
                        d = Math.max(d, depth.getOrDefault(p, 0) + 1);
                    }
                }
                if (depth.getOrDefault(tech.id(), 0) != d) {
                    depth.put(tech.id(), d);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        int maxDepth = 0;
        for (Technology tech : techs) {
            maxDepth = Math.max(maxDepth, depth.getOrDefault(tech.id(), 0));
        }
        Map<ResourceLocation, Integer> column = new HashMap<>();
        for (int level = 0; level <= maxDepth; level++) {
            List<Technology> row = new ArrayList<>();
            for (Technology tech : techs) {
                if (depth.getOrDefault(tech.id(), 0) == level) {
                    row.add(tech);
                }
            }
            // 先按「父列平均位置」排，让子节点尽量落在父节点正下方；没有父节点时按编辑坐标排。
            row.sort((a, b) -> {
                double ka = parentBias(a, present, column, depth);
                double kb = parentBias(b, present, column, depth);
                if (ka != kb) {
                    return Double.compare(ka, kb);
                }
                if (a.x() != b.x()) {
                    return Integer.compare(a.x(), b.x());
                }
                return a.id().toString().compareTo(b.id().toString());
            });
            for (int i = 0; i < row.size(); i++) {
                column.put(row.get(i).id(), i);
            }
            // 有父节点时整层平移，让本层中心对齐父节点中心
            if (level > 0) {
                double parentSum = 0;
                int parentCount = 0;
                for (Technology tech : row) {
                    for (ResourceLocation p : tech.effectivePrerequisites()) {
                        Integer pc = column.get(p);
                        if (pc != null) {
                            parentSum += pc;
                            parentCount++;
                        }
                    }
                }
                if (parentCount > 0) {
                    double parentMean = parentSum / parentCount;
                    double rowMean = (row.size() - 1) / 2.0;
                    int shift = (int) Math.round(parentMean - rowMean);
                    for (Technology tech : row) {
                        column.put(tech.id(), column.get(tech.id()) + shift);
                    }
                }
            }
        }
        // 归一化：最小列从 0 开始，避免整片布局被推到画布外
        int minCol = Integer.MAX_VALUE;
        for (int c : column.values()) {
            minCol = Math.min(minCol, c);
        }
        if (minCol == Integer.MAX_VALUE) {
            minCol = 0;
        }
        for (Technology tech : techs) {
            layout.put(tech.id(), new int[]{
                    column.getOrDefault(tech.id(), 0) - minCol,
                    depth.getOrDefault(tech.id(), 0)});
        }
        relatedLayout = layout;
    }

    /** 排序用：父节点列的均值；没有父节点时用编辑坐标的 x 作为参考。 */
    private double parentBias(Technology tech, java.util.Set<ResourceLocation> present,
                              Map<ResourceLocation, Integer> column, Map<ResourceLocation, Integer> depth) {
        double sum = 0;
        int count = 0;
        for (ResourceLocation p : tech.effectivePrerequisites()) {
            if (present.contains(p) && column.containsKey(p)) {
                sum += column.get(p);
                count++;
            }
        }
        return count > 0 ? sum / count : tech.x() * 0.001;
    }

    /** 布局里的格子坐标（关联模式下用临时布局，总览模式用编辑坐标）。 */
    private int layoutX(Technology tech) {
        int[] pos = relatedLayout == null ? null : relatedLayout.get(tech.id());
        return pos != null ? pos[0] : tech.x();
    }

    private int layoutY(Technology tech) {
        int[] pos = relatedLayout == null ? null : relatedLayout.get(tech.id());
        return pos != null ? pos[1] : tech.y();
    }

    /** 打开树时先缩放到「整棵树刚好铺满画布」，避免节点被挤出可视区。 */
    private float fitZoom(List<Technology> techs, int canvasW, int canvasH) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Technology tech : techs) {
            minX = Math.min(minX, layoutX(tech));
            maxX = Math.max(maxX, layoutX(tech));
            minY = Math.min(minY, layoutY(tech));
            maxY = Math.max(maxY, layoutY(tech));
        }
        float contentW = (maxX - minX) * (float) (NODE_W + NODE_GAP_X) + NODE_W;
        float contentH = (maxY - minY) * (float) (NODE_H + NODE_GAP_Y) + NODE_H;
        float fit = Math.min((canvasW - 24f) / contentW, (canvasH - 24f) / contentH);
        return Math.max(0.45f, Math.min(1.2f, fit));
    }

    private void centerTree(Technology focus, List<Technology> techs, int canvasW, int canvasH) {
        int node = Math.max(12, Math.round(NODE_W * treeZoom));
        int stepX = Math.max(8, Math.round((NODE_W + NODE_GAP_X) * treeZoom));
        int stepY = Math.max(8, Math.round((NODE_H + NODE_GAP_Y) * treeZoom));
        // 优先把「当前选中的研究」放到画布正中，没有选中时才按整体包围盒居中
        if (focus != null) {
            treePanX = canvasW / 2.0 - (layoutX(focus) * stepX + node / 2.0);
            treePanY = canvasH / 2.0 - (layoutY(focus) * stepY + node / 2.0);
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Technology tech : techs) {
            minX = Math.min(minX, layoutX(tech));
            maxX = Math.max(maxX, layoutX(tech));
            minY = Math.min(minY, layoutY(tech));
            maxY = Math.max(maxY, layoutY(tech));
        }
        int contentW = (maxX - minX) * stepX + node;
        int contentH = (maxY - minY) * stepY + node;
        treePanX = (canvasW - contentW) / 2.0 - minX * stepX;
        treePanY = (canvasH - contentH) / 2.0 - minY * stepY;
    }

    private int nodeX(Technology tech, int ox, int stepX) {
        return ox + layoutX(tech) * stepX;
    }

    private int nodeY(Technology tech, int oy, int stepY) {
        return oy + layoutY(tech) * stepY;
    }

    private void drawConnector(GuiGraphics g, int fx, int fy, int tx, int ty, int node,
                               int gx1, int gy1, int gx2, int gy2) {
        int color = 0xFF4C4C58;
        // 线宽跟着缩放走：标准缩放下是 1px 细线，放大后才逐渐变粗，
        // 缩小到极限也保留 1px，保证「看得出谁连着谁」。
        int thickness = Math.max(1, Math.round(treeZoom));
        int x1 = fx + node / 2;
        int y1 = fy + node;
        int x2 = tx + node / 2;
        int y2 = ty;
        // 同一列（上下相邻）直接竖线连下来，不要拐弯
        if (x1 == x2) {
            g.fill(x1, y1, x1 + thickness, y2, color);
            return;
        }
        // 两条横线之间按「节点网格坐标」错开车道：网格坐标不随平移/缩放变化，
        // 拖动研究树时横折线不会左右抖动（曾用屏幕坐标参与计算，导致连线诡异抖动）。
        int midY = (y1 + y2) / 2;
        int lane = Math.floorMod(gx1 * 31 + gy1 * 17 + gx2 * 7 + gy2 * 11, 9) - 4;
        midY += lane;
        int lo = Math.min(y1, y2) + 2;
        int hi = Math.max(y1, y2) - 2;
        if (lo <= hi) {
            midY = Math.max(lo, Math.min(hi, midY));
        }
        g.fill(x1, y1, x1 + thickness, midY + thickness, color);
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        g.fill(left, midY, right + thickness, midY + thickness, color);
        g.fill(x2, midY, x2 + thickness, y2 + thickness, color);
    }

    /* ---------------- 交互 ---------------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double vx = mx(mouseX);
        double vy = my(mouseY);

        int sx = leftX + 6;
        int sy = listY + 20;
        if (vx >= sx && vx <= sx + leftW - 12 && vy >= sy && vy <= sy + 16) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        // 研究树标题栏右上角的「总览 / 关联」切换
        if (vx >= viewBtnX() && vx <= viewBtnX() + VIEW_BTN_W
                && vy >= viewBtnY() && vy <= viewBtnY() + VIEW_BTN_H) {
            ClientSettings.setTreeOverview(!ClientSettings.treeOverview());
            treeCentered = false;
            return true;
        }

        // 点击队列方块：切换到对应科技
        List<ResourceLocation> queue = ClientResearchState.queue();
        int qby = queueY + 22;
        for (int i = 0; i < queue.size(); i++) {
            int bx = leftX + 6 + i * (queueBlockW + QB_GAP);
            if (bx + queueBlockW > leftX + leftW - 6) {
                break;
            }
            if (vx >= bx && vx <= bx + queueBlockW && vy >= qby && vy <= qby + queueBlockH) {
                Technology tech = ClientResearchState.get(queue.get(i));
                if (tech != null) {
                    select(tech);
                }
                // 记录拖拽起点，松开时若拖到别的位置就提交新顺序
                if (button == 0) {
                    queueDragIndex = i;
                    queueDragTarget = i;
                    queueDragging = false;
                    queueDragStartX = vx;
                }
                return true;
            }
        }

        if (selected != null) {
            int by = detailY + detailH - 20;
            int x0 = leftX + 6;
            if (vy >= by && vy <= by + 16) {
                if (vx >= x0 && vx <= x0 + 88) {
                    boolean unlocked = ClientResearchState.isUnlocked(selected.id());
                    boolean inQueue = ClientResearchState.queue().contains(selected.id());
                    if (!unlocked && !selected.isTriggerOnly()) {
                        Network.toServer(new ActionPacket(inQueue ? ActionPacket.STOP : ActionPacket.START,
                                selected.id().toString()));
                    }
                    return true;
                }
                if (vx >= x0 + 92 && vx <= x0 + 144) {
                    boolean unlocked = ClientResearchState.isUnlocked(selected.id());
                    boolean inQueue = ClientResearchState.queue().contains(selected.id());
                    if (!unlocked && !inQueue && !selected.isTriggerOnly()) {
                        Network.toServer(new ActionPacket(ActionPacket.START, selected.id().toString()));
                    }
                    return true;
                }
                List<ResourceLocation> q = ClientResearchState.queue();
                int qi = q.indexOf(selected.id());
                if (qi >= 0 && q.size() > 1) {
                    int bx1 = x0 + leftW - 12 - 34;
                    int bx2 = bx1 + 18;
                    if (vx >= bx1 && vx <= bx1 + 16) {
                        if (qi > 0) {
                            moveInQueueLocal(qi, 0);
                        }
                        return true;
                    }
                    if (vx >= bx2 && vx <= bx2 + 16) {
                        if (qi < q.size() - 1) {
                            moveInQueueLocal(qi, q.size() - 1);
                        }
                        return true;
                    }
                }
            }
        }

        List<Technology> techs = filtered();
        int cell = 32;
        int cols = Math.max(1, (leftW - 12) / cell);
        int areaY = listY + 46;
        int areaH = listAreaH();
        int offset = Math.round(listScrollPx);
        for (int i = 0; i < techs.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int y = areaY + row * cell - offset;
            if (y + cell < areaY || y > areaY + areaH) {
                continue;
            }
            int x = leftX + 6 + col * cell;
            if (vx >= x && vx <= x + cell - 3 && vy >= y && vy <= y + cell - 3) {
                select(techs.get(i));
                return true;
            }
        }

        // 研究列表右侧的滚动条：点一下跳转，按住可拖动
        if (listMaxScrollPx() > 0) {
            int trackX = listScrollTrackX();
            int trackTop = listScrollTrackTop();
            if (vx >= trackX - 2 && vx <= trackX + 6 && vy >= trackTop && vy <= trackTop + listScrollTrackH()) {
                listScrollDrag = true;
                dragListScroll(vy);
                return true;
            }
        }

        int canvasX = treeX + 2;
        int canvasY = treeY + 20;
        int canvasW = treeW - 4;
        int canvasH = treeH - 24;
        if (vx >= canvasX && vx <= canvasX + canvasW && vy >= canvasY && vy <= canvasY + canvasH) {
            int ox = canvasX + (int) treePanX;
            int oy = canvasY + (int) treePanY;
            int node = Math.max(12, Math.round(NODE_W * treeZoom));
            int stepX = Math.max(8, Math.round((NODE_W + NODE_GAP_X) * treeZoom));
            int stepY = Math.max(8, Math.round((NODE_H + NODE_GAP_Y) * treeZoom));
            for (Technology tech : treeTechs()) {
                int px = nodeX(tech, ox, stepX);
                int py = nodeY(tech, oy, stepY);
                if (vx >= px && vx <= px + node && vy >= py && vy <= py + node) {
                    select(tech);
                    return true;
                }
            }
            draggingTree = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void select(Technology tech) {
        selected = tech;
        selectedId = tech.id().toString();
        // 记住这次选中的研究，下次打开界面时以它居中
        ClientSettings.setLastSelectedTech(selectedId);
        effectScroll = 0;
        // 选中新研究后，研究树重新按「关联两级」居中显示
        treeCentered = false;
    }

    /** 队列内移动：先本地重排让界面立即响应，再把新顺序发给服务端。 */
    private void moveInQueueLocal(int from, int to) {
        List<ResourceLocation> queue = ClientResearchState.queue();
        if (from < 0 || from >= queue.size() || to < 0 || to >= queue.size() || from == to) {
            return;
        }
        ResourceLocation moved = queue.remove(from);
        queue.add(to, moved);
        Network.toServer(new QueueReorderPacket(from, to));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (queueDragIndex >= 0) {
            double vx = mx(mouseX);
            if (!queueDragging && Math.abs(vx - queueDragStartX) > 3) {
                queueDragging = true;
            }
            if (queueDragging) {
                queueDragTarget = queueTargetIndex(vx);
                return true;
            }
        }
        if (draggingTree) {
            treePanX += mouseX - lastMouseX;
            treePanY += mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        if (listScrollDrag) {
            dragListScroll(my(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (queueDragIndex >= 0) {
            List<ResourceLocation> queue = ClientResearchState.queue();
            if (queueDragging && queueDragTarget >= 0 && queueDragTarget != queueDragIndex
                    && queueDragIndex < queue.size()) {
                Network.toServer(new QueueReorderPacket(queueDragIndex, queueDragTarget));
                // 本地先重排一次，视觉上立即生效；服务端同步会再覆盖成权威顺序
                ResourceLocation moved = queue.remove(queueDragIndex);
                queue.add(Math.max(0, Math.min(queueDragTarget, queue.size())), moved);
            }
            queueDragIndex = -1;
            queueDragTarget = -1;
            queueDragging = false;
        }
        draggingTree = false;
        listScrollDrag = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        double vx = mx(mouseX);
        double vy = my(mouseY);
        int canvasX = treeX + 2;
        int canvasY = treeY + 20;
        int canvasW = treeW - 4;
        int canvasH = treeH - 24;
        if (vx >= canvasX && vx <= canvasX + canvasW && vy >= canvasY && vy <= canvasY + canvasH) {
            double worldX = (vx - canvasX - treePanX) / treeZoom;
            double worldY = (vy - canvasY - treePanY) / treeZoom;
            treeZoom = Math.max(0.4f, Math.min(2.2f, treeZoom + (float) delta * 0.1f));
            treePanX = vx - canvasX - worldX * treeZoom;
            treePanY = vy - canvasY - worldY * treeZoom;
            return true;
        }
        if (vx >= leftX && vx <= leftX + leftW) {
            if (vy >= listY + 40 && vy <= listY + listH) {
                int maxScroll = listMaxScrollPx();
                listScrollPx = Math.max(0f, Math.min(maxScroll, listScrollPx - (float) delta * 24f));
                return true;
            }
            if (vy >= detailY + 100 && vy <= detailY + detailH) {
                effectScroll -= (int) Math.signum(delta);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (searchText.length() > 0) {
                    searchText.deleteCharAt(searchText.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && codePoint >= ' ' && codePoint != 127) {
            if (searchText.length() < 40) {
                searchText.append(codePoint);
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label, boolean disabled,
                            double mouseX, double mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int color = disabled ? 0xFF2A2A30 : hovered ? 0xFF45454F : 0xFF35353D;
        g.fill(x, y, x + w, y + h, color);
        border(g, x, y, w, h);
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2,
                disabled ? MUTED : TEXT, false);
    }
    /* ---------------- 数据工具 ---------------- */

    private List<Technology> filtered() {
        String needle = searchText.toString().trim().toLowerCase();
        List<Technology> result = new ArrayList<>();
        for (Technology tech : ClientResearchState.techs()) {
            if (!ClientResearchState.isVisible(tech)) {
                continue;
            }
            if (needle.isEmpty() || searchable(tech).contains(needle)) {
                result.add(tech);
            }
        }
        result.sort(Comparator.comparing((Technology t) -> !ClientResearchState.canResearch(t))
                .thenComparing(t -> ClientResearchState.isUnlocked(t.id()))
                .thenComparing(Technology::id));
        return result;
    }

    private String searchable(Technology tech) {
        return searchCache.computeIfAbsent(tech.id(), id -> {
            StringBuilder sb = new StringBuilder();
            sb.append(tech.name().toLowerCase()).append(' ').append(id.toString()).append(' ');
            sb.append(tech.description().toLowerCase()).append(' ');
            for (ResourceLocation unlock : tech.unlocks()) {
                sb.append(unlock).append(' ');
                if (this.minecraft != null && this.minecraft.level != null) {
                    var recipe = this.minecraft.level.getRecipeManager().byKey(unlock);
                    if (recipe.isPresent()) {
                        try {
                            ItemStack out = recipe.get().value().getResultItem(this.minecraft.level.registryAccess());
                            if (!out.isEmpty()) {
                                sb.append(out.getHoverName().getString().toLowerCase()).append(' ');
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            for (Technology.Trigger trig : tech.triggers()) {
                sb.append(trig.id()).append(' ').append(trig.type()).append(' ');
                sb.append(triggerName(trig).toLowerCase()).append(' ');
            }
            return sb.toString();
        });
    }

    /** 触发条件的类型名（「挖掘」「击败」等），物品持有条件不显示前缀。 */
    private String triggerTypeLabel(Technology.Trigger trigger) {
        return switch (trigger.type()) {
            case Technology.Trigger.BLOCK_BREAK -> Component.translatable("gui.sciencepacks.trigger.block_break").getString();
            case Technology.Trigger.KILL -> Component.translatable("gui.sciencepacks.trigger.kill").getString();
            case Technology.Trigger.CRAFT -> Component.translatable("gui.sciencepacks.trigger.craft").getString();
            case Technology.Trigger.SMELT -> Component.translatable("gui.sciencepacks.trigger.smelt").getString();
            case Technology.Trigger.PICKUP -> Component.translatable("gui.sciencepacks.trigger.pickup").getString();
            case Technology.Trigger.DIMENSION -> Component.translatable("gui.sciencepacks.trigger.dimension").getString();
            default -> "";
        };
    }

    /** 触发目标的名字：物品/方块/生物/维度。 */
    private String triggerName(Technology.Trigger trigger) {
        if (trigger.isItem()) {
            Item item = BuiltInRegistries.ITEM.get(trigger.id());
            return item == null ? trigger.id().toString() : new ItemStack(item).getHoverName().getString();
        }
        if (Technology.Trigger.BLOCK_BREAK.equals(trigger.type())) {
            var block = BuiltInRegistries.BLOCK.get(trigger.id());
            return block == null ? trigger.id().toString() : block.getName().getString();
        }
        if (Technology.Trigger.KILL.equals(trigger.type())) {
            var type = BuiltInRegistries.ENTITY_TYPE.get(trigger.id());
            return type == null ? trigger.id().toString() : type.getDescription().getString();
        }
        if (Technology.Trigger.DIMENSION.equals(trigger.type())) {
            return trigger.id().getPath().replace('_', ' ');
        }
        Item item = BuiltInRegistries.ITEM.get(trigger.id());
        return item == null ? trigger.id().toString() : new ItemStack(item).getHoverName().getString();
    }

    private String triggerNames(Technology tech) {
        StringBuilder sb = new StringBuilder();
        for (Technology.Trigger trig : tech.triggers()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            String prefix = triggerTypeLabel(trig);
            if (!prefix.isEmpty()) {
                sb.append(prefix).append(' ');
            }
            sb.append(triggerName(trig)).append(" ×").append(trig.count());
        }
        return sb.toString();
    }

    private ItemStack iconOf(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            item = net.minecraft.world.item.Items.PAPER;
        }
        return new ItemStack(item);
    }

    /** 数值型研究效果的展示图标：悬停名直接写成「每级挖掘速度 +15%」之类。 */
    private ItemStack effectStack(Technology.Effect effect) {
        ItemStack stack;
        Component name;
        switch (effect.type()) {
            case Technology.Effect.MINING_SPEED -> {
                stack = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
                name = Component.translatable("effect.sciencepacks.mining_speed", trimNumber(effect.amount() * 100));
            }
            case Technology.Effect.REACH -> {
                stack = new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL);
                name = Component.translatable("effect.sciencepacks.reach", trimNumber(effect.amount()));
            }
            case Technology.Effect.PLACE_LIMIT -> {
                Item icon = effect.block() == null ? null : BuiltInRegistries.ITEM.get(effect.block());
                stack = new ItemStack(icon == null ? net.minecraft.world.item.Items.BARRIER : icon);
                String blockName = stack.getHoverName().getString();
                name = Component.translatable("effect.sciencepacks.place_limit", blockName, trimNumber(effect.amount()));
            }
            default -> {
                stack = new ItemStack(net.minecraft.world.item.Items.BARRIER);
                name = Component.literal(effect.type());
            }
        }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static String trimNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private List<Effect> effects(Technology tech) {
        return effectCache.computeIfAbsent(tech.id(), id -> {
            Map<String, Effect> map = new LinkedHashMap<>();
            for (Technology.Trigger trig : tech.triggers()) {
                Item trigItem = BuiltInRegistries.ITEM.get(trig.id());
                if (trigItem != null) {
                    map.putIfAbsent("i:" + trig.id(), new Effect(new ItemStack(trigItem), null, null));
                }
            }
            for (ResourceLocation unlock : tech.unlocks()) {
                if (this.minecraft != null && this.minecraft.level != null) {
                    var recipe = this.minecraft.level.getRecipeManager().byKey(unlock);
                    if (recipe.isPresent()) {
                        try {
                            ItemStack out = recipe.get().value().getResultItem(this.minecraft.level.registryAccess());
                            if (!out.isEmpty()) {
                                map.putIfAbsent("r:" + unlock, new Effect(out, null, null));
                                continue;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                Item item = BuiltInRegistries.ITEM.get(unlock);
                if (item != null) {
                    map.putIfAbsent("i:" + unlock, new Effect(new ItemStack(item), null, null));
                }
            }
            // 多方块结构：图标 + 绿色加号，悬停显示「允许进入下界」之类的说明
            for (String entry : tech.structures()) {
                map.putIfAbsent("s:" + Structures.rawKey(entry),
                        new Effect(Structures.icon(entry), entry, Structures.badge(entry)));
            }
            // 数值型效果：每级挖掘速度 / 触及距离 / 方块放置上限
            for (Technology.Effect effect : tech.effects()) {
                String block = effect.block() == null ? "" : effect.block().toString();
                map.putIfAbsent("e:" + effect.type() + ":" + block, new Effect(effectStack(effect), null, null));
            }
            return new ArrayList<>(map.values());
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
