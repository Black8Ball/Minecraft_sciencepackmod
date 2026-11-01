package com.sciencepacks.client;

import com.sciencepacks.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 模组配置界面：在「模组列表 → 科技瓶 → 配置」里打开。
 *
 * <p>三个分页：
 * <ul>
 *   <li>玩法：各种锁定开关、研究耗时、触发扫描间隔、共享模式；</li>
 *   <li>研究台：固定槽位表（一个条目 = 一个槽位，顺序即界面从左到右）；</li>
 *   <li>外观：研究树背景、面板不透明度、界面明暗度（只影响本机）。</li>
 * </ul>
 * 点「保存」会把改动写回配置文件并立即落盘。</p>
 */
@OnlyIn(Dist.CLIENT)
public class ModConfigScreen extends Screen {

    /** 每行高度。 */
    private static final int ROW_H = 22;
    /** 右侧控件的宽度。 */
    private static final int CONTROL_W = 150;

    private final Screen parent;

    /** 当前分页：0 = 玩法，1 = 研究台，2 = 外观。 */
    private int page;

    /** 待保存的开关行。 */
    private final List<BoolRow> boolRows = new ArrayList<>();
    /** 待保存的整数输入行。 */
    private final List<IntRow> intRows = new ArrayList<>();
    /** 待保存的字符串输入行。 */
    private final List<StrRow> strRows = new ArrayList<>();
    /** 待保存的循环切换行（共享模式）。 */
    private final List<CycleRow> cycleRows = new ArrayList<>();
    /** 待保存的外观滑块。 */
    private final List<ValueSlider> sliders = new ArrayList<>();
    /** 研究台槽位表输入框。 */
    private EditBox stationBox;
    /** 方块放置上限输入框（基础值，逗号分隔）。 */
    private EditBox placeLimitBox;
    /** 研究树背景开关按钮与它的临时值。 */
    private Button treeBgButton;
    private boolean treeBgTemp;

    /** 标签文字的左边界与控件坐标。 */
    private int labelX;
    private int controlX;
    private int rowsTop;

    public ModConfigScreen(Screen parent) {
        super(Component.translatable("gui.sciencepacks.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.labelX = 16;
        this.controlX = this.width / 2 + 10;
        this.rowsTop = 62;

        this.tabButtons();
        int buttonY = this.height - 32;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.sciencepacks.config.save"), b -> {
            apply();
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 110, buttonY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.sciencepacks.config.cancel"),
                        b -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 + 10, buttonY, 100, 20).build());

        buildPage();
    }

    /** 顶部三个分页按钮；当前页的按钮置灰。 */
    private void tabButtons() {
        String[] keys = {"gameplay", "station", "appearance"};
        for (int i = 0; i < keys.length; i++) {
            final int index = i;
            Button button = Button.builder(Component.translatable("gui.sciencepacks.config.tab." + keys[i]), b -> {
                this.page = index;
                rebuildWidgets();
            }).bounds(16 + i * 112, 32, 104, 20).build();
            button.active = i != this.page;
            this.addRenderableWidget(button);
        }
    }

    /** 构建当前分页的控件。 */
    private void buildPage() {
        boolRows.clear();
        intRows.clear();
        strRows.clear();
        cycleRows.clear();
        sliders.clear();
        stationBox = null;
        placeLimitBox = null;
        treeBgButton = null;
        if (page == 0) {
            buildGameplay();
        } else if (page == 1) {
            buildStation();
        } else {
            buildAppearance();
        }
    }

    /* ---------------- 玩法分页 ---------------- */

