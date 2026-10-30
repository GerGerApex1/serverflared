package me.gergerapex1.serverflared.platform.spigot;

import java.util.List;
import me.gergerapex1.serverflared.command.CommandSenderAdapter;
import me.gergerapex1.serverflared.command.ServerFlaredCommand;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SpigotCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return ServerFlaredCommand.execute(wrapSender(sender), args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return ServerFlaredCommand.suggest(wrapSender(sender), args);
    }

    public static CommandSenderAdapter wrapSender(final CommandSender sender) {
        return new CommandSenderAdapter() {
            @Override
            public boolean isConsole() {
                return sender instanceof ConsoleCommandSender;
            }

            @Override
            public boolean hasPermission(String node, int opLevel) {
                return sender.isOp() || sender.hasPermission(node);
            }

            @Override
            public void sendMessage(String message) {
                sender.sendMessage(message);
            }

            @Override
            public void sendClickableUrl(String text, String url) {
                if (sender instanceof Player) {
                    try {
                        TextComponent component = new TextComponent(text);
                        component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                        component.setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder("\u00A7eClick to open URL:\n\u00A7b" + url).create()
                        ));
                        ((Player) sender).spigot().sendMessage(component);
                        return;
                    } catch (Throwable ignored) {
                    }
                }
                sender.sendMessage(text + " " + url);
            }

            @Override
            public void sendCopyableText(String text, String copyValue) {
                if (sender instanceof Player) {
                    try {
                        TextComponent component = new TextComponent(text);
                        ClickEvent.Action action;
                        try {
                            action = ClickEvent.Action.valueOf("COPY_TO_CLIPBOARD");
                        } catch (IllegalArgumentException e) {
                            action = ClickEvent.Action.SUGGEST_COMMAND;
                        }
                        component.setClickEvent(new ClickEvent(action, copyValue));
                        component.setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder("\u00A7eClick to copy command to clipboard").create()
                        ));
                        ((Player) sender).spigot().sendMessage(component);
                        return;
                    } catch (Throwable ignored) {
                    }
                }
                sender.sendMessage(copyValue);
            }
        };
    }
}
