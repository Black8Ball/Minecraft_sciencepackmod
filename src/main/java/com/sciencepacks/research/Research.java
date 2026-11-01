package com.sciencepacks.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sciencepacks.Config;
import com.sciencepacks.network.Network;
import com.sciencepacks.network.StateSyncPacket;
import com.sciencepacks.network.TechSyncPacket;
import com.sciencepacks.network.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 研究系统门面：配方锁、解锁、研究台推进、按研究组同步。 */
public final class Research {

    private static final Gson GSON = new GsonBuilder().create();

    private Research() {
    }

    /* ---------------- 研究组 ---------------- */

    public static PlayerResearch of(ServerPlayer player) {
        return ResearchSavedData.get(player.getServer()).forGroup(ResearchGroups.keyFor(player));
    }

    public static PlayerResearch group(MinecraftServer server, String groupKey) {
        return ResearchSavedData.get(server).forGroup(groupKey);
    }

    public static List<ServerPlayer> members(MinecraftServer server, String groupKey) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ResearchGroups.keyFor(player).equals(groupKey)) {
                players.add(player);
            }
        }
        return players;
    }

    /* ---------------- 配方锁 ---------------- */

    /**
     * 配方归属的科技：
     * 先按配方 id 精确匹配（原版配方 id 与产物物品 id 一致）；
     * 匹配不到时再按「配方产物物品」匹配——KubeJS 或其它模组生成的配方 id 通常是
     * kubejs:xxx / 模组名:xxx 之类，与产物物品名不一致，这样也能被同一项研究锁住。
     */
    public static ResourceLocation ownerOfRecipe(MinecraftServer server, RecipeHolder<?> holder) {
        ResourceLocation direct = TechLibrary.ownerOfRecipe(holder.id());
        if (direct != null) {
            return direct;
        }
        if (server == null) {
            return null;
        }
        try {
            ItemStack output = holder.value().getResultItem(server.registryAccess());
            if (!output.isEmpty()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(output.getItem());
                if (itemId != null) {
                    return TechLibrary.ownerOfItem(itemId);
                }
            }
        } catch (Exception ignored) {
            // 部分模组配方在缺少上下文时取产物会抛异常，忽略即可
        }
        return null;
    }

    private static boolean anyLockEnabled() {
        return Config.COMMON.lockCrafting.get() || Config.COMMON.lockSmelting.get()
                || Config.COMMON.lockSmithing.get() || Config.COMMON.lockStonecutting.get();
    }

    /**
     * 研究是否已经「完成过」。
     *
     * <p>普通研究看是否已解锁；无限研究看等级是否 ≥ 1（升过一级就放行配方与结构锁）。</p>
     */
    public static boolean completedFor(PlayerResearch research, ResourceLocation techId) {
        if (research.isUnlocked(techId)) {
            return true;
        }
        Technology tech = TechLibrary.get(techId);
        return tech != null && tech.infinite() && research.levelOf(techId) > 0;
    }

    public static boolean isRecipeLocked(ServerPlayer player, ResourceLocation recipeId) {
        if (!anyLockEnabled() || TechLibrary.whitelistedRecipe(recipeId)) {
            return false;
        }
        ResourceLocation techId = TechLibrary.ownerOfRecipe(recipeId);
        return techId != null && !completedFor(of(player), techId);
    }

    /** 按配方对象判断锁定：兼容 KubeJS 等以产物物品决定锁定的配方。 */
    public static boolean isRecipeLocked(ServerPlayer player, RecipeHolder<?> holder) {
        if (!anyLockEnabled() || whitelisted(holder)) {
            return false;
        }
        ResourceLocation techId = ownerOfRecipe(player.getServer(), holder);
        return techId != null && !completedFor(of(player), techId);
    }

    public static boolean isLockedFor(PlayerResearch research, ResourceLocation recipeId) {
        if (TechLibrary.whitelistedRecipe(recipeId)) {
            return false;
        }
        ResourceLocation techId = TechLibrary.ownerOfRecipe(recipeId);
        return techId != null && !completedFor(research, techId);
    }

    public static boolean isLockedFor(PlayerResearch research, MinecraftServer server, RecipeHolder<?> holder) {
        if (whitelisted(holder)) {
            return false;
        }
        ResourceLocation techId = ownerOfRecipe(server, holder);
        return techId != null && !completedFor(research, techId);
    }

    /**
     * 白名单判定：命中指定配方 id，或命中该配方产物物品的白名单，就放行。
     */
    private static boolean whitelisted(RecipeHolder<?> holder) {
        if (TechLibrary.whitelistedRecipe(holder.id())) {
            return true;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        try {
            ItemStack output = holder.value().getResultItem(server.registryAccess());
            if (!output.isEmpty()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(output.getItem());
                return itemId != null && TechLibrary.whitelistedItem(itemId);
            }
        } catch (Exception ignored) {
            // 忽略无法取产物的配方
        }
        return false;
    }

    public static boolean isOutputLocked(ServerPlayer player, ItemStack output, RecipeType<?>... types) {
        if (output.isEmpty()) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        PlayerResearch research = of(player);
        boolean anyMatch = false;
        boolean anyUnlocked = false;
        for (RecipeType<?> type : types) {
            @SuppressWarnings("unchecked")
            Collection<RecipeHolder<?>> recipes = (Collection<RecipeHolder<?>>) (Collection<?>)
                    server.getRecipeManager().getAllRecipesFor((RecipeType) type);
            for (RecipeHolder<?> holder : recipes) {
                ItemStack result;
                try {
                    result = holder.value().getResultItem(server.registryAccess());
                } catch (Exception exception) {
                    continue;
                }
                if (!result.isEmpty() && result.getItem() == output.getItem()) {
                    anyMatch = true;
                    if (!isLockedFor(research, server, holder)) {
                        anyUnlocked = true;
                        break;
                    }
                }
            }
            if (anyUnlocked) {
                break;
            }
        }
        return anyMatch && !anyUnlocked;
    }

    /* ---------------- 解锁 ---------------- */

    public static boolean canResearch(PlayerResearch research, Technology tech) {
        for (ResourceLocation prereq : tech.effectivePrerequisites()) {
            if (!completedFor(research, prereq)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 排入队列时的前置判断：前置已经完成，或者前置正排在队列里（还没完成也行）。
     *
     * <p>这样可以把整条科技线一次性排好，队列会按顺序依次研究；
     * 若前置被取消，依赖它的排队研究会一起被取消。</p>
     */
    public static boolean canResearchAllowingQueue(PlayerResearch research, Technology tech) {
        for (ResourceLocation prereq : tech.effectivePrerequisites()) {
            if (completedFor(research, prereq)) {
                continue;
            }
            if (research.queue().contains(prereq)) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static boolean canResearch(ServerPlayer player, Technology tech) {
        return canResearch(of(player), tech);
    }

    public static void unlock(ServerPlayer player, Technology tech, boolean announce) {
        PlayerResearch research = of(player);
        unlockGroup(player.getServer(), research.groupKey(), tech);
    }

    public static void unlockGroup(MinecraftServer server, String groupKey, Technology tech) {
        if (server == null) {
            return;
        }
        PlayerResearch research = group(server, groupKey);
        if (!research.unlock(tech.id())) {
            return;
        }
        ResearchSavedData.get(server).setDirty();
        cascadeFree(server, research);
        for (ServerPlayer member : members(server, groupKey)) {
            if (Config.COMMON.announceUnlocks.get()) {
                member.displayClientMessage(
                        Component.translatable("message.sciencepacks.unlocked", tech.name()), false);
            }
            refreshRecipeBook(member);
            syncState(member, research);
        }
        completeNotice(server, groupKey, tech);
    }

    /** 研究完成：给组内成员播放可配置音效 + 右上角弹提示框。 */
    private static void completeNotice(MinecraftServer server, String groupKey, Technology tech) {
        if (server == null) {
            return;
        }
        SoundEvent sound = resolveCompleteSound();
        boolean toast = Config.COMMON.completeToast.get();
        if (sound == null && !toast) {
            return;
        }
        for (ServerPlayer member : members(server, groupKey)) {
            if (sound != null) {
                member.playNotifySound(sound, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            if (toast) {
                Network.toPlayer(new ToastPacket(tech.name(), tech.id().toString()), member);
            }
        }
    }

    /** 读取配置的研究完成音效；找不到时回落到升级音效。 */
    private static SoundEvent resolveCompleteSound() {
        String raw = Config.COMMON.completeSound.get();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        if (id == null) {
            return SoundEvents.PLAYER_LEVELUP;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(id);
        return sound == null ? SoundEvents.PLAYER_LEVELUP : sound;
    }

    /** 免费科技（无成本、无触发物品）在前置满足时自动解锁，并继续级联。 */
    public static void cascadeFree(MinecraftServer server, PlayerResearch research) {
        if (!Config.COMMON.autoUnlockFree.get()) {
            return;
        }
        boolean changed = true;
        boolean any = false;
        while (changed) {
            changed = false;
            for (Technology tech : TechLibrary.ordered()) {
                if (research.isUnlocked(tech.id()) || tech.infinite() || !tech.isFree()) {
                    continue;
                }
                if (!canResearch(research, tech)) {
                    continue;
                }
                if (research.unlock(tech.id())) {
                    changed = true;
                    any = true;
                }
            }
        }
        if (any && server != null) {
            ResearchSavedData.get(server).setDirty();
        }
    }

    public static void refreshRecipeBook(ServerPlayer player) {
        if (!Config.COMMON.hideLockedRecipeBook.get()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Collection<RecipeHolder<?>> all = server.getRecipeManager().getRecipes();
        List<ResourceLocation> known = new ArrayList<>(all.size());
        for (RecipeHolder<?> holder : all) {
            if (!isRecipeLocked(player, holder)) {
                known.add(holder.id());
            }
        }
        player.resetRecipes(all);
        player.awardRecipesByKey(known);
    }

    /* ---------------- 触发解锁 ---------------- */

    public static void scanTriggers(ServerPlayer player) {
        if (!Config.COMMON.autoUnlockTriggered.get()) {
            return;
        }
        PlayerResearch research = of(player);
        for (Technology tech : TechLibrary.ordered()) {
            if (tech.triggers().isEmpty() || tech.infinite() || research.isUnlocked(tech.id())) {
                continue;
            }
            if (!canResearch(research, tech)) {
                continue;
            }
            if (triggersSatisfied(player, research, tech)) {
                unlock(player, tech, true);
            }
        }
    }

    /** 一项科技的所有触发条件是否都已满足。 */
    public static boolean triggersSatisfied(ServerPlayer player, PlayerResearch research, Technology tech) {
        if (tech.triggers().isEmpty()) {
            return false;
        }
        for (Technology.Trigger trigger : tech.triggers()) {
            if (!triggerSatisfied(player, research, trigger)) {
                return false;
            }
        }
        return true;
    }

    /** 一组触发条件是否全部满足（空列表视为「无额外条件」）。 */
    public static boolean triggersSatisfied(ServerPlayer player, PlayerResearch research,
                                            List<Technology.Trigger> triggers) {
        for (Technology.Trigger trigger : triggers) {
            if (!triggerSatisfied(player, research, trigger)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 单个触发条件是否满足。
     *
     * <p>item 类型看背包里的实时数量；挖掘/击败/合成等事件类型看存档里累计的次数。</p>
     */
    public static boolean triggerSatisfied(ServerPlayer player, PlayerResearch research, Technology.Trigger trigger) {
        if (trigger.isItem()) {
            Item item = BuiltInRegistries.ITEM.get(trigger.id());
            return item != null && player.getInventory().countItem(item) >= trigger.count();
        }
        return research.triggerCount(trigger.key()) >= trigger.count();
    }

    /**
     * 隐藏研究是否对玩家可见。
     *
     * <p>非隐藏研究永远可见；隐藏研究需要「前置全部解锁」且「showWhen 全部满足」才显示。</p>
     */
    public static boolean visibleFor(ServerPlayer player, PlayerResearch research, Technology tech) {
        if (!tech.hidden()) {
            return true;
        }
        return canResearch(research, tech) && triggersSatisfied(player, research, tech.showWhen());
    }

    /** 隐藏研究是否可见（不检查 showWhen 的实时物品数量，只在同步时调用）。 */
    public static boolean revealed(ServerPlayer player, PlayerResearch research, Technology tech) {
        if (!tech.hidden()) {
            return false;
        }
        return visibleFor(player, research, tech);
    }
    /**
     * 记录一次事件触发（挖掘方块、击败生物、合成、烧炼、拾取、到达维度），
     * 并立刻检查相关科技是否可以解锁。
     */
    public static void onTriggerEvent(ServerPlayer player, String type, ResourceLocation id, int amount) {
        if (player == null || id == null || amount <= 0) {
            return;
        }
        PlayerResearch research = of(player);
        String key = type + "|" + id;
        research.addTriggerCount(key, amount);
        ResearchSavedData.get(player.getServer()).setDirty();
        if (!Config.COMMON.autoUnlockTriggered.get()) {
            return;
        }
        for (Technology tech : TechLibrary.ordered()) {
            if (tech.triggers().isEmpty() || tech.infinite() || research.isUnlocked(tech.id())) {
                continue;
            }
            boolean relevant = false;
            for (Technology.Trigger trigger : tech.triggers()) {
                if (trigger.key().equals(key)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant || !canResearch(research, tech)) {
                continue;
            }
            if (triggersSatisfied(player, research, tech)) {
                unlock(player, tech, true);
            }
        }
    }

    /* ---------------- 研究台推进 ---------------- */

    public static Technology current(PlayerResearch research) {
        while (!research.queue().isEmpty()) {
            Technology tech = TechLibrary.get(research.queue().get(0));
            // 无限研究不算「已解锁」，只有到达等级上限被标记后才出队
            boolean finished = tech == null || (!tech.infinite() && research.isUnlocked(tech.id()))
                    || (tech.infinite() && tech.levelCapped(research.levelOf(tech.id())));
            if (finished) {
                if (tech != null) {
                    research.clearProgress(tech.id());
                }
                research.queue().remove(0);
                continue;
            }
            return tech;
        }
        return null;
    }

    /**
     * 执行一轮消耗：当前科技需要的**所有**材料按各自倍率同时扣除一次。
     *
     * <p>研究台是轮次制：一轮消耗 {@code cost.count} 个对应材料，
     * 每轮耗时 {@code researchTime}（为 0 用全局 ticksPerPack），
     * 完成 {@code rounds} 轮后解锁。材料不齐时整轮不工作（不扣任何材料、不推进）。</p>
     *
     * <p>无限研究：每完成一轮组等级 +1，下一级每轮消耗按 {@code costMultiplier} 增长；
     * 研究保持在本组队列里，玩家可以继续研究下一级，也可以随时取消。</p>
     *
     * @return 本轮需要的 tick 数；0 表示没有消耗（缺材料或队列为空）
     */
    public static int consumeCycle(MinecraftServer server, PlayerResearch research, Container packs) {
        Technology tech = current(research);
        if (tech == null || !tech.hasCost()) {
            return 0;
        }
        int level = tech.infinite() ? research.levelOf(tech.id()) : 0;
        int rounds = Math.max(1, tech.rounds());
        if (roundsDone(tech, research, level) >= rounds) {
            finishRoundSet(server, research, tech, level);
            return 0;
        }
        // 整轮材料必须同时齐全（按当前等级每轮消耗），否则这一轮直接跳过
        for (Technology.PackCost cost : tech.costs()) {
            Item item = BuiltInRegistries.ITEM.get(cost.item());
            if (item == null || countOf(packs, item) < tech.packsForLevel(cost, level)) {
                return 0;
            }
        }
        for (Technology.PackCost cost : tech.costs()) {
            Item item = BuiltInRegistries.ITEM.get(cost.item());
            if (item != null) {
                int need = tech.packsForLevel(cost, level);
                removeAmount(packs, item, need);
                research.consumed(tech.id()).merge(cost.item(), need, Integer::sum);
            }
        }
        ResearchSavedData.get(server).setDirty();
        if (roundsDone(tech, research, level) >= rounds) {
            finishRoundSet(server, research, tech, level);
        } else {
            syncGroup(server, research.groupKey());
        }
        return tech.researchTime() > 0 ? tech.researchTime() : Config.COMMON.ticksPerPack.get();
    }

    /**
     * 一轮组（rounds 轮）结束：普通研究解锁并出队；无限研究等级 +1，继续留在队列里。
     */
    private static void finishRoundSet(MinecraftServer server, PlayerResearch research, Technology tech, int level) {
        research.clearProgress(tech.id());
        if (tech.infinite() && !tech.levelCapped(level)) {
            int next = research.addLevel(tech.id());
            ResearchSavedData.get(server).setDirty();
            // 达到等级上限后按普通研究结束（标记为已解锁并出队）
            if (tech.maxLevel() > 0 && next >= tech.maxLevel()) {
                research.queue().remove(tech.id());
                unlockGroup(server, research.groupKey(), tech);
                return;
            }
            for (ServerPlayer member : members(server, research.groupKey())) {
                member.displayClientMessage(
                        Component.translatable("message.sciencepacks.levelup", tech.name(), next), false);
                ResearchEffects.applyAttributes(member);
            }
            completeNotice(server, research.groupKey(), tech);
            syncGroup(server, research.groupKey());
            return;
        }
        research.queue().remove(tech.id());
        unlockGroup(server, research.groupKey(), tech);
    }

    /** 已经完成的轮数（每种材料按当前等级每轮倍率取整后再取最小值）。 */
    public static int roundsDone(Technology tech, PlayerResearch research) {
        int level = tech.infinite() ? research.levelOf(tech.id()) : 0;
        return roundsDone(tech, research, level);
    }

    /** 已经完成的轮数：按指定等级（无限研究的等级）换算每轮倍率。 */
    public static int roundsDone(Technology tech, PlayerResearch research, int level) {
        if (!tech.hasCost()) {
            return 0;
        }
        int done = Integer.MAX_VALUE;
        for (Technology.PackCost cost : tech.costs()) {
            int perRound = tech.packsForLevel(cost, level);
            done = Math.min(done, research.consumedOf(tech.id(), cost.item()) / perRound);
        }
        if (done == Integer.MAX_VALUE) {
            return 0;
        }
        return Math.min(done, Math.max(1, tech.rounds()));
    }

    private static int countOf(Container container, Item item) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeAmount(Container container, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                container.setChanged();
            }
        }
    }

    private static void removeOne(Container container, Item item) {
        removeAmount(container, item, 1);
    }

    /* ---------------- 队列操作 ---------------- */

    public static boolean start(ServerPlayer player, ResourceLocation techId) {
        Technology tech = TechLibrary.get(techId);
        if (tech == null) {
            return false;
        }
        PlayerResearch research = of(player);
        if (research.isUnlocked(techId) || research.queue().contains(techId)) {
            return false;
        }
        if (!canResearchAllowingQueue(research, tech)) {
            player.displayClientMessage(Component.translatable("message.sciencepacks.missing_prereq"), true);
            return false;
        }
        // 隐藏研究：显示条件没满足时不允许开始（防止脚本猜 id）
        if (!visibleFor(player, research, tech)) {
            return false;
        }
        if (tech.isTriggerOnly()) {
            return false;
        }
        if (tech.isFree()) {
            unlock(player, tech, true);
            return true;
        }
        research.queue().add(techId);
        ResearchSavedData.get(player.getServer()).setDirty();
        syncGroup(player.getServer(), research.groupKey());
        broadcast(player.getServer(), research.groupKey(),
                Component.translatable("message.sciencepacks.queue.start",
                        player.getScoreboardName(), tech.name()));
        return true;
    }

    public static boolean stop(ServerPlayer player, ResourceLocation techId) {
        PlayerResearch research = of(player);
        if (!research.queue().remove(techId)) {
            return false;
        }
        // 进度按研究单独保存，移出队列不会丢进度，重新加入即可接着研究
        Technology tech = TechLibrary.get(techId);
        List<ResourceLocation> removed = new ArrayList<>();
        removed.add(techId);
        // 前置被取消时，依赖它、且前置仍未完成的排队研究一起取消
        removeDependents(research, removed);
        ResearchSavedData.get(player.getServer()).setDirty();
        syncGroup(player.getServer(), research.groupKey());
        if (removed.size() > 1) {
            List<ResourceLocation> others = new ArrayList<>(removed);
            others.remove(techId);
            broadcast(player.getServer(), research.groupKey(),
                    Component.translatable("message.sciencepacks.queue.stop_many",
                            player.getScoreboardName(),
                            tech == null ? techId.toString() : tech.name(),
                            joinedNames(others)));
        } else {
            broadcast(player.getServer(), research.groupKey(),
                    Component.translatable("message.sciencepacks.queue.stop",
                            player.getScoreboardName(),
                            tech == null ? techId.toString() : tech.name()));
        }
        return true;
    }

    /** 把依赖被取消研究（且前置仍未完成）的排队研究递归移出队列。 */
    private static void removeDependents(PlayerResearch research, List<ResourceLocation> removed) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = research.queue().size() - 1; i >= 0; i--) {
                ResourceLocation id = research.queue().get(i);
                Technology tech = TechLibrary.get(id);
                if (tech == null) {
                    continue;
                }
                for (ResourceLocation prereq : tech.effectivePrerequisites()) {
                    if (removed.contains(prereq) && !completedFor(research, prereq)) {
                        research.queue().remove(i);
                        removed.add(id);
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    private static String joinedNames(List<ResourceLocation> ids) {
        List<String> names = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Technology tech = TechLibrary.get(id);
            names.add(tech == null ? id.toString() : tech.name());
        }
        return String.join("、", names);
    }

    public static void clearQueue(ServerPlayer player) {
        PlayerResearch research = of(player);
        if (research.queue().isEmpty()) {
            return;
        }
        research.queue().clear();
        research.resetAllProgress();
        ResearchSavedData.get(player.getServer()).setDirty();
        syncGroup(player.getServer(), research.groupKey());
        broadcast(player.getServer(), research.groupKey(),
                Component.translatable("message.sciencepacks.queue.clear", player.getScoreboardName()));
    }

    /** 拖动队列后调整顺序：进度按研究单独保存，调整顺序不影响任何进度。 */
    public static boolean moveInQueue(ServerPlayer player, int from, int to) {
        PlayerResearch research = of(player);
        List<ResourceLocation> queue = research.queue();
        if (from < 0 || from >= queue.size()) {
            return false;
        }
        int target = Math.max(0, Math.min(queue.size() - 1, to));
        if (from == target) {
            return false;
        }
        ResourceLocation moved = queue.remove(from);
        queue.add(target, moved);
        ResearchSavedData.get(player.getServer()).setDirty();
        syncGroup(player.getServer(), research.groupKey());
        Technology tech = TechLibrary.get(moved);
        broadcast(player.getServer(), research.groupKey(),
                Component.translatable("message.sciencepacks.queue.reorder",
                        player.getScoreboardName(),
                        tech == null ? moved.toString() : tech.name()));
        return true;
    }

    /** 向研究组内所有在线成员播报（受 announceQueue 开关控制）。 */
    private static void broadcast(MinecraftServer server, String groupKey, Component message) {
        if (server == null || !Config.COMMON.announceQueue.get()) {
            return;
        }
        for (ServerPlayer member : members(server, groupKey)) {
            member.displayClientMessage(message, false);
        }
    }

    /* ---------------- 网络同步 ---------------- */

    public static String techJson() {
        JsonArray array = new JsonArray();
        for (Technology tech : TechLibrary.ordered()) {
            JsonObject object = tech.toJson();
            object.addProperty("id", tech.id().toString());
            array.add(object);
        }
        return GSON.toJson(array);
    }

    public static void syncTechs(ServerPlayer player) {
        Network.toPlayer(new TechSyncPacket(techJson()), player);
    }

    public static void syncState(ServerPlayer player) {
        syncState(player, of(player));
    }

    public static void syncState(ServerPlayer player, PlayerResearch research) {
        List<ResourceLocation> unlocked = new ArrayList<>(research.unlocked());
        List<ResourceLocation> queue = new ArrayList<>(research.queue());
        // 每个队列研究的已消耗数量都要发过去，否则调整队列后
        // 不在队首的研究会显示成「没有进度」。
        List<List<Integer>> queueConsumed = new ArrayList<>();
        for (ResourceLocation id : queue) {
            Technology tech = TechLibrary.get(id);
            List<Integer> consumed = new ArrayList<>();
            if (tech != null) {
                for (Technology.PackCost cost : tech.costs()) {
                    consumed.add(research.consumedOf(tech.id(), cost.item()));
                }
            }
            queueConsumed.add(consumed);
        }
        // 隐藏研究：只把「前置已满足 + showWhen 已满足」的发给客户端显示
        List<ResourceLocation> revealed = new ArrayList<>();
        for (Technology tech : TechLibrary.ordered()) {
            if (tech.hidden() && revealed(player, research, tech)) {
                revealed.add(tech.id());
            }
        }
        // 无限研究等级：客户端要显示等级并按等级换算每轮消耗
        Map<ResourceLocation, Integer> levels = new LinkedHashMap<>(research.levels());
        Network.toPlayer(new StateSyncPacket(unlocked, queue, queueConsumed, revealed, levels), player);
        // 触及距离等数值型研究效果依赖等级，状态同步后重新应用一遍
        ResearchEffects.applyAttributes(player);
    }

    public static void syncGroup(MinecraftServer server, String groupKey) {
        if (server == null) {
            return;
        }
        PlayerResearch research = group(server, groupKey);
        for (ServerPlayer member : members(server, groupKey)) {
            syncState(member, research);
        }
    }

    public static void sync(ServerPlayer player) {
        syncTechs(player);
        syncState(player);
    }
}