    private void buildGameplay() {
        int i = 0;
        i = toggle(i, "lockCrafting", Config.COMMON.lockCrafting);
        i = toggle(i, "lockSmelting", Config.COMMON.lockSmelting);
        i = toggle(i, "lockSmithing", Config.COMMON.lockSmithing);
        i = toggle(i, "lockStonecutting", Config.COMMON.lockStonecutting);
        i = toggle(i, "lockBrewing", Config.COMMON.lockBrewing);
        i = toggle(i, "lockStructures", Config.COMMON.lockStructures);
        i = toggle(i, "hideLockedRecipeBook", Config.COMMON.hideLockedRecipeBook);
        i = toggle(i, "autoUnlockTriggered", Config.COMMON.autoUnlockTriggered);
        i = toggle(i, "autoUnlockFree", Config.COMMON.autoUnlockFree);
        i = toggle(i, "announceUnlocks", Config.COMMON.announceUnlocks);
        i = intRow(i, "ticksPerPack", Config.COMMON.ticksPerPack, 1, 1200);
        i = intRow(i, "triggerScanInterval", Config.COMMON.triggerScanInterval, 1, 200);
        i = cycleRow(i, "shareMode", Config.COMMON.shareMode, List.of("personal", "team", "global"));
        i = strRow(i, "teamPattern", Config.COMMON.teamPattern);
        intRow(i, "teamRegexGroup", Config.COMMON.teamRegexGroup, 0, 20);
    }

    /* ---------------- 研究台分页 ---------------- */

    private void buildStation() {
        // 一个条目 = 一个槽位，用逗号分隔，顺序就是界面里从左到右的顺序
        stationBox = new EditBox(this.font, this.labelX, this.rowsTop + 24, this.width - this.labelX * 2, 20,
                Component.translatable("gui.sciencepacks.config.stationItems"));
        stationBox.setMaxLength(2048);
        stationBox.setValue(String.join(", ", Config.COMMON.stationAllowedItems.get()));
        this.addRenderableWidget(stationBox);

        // 方块放置上限基础值：一个条目 = 一种方块，写法 modid:block=数量
        placeLimitBox = new EditBox(this.font, this.labelX, this.rowsTop + 112, this.width - this.labelX * 2, 20,
                Component.translatable("gui.sciencepacks.config.placeLimits"));
        placeLimitBox.setMaxLength(2048);
        placeLimitBox.setValue(String.join(", ", Config.COMMON.blockPlaceLimits.get()));
        this.addRenderableWidget(placeLimitBox);
    }

    /* ---------------- 外观分页 ---------------- */

    private void buildAppearance() {
        int y = this.rowsTop;
        treeBgTemp = ClientSettings.treeTransparent();
        treeBgButton = Button.builder(treeBgLabel(), b -> {
            treeBgTemp = !treeBgTemp;
            b.setMessage(treeBgLabel());
        }).bounds(this.controlX, y, CONTROL_W, 20).build();
        this.addRenderableWidget(treeBgButton);

        ValueSlider alpha = new ValueSlider(this.controlX, y + ROW_H, CONTROL_W, 20, 0.25, 1.0,
                ClientSettings.panelAlpha(), "gui.sciencepacks.config.panelAlpha");
        ValueSlider bright = new ValueSlider(this.controlX, y + ROW_H * 2, CONTROL_W, 20, 0.4, 1.6,
                ClientSettings.brightness(), "gui.sciencepacks.config.brightness");
        sliders.add(alpha);
        sliders.add(bright);
        this.addRenderableWidget(alpha);
        this.addRenderableWidget(bright);
    }

    private Component treeBgLabel() {
        return Component.translatable(treeBgTemp
                ? "gui.sciencepacks.tree.bg.transparent"
                : "gui.sciencepacks.tree.bg.opaque");
    }

    /* ---------------- 控件工厂 ---------------- */

    private int toggle(int rowIndex, String key, ModConfigSpec.BooleanValue value) {
        BoolRow row = new BoolRow(value, value.get(),
                Component.translatable("gui.sciencepacks.config." + key), rowY(rowIndex));
        Button button = Button.builder(onOff(row.current), b -> {
            row.current = !row.current;
            b.setMessage(onOff(row.current));
        }).bounds(this.controlX, row.y, CONTROL_W, 20).build();
        row.button = button;
        boolRows.add(row);
        this.addRenderableWidget(button);
        return rowIndex + 1;
    }

