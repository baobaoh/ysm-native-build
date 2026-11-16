package com.elfmcys.ysm.command.sub;

import com.elfmcys.ysm.capability.AuthModelsCapabilityProvider;
import com.elfmcys.ysm.capability.ModelInfoCapabilityProvider;
import com.elfmcys.ysm.capability.fabric.EntityCapabilityHolder;
import com.elfmcys.ysm.event.CommandRegistry;
import com.elfmcys.ysm.model.source.AccessPolicy;
import com.elfmcys.ysm.model.server.ServerModelService;
import com.elfmcys.ysm.util.CommandUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import com.elfmcys.ysm.capability.fabric.EntityCapabilityHolder;

public final class ModelCommand {
    private static final String TARGETS = "targets";
    private static final String MODEL_PATH = "model_path";

    private ModelCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        var model = Commands.literal("model").requires(source -> CommandUtil.hasPermission(source, 2));
        model.then(Commands.literal("reload").executes(ModelCommand::reload));
        model.then(Commands.literal("errors").executes(ModelCommand::errors));
        model.then(Commands.literal("disable")
                .then(Commands.argument(TARGETS, EntityArgument.players())
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(ModelCommand::disable))));
        // 模型路径必须用 greedyString：Brigadier 的普通 string 参数按字符白名单（字母/数字/_-.+）截断，
        // 而内置模型路径形如 wine_fox/01_taisho_maid，斜杠不在白名单里 —— 玩家敲出来会得到
        // 「参数后应有空格分隔，但发现了尾随数据」。greedy 会一口气吃掉整行，所以贴图与「跳过鉴权」
        // 改成后缀写法：<path>#<texture>@force（都缺省时用模型默认贴图）。
        var set = Commands.literal("set")
                .then(Commands.argument(TARGETS, EntityArgument.players())
                        .then(Commands.argument(MODEL_PATH, StringArgumentType.greedyString())
                                .suggests(CommandRegistry.ALL_MODELS)
                                .executes(context -> set(context))));
        model.then(set);
        return model;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var raw = StringArgumentType.getString(context, MODEL_PATH).trim();
        var ignoreAuth = false;
        if (raw.endsWith("@force")) {
            ignoreAuth = true;
            raw = raw.substring(0, raw.length() - "@force".length());
        }
        var texture = "-";
        var hashIndex = raw.indexOf('#');
        if (hashIndex >= 0) {
            texture = raw.substring(hashIndex + 1);
            raw = raw.substring(0, hashIndex);
        }
        var path = raw;
        var model = ServerModelService.instance().snapshot().flatMap(snapshot -> snapshot.findPath(path));
        if (model.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown or ambiguous model path: " + path));
            return 0;
        }
        var handle = model.get();
        if ("-".equals(texture)) {
            var requested = handle.view().getManifest().getInfo().getSettings().getDefaultTexture();
            texture = handle.view().getPlayer().getTextureNames().contains(requested)
                    ? requested : handle.view().getPlayer().getTextureNames().stream().sorted().findFirst().orElse("");
        }
        if (!handle.view().getPlayer().getTextureNames().contains(texture)) {
            context.getSource().sendFailure(Component.literal("Unknown texture for " + path + ": " + texture));
            return 0;
        }
        var finalIgnoreAuth = ignoreAuth;

        var hash = handle.descriptor().modelHash();
        var selectedTexture = texture;
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, TARGETS);
        for (var player : targets) {
            EntityCapabilityHolder.get(player, ModelInfoCapabilityProvider.MODEL_INFO_CAP).ifPresent(selection ->
                    EntityCapabilityHolder.get(player, AuthModelsCapabilityProvider.AUTH_MODELS_CAP).ifPresent(auth -> {
                        if (!finalIgnoreAuth && handle.location().rootKind().accessPolicy()
                                == AccessPolicy.SESSION_AUTHORIZED
                                && !auth.containModel(hash)) {
                            context.getSource().sendFailure(Component.literal(
                                    player.getScoreboardName() + " is not authorized for " + path));
                            return;
                        }
                        selection.setModelAndTexture(hash, selectedTexture);
                        selection.setMandatory(true);
                        selection.stopAnimation(player);
                    }));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Reloading model catalog..."), true);
        ServerModelService.instance().reload(true).thenAccept(result ->
                CommandUtil.sendAsyncFeedback(context.getSource(), Component.literal(result.success()
                        ? "Model catalog reloaded: " + result.modelCount() + " model(s), "
                        + result.errorCount() + " scan error(s)"
                        : "Model catalog reload failed: " + result.message()), true));
        return Command.SINGLE_SUCCESS;
    }

    private static int errors(CommandContext<CommandSourceStack> context) {
        var errors = ServerModelService.instance().lastReport().errors();
        if (errors.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("The last model scan had no errors."), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendSuccess(() -> Component.literal("Model scan errors: " + errors.size()), false);
        for (var error : errors) {
            var text = "[" + error.rootKind() + "] " + error.source() + ": " + error.message();
            if (!error.detail().isBlank()) {
                text += "\n" + error.detail();
            }
            var output = text;
            context.getSource().sendSuccess(() -> Component.literal(output), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int disable(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var disabled = BoolArgumentType.getBool(context, "value");
        for (var player : EntityArgument.getPlayers(context, TARGETS)) {
            EntityCapabilityHolder.get(player, ModelInfoCapabilityProvider.MODEL_INFO_CAP)
                    .ifPresent(capability -> capability.setDisabled(disabled));
        }
        return Command.SINGLE_SUCCESS;
    }
}
