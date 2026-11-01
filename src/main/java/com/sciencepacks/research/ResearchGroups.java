package com.sciencepacks.research;

import com.sciencepacks.Config;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 研究共享分组：根据用户名和配置决定一名玩家属于哪个研究组。
 * personal -> 每个玩家独立；team -> 按用户名正则分组；global -> 全服共享。
 */
public final class ResearchGroups {

    public static final String GLOBAL_KEY = "*";

    private static volatile Pattern cachedPattern;
    private static volatile String cachedPatternText = "";
    private static volatile int cachedGroup = -1;

    private ResearchGroups() {
    }

    public static String keyFor(ServerPlayer player) {
        String mode = Config.COMMON.shareMode.get().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "global" -> GLOBAL_KEY;
            case "team" -> teamKey(player.getGameProfile().getName());
            default -> "player:" + player.getUUID();
        };
    }

    public static String keyForName(String name) {
        return "player:" + name;
    }

    public static String teamKey(String playerName) {
        String regex = Config.COMMON.teamPattern.get();
        int group = Config.COMMON.teamRegexGroup.get();
        if (regex == null || regex.isBlank()) {
            return "team:" + playerName;
        }
        try {
            if (cachedPattern == null || !regex.equals(cachedPatternText) || group != cachedGroup) {
                cachedPattern = Pattern.compile(regex);
                cachedPatternText = regex;
                cachedGroup = group;
            }
            Matcher matcher = cachedPattern.matcher(playerName);
            if (matcher.find()) {
                if (group > 0 && group <= matcher.groupCount()) {
                    String value = matcher.group(group);
                    if (value != null && !value.isBlank()) {
                        return "team:" + value;
                    }
                }
                return "team:" + matcher.group();
            }
        } catch (Exception ignored) {
        }
        return "team:" + playerName;
    }
}