    private Component onOff(boolean on) {
        return Component.translatable(on ? "gui.sciencepacks.config.on" : "gui.sciencepacks.config.off");
    }

    private int intRow(int rowIndex, String key, ModConfigSpec.IntValue value, int min, int max) {
        int y = rowY(rowIndex);
        EditBox box = new EditBox(this.font, this.controlX, y, CONTROL_W, 20,
                Component.translatable("gui.sciencepacks.config." + key));
        box.setMaxLength(8);
        box.setFilter(text -> text.isEmpty() || text.matches("\\d{1,6}"));
        box.setValue(String.valueOf(value.get()));
        this.addRenderableWidget(box);
        intRows.add(new IntRow(value, box, min, max,
                Component.translatable("gui.sciencepacks.config." + key), y));
        return rowIndex + 1;
    }

    private int strRow(int rowIndex, String key, ModConfigSpec.ConfigValue<String> value) {
        int y = rowY(rowIndex);
        EditBox box = new EditBox(this.font, this.controlX, y, CONTROL_W, 20,
                Component.translatable("gui.sciencepacks.config." + key));
        box.setMaxLength(256);
        box.setValue(value.get());
        this.addRenderableWidget(box);
        strRows.add(new StrRow(value, box, Component.translatable("gui.sciencepacks.config." + key), y));
        return rowIndex + 1;
    }

    private int cycleRow(int rowIndex, String key, ModConfigSpec.ConfigValue<String> value, List<String> options) {
        int y = rowY(rowIndex);
        int start = Math.max(0, options.indexOf(value.get()));
        CycleRow row = new CycleRow(value, options, start,
                Component.translatable("gui.sciencepacks.config." + key), y);
        Button button = Button.builder(Component.literal(options.get(start)), b -> {
            row.index = (row.index + 1) % row.options.size();
            b.setMessage(Component.literal(row.options.get(row.index)));
        }).bounds(this.controlX, y, CONTROL_W, 20).build();
        row.button = button;
        cycleRows.add(row);
        this.addRenderableWidget(button);
        return rowIndex + 1;
    }

    private int rowY(int rowIndex) {
        return this.rowsTop + rowIndex * ROW_H;
    }

