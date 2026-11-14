package net.blazingpractice.command;

import net.blazingpractice.BlazingPractice;
import org.bukkit.command.PluginCommand;

import java.util.List;
import java.util.logging.Level;

/** Registers declared command roots from plugin.yml. Aliases are handled by Bukkit. */
public final class CommandRegistry {
    private static final List<String> ROOT_COMMANDS = List.of(
            "bp", "arena", "ladder", "setup", "duel", "accept", "settings", "practice",
            "staff", "spectate", "party", "matchinv", "event", "statistics", "preview",
            "divisions", "unranked", "ranked", "queue", "editor", "ffa", "copykit",
            "ignorequeue", "cosmetics", "customqueue", "matchhistory", "nick"
    );

    private CommandRegistry() { }

    public static void register(BlazingPractice plugin, CommandRouter router) {
        for (String name : ROOT_COMMANDS) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().log(Level.SEVERE, "Command /{0} is missing from plugin.yml", name);
                continue;
            }
            command.setExecutor(router);
            command.setTabCompleter(router);
        }
    }
}
