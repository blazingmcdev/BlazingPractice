package net.blazingpractice.command;

import net.blazingpractice.BlazingPractice;
import net.blazingpractice.arena.Arena;
import net.blazingpractice.ladder.LadderRegistry;
import org.bukkit.Bukkit;
import net.blazingpractice.update.UpdateChecker;
import net.blazingpractice.world.WorldProfileManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandRouter implements CommandExecutor, TabCompleter {
    private final BlazingPractice plugin;
    private UpdateChecker updateChecker;

    public CommandRouter(BlazingPractice plugin) {
        this.plugin = plugin;
    }

    public void setUpdateChecker(UpdateChecker updateChecker) {
        this.updateChecker = updateChecker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("bp")) {
            return handleLegacyCommand(sender, command.getName(), args);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, args.length > 1 ? args[1] : null);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "about" -> sender.sendMessage(color("&aBlazingPractice &7Season 3 — Practice, Ranked/Unranked queues, FFA and dedicated duels. BedWars is planned, not active yet."));
            case "status" -> sendStatus(sender);
            case "diagnostics" -> {
                if (!sender.hasPermission("blazingpractice.admin.diagnostics")) {
                    sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
                    return true;
                }
                sendDiagnostics(sender);
            }
            case "updatecheck" -> {
                if (!sender.hasPermission("blazingpractice.admin.updates")) {
                    sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
                    return true;
                }
                if (updateChecker == null) {
                    sender.sendMessage(color("&cUpdate checker is not ready."));
                } else {
                    sender.sendMessage(color("&7Checking GitHub Releases..."));
                    updateChecker.checkNow().thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(color(result.message()))));
                }
            }
            case "lobby" -> handlePracticeRoot(sender, new String[]{"lobby", args.length > 1 ? args[1] : ""});
            case "world" -> handleWorldCommand(sender, args);
            case "arena" -> handleArenaCommand(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "practice" -> handlePracticeAdminCommand(sender, args);
            case "npc" -> handleNpcCommand(sender, args);
            case "selector" -> handleSelectorCommand(sender, args);
            case "bedwars" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                    sender.sendMessage(color("&eBedWars module: planned; gameplay is not included in this foundation milestone."));
                } else {
                    sender.sendMessage(color("&7Usage: /bp bedwars status"));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }


    private boolean handleLegacyCommand(CommandSender sender, String name, String[] args) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "blazingpractice" -> {
                handlePracticeRoot(sender, args);
                return true;
            }
            case "practice", "prac", "zonepractice", "zoneprac", "zonep" -> {
                handlePracticeRoot(sender, args);
                return true;
            }
            case "arena" -> {
                handleArenaCommand(sender, args);
                return true;
            }
            case "duel" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, "duel"); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 1 || args.length > 2) {
                    sender.sendMessage(color("&eUsage: /duel <online-player> [ladder]"));
                    sender.sendMessage(color("&7Example: /duel Steve sword"));
                    sender.sendMessage(color("&7Use /duel <online-player> to open the ladder selector, or add a ladder ID for a direct invite."));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) { sender.sendMessage(color("&cThat player is not online.")); return true; }
                if (args.length == 1) plugin.selector().openDuelLadderMenu(player, target);
                else if (!plugin.duels().invite(player, target, args[1])) sender.sendMessage(color("&cDuel request could not be sent. Check the ladder, match status and target settings."));
                return true;
            }
            case "accept" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, "accept"); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                plugin.duels().accept(player, args.length > 0 ? args[0] : null); return true;
            }
            case "ranked", "unranked" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, name.toLowerCase(Locale.ROOT)); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
                    plugin.selector().openLadderMenu(player, name.toLowerCase(Locale.ROOT));
                } else if (args[0].equalsIgnoreCase("leave")) {
                    plugin.queues().leave(player);
                } else if (!plugin.queues().join(player, name.toLowerCase(Locale.ROOT), args[0])) {
                    sender.sendMessage(color("&cCould not join. Choose an enabled ladder from /ladder list."));
                }
                return true;
            }
            case "queue", "que" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, "queue"); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length == 0) {
                    sender.sendMessage(color("&e/queue <ranked|unranked> [ladder] | /queue join <ranked|unranked> <ladder> | /queue leave | /queue list"));
                    sender.sendMessage(color("&7Example: /queue ranked sword"));
                    return true;
                }
                String queueAction = args[0].toLowerCase(Locale.ROOT);
                switch (queueAction) {
                    case "leave", "exit" -> plugin.queues().leave(player);
                    case "list", "status" -> {
                        player.sendMessage(color("&aQueues by type and ladder:"));
                        plugin.queues().snapshot().forEach((queue, count) ->
                                player.sendMessage(color("&7- &f" + queue + " &8| &f" + count + " waiting")));
                    }
                    case "ranked", "unranked" -> {
                        if (args.length == 1) {
                            plugin.selector().openLadderMenu(player, queueAction);
                        } else if (args.length == 2 && !plugin.queues().join(player, queueAction, args[1])) {
                            sender.sendMessage(color("&cQueue join failed. Check the ladder, Practice world and arena setup."));
                        } else if (args.length > 2) {
                            sender.sendMessage(color("&eUsage: /queue <ranked|unranked> [ladder]"));
                        }
                    }
                    case "join" -> {
                        if (args.length < 3 || args.length > 3) {
                            player.sendMessage(color("&eUsage: /queue join <ranked|unranked> <ladder>"));
                            player.sendMessage(color("&7Example: /queue join ranked sword"));
                        } else if (!plugin.queues().join(player, args[1], args[2])) {
                            player.sendMessage(color("&cQueue join failed. Check the type, ladder, Practice world and arena setup."));
                        }
                    }
                    default -> player.sendMessage(color("&e/queue <ranked|unranked> [ladder] | join <ranked|unranked> <ladder> | leave | list"));
                }
                return true;
            }
            case "ladder" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, "ladder"); return true; }
                if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                    sender.sendMessage(color("&aAvailable BlazingPractice ladders:"));
                    for (var ladder : plugin.ladders().all()) {
                        sender.sendMessage(color((ladder.enabled() && ladder.queueEnabled() ? "&a" : "&c")
                                + ladder.id() + " &7- &f" + ladder.name() + " &8[" + ladder.type() + "]"));
                    }
                    sender.sendMessage(color("&7Queue: &f/ranked <ladder> &7or &f/unranked <ladder>"));
                    return true;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "info" -> {
                        if (args.length < 2) { sender.sendMessage(color("&e/ladder info <ladder>")); return true; }
                        var found = plugin.ladders().find(args[1]);
                        if (found.isEmpty()) { sender.sendMessage(color("&cLadder not found.")); return true; }
                        var ladder = found.get();
                        sender.sendMessage(color("&a" + ladder.name() + " &7(" + ladder.id() + ")"));
                        sender.sendMessage(color("&7Type: &f" + ladder.type() + " &7| Enabled: &f" + ladder.enabled()));
                        sender.sendMessage(color("&7Ranked: &f" + ladder.ranked() + " &7| Unranked: &f" + ladder.unranked()));
                    }
                    case "reload" -> {
                        if (!hasWorldAdmin(sender)) { sender.sendMessage(color("&cNo permission.")); return true; }
                        plugin.ladders().reload();
                        sender.sendMessage(color("&aReloaded &f" + plugin.ladders().all().size() + " &aladder definitions."));
                    }
                    case "create" -> {
                        if (!hasWorldAdmin(sender)) { sender.sendMessage(color("&cNo permission.")); return true; }
                        if (args.length < 2 || !plugin.ladders().create(args[1])) sender.sendMessage(color("&eUsage: /ladder create <name>"));
                        else sender.sendMessage(color("&aCreated disabled ladder &f" + args[1] + "&a. Configure it before enabling."));
                    }
                    case "clone" -> {
                        if (!hasWorldAdmin(sender)) { sender.sendMessage(color("&cNo permission.")); return true; }
                        if (args.length < 3 || !plugin.ladders().cloneLadder(args[1], args[2])) sender.sendMessage(color("&eUsage: /ladder clone <ladder> <new-name>"));
                        else sender.sendMessage(color("&aCloned &f" + args[1] + " &ato &f" + args[2] + "&a. The clone is disabled until enabled."));
                    }
                    case "delete" -> {
                        if (!hasWorldAdmin(sender)) { sender.sendMessage(color("&cNo permission.")); return true; }
                        if (args.length < 2 || !plugin.ladders().delete(args[1])) sender.sendMessage(color("&eUsage: /ladder delete <ladder>"));
                        else sender.sendMessage(color("&aLadder deleted."));
                    }
                    case "set" -> {
                        if (!hasWorldAdmin(sender)) { sender.sendMessage(color("&cNo permission.")); return true; }
                        if (args.length < 3 || !args[1].equalsIgnoreCase("icon") || !(sender instanceof Player player)) {
                            sender.sendMessage(color("&eUsage: hold an item and run /ladder set icon <ladder>"));
                        } else if (player.getInventory().getItemInMainHand().getType().isAir()
                                || !plugin.ladders().setIcon(args[2], player.getInventory().getItemInMainHand().getType())) {
                            sender.sendMessage(color("&cCould not set that ladder icon. Hold a valid item first."));
                        } else sender.sendMessage(color("&aLadder icon updated from your held item."));
                    }
                    case "enable", "disable" -> {
                        if (!hasWorldAdmin(sender)) { sender.sendMessage(color("&cNo permission.")); return true; }
                        if (args.length < 2) { sender.sendMessage(color("&e/ladder enable|disable <ladder>")); return true; }
                        boolean enabled = args[0].equalsIgnoreCase("enable");
                        sender.sendMessage(plugin.ladders().setEnabled(args[1], enabled)
                                ? color("&aLadder " + args[1] + (enabled ? " enabled." : " disabled."))
                                : color("&cCould not update that ladder."));
                    }
                    default -> sender.sendMessage(color("&e/ladder list | info <ladder> | create <name> | clone <ladder> <new-name> | delete <ladder> | set icon <ladder> | enable/disable <ladder> | reload"));
                }
                return true;
            }
            case "ffa" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, "ffa"); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                String action = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
                switch (action) {
                    case "join", "play" -> {
                        if (args.length >= 2) plugin.ffa().join(player, args[1]);
                        else if (plugin.selector() != null) plugin.selector().openFfaMenu(player);
                        else plugin.ffa().join(player);
                    }
                    case "leave", "exit" -> plugin.ffa().leave(player);
                    case "setspawn", "set-spawn" -> {
                        if (!sender.hasPermission("zpp.setup") && !sender.isOp()) {
                            sender.sendMessage(color("&cYou need zpp.setup permission to set the FFA spawn."));
                        } else plugin.ffa().setSpawn(player);
                    }
                    case "status", "info" -> sender.sendMessage(color("&6FFA &7| World: &f" +
                            plugin.getConfig().getString("selector.destinations.ffa-world", "ffa") +
                            " &7| Online: &f" + plugin.ffa().onlineCount()));
                    default -> sender.sendMessage(color("&eUsage: /ffa join [mode]|leave|setspawn|status"));
                }
                return true;
            }
            case "party", "p" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, "party"); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (args.length == 0 || args[0].equalsIgnoreCase("info")) { sender.sendMessage(plugin.parties().info(player)); return true; }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "create" -> sender.sendMessage(plugin.parties().create(player) ? color("&aParty created.") : color("&cYou are already in a party."));
                    case "invite" -> {
                        if (args.length < 2) { sender.sendMessage(color("&eUsage: /party invite <player>")); break; }
                        Player target = Bukkit.getPlayerExact(args[1]);
                        sender.sendMessage(target != null && plugin.parties().invite(player, target) ? color("&aInvitation sent.") : color("&cCould not invite that player."));
                    }
                    case "accept" -> sender.sendMessage(plugin.parties().accept(player) ? color("&aYou joined the party.") : color("&cNo valid party invitation."));
                    case "leave" -> sender.sendMessage(plugin.parties().leave(player) ? color("&eYou left the party.") : color("&cYou are not in a party."));
                    case "chat" -> {
                        if (args.length < 2 || !plugin.parties().chat(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))))
                            sender.sendMessage(color("&cUsage: /party chat <message> (you must be in a party)"));
                    }
                    default -> sender.sendMessage(color("&e/party create|invite <player>|accept|leave|info|chat <message>"));
                }
                return true;
            }
            case "statistics", "stats", "stat" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) { sendCommandHelp(sender, "statistics"); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                Player target = player;
                String ladderId = "sword";
                if (args.length > 0) {
                    if (plugin.ladders().find(args[0]).isPresent()) ladderId = args[0];
                    else {
                        target = Bukkit.getPlayerExact(args[0]);
                        if (target == null) { sender.sendMessage(color("&cThat player must be online for this statistics lookup.")); return true; }
                    }
                }
                if (args.length > 1) ladderId = args[1];
                String selectedLadder = ladderId;
                Player selectedTarget = target;
                if (plugin.ladders().find(selectedLadder).isEmpty()) { sender.sendMessage(color("&cUnknown ladder. Use /ladder list.")); return true; }
                plugin.database().getRating(selectedTarget.getUniqueId(), selectedLadder).thenAccept(rating -> Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(color("&aStats for &f" + selectedTarget.getName() + " &7[" + selectedLadder + "]&a: Rating &f" + rating.rating() + " &7| W " + rating.wins() + " / L " + rating.losses() + " | Streak " + rating.streak() + " | Peak " + rating.peak()))));
                return true;
            }
            default -> {
                sendCompatibilityPending(sender, name);
                return true;
            }
        }
    }


    private void handleArenaCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendArenaHelp(sender);
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            sender.sendMessage(color("&aDedicated duel arena pools:"));
            for (var ladder : plugin.ladders().all()) {
                int total = plugin.arenas().configuredCount(ladder.id());
                int ready = plugin.arenas().readyCount(ladder.id());
                int busy = plugin.arenas().busyCount(ladder.id());
                sender.sendMessage(color("&7- &f" + ladder.id() + " &8| registered: &f" + total
                        + " &8| ready: &a" + ready + " &8| busy: &e" + busy
                        + " &8| target: &f" + plugin.arenas().minArenas(ladder.id())
                        + "-" + plugin.arenas().maxArenas(ladder.id())));
            }
            for (Arena arena : plugin.arenas().all()) {
                boolean busy = plugin.arenas().isBusy(arena.name());
                String status = busy ? "&eBUSY" : plugin.arenas().validationError(arena) == null ? "&aREADY" : "&cNOT READY";
                sender.sendMessage(color("&8  • &f" + arena.name() + " &7[" + arena.ladder()
                        + "] &8region=&f" + arena.region() + " &8" + status));
            }
            sender.sendMessage(color("&7WorldGuard: &f" + plugin.arenas().worldGuardStatus()));
            return;
        }

        if (action.equals("info")) {
            if (args.length < 2) {
                sender.sendMessage(color("&eUsage: /blazingpractice:arena info <id>"));
                return;
            }
            var found = plugin.arenas().get(args[1]);
            if (found.isEmpty()) {
                sender.sendMessage(color("&cArena not found."));
                return;
            }
            Arena arena = found.get();
            sender.sendMessage(color("&aArena &f" + arena.name()));
            sender.sendMessage(color("&7Ladder: &f" + arena.ladder() + " &7| Region: &f" + arena.region()));
            sender.sendMessage(color("&7World: &f" + (arena.world() == null ? "not loaded" : arena.world().getName())));
            sender.sendMessage(color("&7Enabled: &f" + arena.enabled() + " &7| Busy: &f" + plugin.arenas().isBusy(arena.name())));
            sender.sendMessage(color("&7Spawn 1: &f" + (arena.spawnOne() == null ? "not set" : formatLocation(arena.spawnOne()))));
            sender.sendMessage(color("&7Spawn 2: &f" + (arena.spawnTwo() == null ? "not set" : formatLocation(arena.spawnTwo()))));
            String problem = plugin.arenas().validationError(arena);
            sender.sendMessage(color(problem == null ? "&aArena is valid and ready." : "&cNot ready: " + problem));
            return;
        }

        if (action.equals("reload")) {
            if (!hasArenaAdmin(sender)) {
                sender.sendMessage(color("&cNo permission."));
                return;
            }
            plugin.arenas().reload();
            sender.sendMessage(color("&aReloaded &f" + plugin.arenas().all().size()
                    + " &aarena definitions. Active arena reservations remain protected."));
            return;
        }

        if (!hasArenaAdmin(sender)) {
            sender.sendMessage(color("&cNo permission. Required: blazingpractice.admin.arenas"));
            return;
        }

        if (action.equals("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return;
            }
            if (args.length < 4) {
                sender.sendMessage(color("&eUsage: /blazingpractice:arena create <id> <ladder> <worldguard-region>"));
                return;
            }
            var result = plugin.arenas().create(args[1], args[2], args[3], player.getWorld());
            sender.sendMessage(color(result.message()));
            return;
        }

        if (action.equals("setspawn1") || action.equals("setspawn2")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return;
            }
            if (args.length < 2) {
                sender.sendMessage(color("&eUsage: /blazingpractice:arena " + action + " <id>"));
                return;
            }
            String point = action.equals("setspawn1") ? "spawn-one" : "spawn-two";
            var result = plugin.arenas().setPoint(args[1], point, player.getLocation());
            sender.sendMessage(color(result.message()));
            return;
        }

        if (action.equals("enable") || action.equals("disable")) {
            if (args.length < 2) {
                sender.sendMessage(color("&eUsage: /blazingpractice:arena " + action + " <id>"));
                return;
            }
            var result = plugin.arenas().setEnabled(args[1], action.equals("enable"));
            sender.sendMessage(color(result.message()));
            return;
        }

        if (action.equals("delete")) {
            if (args.length < 2) {
                sender.sendMessage(color("&eUsage: /blazingpractice:arena delete <id>"));
                return;
            }
            var result = plugin.arenas().delete(args[1]);
            sender.sendMessage(color(result.message()));
            return;
        }

        sendArenaHelp(sender);
    }

    private boolean hasArenaAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("blazingpractice.admin.arenas")
                || sender.hasPermission("zpp.setup");
    }

    private String formatLocation(org.bukkit.Location location) {
        return location.getWorld().getName() + " "
                + String.format(Locale.US, "%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }

    private void sendArenaHelp(CommandSender sender) {
        sender.sendMessage(color("&a&lBlazingPractice &7- Dedicated duel arena commands"));
        if (hasArenaAdmin(sender)) {
            sender.sendMessage(color("&e/blazingpractice:arena create <id> <ladder> <region> &7- Register a WorldGuard region"));
            sender.sendMessage(color("&e/blazingpractice:arena setspawn1 <id> &7- Set Player 1 spawn inside the region"));
            sender.sendMessage(color("&e/blazingpractice:arena setspawn2 <id> &7- Set Player 2 spawn inside the region"));
            sender.sendMessage(color("&e/blazingpractice:arena enable <id> &7- Validate and enable the arena"));
            sender.sendMessage(color("&e/blazingpractice:arena disable <id> &7- Disable an arena"));
            sender.sendMessage(color("&e/blazingpractice:arena delete <id> &7- Delete an arena registration"));
            sender.sendMessage(color("&e/blazingpractice:arena reload &7- Reload arenas.yml"));
        }
        sender.sendMessage(color("&e/blazingpractice:arena list &7- Show ladder pools and arena status"));
        sender.sendMessage(color("&e/blazingpractice:arena info <id> &7- Show arena configuration"));
        sender.sendMessage(color("&7Use a WorldGuard region inside the shared &fpractice &7world. Player 1 uses spawn1; Player 2 uses spawn2."));
        sender.sendMessage(color("&7Alias: &f/bp arena ..."));
    }

    private boolean hasWorldAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("blazingpractice.admin.worlds")
                || sender.hasPermission("zpp.setup");
    }

    private void handleWorldCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("help")) {
            sendCommandHelp(sender, "world");
            return;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("status") || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(color("&aBlazingPractice world roles:"));
            for (String role : List.of("lobby", "pvp", "practice", "ffa", "survival")) {
                List<String> names = plugin.worldProfiles().configuredWorldNames(role);
                String display = names.isEmpty() ? "<not configured>" : String.join(", ", names);
                sender.sendMessage(color("&7- &e" + role + "&7: &f" + display));
            }
            sender.sendMessage(color("&7Recommended: stand in your single Practice+FFA world and run &f/bp world set pvp"));
            return;
        }
        if (!hasWorldAdmin(sender)) {
            sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cRun /bp world set from inside the world you want to assign."));
            return;
        }
        if (!args[1].equalsIgnoreCase("set") || args.length < 3) {
            sender.sendMessage(color("&eUsage: /bp world status | set <lobby|pvp|survival>"));
            return;
        }
        String role = args[2].toLowerCase(Locale.ROOT);
        switch (role) {
            case "pvp" -> {
                if (!plugin.worldProfiles().setPvpWorld(player.getWorld())) {
                    sender.sendMessage(color("&cThis world is already assigned to Survival or the BlazingPractice lobby. Choose a dedicated PvP world."));
                } else {
                    plugin.worldProfiles().setPracticeSpawn(player);
                    sender.sendMessage(color("&aShared PvP world set to &f" + player.getWorld().getName()
                            + "&a. Practice and FFA now use this same world. The Practice spawn was saved here; stand at the FFA spawn and run &f/ffa setspawn&a."));
                }
            }
            case "lobby" -> {
                if (plugin.lobby().set(player)) {
                    if (plugin.selector() != null) plugin.selector().ensureForWorld(player);
                    sender.sendMessage(color("&aLobby world and exact lobby location set to &f" + player.getWorld().getName()));
                } else sender.sendMessage(color("&cThis world is already assigned to Survival or the shared PvP role. Choose a separate lobby world."));
            }
            case "practice", "ffa" -> sender.sendMessage(color(
                    "&cThis server uses one shared PvP world for both modes. Stand inside that world and run " +
                    "&f/bp world set pvp&c. Set the two locations separately with " +
                    "&f/bp practice setspawn &cand &f/ffa setspawn&c."));
            case "survival" -> {
                if (!plugin.worldProfiles().setWorldRole("survival", player.getWorld())) {
                    sender.sendMessage(color("&cThis world is already assigned to Lobby or the shared PvP role."));
                } else sender.sendMessage(color("&aAdded &f" + player.getWorld().getName() + " &ato the Survival inventory profile."));
            }
            default -> sender.sendMessage(color("&eRoles: lobby, pvp, survival (Practice and FFA share pvp)"));
        }
    }

    private void handlePracticeAdminCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("help")) {
            sendPracticeHelp(sender);
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player player && plugin.worldProfiles().isPracticeWorld(player.getWorld())) {
                plugin.selector().openPracticeMenu(player);
            } else sender.sendMessage(color("&eUsage: /bp practice setspawn | /bp practice join"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "ranked", "unranked" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (args.length < 3) plugin.selector().openLadderMenu(player, type);
                else if (!plugin.queues().join(player, type, args[2])) sender.sendMessage(color("&cCould not join that ladder queue."));
            }
            case "setspawn" -> {
                if (!hasWorldAdmin(sender)) { sender.sendMessage(color("&cNo permission.")); return; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
                sender.sendMessage(plugin.worldProfiles().setPracticeSpawn(player)
                        ? color("&aPractice spawn saved in &f" + player.getWorld().getName())
                        : color("&cStand in the shared PvP world first. Use /bp world set pvp if needed."));
            }
            case "join", "enter" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
                if (plugin.ffa() != null && plugin.ffa().isActive(player)) {
                    sender.sendMessage(color("&cLeave FFA first with /ffa leave.")); return;
                }
                if (!plugin.worldProfiles().teleportToPractice(player)) sender.sendMessage(color("&cShared PvP world is not loaded. Check /bp world status."));
                else Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.worldProfiles().isPracticeWorld(player.getWorld())) plugin.selector().openPracticeMenu(player);
                }, 6L);
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
                if (plugin.worldProfiles().isLobbyWorld(player.getWorld())) plugin.selector().openLobbyModeMenu(player);
                else plugin.selector().openPracticeMenu(player);
            }
            default -> sender.sendMessage(color("&eUsage: /bp practice setspawn | join | menu"));
        }
    }

    private void handleNpcCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("help")) {
            sendCommandHelp(sender, "npc");
            return;
        }
        if (!hasWorldAdmin(sender) && !sender.hasPermission("blazingpractice.selector.admin")
                && !sender.hasPermission("blazingpractice.admin.npc")) {
            sender.sendMessage(color(plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
            return;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(color("&aBlazingPractice lobby NPCs:"));
            plugin.npcs().list().forEach(entry -> sender.sendMessage(color("&7- &f" + entry)));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
                if (args.length < 3 || !plugin.npcs().create(player, args[2])) {
                    sender.sendMessage(color("&eUsage: /bp npc create <survival|practice|discord|hosting>"));
                }
            }
            case "remove", "delete" -> {
                if (args.length < 3 || !plugin.npcs().remove(args[2])) sender.sendMessage(color("&eUsage: /bp npc remove <id>"));
                else sender.sendMessage(color("&aNPC removed."));
            }
            case "url", "seturl" -> {
                if (args.length < 4 || !plugin.npcs().setUrl(args[2], args[3])) {
                    sender.sendMessage(color("&eUsage: /bp npc url <discord|hosting> <https-url>"));
                } else sender.sendMessage(color("&aNPC link updated."));
            }
            default -> sender.sendMessage(color("&e/bp npc create <survival|practice|discord|hosting> | remove <id> | list | url <discord|hosting> <https-url>"));
        }
    }

    private void handleSelectorCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("help")) {
            sendCommandHelp(sender, "selector");
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) plugin.selector().openServerSelector(player);
            else sender.sendMessage(color("&eUsage: /bp selector give | menu | reload | block set|clear|info"));
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("blazingpractice.selector.admin") && !sender.hasPermission("blazingpractice.admin") && !sender.isOp()) {
                sender.sendMessage(color("&cNo permission.")); return;
            }
            if (plugin.selector().reloadSelectorConfig()) {
                plugin.selector().refreshOnlinePlayers();
                sender.sendMessage(color("&aSelector configuration reloaded from plugins/BlazingPractice/selector/selector.yml."));
            } else sender.sendMessage(color("&cCould not reload selector/selector.yml. Check the server console."));
            return;
        }
        if (sub.equals("give")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
            if (!sender.hasPermission("blazingpractice.selector.admin") && !sender.hasPermission("blazingpractice.admin") && !sender.isOp()) {
                sender.sendMessage(color("&cNo permission.")); return;
            }
            plugin.selector().giveSelector(player);
            return;
        }
        if (!sub.equals("block")) {
            sender.sendMessage(color("&eUsage: /bp selector give | menu | reload | block set|clear|info"));
            return;
        }
        if (!hasWorldAdmin(sender) && !sender.hasPermission("blazingpractice.selector.admin")
                && !sender.hasPermission("blazingpractice.admin.npc")) {
            sender.sendMessage(color("&cNo permission.")); return;
        }
        if (args.length < 3) {
            sender.sendMessage(color("&eUsage: /bp selector block set|clear|info")); return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
                org.bukkit.block.Block block = player.getTargetBlockExact(6);
                if (!plugin.selector().setSelectorBlock(block)) {
                    sender.sendMessage(color("&cLook at a wooden block within 6 blocks in the configured lobby."));
                } else {
                    sender.sendMessage(color("&aServer-selector block saved at &f" + block.getWorld().getName()
                            + " &7(" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")."));
                }
            }
            case "clear", "remove" -> {
                plugin.selector().clearSelectorBlock();
                sender.sendMessage(color("&eServer-selector block disabled."));
            }
            case "info" -> sender.sendMessage(color("&aServer-selector block: &f" + plugin.selector().selectorBlockInfo()));
            default -> sender.sendMessage(color("&eUsage: /bp selector block set|clear|info"));
        }
    }

    private void handlePracticeRoot(CommandSender sender, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("ranked") || args[0].equalsIgnoreCase("unranked"))) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
            String type = args[0].toLowerCase(Locale.ROOT);
            if (args.length < 2) plugin.selector().openLadderMenu(player, type);
            else if (!plugin.queues().join(player, type, args[1])) sender.sendMessage(color("&cCould not join that ladder queue."));
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player && plugin.selector() != null) {
                if (plugin.worldProfiles().isLobbyWorld(player.getWorld())) plugin.selector().openLobbyModeMenu(player);
                else plugin.selector().openPracticeMenu(player);
            } else {
                sendPracticeHelp(sender);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sendPracticeHelp(sender);
            return;
        }
        if (args[0].equalsIgnoreCase("lobby")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("help")) {
                sendCommandHelp(sender, "lobby");
                return;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
                if (!hasWorldAdmin(sender) && !sender.hasPermission("zpp.practice.lobby")) { sender.sendMessage(color("&cNo permission.")); return; }
                if (!plugin.lobby().set(player)) {
                    sender.sendMessage(color("&cYou cannot set the BlazingPractice lobby inside a configured Survival world. Choose a separate lobby world so Survival stays untouched."));
                    return;
                }
                if (plugin.selector() != null) plugin.selector().ensureForWorld(player);
                sender.sendMessage(color("&aBlazingPractice lobby saved at &f" + player.getWorld().getName() + " &7(" +
                        String.format(Locale.US, "%.1f, %.1f, %.1f", player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()) + ")"));
                return;
            }
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return; }
            if (!plugin.lobby().teleport(player)) { sender.sendMessage(color("&cBlazingPractice lobby is not set. Stand at the desired lobby location and run /blazingpractice lobby set.")); return; }
            sender.sendMessage(color("&aTeleported to the configured BlazingPractice lobby."));
            return;
        }
        if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return;
        }
        sendPracticeHelp(sender);
    }

    private void sendPracticeHelp(CommandSender sender) {
        sender.sendMessage(color("&a&lBlazingPractice &7- Practice help"));
        sender.sendMessage(color("&e/practice &7- Open the Practice menu"));
        sender.sendMessage(color("&e/practice ranked [ladder] &7- Open or join Ranked"));
        sender.sendMessage(color("&e/practice unranked [ladder] &7- Open or join Unranked"));
        sender.sendMessage(color("&e/practice lobby &7- Return to the configured BlazingPractice lobby"));
        sender.sendMessage(color("&e/practice help &7- Show this page"));
        sender.sendMessage(color("&7Tip: use &fTAB &7after a command argument to see valid online players or enabled ladders."));
        sender.sendMessage(color("&7More help: &f/bp help <command>"));
    }

    private void sendHelp(CommandSender sender) {
        sendHelp(sender, null);
    }

    private void sendHelp(CommandSender sender, String topic) {
        if (topic != null && !topic.isBlank()) {
            String normalized = topic.toLowerCase(Locale.ROOT).replaceFirst("^/", "");
            sendCommandHelp(sender, normalized);
            return;
        }
        sender.sendMessage(color("&8&m------------------------------------------------"));
        sender.sendMessage(color("&a&lBlazingPractice &7Season 3 &f" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(color("&7Player commands — use &fTAB &7to complete arguments:"));
        sender.sendMessage(color("&e/practice &7- Open Practice mode selection"));
        sender.sendMessage(color("&e/ranked [ladder] &7- Join a Ranked ladder queue"));
        sender.sendMessage(color("&e/unranked [ladder] &7- Join an Unranked ladder queue"));
        sender.sendMessage(color("&e/queue <ranked|unranked> [ladder] &7- Open a queue menu or join directly"));
        sender.sendMessage(color("&e/queue leave|list &7- Leave your queue or see waiting counts"));
        sender.sendMessage(color("&e/duel <player> [ladder] &7- Choose a duel ladder or send a direct challenge"));
        sender.sendMessage(color("&e/accept [player] &7- Accept a pending duel request"));
        sender.sendMessage(color("&e/ffa join [ladder]|leave|status &7- Join or leave Free-for-All"));
        sender.sendMessage(color("&e/party create|invite|accept|leave|info|chat &7- Party tools"));
        sender.sendMessage(color("&e/statistics [player] [ladder] &7- View your or an online player's ladder stats"));
        sender.sendMessage(color("&e/ladder list|info <ladder> &7- Browse available ladders"));
        sender.sendMessage(color("&e/bp status &7- Show plugin, server and current-world status"));
        sender.sendMessage(color("&e/bp about &7- Show plugin information"));
        sender.sendMessage(color("&e/bp help <command> &7- Show detailed help for one command"));
        if (hasWorldAdmin(sender) || hasArenaAdmin(sender)
                || sender.hasPermission("blazingpractice.admin.npc")
                || sender.hasPermission("blazingpractice.selector.admin")) {
            sender.sendMessage(color("&8&m------------------------------------------------"));
            sender.sendMessage(color("&c&lAdministrator commands"));
            if (hasWorldAdmin(sender)) {
                sender.sendMessage(color("&e/bp world status|set <lobby|pvp|survival> &7- Assign world roles"));
                sender.sendMessage(color("&e/bp practice setspawn|join|menu &7- Manage the shared Practice world"));
                sender.sendMessage(color("&e/bp lobby [set] &7- Teleport to or set the configured BlazingPractice lobby"));
                sender.sendMessage(color("&e/ladder create|clone|enable|disable|delete|reload &7- Manage ladders"));
            }
            if (hasArenaAdmin(sender)) sender.sendMessage(color("&e/blazingpractice:arena help &7- Dedicated duel arena setup"));
            if (hasWorldAdmin(sender) || sender.hasPermission("blazingpractice.admin.npc")) {
                sender.sendMessage(color("&e/bp npc create|remove|list|url &7- Manage BlazingPractice NPCs"));
            }
            if (sender.hasPermission("blazingpractice.selector.admin") || sender.hasPermission("blazingpractice.admin")) {
                sender.sendMessage(color("&e/bp selector give|reload|block &7- Manage the Lobby selector"));
            }
            if (sender.hasPermission("blazingpractice.admin.diagnostics")) sender.sendMessage(color("&e/bp diagnostics &7- Inspect runtime dependencies and setup"));
            if (sender.hasPermission("blazingpractice.admin.updates")) sender.sendMessage(color("&e/bp updatecheck &7- Check the configured release feed"));
        }
        sender.sendMessage(color("&8&m------------------------------------------------"));
        sender.sendMessage(color("&7Some registered compatibility commands are not implemented in this build. Run &f/bp help features &7to see their status."));
        sender.sendMessage(color("&7For command details, use &f/bp help duel&7, &f/bp help queue&7, &f/bp help ffa&7, etc."));
    }

    private void sendCommandHelp(CommandSender sender, String topic) {
        String key = topic.toLowerCase(Locale.ROOT).replaceFirst("^/", "");
        switch (key) {
            case "about" -> sender.sendMessage(color("&aBlazingPractice &7Season 3 &f" + plugin.getPluginMeta().getVersion() + " &8- &7Practice, Ranked/Unranked queues, FFA and dedicated duels."));
            case "status" -> sendStatus(sender);
            case "bedwars" -> sender.sendMessage(color("&eBedWars is a planned module in this build; BedWars gameplay is not available yet."));
            case "diagnostics" -> {
                if (!sender.hasPermission("blazingpractice.admin.diagnostics")) sender.sendMessage(color("&cYou do not have permission to view diagnostics."));
                else sender.sendMessage(color("&eUse &f/bp diagnostics &efor the live diagnostic report."));
            }
            case "updatecheck" -> {
                if (!sender.hasPermission("blazingpractice.admin.updates")) sender.sendMessage(color("&cYou do not have permission to run update checks."));
                else sender.sendMessage(color("&eUse &f/bp updatecheck &eto check the configured release source."));
            }
            case "player" -> {
                sender.sendMessage(color("&a&lBlazingPractice &7- Player commands"));
                sender.sendMessage(color("&e/practice &7- Open Practice/Ranked/Unranked selection"));
                sender.sendMessage(color("&e/ranked [ladder] &7- Open Ranked picker or join a ladder"));
                sender.sendMessage(color("&e/unranked [ladder] &7- Open Unranked picker or join a ladder"));
                sender.sendMessage(color("&e/queue leave|list &7- Leave queue or view waiting counts"));
                sender.sendMessage(color("&e/duel <player> [ladder] &7- Challenge an online Practice player"));
                sender.sendMessage(color("&e/accept [player] &7- Accept a duel request"));
                sender.sendMessage(color("&e/ffa join [ladder]|leave|status &7- FFA controls"));
                sender.sendMessage(color("&e/party create|invite|accept|leave|info|chat &7- Party controls"));
                sender.sendMessage(color("&e/statistics [player] [ladder] &7- Ladder statistics"));
            }
            case "admin", "administrator" -> {
                if (!hasWorldAdmin(sender) && !hasArenaAdmin(sender)
                        && !sender.hasPermission("blazingpractice.admin.npc")
                        && !sender.hasPermission("blazingpractice.selector.admin")) {
                    sender.sendMessage(color("&cYou do not have access to administrator help."));
                    return;
                }
                sender.sendMessage(color("&c&lBlazingPractice &7- Administrator commands"));
                if (hasWorldAdmin(sender)) {
                    sender.sendMessage(color("&e/bp world status|set <lobby|pvp|survival> &7- World roles"));
                    sender.sendMessage(color("&e/bp practice setspawn|join|menu &7- Practice spawn and entry"));
                    sender.sendMessage(color("&e/bp lobby [set] &7- BlazingPractice lobby location"));
                    sender.sendMessage(color("&e/ladder create|clone|delete|enable|disable|reload &7- Ladder setup"));
                }
                if (hasArenaAdmin(sender)) sender.sendMessage(color("&e/blazingpractice:arena create|setspawn1|setspawn2|enable|disable|delete|list|info|reload"));
                if (hasWorldAdmin(sender) || sender.hasPermission("blazingpractice.admin.npc")) sender.sendMessage(color("&e/bp npc create|remove|list|url &7- NPC management"));
                if (sender.hasPermission("blazingpractice.selector.admin") || sender.hasPermission("blazingpractice.admin")) sender.sendMessage(color("&e/bp selector give|menu|reload|block &7- Selector management"));
                if (sender.hasPermission("blazingpractice.admin.diagnostics")) sender.sendMessage(color("&e/bp diagnostics &7- Diagnostics"));
                if (sender.hasPermission("blazingpractice.admin.updates")) sender.sendMessage(color("&e/bp updatecheck &7- Update check"));
            }
            case "duel" -> {
                sender.sendMessage(color("&a&lDuel help"));
                sender.sendMessage(color("&e/duel <online-player> &7- Open the ladder selector for that player"));
                sender.sendMessage(color("&e/duel <online-player> <ladder> &7- Send a direct duel challenge"));
                sender.sendMessage(color("&e/accept [challenger] &7- Accept the pending invitation"));
                sender.sendMessage(color("&7Both players must be in the Practice world; a free arena for the selected ladder is required."));
                sender.sendMessage(color("&7Example: &f/duel Steve sword"));
            }
            case "accept" -> {
                sender.sendMessage(color("&a&lAccept help"));
                sender.sendMessage(color("&e/accept &7- Accept your pending duel request"));
                sender.sendMessage(color("&e/accept <challenger> &7- Accept only the named challenger's pending request"));
                sender.sendMessage(color("&7TAB shows the challenger when an unexpired request is waiting."));
            }
            case "queue", "que" -> {
                sender.sendMessage(color("&a&lQueue help"));
                sender.sendMessage(color("&e/queue ranked [ladder] &7- Open Ranked picker or join directly"));
                sender.sendMessage(color("&e/queue unranked [ladder] &7- Open Unranked picker or join directly"));
                sender.sendMessage(color("&e/queue join <ranked|unranked> <ladder> &7- Alternative full syntax"));
                sender.sendMessage(color("&e/queue leave &7- Leave the current queue"));
                sender.sendMessage(color("&e/queue list &7- Show waiting player counts"));
                sender.sendMessage(color("&7Example: &f/queue ranked sword"));
            }
            case "ranked", "unranked" -> {
                sender.sendMessage(color("&a&l" + key + " help"));
                sender.sendMessage(color("&e/" + key + " &7- Open the ladder picker"));
                sender.sendMessage(color("&e/" + key + " <ladder> &7- Join that enabled ladder"));
                sender.sendMessage(color("&e/" + key + " leave &7- Leave the current queue"));
            }
            case "ffa" -> {
                sender.sendMessage(color("&a&lFFA help"));
                sender.sendMessage(color("&e/ffa join &7- Open the FFA ladder selector"));
                sender.sendMessage(color("&e/ffa join <ladder> &7- Join a specific enabled FFA ladder"));
                sender.sendMessage(color("&e/ffa leave &7- Leave FFA and restore your previous Practice state"));
                sender.sendMessage(color("&e/ffa status &7- Show FFA status"));
                if (sender.hasPermission("zpp.setup") || sender.isOp()) sender.sendMessage(color("&e/ffa setspawn &7- Set the FFA spawn at your current Practice-world location"));
            }
            case "practice", "prac", "zonepractice", "zoneprac", "zonep" -> sendPracticeHelp(sender);
            case "party", "p" -> {
                sender.sendMessage(color("&a&lParty help"));
                sender.sendMessage(color("&e/party create &7- Create a party"));
                sender.sendMessage(color("&e/party invite <player> &7- Invite an online player (leader only)"));
                sender.sendMessage(color("&e/party accept &7- Accept your latest party invitation"));
                sender.sendMessage(color("&e/party leave &7- Leave your party"));
                sender.sendMessage(color("&e/party info &7- Show party leader and members"));
                sender.sendMessage(color("&e/party chat <message> &7- Send a party-only message"));
            }
            case "statistics", "stats", "stat" -> {
                sender.sendMessage(color("&a&lStatistics help"));
                sender.sendMessage(color("&e/statistics &7- Show your Sword statistics"));
                sender.sendMessage(color("&e/statistics <ladder> &7- Show your chosen ladder statistics"));
                sender.sendMessage(color("&e/statistics <online-player> [ladder] &7- Show an online player's statistics"));
                sender.sendMessage(color("&7Example: &f/statistics Steve sword"));
            }
            case "ladder" -> {
                sender.sendMessage(color("&a&lLadder help"));
                sender.sendMessage(color("&e/ladder list &7- Show ladder IDs and availability"));
                sender.sendMessage(color("&e/ladder info <ladder> &7- Show ladder settings"));
                if (hasWorldAdmin(sender)) {
                    sender.sendMessage(color("&e/ladder create <name> &7- Create a disabled ladder"));
                    sender.sendMessage(color("&e/ladder clone <source> <new-name> &7- Copy ladder metadata"));
                    sender.sendMessage(color("&e/ladder set icon <ladder> &7- Use your held item as the ladder icon"));
                    sender.sendMessage(color("&e/ladder enable|disable <ladder> &7- Change queue availability"));
                    sender.sendMessage(color("&e/ladder delete <ladder> &7- Delete a ladder definition"));
                    sender.sendMessage(color("&e/ladder reload &7- Reload ladder files"));
                }
            }
            case "arena" -> sendArenaHelp(sender);
            case "lobby" -> {
                sender.sendMessage(color("&a&lBlazingPractice lobby help"));
                sender.sendMessage(color("&e/bp lobby &7- Teleport to the configured BlazingPractice lobby"));
                if (hasWorldAdmin(sender)) sender.sendMessage(color("&e/bp lobby set &7- Save your current location as the BlazingPractice lobby"));
                sender.sendMessage(color("&7This is the configured plugin lobby location; it does not change the Survival spawn."));
            }
            case "world" -> {
                sender.sendMessage(color("&a&lWorld setup help"));
                sender.sendMessage(color("&e/bp world status &7- View configured world roles"));
                if (hasWorldAdmin(sender)) sender.sendMessage(color("&e/bp world set lobby|pvp|survival &7- Assign the world you are standing in"));
                sender.sendMessage(color("&7Practice and FFA share the same &fPVP &7world role."));
            }
            case "npc" -> {
                if (!hasWorldAdmin(sender) && !sender.hasPermission("blazingpractice.admin.npc")
                        && !sender.hasPermission("blazingpractice.selector.admin")) {
                    sender.sendMessage(color("&cYou do not have permission to use NPC management commands."));
                    return;
                }
                sender.sendMessage(color("&a&lNPC help"));
                sender.sendMessage(color("&e/bp npc list &7- List plugin-managed NPC entries"));
                sender.sendMessage(color("&e/bp npc create <survival|practice|discord|hosting> &7- Create an NPC at your location"));
                sender.sendMessage(color("&e/bp npc remove <id> &7- Remove a managed NPC"));
                sender.sendMessage(color("&e/bp npc url <discord|hosting> <https-url> &7- Set a link"));
            }
            case "selector" -> {
                sender.sendMessage(color("&a&lSelector help"));
                sender.sendMessage(color("&e/bp selector menu &7- Open the server selector"));
                if (sender.hasPermission("blazingpractice.selector.admin") || sender.hasPermission("blazingpractice.admin")) {
                    sender.sendMessage(color("&e/bp selector give &7- Give yourself the selector item"));
                    sender.sendMessage(color("&e/bp selector reload &7- Reload selector/selector.yml"));
                    sender.sendMessage(color("&e/bp selector block set|clear|info &7- Manage the optional selector block"));
                }
            }
            case "bp", "blazingpractice", "blazing" -> sendHelp(sender);
            case "features", "compatibility" -> {
                sender.sendMessage(color("&a&lCommand implementation status"));
                sender.sendMessage(color("&aWorking in this build: &f/bp, /duel, /accept, /ranked, /unranked, /queue, /practice, /ffa, /party, /statistics, /ladder, /blazingpractice:arena"));
                sender.sendMessage(color("&eRegistered but not fully implemented: &f/setup, /settings, /staff, /spectate, /matchinv, /event, /preview, /divisions, /editor, /copykit, /ignorequeue, /cosmetics, /customqueue, /matchhistory, /nick"));
                sender.sendMessage(color("&7These reserved command roots are not advertised as working features. Their tab arguments are intentionally not fabricated."));
            }
            case "setup", "settings", "staff", "staffmode", "spectate", "spec", "matchinv", "event", "e", "preview", "divisions", "editor", "kiteditor", "copykit", "ignorequeue", "cosmetics", "cosmetc", "csmetic", "customqueue", "cqueue", "customkitqueue", "matchhistory", "mh", "matchhist", "nick", "nickname" -> {
                sender.sendMessage(color("&e/" + key + " is registered for compatibility but its full feature is not implemented in this build yet."));
                sender.sendMessage(color("&7Use &f/bp help &7to see commands that currently work."));
            }
            default -> {
                sender.sendMessage(color("&cUnknown help topic: &f" + topic));
                sender.sendMessage(color("&7Use &f/bp help &7or &f/bp help features&7."));
            }
        }
    }

    private void sendCompatibilityPending(CommandSender sender, String commandName) {
        sender.sendMessage(color("&e/" + commandName + " &7is registered for compatibility, but its full feature is not implemented in this build yet."));
        sender.sendMessage(color("&7Use &f/bp help &7to see the player commands that currently work."));
    }

    private void sendStatus(CommandSender sender) {
        WorldProfileManager profiles = plugin.worldProfiles();
        sender.sendMessage(color("&aBlazingPractice status"));
        sender.sendMessage(color("&7Version: &f" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(color("&7Server: &f" + plugin.getServer().getName() + " / " + plugin.getServer().getBukkitVersion()));
        sender.sendMessage(color("&7World inventory backend: &f" + (profiles == null ? "initializing" : profiles.backendName())));
        sender.sendMessage(color("&7World: &f" + (sender instanceof Player player ? player.getWorld().getName() : "console")));
    }

    private void sendDiagnostics(CommandSender sender) {
        WorldProfileManager profiles = plugin.worldProfiles();
        sender.sendMessage(color("&aBlazingPractice diagnostics"));
        sender.sendMessage(color("&7Java: &f" + System.getProperty("java.version")));
        sender.sendMessage(color("&7Paper/Bukkit: &f" + plugin.getServer().getBukkitVersion()));
        sender.sendMessage(color("&7Multiverse-Core: &f" + enabled("Multiverse-Core")));
        sender.sendMessage(color("&7Multiverse-Inventories: &f" + enabled("Multiverse-Inventories")));
        sender.sendMessage(color("&7WorldEdit: &f" + enabled("WorldEdit")));
        sender.sendMessage(color("&7WorldGuard: &f" + enabled("WorldGuard")));
        sender.sendMessage(color("&7Registered duel arenas: &f" + (plugin.arenas() == null ? 0 : plugin.arenas().all().size())));
        sender.sendMessage(color("&7World profile backend: &f" + (profiles == null ? "initializing" : profiles.backendName())));
        sender.sendMessage(color("&7Loaded worlds: &f" + plugin.getServer().getWorlds().size()));
        sender.sendMessage(color("&7Database: &fSQLite configured; asynchronous runtime executor"));
        sender.sendMessage(color("&7Compatibility handlers: &epending port and server verification"));
    }

    private boolean enabled(String pluginName) {
        return plugin.getServer().getPluginManager().isPluginEnabled(pluginName);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String root = command.getName().toLowerCase(Locale.ROOT);

        if (root.equals("duel")) {
            if (args.length == 1) {
                List<String> choices = new ArrayList<>(onlinePlayerNames(sender, args[0], true));
                if (startsWithIgnoreCase("help", args[0])) choices.add("help");
                return choices;
            }
            if (args.length == 2) return enabledLadderIds(args[1]);
            return List.of();
        }
        if (root.equals("accept")) {
            if (args.length == 1 && sender instanceof Player player) {
                List<String> choices = new ArrayList<>(plugin.duels().pendingChallengerNames(player));
                if (startsWithIgnoreCase("help", args[0])) choices.add("help");
                return filter(choices, args[0]);
            }
            return List.of();
        }
        if (root.equals("ranked") || root.equals("unranked")) {
            if (args.length == 1) {
                List<String> choices = new ArrayList<>(List.of("menu", "leave", "help"));
                plugin.ladders().available(root).stream().map(LadderRegistry.Ladder::id).forEach(choices::add);
                return filter(choices, args[0]);
            }
            return List.of();
        }
        if (root.equals("queue")) {
            if (args.length == 1) return filter(List.of("ranked", "unranked", "join", "leave", "list", "help"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("join")) return filter(List.of("ranked", "unranked"), args[1]);
            if (args.length == 2 && (args[0].equalsIgnoreCase("ranked") || args[0].equalsIgnoreCase("unranked"))) {
                return plugin.ladders().available(args[0]).stream().map(LadderRegistry.Ladder::id)
                        .filter(value -> startsWithIgnoreCase(value, args[1])).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("join")
                    && (args[1].equalsIgnoreCase("ranked") || args[1].equalsIgnoreCase("unranked"))) {
                return plugin.ladders().available(args[1]).stream().map(LadderRegistry.Ladder::id)
                        .filter(value -> startsWithIgnoreCase(value, args[2])).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
            return List.of();
        }
        if (root.equals("arena")) return arenaTabComplete(sender, args);
        if (root.equals("ffa")) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(List.of("join", "play", "leave", "exit", "status", "info", "help"));
                if (sender.hasPermission("zpp.setup") || sender.isOp()) options.add("setspawn");
                return filter(options, args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("play"))) return enabledLadderIds(args[1]);
            return List.of();
        }
        if (root.equals("practice")) {
            if (args.length == 1) return filter(List.of("menu", "ranked", "unranked", "lobby", "info", "status", "help"), args[0]);
            if (args.length == 2 && (args[0].equalsIgnoreCase("ranked") || args[0].equalsIgnoreCase("unranked"))) {
                return plugin.ladders().available(args[0]).stream().map(LadderRegistry.Ladder::id)
                        .filter(value -> startsWithIgnoreCase(value, args[1])).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
                return hasWorldAdmin(sender) || sender.hasPermission("zpp.practice.lobby") ? filter(List.of("set"), args[1]) : List.of();
            }
            return List.of();
        }
        if (root.equals("ladder")) {
            if (args.length == 1) {
                List<String> actions = new ArrayList<>(List.of("list", "info", "help"));
                if (hasWorldAdmin(sender)) actions.addAll(List.of("create", "clone", "delete", "set", "enable", "disable", "reload"));
                return filter(actions, args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("clone")
                    || args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")
                    || args[0].equalsIgnoreCase("delete"))) {
                return ladderIds(args[1], false);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set") && hasWorldAdmin(sender)) return filter(List.of("icon"), args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("icon") && hasWorldAdmin(sender)) return ladderIds(args[2], false);
            return List.of();
        }
        if (root.equals("party")) {
            if (args.length == 1) return filter(List.of("create", "invite", "accept", "leave", "info", "chat", "help"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("invite")) return onlinePlayerNames(sender, args[1], true);
            return List.of();
        }
        if (root.equals("statistics")) {
            if (args.length == 1) {
                List<String> choices = new ArrayList<>(enabledLadderIds(""));
                choices.addAll(onlinePlayerNames(sender, "", false));
                choices.add("help");
                return filter(choices, args[0]);
            }
            if (args.length == 2) return enabledLadderIds(args[1]);
            return List.of();
        }
        if (!root.equals("bp")) return List.of();

        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "about", "status", "lobby", "practice", "bedwars", "world", "arena"));
            if (hasWorldAdmin(sender) || sender.hasPermission("blazingpractice.admin.npc") || sender.hasPermission("blazingpractice.selector.admin")) options.add("npc");
            if (sender.hasPermission("blazingpractice.selector.use") || sender.hasPermission("blazingpractice.selector.admin")) options.add("selector");
            if (sender.hasPermission("blazingpractice.admin.diagnostics")) options.add("diagnostics");
            if (sender.hasPermission("blazingpractice.admin.updates")) options.add("updatecheck");
            return filter(options, args[0]);
        }
        if (args[0].equalsIgnoreCase("help") && args.length == 2) {
            return filter(List.of("player", "admin", "duel", "accept", "ranked", "unranked", "queue", "ffa", "practice", "party", "statistics", "ladder", "arena", "world", "lobby", "npc", "selector", "about", "status", "bedwars", "diagnostics", "updatecheck", "features"), args[1]);
        }
        if (args[0].equalsIgnoreCase("arena")) {
            // Arena list/info/help are visible to players; arenaTabComplete hides admin-only actions.
            return arenaTabComplete(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (args[0].equalsIgnoreCase("world")) {
            if (args.length == 2) return filter(hasWorldAdmin(sender) ? List.of("status", "list", "set", "help") : List.of("status", "list", "help"), args[1]);
            if (args.length == 3 && args[1].equalsIgnoreCase("set") && hasWorldAdmin(sender)) return filter(List.of("lobby", "pvp", "survival"), args[2]);
            return List.of();
        }
        if (args[0].equalsIgnoreCase("practice")) {
            if (args.length == 2) {
                List<String> choices = new ArrayList<>(List.of("join", "menu", "ranked", "unranked", "help"));
                if (hasWorldAdmin(sender)) choices.add("setspawn");
                return filter(choices, args[1]);
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("ranked") || args[1].equalsIgnoreCase("unranked"))) {
                return plugin.ladders().available(args[1]).stream().map(LadderRegistry.Ladder::id)
                        .filter(value -> startsWithIgnoreCase(value, args[2])).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
            return List.of();
        }
        if (args[0].equalsIgnoreCase("lobby")) {
            if (args.length == 2) {
                List<String> choices = new ArrayList<>(List.of("help"));
                if (hasWorldAdmin(sender)) choices.add("set");
                return filter(choices, args[1]);
            }
            return List.of();
        }
        if (args[0].equalsIgnoreCase("npc") && (hasWorldAdmin(sender) || sender.hasPermission("blazingpractice.admin.npc") || sender.hasPermission("blazingpractice.selector.admin"))) {
            if (args.length == 2) return filter(List.of("create", "remove", "list", "url", "help"), args[1]);
            if (args.length == 3 && args[1].equalsIgnoreCase("create")) return filter(List.of("survival", "practice", "discord", "hosting"), args[2]);
            if (args.length == 3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("delete"))) return filter(plugin.npcs().list(), args[2]);
            if (args.length == 3 && args[1].equalsIgnoreCase("url")) return filter(List.of("discord", "hosting"), args[2]);
            return List.of();
        }
        if (args[0].equalsIgnoreCase("selector")) {
            if (args.length == 2) {
                List<String> choices = new ArrayList<>(List.of("menu", "help"));
                if (sender.hasPermission("blazingpractice.selector.admin") || sender.hasPermission("blazingpractice.admin")) choices.addAll(List.of("give", "reload", "block"));
                return filter(choices, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("block")
                    && (hasWorldAdmin(sender) || sender.hasPermission("blazingpractice.selector.admin") || sender.hasPermission("blazingpractice.admin.npc"))) {
                return filter(List.of("set", "clear", "info"), args[2]);
            }
            return List.of();
        }
        if (args[0].equalsIgnoreCase("bedwars") && args.length == 2) return filter(List.of("status"), args[1]);
        return List.of();
    }

    private List<String> onlinePlayerNames(CommandSender sender, String token, boolean excludeSelf) {
        Player viewer = sender instanceof Player player ? player : null;
        return Bukkit.getOnlinePlayers().stream()
                .filter(target -> viewer == null || viewer.canSee(target))
                .filter(target -> !excludeSelf || viewer == null || !viewer.getUniqueId().equals(target.getUniqueId()))
                .map(Player::getName)
                .filter(value -> startsWithIgnoreCase(value, token))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> ladderIds(String token, boolean onlyEnabled) {
        return plugin.ladders().all().stream()
                .filter(ladder -> !onlyEnabled || ladder.enabled())
                .map(LadderRegistry.Ladder::id)
                .filter(value -> startsWithIgnoreCase(value, token))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> practiceRegionIds(Player player, String token) {
        if (player == null || plugin.worldProfiles() == null
                || !plugin.worldProfiles().isPracticeWorld(player.getWorld())) return List.of();
        return plugin.arenas().worldGuardRegionIds(player.getWorld()).stream()
                .filter(value -> startsWithIgnoreCase(value, token)).toList();
    }

    private List<String> enabledLadderIds(String token) {
        return plugin.ladders().all().stream()
                .filter(LadderRegistry.Ladder::enabled)
                .map(LadderRegistry.Ladder::id)
                .filter(value -> startsWithIgnoreCase(value, token))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> arenaNames(String token) {
        return plugin.arenas().all().stream().map(Arena::name)
                .filter(value -> startsWithIgnoreCase(value, token))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private boolean startsWithIgnoreCase(String value, String token) {
        return value != null && token != null && value.regionMatches(true, 0, token, 0, token.length());
    }

    private List<String> arenaTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> actions = new ArrayList<>(List.of("list", "info", "help"));
            if (hasArenaAdmin(sender)) actions.addAll(List.of("create", "setspawn1", "setspawn2", "enable", "disable", "delete", "reload"));
            return filter(actions, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("setspawn1") || args[0].equalsIgnoreCase("setspawn2")
                || args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")
                || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info"))) return arenaNames(args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("create") && hasArenaAdmin(sender)) return ladderIds(args[2], false);
        if (args.length == 4 && args[0].equalsIgnoreCase("create") && hasArenaAdmin(sender)
                && sender instanceof Player player) return practiceRegionIds(player, args[3]);
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        return values.stream().filter(value -> startsWithIgnoreCase(value, token))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
