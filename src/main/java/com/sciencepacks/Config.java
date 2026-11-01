package com.sciencepacks;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 全局配置（config/sciencepacks-common.toml）。
 *
 * <p>所有开关都在这里，修改后重启生效；科技内容本身由数据包 JSON 定义，
 * 可以用 /reload 热重载。</p>
 */
public final class Config {

    public static final ModConfigSpec SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config() {
    }

    /** 给注释加一个前导空格，Forge 生成的 toml 里显示为 "# 文本"。 */
    private static ModConfigSpec.Builder c(ModConfigSpec.Builder builder, String... lines) {
        String[] spaced = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            spaced[i] = " " + lines[i];
        }
        return builder.comment(spaced);
    }

    /** 配置项容器。 */
    public static final class Common {

        /* ---------------- 研究 ---------------- */

        /** 是否锁定合成台 / 背包合成。 */
        public final ModConfigSpec.BooleanValue lockCrafting;
        /** 是否锁定熔炉族（熔炉/高炉/烟熏炉/营火）。 */
        public final ModConfigSpec.BooleanValue lockSmelting;
        /** 是否锁定锻造台。 */
        public final ModConfigSpec.BooleanValue lockSmithing;
        /** 是否锁定切石机。 */
        public final ModConfigSpec.BooleanValue lockStonecutting;
        /** 是否锁定酿造台（材料配方未解锁时暂停酿造）。 */
        public final ModConfigSpec.BooleanValue lockBrewing;
        /** 研究台消耗一份材料的默认耗时（tick）。 */
        public final ModConfigSpec.IntValue ticksPerPack;
        /** 解锁时是否发消息。 */
        public final ModConfigSpec.BooleanValue announceUnlocks;
        /** 扫描触发物品的间隔（tick）。 */
        public final ModConfigSpec.IntValue triggerScanInterval;
        /** 是否在配方书里隐藏未解锁配方。 */
        public final ModConfigSpec.BooleanValue hideLockedRecipeBook;
        /** 带触发物品的科技是否自动解锁。 */
        public final ModConfigSpec.BooleanValue autoUnlockTriggered;
        /** 免费科技（无成本无触发）是否在前置满足时自动解锁。 */
        public final ModConfigSpec.BooleanValue autoUnlockFree;
        /** 队列操作（开始/取消/调序/清空）是否向组内所有成员播报。 */
        public final ModConfigSpec.BooleanValue announceQueue;
        /** 研究完成时播放的音效 id（空字符串 = 不播放）。 */
        public final ModConfigSpec.ConfigValue<String> completeSound;
        /** 研究完成时是否在右上角弹出提示框。 */
        public final ModConfigSpec.BooleanValue completeToast;

        /* ---------------- 研究台 ---------------- */

        /**
         * 研究台的固定槽位表：每个条目对应一个槽位，顺序就是界面里从左到右的顺序。
         *
         * <p>槽位数量不再单独配置，完全由这个列表的长度决定（最多 54 个）；
         * 每个槽位只接受它对应的那一种物品，并且一次只放一个。</p>
         */
        public final ModConfigSpec.ConfigValue<List<? extends String>> stationAllowedItems;

        /* ---------------- 多方块结构 ---------------- */

        /**
         * 是否锁定多方块结构（下界传送门、末地传送门、凋灵/傀儡搭建等）。
         *
         * <p>具体哪项研究解锁哪个结构，由科技 JSON 的 "structures" 字段决定，
         * 这里只控制总开关；没有科技声明的结构不受影响。</p>
         */
        public final ModConfigSpec.BooleanValue lockStructures;

        /* ---------------- 数值研究 ---------------- */

        /**
         * 方块放置数量上限的基础值，条目写法 {@code modid:block=数量}。
         *
         * <p>研究 JSON 里的 {@code place_limit} 效果会在这个基础上叠加；
         * 没写在这里、也没有研究的方块不限制。</p>
         */
        public final ModConfigSpec.ConfigValue<List<? extends String>> blockPlaceLimits;

        /* ---------------- 研究共享 ---------------- */

        /** personal / team / global。 */
        public final ModConfigSpec.ConfigValue<String> shareMode;
        /** team 模式下对用户名取队伍名的正则。 */
        public final ModConfigSpec.ConfigValue<String> teamPattern;
        /** 正则捕获组编号。 */
        public final ModConfigSpec.IntValue teamRegexGroup;

        Common(ModConfigSpec.Builder b) {
            c(b, "Science Packs 研究设置").push("research");
            lockCrafting = c(b, "锁定工作台与背包 2x2 的合成配方。")
                    .define("lockCrafting", true);
            lockSmelting = c(b, "锁定熔炉/高炉/烟熏炉/营火：输入配方未解锁时整个炉子暂停（不烧、不耗燃料），产物也取不走。")
                    .define("lockSmelting", true);
            lockSmithing = c(b, "锁定锻造台产物。")
                    .define("lockSmithing", true);
            lockStonecutting = c(b, "锁定切石机产物。")
                    .define("lockStonecutting", true);
            lockBrewing = c(b, "锁定酿造台：原料未解锁时整个酿造台暂停（不推进、不耗材料）。",
                            "只对玩家自己放置的酿造台生效，村庄/遗迹里的天然酿造台不拦。")
                    .define("lockBrewing", true);
            ticksPerPack = c(b, "研究台消耗一份材料的默认耗时（单位 tick，20 tick = 1 秒）。",
                            "科技 JSON 的 cost 条目可以用 \"ticks\" 单独覆盖。")
                    .defineInRange("ticksPerPack", 20, 1, 20 * 60);
            announceUnlocks = c(b, "科技解锁时给在线成员发提示。")
                    .define("announceUnlocks", true);
            triggerScanInterval = c(b, "扫描玩家背包里触发物品的间隔（tick）。")
                    .defineInRange("triggerScanInterval", 20, 1, 200);
            hideLockedRecipeBook = c(b, "未解锁的配方不在配方书中显示。")
                    .define("hideLockedRecipeBook", true);
            autoUnlockTriggered = c(b, "带 triggers 的科技，拿到物品后自动解锁。")
                    .define("autoUnlockTriggered", true);
            autoUnlockFree = c(b, "既没有 cost 也没有 triggers 的「免费科技」，前置满足后自动解锁。")
                    .define("autoUnlockFree", true);
            announceQueue = c(b, "开始研究、取消研究、调整顺序、清空队列时，向同组所有在线成员播报。")
                    .define("announceQueue", true);
            completeSound = c(b, "研究完成时播放的音效 id，留空则不播放。",
                            "默认 minecraft:entity.player.levelup（升级音效），可换成任意注册过的音效。")
                    .define("completeSound", "minecraft:entity.player.levelup");
            completeToast = c(b, "研究完成时在右上角弹出提示框。")
                    .define("completeToast", true);
            b.pop();

            c(b, "研究台设置").push("station");
            stationAllowedItems = c(b, "研究台的固定槽位表：一个条目 = 一个槽位，顺序就是界面从左到右的顺序。",
                            "槽位数量不单独配置，完全由这个列表的长度决定（上限 54）；超过 9 个时按每行 9 个换行。",
                            "每个槽位只接受对应的那一种物品，一次只放一个；空槽会显示该物品的虚影。",
                            "默认顺序：铁质 / 物流 / 红石 / 末影 / 军备 / 效能 科技包。")
                    .defineListAllowEmpty("stationAllowedItems", List.of(
                            "sciencepacks:pack_red",
                            "sciencepacks:pack_green",
                            "sciencepacks:pack_blue",
                            "sciencepacks:pack_purple",
                            "sciencepacks:pack_gray",
                            "sciencepacks:pack_yellow"), entry -> entry instanceof String);
            b.pop();

            c(b, "多方块结构锁定").push("structures");
            lockStructures = c(b, "未解锁对应研究时，禁止搭建/点燃/进入多方块结构。",
                            "支持的结构类型（写在科技 JSON 的 structures 里）：",
                            "  nether_portal 下界传送门（打火石/火焰弹点燃 + 穿过传送门）",
                            "  end_portal    末地传送门（插入末影之眼 + 进入末地）",
                            "  wither        凋灵（摆放灵魂沙与凋灵头颅）",
                            "  iron_golem    铁傀儡、snow_golem 雪傀儡")
                    .define("lockStructures", true);
            b.pop();

            c(b, "数值研究（挖掘速度 / 触及距离 / 方块放置上限）").push("limits");
            blockPlaceLimits = c(b, "方块放置数量上限的基础值，条目写法 modid:block=数量。",
                            "研究 JSON 里的 place_limit 效果会在这个基础上叠加（每级 +amount 个）。",
                            "例：minecraft:torch=32 表示每个玩家最多放 32 个火把，研究可提升上限。",
                            "没写在这里、也没有研究的方块不限制。")
                    .defineListAllowEmpty("blockPlaceLimits", List.of(), entry -> entry instanceof String);
            b.pop();

            c(b, "研究进度共享").push("sharing");
            shareMode = c(b, "personal = 每人独立",
                            "team     = 用户名匹配 teamPattern 的玩家共享一份进度",
                            "global   = 全服共享一份进度")
                    .define("shareMode", "personal");
            teamPattern = c(b, "team 模式下用于提取队伍名的正则，对玩家名做匹配。",
                            "取第 teamRegexGroup 个捕获组；匹配失败则整名作为队伍。",
                            "例：^(.+?)_  表示 alice_01 / alice_02 同属 alice。")
                    .define("teamPattern", "^(.+)$");
            teamRegexGroup = c(b, "teamPattern 使用的捕获组编号。")
                    .defineInRange("teamRegexGroup", 1, 0, 20);
            b.pop();
        }
    }
}
