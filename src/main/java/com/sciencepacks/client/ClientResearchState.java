package com.sciencepacks.client;

import com.sciencepacks.research.Technology;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 客户端缓存：科技定义 + 当前研究组状态。 */
@OnlyIn(Dist.CLIENT)
public final class ClientResearchState {

    private static List<Technology> ordered = List.of();
    private static Map<ResourceLocation, Technology> byId = Map.of();
    private static Set<ResourceLocation> unlocked = new LinkedHashSet<>();
    private static List<ResourceLocation> queue = new ArrayList<>();
    /** 研究 id -> 该研究每种材料已消耗数量（与 cost 顺序一致）。 */
    private static Map<ResourceLocation, List<Integer>> consumedByTech = new LinkedHashMap<>();
    /** 已满足显示条件、可以从隐藏状态显示出来的隐藏研究。 */
    private static Set<ResourceLocation> revealed = new LinkedHashSet<>();
    /** 无限研究等级：研究 id -> 已完成等级数。 */
    private static Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();

    private ClientResearchState() {
    }

    public static void setTechs(List<Technology> techs) {
        List<Technology> copy = new ArrayList<>(techs);
        copy.sort((a, b) -> a.id().compareTo(b.id()));
        ordered = List.copyOf(copy);
        Map<ResourceLocation, Technology> map = new LinkedHashMap<>();
        for (Technology tech : copy) {
            map.put(tech.id(), tech);
        }
        byId = map;
    }

    public static void setState(List<ResourceLocation> newUnlocked, List<ResourceLocation> newQueue,
                                List<List<Integer>> queueConsumed, List<ResourceLocation> newRevealed,
                                Map<ResourceLocation, Integer> newLevels) {
        unlocked = new LinkedHashSet<>(newUnlocked);
        queue = new ArrayList<>(newQueue);
        Map<ResourceLocation, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < queue.size(); i++) {
            List<Integer> list = i < queueConsumed.size() ? queueConsumed.get(i) : List.of();
            map.put(queue.get(i), new ArrayList<>(list));
        }
        consumedByTech = map;
        revealed = new LinkedHashSet<>(newRevealed);
        levels = newLevels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(newLevels);
    }

    /** 该研究是否应该在界面上显示（隐藏研究只有满足条件后才显示）。 */
    public static boolean isVisible(Technology tech) {
        return !tech.hidden() || revealed.contains(tech.id());
    }

    public static List<Technology> techs() {
        return ordered;
    }

    public static Technology get(ResourceLocation id) {
        return byId.get(id);
    }

    public static boolean isUnlocked(ResourceLocation id) {
        return unlocked.contains(id);
    }

    /** 无限研究已经完成的等级（0 = 还没完成过）。 */
    public static int levelOf(Technology tech) {
        return levels.getOrDefault(tech.id(), 0);
    }

    /**
     * 研究是否已经「完成过」：普通研究看是否解锁，无限研究看等级是否 ≥ 1。
     * 结构锁、配方锁都用这个判定，无限研究升到 1 级后就会放行。
     */
    public static boolean completedFor(Technology tech) {
        return tech.infinite() ? levelOf(tech) > 0 : unlocked.contains(tech.id());
    }

    public static boolean canResearch(Technology tech) {
        for (ResourceLocation prereq : tech.effectivePrerequisites()) {
            Technology other = byId.get(prereq);
            boolean done = other != null && other.infinite()
                    ? levelOf(other) > 0
                    : unlocked.contains(prereq);
            if (done) {
                continue;
            }
            // 前置还在队列里也算可以排入（不必等它先完成）
            if (queue.contains(prereq)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * 前置只差「排队中的研究」：现在能排进队列，但要等前面的研究完成才会真正开始。
     *
     * <p>界面上用黄色标记这类研究，和「前置已全部完成（可直接开始）」区分开。</p>
     */
    public static boolean waitingOnQueuedPrereq(Technology tech) {
        boolean queued = false;
        for (ResourceLocation prereq : tech.effectivePrerequisites()) {
            Technology other = byId.get(prereq);
            boolean done = other != null && other.infinite()
                    ? levelOf(other) > 0
                    : unlocked.contains(prereq);
            if (done) {
                continue;
            }
            if (!queue.contains(prereq)) {
                return false;
            }
            queued = true;
        }
        return queued;
    }

    public static List<ResourceLocation> queue() {
        return queue;
    }

    /** 指定研究第 index 种材料已消耗的数量（与 tech.costs() 顺序一致）。 */
    public static int consumedOf(ResourceLocation techId, int index) {
        List<Integer> list = consumedByTech.get(techId);
        if (list == null || index < 0 || index >= list.size()) {
            return 0;
        }
        return list.get(index);
    }

    /** 指定研究已经完成的轮数（每种材料按当前等级每轮倍率取整后取最小值）。 */
    public static int progressOf(Technology tech) {
        if (tech.costs().isEmpty()) {
            return 0;
        }
        int level = levelOf(tech);
        int done = Integer.MAX_VALUE;
        for (int i = 0; i < tech.costs().size(); i++) {
            int perRound = tech.packsForLevel(tech.costs().get(i), level);
            done = Math.min(done, consumedOf(tech.id(), i) / perRound);
        }
        if (done == Integer.MAX_VALUE) {
            return 0;
        }
        return Math.min(done, totalOf(tech));
    }

    /** 指定研究需要的总轮数。 */
    public static int totalOf(Technology tech) {
        return Math.max(1, tech.rounds());
    }

    public static Set<ResourceLocation> unlocked() {
        return unlocked;
    }

    public static Map<ResourceLocation, Integer> levels() {
        return levels;
    }
}
