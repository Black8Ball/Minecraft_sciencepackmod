package com.sciencepacks.research;

import net.neoforged.fml.ModList;

import java.util.List;

/**
 * 模组条件：研究树与配方覆盖都可以用 {@code requiredMods} / {@code mods} 声明依赖。
 *
 * <p>写法：{@code "create"} 表示「装有 Create 时生效」，{@code "!create"} 表示「没有 Create 时生效」；
 * 列表里的条件是与关系，全部满足才生效。留空表示无条件。</p>
 *
 * <p>配合「有对应模组才启用研究树」的用法：给科技 JSON 加
 * {@code "requiredMods": ["create"]}，缺模组时这项研究既不出现在树里、也不参与配方锁定。</p>
 */
public final class ModConditions {

    private ModConditions() {
    }

    /** 判断单个条件是否满足（支持前置 {@code !} 取反）。 */
    public static boolean test(String spec) {
        if (spec == null) {
            return true;
        }
        String value = spec.trim();
        if (value.isEmpty()) {
            return true;
        }
        boolean negate = value.startsWith("!");
        String id = negate ? value.substring(1).trim() : value;
        if (id.isEmpty()) {
            return true;
        }
        boolean loaded = isLoaded(id);
        return negate != loaded;
    }

    /** 列表里的条件全部满足才返回 true。 */
    public static boolean allMatch(List<String> specs) {
        if (specs == null || specs.isEmpty()) {
            return true;
        }
        for (String spec : specs) {
            if (!test(spec)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLoaded(String id) {
        try {
            return ModList.get().isLoaded(id);
        } catch (Throwable ignored) {
            // 模组列表尚未就绪（极早期调用）时按「不存在」处理
            return false;
        }
    }
}
