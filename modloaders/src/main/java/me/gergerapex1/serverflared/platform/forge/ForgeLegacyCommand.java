package me.gergerapex1.serverflared.platform.forge;

//? if forge && legacy_forge && <1.9 {
/*import java.util.Collections;
import java.util.List;
import me.gergerapex1.serverflared.command.CommandSenderAdapter;
import me.gergerapex1.serverflared.command.PermissionHelper;
import me.gergerapex1.serverflared.command.ServerFlaredCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

public class ForgeLegacyCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return ServerFlaredCommand.COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + ServerFlaredCommand.COMMAND_NAME + " <status|reload|start|stop|login|clientcmd>";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList(ServerFlaredCommand.COMMAND_ALIAS);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return ServerFlaredCommand.REQUIRED_OP_LEVEL;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return ServerFlaredCommand.isAuthorized(wrapSender(sender));
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        ServerFlaredCommand.execute(wrapSender(sender), args);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        List<String> suggestions = ServerFlaredCommand.suggest(wrapSender(sender), args);
        return getListOfStringsMatchingLastWord(args, suggestions);
    }

    public static CommandSenderAdapter wrapSender(final ICommandSender sender) {
        return new CommandSenderAdapter() {
            @Override
            public boolean isConsole() {
                return sender instanceof MinecraftServer || (sender.getCommandSenderEntity() == null && "Server".equalsIgnoreCase(sender.getName()));
            }

            @Override
            public boolean hasPermission(String node, int opLevel) {
                Entity entity = sender.getCommandSenderEntity();
                if (entity instanceof EntityPlayer) {
                    Boolean lp = PermissionHelper.checkLuckPerms(entity.getUniqueID(), node);
                    if (Boolean.TRUE.equals(lp)) {
                        return true;
                    }
                }
                return sender.canCommandSenderUseCommand(opLevel, ServerFlaredCommand.COMMAND_NAME);
            }

            @Override
            public void sendMessage(String message) {
                sender.addChatMessage(new ChatComponentText(message));
            }

            @Override
            public void sendClickableUrl(String text, String url) {
                if (isConsole()) {
                    sendMessage(text + " " + url);
                    return;
                }
                ChatComponentText component = new ChatComponentText(text);
                ChatStyle style = new ChatStyle();
                style.setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("\u00A7eClick to open URL:\n\u00A7b" + url)));
                component.setChatStyle(style);
                sender.addChatMessage(component);
            }

            @Override
            public void sendCopyableText(String text, String copyValue) {
                if (isConsole()) {
                    sendMessage(copyValue);
                    return;
                }
                ChatComponentText component = new ChatComponentText(text);
                ChatStyle style = new ChatStyle();
                style.setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, copyValue));
                style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("\u00A7eClick to insert command into chat")));
                component.setChatStyle(style);
                sender.addChatMessage(component);
            }
        };
    }
}
*///?}

