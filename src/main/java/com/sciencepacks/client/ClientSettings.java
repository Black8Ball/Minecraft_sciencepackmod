package com.sciencepacks.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sciencepacks.SciencePacks;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端本地设置：只影响本机显示，不进服务端、不影响玩法逻辑。
 * <p>
 * 研究树背景是否透明、界面的不透明度与明暗度。
 * 设置保存在 config/sciencepacks-client.json，改动后立即写入，重启游戏后依然有效。
 */
public final class ClientSettings {

    /** 配置文件位置：<游戏目录>/config/sciencepacks-client.json */
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("sciencepacks-client.json");

    /** 研究树背景是否透明（true = 透明，能看见后面的世界；false = 不透明深色底板）。 */
    private static boolean treeTransparent = false;

    /** 面板不透明度：0.25 = 很透，1.0 = 完全不透明。 */
    private static double panelAlpha = 1.0;

    /** 界面明暗度：0.5 = 更暗，1.0 = 原色，1.5 = 更亮。 */
    private static double brightness = 1.0;

    /** 上次在研究树里选中的研究 id，下次打开界面时以它居中显示。 */
    private static String lastSelectedTech = "";

    /** 研究树是否处于「总览」模式（true = 显示整棵树，false = 只显示与选中研究相关的前后两级）。 */
    private static boolean treeOverview = false;

    /** 是否已经读过配置文件，避免每帧都读盘。 */
    private static boolean loaded = false;

    private ClientSettings() {
    }

    public static boolean treeTransparent() {
        load();
        return treeTransparent;
    }

    /** 切换并立即保存，返回切换后的值。 */
    public static boolean toggleTreeTransparent() {
        load();
        treeTransparent = !treeTransparent;
        save();
        return treeTransparent;
    }

    /** 直接设置研究树背景是否透明，并立即保存。 */
    public static void setTreeTransparent(boolean value) {
        load();
        treeTransparent = value;
        save();
    }

    public static double panelAlpha() {
        load();
        return panelAlpha;
    }

    public static double brightness() {
        load();
        return brightness;
    }

    /** 直接修改面板不透明度（0.25–1.0），越界自动夹紧。 */
    public static void setPanelAlpha(double value) {
        load();
        panelAlpha = clamp(value, 0.25, 1.0);
        save();
    }

    /** 直接修改界面明暗度（0.4–1.6），越界自动夹紧。 */
    public static void setBrightness(double value) {
        load();
        brightness = clamp(value, 0.4, 1.6);
        save();
    }

    /** 上次选中的研究 id（可能为空字符串）。 */
    public static String lastSelectedTech() {
        load();
        return lastSelectedTech;
    }

    /** 记住当前选中的研究，下次打开界面时以它居中。 */
    public static void setLastSelectedTech(String id) {
        load();
        lastSelectedTech = id == null ? "" : id;
        save();
    }

    /** 研究树「总览」模式开关。 */
    public static boolean treeOverview() {
        load();
        return treeOverview;
    }

    /** 切换研究树「总览」模式，并立即保存。 */
    public static void setTreeOverview(boolean value) {
        load();
        treeOverview = value;
        save();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 把一个 ARGB 颜色按当前的明暗度/不透明度调整：
     * RGB 乘以 brightness，alpha 乘以 panelAlpha。
     */
    public static int tint(int argb) {
        load();
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * panelAlpha);
        int r = (int) Math.round(((argb >>> 16) & 0xFF) * brightness);
        int g = (int) Math.round(((argb >>> 8) & 0xFF) * brightness);
        int b = (int) Math.round((argb & 0xFF) * brightness);
        return (clampInt(a) << 24) | (clampInt(r) << 16) | (clampInt(g) << 8) | clampInt(b);
    }

    private static int clampInt(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!Files.exists(FILE)) {
                return;
            }
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.has("treeTransparent")) {
                treeTransparent = json.get("treeTransparent").getAsBoolean();
            }
            if (json.has("panelAlpha")) {
                panelAlpha = clamp(json.get("panelAlpha").getAsDouble(), 0.25, 1.0);
            }
            if (json.has("brightness")) {
                brightness = clamp(json.get("brightness").getAsDouble(), 0.4, 1.6);
            }
            if (json.has("lastSelectedTech")) {
                lastSelectedTech = json.get("lastSelectedTech").getAsString();
            }
            if (json.has("treeOverview")) {
                treeOverview = json.get("treeOverview").getAsBoolean();
            }
        } catch (Exception e) {
            // 配置坏了就用默认值，不影响进游戏
            SciencePacks.LOGGER.warn("[sciencepacks] 读取客户端设置失败，使用默认值：{}", e.toString());
        }
    }

    private static void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("treeTransparent", treeTransparent);
            json.addProperty("panelAlpha", panelAlpha);
            json.addProperty("brightness", brightness);
            json.addProperty("lastSelectedTech", lastSelectedTech);
            json.addProperty("treeOverview", treeOverview);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SciencePacks.LOGGER.warn("[sciencepacks] 保存客户端设置失败：{}", e.toString());
        }
    }
}
