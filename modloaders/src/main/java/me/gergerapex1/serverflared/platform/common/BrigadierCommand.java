package me.gergerapex1.serverflared.platform.common;

//? if !legacy_forge {
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.UUID;
import me.gergerapex1.serverflared.ServerFlared;
import me.gergerapex1.serverflared.command.CommandSenderAdapter;
import me.gergerapex1.serverflared.command.PermissionHelper;
import me.gergerapex1.serverflared.command.ServerFlaredCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if <1.19 {
/*import net.minecraft.network.chat.TextComponent;
*///? }
//? if >=1.16 {
import net.minecraft.network.chat.MutableComponent;
//? }
//? if >=26.1 {
/*import java.net.URI;
*///? }

public final class BrigadierCommand {
    private BrigadierCommand() {
    }

    public static void register(final MinecraftServer server) {
        if (server == null) {
            return;
        }
        register(server.getCommands().getDispatcher());
        ServerFlared.setCommandTreeSyncCallback(() -> {
            try {
                server.execute(() -> {
                    if (server.getPlayerList() != null) {
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            server.getCommands().sendCommands(player);
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        });
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.register(buildCommand(ServerFlaredCommand.COMMAND_NAME));
        dispatcher.register(buildCommand(ServerFlaredCommand.COMMAND_ALIAS));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommand(String literalName) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(literalName)
            .requires(source -> ServerFlaredCommand.isAuthorized(wrapSource(source)))
            .executes(ctx -> {
                ServerFlaredCommand.execute(wrapSource(ctx.getSource()), new String[0]);
                return 1;
            });

        for (final String sub : ServerFlaredCommand.SUBCOMMANDS) {
            root.then(
                Commands.literal(sub)
                    .requires(source -> ServerFlaredCommand.isAuthorized(wrapSource(source)))
                    .executes(ctx -> {
                        ServerFlaredCommand.execute(wrapSource(ctx.getSource()), new String[]{sub});
                        return 1;
                    })
            );
        }

        root.then(
            Commands.argument("args", StringArgumentType.greedyString())
                .requires(source -> ServerFlaredCommand.isAuthorized(wrapSource(source)))
                .suggests((CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) -> {
                    String remaining = builder.getRemaining();
                    String[] parts = remaining.isEmpty() ? new String[]{""} : remaining.split("\\s+", -1);
                    for (String suggestion : ServerFlaredCommand.suggest(wrapSource(ctx.getSource()), parts)) {
                        builder.suggest(suggestion);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    String raw = StringArgumentType.getString(ctx, "args");
                    String[] args = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
                    ServerFlaredCommand.execute(wrapSource(ctx.getSource()), args);
                    return 1;
                })
        );

        return root;
    }

    public static CommandSenderAdapter wrapSource(final CommandSourceStack source) {
        return new CommandSenderAdapter() {
            @Override
            public boolean isConsole() {
                Entity entity = source.getEntity();
                if (entity != null) {
                    return false;
                }
                String name = source.getTextName();
                return !"@".equals(name);
            }

            @Override
            public boolean hasPermission(String node, int opLevel) {
                Boolean fabricPerm = PermissionHelper.checkFabricPermissionsApi(source, node, opLevel);
                if (Boolean.TRUE.equals(fabricPerm)) {
                    return true;
                }
                Entity entity = source.getEntity();
                if (entity instanceof ServerPlayer) {
                    UUID uuid = entity.getUUID();
                    Boolean lpPerm = PermissionHelper.checkLuckPerms(uuid, node);
                    if (Boolean.TRUE.equals(lpPerm)) {
                        return true;
                    }
                }
                return hasVanillaOpLevel(source, opLevel);
            }

            @Override
            public void sendMessage(String message) {
                sendComponent(source, createLiteralComponent(message));
            }

            @Override
            public void sendClickableUrl(String text, String url) {
                if (isConsole()) {
                    sendMessage(text + " " + url);
                    return;
                }
                sendClickableUrlComponent(source, text, url);
            }

            @Override
            public void sendCopyableText(String text, String copyValue) {
                if (isConsole()) {
                    sendMessage(copyValue);
                    return;
                }
                sendCopyableTextComponent(source, text, copyValue);
            }
        };
    }

    //? if >=1.19 {
    private static MutableComponent createLiteralComponent(String text) {
        return Component.literal(text);
    }
    //? } else if >=1.16 {
    /*private static MutableComponent createLiteralComponent(String text) {
        return new TextComponent(text);
    }
    *///? } else {
    /*private static Component createLiteralComponent(String text) {
        return new TextComponent(text);
    }
    *///? }

    private static void sendComponent(CommandSourceStack source, Component component) {
        //? if >=1.20 {
        source.sendSuccess(() -> component, false);
        //? } else {
        /*source.sendSuccess(component, false);
        *///? }
    }

    private static boolean hasVanillaOpLevel(CommandSourceStack source, int opLevel) {
        //? if >=26.1 {
        /*return Commands.LEVEL_OWNERS.check(source.permissions());
        *///? } else {
        return source.hasPermission(opLevel);
        //? }
    }

    private static void sendClickableUrlComponent(CommandSourceStack source, String text, String url) {
        //? if >=26.1 {
        /*Component hoverText = createLiteralComponent("\u00A7eClick to open URL:\n\u00A7b" + url);
        MutableComponent comp = createLiteralComponent(text).withStyle(style ->
            style.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                 .withHoverEvent(new HoverEvent.ShowText(hoverText))
        );
        sendComponent(source, comp);
        *///? } else if >=1.16 {
        Component hoverText = createLiteralComponent("\u00A7eClick to open URL:\n\u00A7b" + url);
        MutableComponent comp = createLiteralComponent(text).withStyle(style ->
            style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                 .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
        );
        sendComponent(source, comp);
        //? } else {
        /*Component hoverText = createLiteralComponent("\u00A7eClick to open URL:\n\u00A7b" + url);
        Component comp = createLiteralComponent(text).withStyle(style -> {
            style.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
        });
        sendComponent(source, comp);
        *///? }
    }

    private static void sendCopyableTextComponent(CommandSourceStack source, String text, String copyValue) {
        //? if >=26.1 {
        /*Component hoverText = createLiteralComponent("\u00A7eClick to copy command to clipboard");
        MutableComponent comp = createLiteralComponent(text).withStyle(style ->
            style.withClickEvent(new ClickEvent.CopyToClipboard(copyValue))
                 .withHoverEvent(new HoverEvent.ShowText(hoverText))
        );
        sendComponent(source, comp);
        *///? } else if >=1.16 {
        Component hoverText = createLiteralComponent("\u00A7eClick to copy command to clipboard");
        MutableComponent comp = createLiteralComponent(text).withStyle(style ->
            style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copyValue))
                 .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
        );
        sendComponent(source, comp);
        //? } else if >=1.15 {
        /*Component hoverText = createLiteralComponent("\u00A7eClick to copy command to clipboard");
        Component comp = createLiteralComponent(text).withStyle(style -> {
            style.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copyValue));
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
        });
        sendComponent(source, comp);
        *///? } else {
        /*Component hoverText = createLiteralComponent("\u00A7eClick to insert command into chat");
        Component comp = createLiteralComponent(text).withStyle(style -> {
            style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, copyValue));
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
        });
        sendComponent(source, comp);
        *///? }
    }
}
//?}