//? if forge && legacy_forge && >=1.9 && <1.11 {
/*import java.util.Collections;
import java.util.List;
import me.gergerapex1.serverflared.command.CommandSenderAdapter;
import me.gergerapex1.serverflared.command.PermissionHelper;
import me.gergerapex1.serverflared.command.ServerFlaredCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

public class ForgeLegacyCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return ServerFlaredCommand.COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/" + ServerFlaredCommand.COMMAND_NAME + " <status|reload|start|stop|login|clientcmd>";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList(ServerFlaredCommand.COMMAND_ALIAS);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return ServerFlaredCommand.REQUIRED_OP_LEVEL;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return ServerFlaredCommand.isAuthorized(wrapSender(sender));
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        ServerFlaredCommand.execute(wrapSender(sender), args);
    }

    @Override
    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        List<String> suggestions = ServerFlaredCommand.suggest(wrapSender(sender), args);
        return getListOfStringsMatchingLastWord(args, suggestions);
    }

    public static CommandSenderAdapter wrapSender(final ICommandSender sender) {
        return new CommandSenderAdapter() {
            @Override
            public boolean isConsole() {
                return sender instanceof MinecraftServer || (sender.getCommandSenderEntity() == null && "Server".equalsIgnoreCase(sender.getName()));
            }

            @Override
            public boolean hasPermission(String node, int opLevel) {
                Entity entity = sender.getCommandSenderEntity();
                if (entity instanceof EntityPlayer) {
                    Boolean lp = PermissionHelper.checkLuckPerms(entity.getUniqueID(), node);
                    if (Boolean.TRUE.equals(lp)) {
                        return true;
                    }
                }
                return sender.canCommandSenderUseCommand(opLevel, ServerFlaredCommand.COMMAND_NAME);
            }

            @Override
            public void sendMessage(String message) {
                sender.addChatMessage(new TextComponentString(message));
            }

            @Override
            public void sendClickableUrl(String text, String url) {
                if (isConsole()) {
                    sendMessage(text + " " + url);
                    return;
                }
                TextComponentString component = new TextComponentString(text);
                Style style = new Style();
                style.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString("\u00A7eClick to open URL:\n\u00A7b" + url)));
                component.setStyle(style);
                sender.addChatMessage(component);
            }

            @Override
            public void sendCopyableText(String text, String copyValue) {
                if (isConsole()) {
                    sendMessage(copyValue);
                    return;
                }
                TextComponentString component = new TextComponentString(text);
                Style style = new Style();
                style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, copyValue));
                style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString("\u00A7eClick to insert command into chat")));
                component.setStyle(style);
                sender.addChatMessage(component);
            }
        };
    }
}
*///?}

//? if forge && legacy_forge && >=1.11 {
/*import java.util.Collections;
import java.util.List;
import me.gergerapex1.serverflared.command.CommandSenderAdapter;
import me.gergerapex1.serverflared.command.PermissionHelper;
import me.gergerapex1.serverflared.command.ServerFlaredCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

public class ForgeLegacyCommand extends CommandBase {
    @Override
    public String getName() {
        return ServerFlaredCommand.COMMAND_NAME;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + ServerFlaredCommand.COMMAND_NAME + " <status|reload|start|stop|login|clientcmd>";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList(ServerFlaredCommand.COMMAND_ALIAS);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return ServerFlaredCommand.REQUIRED_OP_LEVEL;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return ServerFlaredCommand.isAuthorized(wrapSender(sender));
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        ServerFlaredCommand.execute(wrapSender(sender), args);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        List<String> suggestions = ServerFlaredCommand.suggest(wrapSender(sender), args);
        return getListOfStringsMatchingLastWord(args, suggestions);
    }

    public static CommandSenderAdapter wrapSender(final ICommandSender sender) {
        return new CommandSenderAdapter() {
            @Override
            public boolean isConsole() {
                return sender instanceof MinecraftServer || (sender.getCommandSenderEntity() == null && "Server".equalsIgnoreCase(sender.getName()));
            }

            @Override
            public boolean hasPermission(String node, int opLevel) {
                Entity entity = sender.getCommandSenderEntity();
                if (entity instanceof EntityPlayer) {
                    Boolean lp = PermissionHelper.checkLuckPerms(entity.getUniqueID(), node);
                    if (Boolean.TRUE.equals(lp)) {
                        return true;
                    }
                }
                return sender.canUseCommand(opLevel, ServerFlaredCommand.COMMAND_NAME);
            }

            @Override
            public void sendMessage(String message) {
                sender.sendMessage(new TextComponentString(message));
            }

            @Override
            public void sendClickableUrl(String text, String url) {
                if (isConsole()) {
                    sendMessage(text + " " + url);
                    return;
                }
                TextComponentString component = new TextComponentString(text);
                Style style = new Style();
                style.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString("\u00A7eClick to open URL:\n\u00A7b" + url)));
                component.setStyle(style);
                sender.sendMessage(component);
            }

            @Override
            public void sendCopyableText(String text, String copyValue) {
                if (isConsole()) {
                    sendMessage(copyValue);
                    return;
                }
                TextComponentString component = new TextComponentString(text);
                Style style = new Style();
                style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, copyValue));
                style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString("\u00A7eClick to insert command into chat")));
                component.setStyle(style);
                sender.sendMessage(component);
            }
        };
    }
}
*///?}
