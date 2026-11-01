package com.sciencepacks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sciencepacks.research.PlayerResearch;
import com.sciencepacks.research.Research;
import com.sciencepacks.research.ResearchSavedData;
import com.sciencepacks.research.TechLibrary;
import com.sciencepacks.research.Technology;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /sciencepacks admin command. */
public final class ResearchCommand {

    private static final SuggestionProvider<CommandSourceStack> TECHS = (context, builder) ->
            SharedSuggestionProvider.suggest(TechLibrary.ordered().stream().map(t -> t.id().toString()), builder);

    private ResearchCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("sciencepacks")
                .then(Commands.literal("techs")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            source.sendSuccess(() -> Component.translatable("command.sciencepacks.techs",
                                    TechLibrary.size()), false);
                            for (Technology tech : TechLibrary.ordered()) {
                                source.sendSuccess(() -> Component.translatable("command.sciencepacks.techs_line",
                                        tech.id().toString(), tech.name(), tech.unlocks().size(), tech.totalPacks(),
                                        Component.literal(tech.triggers().isEmpty()
                                                ? "" : "  triggers=" + tech.triggers())), false);
                            }
                            return TechLibrary.size();
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("tech", StringArgumentType.string())
                                .suggests(TECHS)
                                .executes(ctx -> unlock(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> unlock(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("lock")
                        .then(Commands.argument("tech", StringArgumentType.string())
                                .suggests(TECHS)
                                .executes(ctx -> lock(ctx, ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("queue")
                        .then(Commands.argument("tech", StringArgumentType.string())
                                .suggests(TECHS)
                                .executes(ctx -> queue(ctx, ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("debug")
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "recipe");
                                    ResourceLocation byId = TechLibrary.ownerOfRecipe(id);
                                    var found = source.getServer().getRecipeManager().byKey(id);
                                    ResourceLocation byOutput = found.isPresent()
                                            ? Research.ownerOfRecipe(source.getServer(), found.get()) : null;
                                    source.sendSuccess(() -> Component.translatable("command.sciencepacks.debug",
                                            id.toString(),
                                            byId == null
                                                    ? Component.translatable("command.sciencepacks.none")
                                                    : Component.literal(byId.toString()),
                                            byOutput == null
                                                    ? Component.translatable("command.sciencepacks.none")
                                                    : Component.literal(byOutput.toString())), false);
                                    return 1;
                                })))
                .then(Commands.literal("dumprecipes")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            try {
                                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                                int locked = 0;
                                for (RecipeHolder<?> holder : source.getServer().getRecipeManager().getRecipes()) {
                                    ResourceLocation owner = Research.ownerOfRecipe(source.getServer(), holder);
                                    if (owner != null) {
                                        locked++;
                                    }
                                    obj.addProperty(holder.id().toString(), owner == null ? "" : owner.toString());
                                }
                                java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
                                java.nio.file.Files.createDirectories(dir);
                                java.nio.file.Path out = dir.resolve("sciencepacks-recipes.json");
                                java.nio.file.Files.writeString(out, new com.google.gson.GsonBuilder()
                                        .setPrettyPrinting().create().toJson(obj));
                                final int lockedCount = locked;
                                source.sendSuccess(() -> Component.translatable("command.sciencepacks.dump_recipes",
                                        out.toString(), lockedCount), false);
                            } catch (Exception exception) {
                                source.sendFailure(Component.translatable("command.sciencepacks.export_failed",
                                        String.valueOf(exception)));
                            }
                            return 1;
                        }))
                .then(Commands.literal("dumpitems")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            try {
                                java.util.List<String> ids = new java.util.ArrayList<>();
                                for (ResourceLocation id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
                                    ids.add(id.toString());
                                }
                                java.util.Collections.sort(ids);
                                com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                                ids.forEach(array::add);
                                java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
                                java.nio.file.Files.createDirectories(dir);
                                java.nio.file.Path out = dir.resolve("sciencepacks-items.json");
                                java.nio.file.Files.writeString(out, new com.google.gson.GsonBuilder()
                                        .setPrettyPrinting().create().toJson(array));
                                source.sendSuccess(() -> Component.translatable("command.sciencepacks.dump_items",
                                        ids.size(), out.toString()), false);
                            } catch (Exception exception) {
                                source.sendFailure(Component.translatable("command.sciencepacks.export_failed",
                                        String.valueOf(exception)));
                            }
                            return 1;
                        }))                .then(Commands.literal("dumpresearch")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            try {
                                java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
                                java.nio.file.Files.createDirectories(dir);
                                java.nio.file.Path out = dir.resolve("sciencepacks-research-export.json");
                                java.nio.file.Files.writeString(out,
                                        com.sciencepacks.research.ResearchFiles.export(
                                                com.sciencepacks.research.TechLibrary.ordered()));
                                source.sendSuccess(() -> Component.translatable("command.sciencepacks.dump_research",
                                        out.toString()), false);
                            } catch (Exception exception) {
                                source.sendFailure(Component.translatable("command.sciencepacks.export_failed",
                                        String.valueOf(exception)));
                            }
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            source.getServer().reloadResources(
                                    source.getServer().getPackRepository().getSelectedIds());
                            source.sendSuccess(() -> Component.translatable("command.sciencepacks.reload"), true);
                            return 1;
                        }))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            source.sendSuccess(() -> Component.literal("""
                                    /sciencepacks techs                  list technologies
                                    /sciencepacks status [player]        show research state
                                    /sciencepacks unlock <tech> [player] unlock a technology
                                    /sciencepacks lock <tech>            lock it again
                                    /sciencepacks reset [player]         wipe progress
                                    /sciencepacks queue <tech>           add to research queue
                                    /sciencepacks debug <recipe>         which tech owns a recipe
                                    /sciencepacks reload                 reload datapacks
                                    """), false);
                            return 1;
                        })));
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        PlayerResearch research = Research.of(player);
        Technology head = Research.current(research);
        int headProgress = head == null ? 0 : research.progressOf(head.id());
        source.sendSuccess(() -> Component.translatable("command.sciencepacks.status",
                player.getGameProfile().getName(), research.unlocked().size(), TechLibrary.size(),
                research.queue().size(), headProgress), false);
        return research.unlocked().size();
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "tech"));
        Technology tech = id == null ? null : TechLibrary.get(id);
        if (tech == null) {
            ctx.getSource().sendFailure(Component.translatable("command.sciencepacks.unknown",
                    StringArgumentType.getString(ctx, "tech")));
            return 0;
        }
        Research.unlock(player, tech, true);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.sciencepacks.unlock",
                tech.id().toString(), player.getGameProfile().getName()), true);
        return 1;
    }

    private static int lock(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "tech"));
        if (id == null) {
            return 0;
        }
        PlayerResearch research = Research.of(player);
        research.unlocked().remove(id);
        ResearchSavedData.get(player.getServer()).setDirty();
        Research.refreshRecipeBook(player);
        Research.syncState(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.sciencepacks.lock",
                id.toString()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        PlayerResearch research = Research.of(player);
        research.reset();
        ResearchSavedData.get(player.getServer()).setDirty();
        Research.refreshRecipeBook(player);
        Research.syncState(player);
        source.sendSuccess(() -> Component.translatable("command.sciencepacks.reset",
                player.getGameProfile().getName()), true);
        return 1;
    }

    private static int queue(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "tech"));
        if (id == null || TechLibrary.get(id) == null) {
            ctx.getSource().sendFailure(Component.translatable("command.sciencepacks.unknown",
                    StringArgumentType.getString(ctx, "tech")));
            return 0;
        }
        boolean ok = Research.start(player, id);
        ctx.getSource().sendSuccess(() -> ok
                ? Component.translatable("command.sciencepacks.queue_ok", id.toString())
                : Component.translatable("command.sciencepacks.queue_fail"), false);
        return ok ? 1 : 0;
    }
}