    /* ---------------- 渲染 ---------------- */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, 0, 0, 0);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);

        for (BoolRow row : boolRows) {
            g.drawString(this.font, row.label, this.labelX, row.y + 6, 0xE6E6E6, false);
        }
        for (IntRow row : intRows) {
            g.drawString(this.font, row.label, this.labelX, row.y + 6, 0xE6E6E6, false);
        }
        for (StrRow row : strRows) {
            g.drawString(this.font, row.label, this.labelX, row.y + 6, 0xE6E6E6, false);
        }
        for (CycleRow row : cycleRows) {
            g.drawString(this.font, row.label, this.labelX, row.y + 6, 0xE6E6E6, false);
        }

        if (page == 1) {
            g.drawString(this.font, Component.translatable("gui.sciencepacks.config.stationItems"), this.labelX,
                    this.rowsTop + 8, 0xE6E6E6, false);
            int y = this.rowsTop + 50;
            for (Component line : wrapHint("gui.sciencepacks.config.stationHint")) {
                g.drawString(this.font, line, this.labelX, y, 0x9A9AA6, false);
                y += 12;
            }
            g.drawString(this.font, Component.translatable("gui.sciencepacks.config.placeLimits"), this.labelX,
                    this.rowsTop + 98, 0xE6E6E6, false);
            int py = this.rowsTop + 138;
            for (Component line : wrapHint("gui.sciencepacks.config.placeLimitsHint")) {
                g.drawString(this.font, line, this.labelX, py, 0x9A9AA6, false);
                py += 12;
            }
        } else if (page == 2) {
            g.drawString(this.font, Component.translatable("gui.sciencepacks.config.treeBg"), this.labelX,
                    this.rowsTop + 6, 0xE6E6E6, false);
            int y = this.rowsTop + ROW_H * 3 + 8;
            for (Component line : wrapHint("gui.sciencepacks.config.appearanceHint")) {
                g.drawString(this.font, line, this.labelX, y, 0x9A9AA6, false);
                y += 12;
            }
        }
    }

    /** 把一条多行提示按换行符拆成多行。 */
    private List<Component> wrapHint(String key) {
        String text = Component.translatable(key).getString();
        List<Component> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.add(Component.literal(line));
        }
        return lines;
    }

    /* ---------------- 保存 ---------------- */

    private void apply() {
        for (BoolRow row : boolRows) {
            row.value.set(row.current);
        }
        for (IntRow row : intRows) {
            int parsed;
            try {
                parsed = Integer.parseInt(row.box.getValue().trim());
            } catch (NumberFormatException e) {
                parsed = row.value.get();
            }
            row.box.setValue(String.valueOf(Math.max(row.min, Math.min(row.max, parsed))));
            row.value.set(Math.max(row.min, Math.min(row.max, parsed)));
        }
        for (StrRow row : strRows) {
            row.value.set(row.box.getValue());
        }
        for (CycleRow row : cycleRows) {
            row.value.set(row.options.get(row.index));
        }
        if (stationBox != null) {
            List<String> items = new ArrayList<>();
            for (String part : Arrays.asList(stationBox.getValue().split(","))) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    items.add(trimmed);
                }
            }
            if (!items.isEmpty()) {
                Config.COMMON.stationAllowedItems.set(items);
            }
        }
        if (placeLimitBox != null) {
            List<String> limits = new ArrayList<>();
            for (String part : placeLimitBox.getValue().split("[,;\\n]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    limits.add(trimmed);
                }
            }
            Config.COMMON.blockPlaceLimits.set(limits);
        }
        if (!sliders.isEmpty()) {
            ClientSettings.setPanelAlpha(sliders.get(0).realValue());
            ClientSettings.setBrightness(sliders.get(1).realValue());
        }
        if (treeBgButton != null) {
            ClientSettings.setTreeTransparent(treeBgTemp);
        }
        Config.SPEC.save();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /* ---------------- 内部数据结构 ---------------- */

    /** 布尔开关行。 */
    private static final class BoolRow {
        final ModConfigSpec.BooleanValue value;
        final Component label;
        final int y;
        Button button;
        boolean current;

        BoolRow(ModConfigSpec.BooleanValue value, boolean current, Component label, int y) {
            this.value = value;
            this.current = current;
            this.label = label;
            this.y = y;
        }
    }

    /** 整数输入行。 */
    private record IntRow(ModConfigSpec.IntValue value, EditBox box, int min, int max, Component label, int y) {
    }

    /** 字符串输入行。 */
    private record StrRow(ModConfigSpec.ConfigValue<String> value, EditBox box, Component label, int y) {
    }

    /** 循环切换行（下拉式按钮）。 */
    private static final class CycleRow {
        final ModConfigSpec.ConfigValue<String> value;
        final List<String> options;
        final Component label;
        final int y;
        Button button;
        int index;

        CycleRow(ModConfigSpec.ConfigValue<String> value, List<String> options, int index, Component label, int y) {
            this.value = value;
            this.options = options;
            this.index = index;
            this.label = label;
            this.y = y;
        }
    }

    /** 外观分页用的滑块。 */
    private final class ValueSlider extends AbstractSliderButton {

        private final double min;
        private final double max;
        private final String labelKey;

        ValueSlider(int x, int y, int w, int h, double min, double max, double current, String labelKey) {
            super(x, y, w, h, Component.empty(), (current - min) / (max - min));
            this.min = min;
            this.max = max;
            this.labelKey = labelKey;
            updateMessage();
        }

        /** 滑块代表的真实值。 */
        double realValue() {
            return min + (max - min) * this.value;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(labelKey)
                    .append(Component.literal("：" + String.format(Locale.ROOT, "%.2f", realValue()))));
        }

        @Override
        protected void applyValue() {
            updateMessage();
        }
    }
}
