package net.blazingpractice.match;

import net.blazingpractice.BlazingPractice;
import net.blazingpractice.arena.Arena;
import net.blazingpractice.arena.ArenaManager;
import net.blazingpractice.arena.ArenaReservation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.*;

/** Invitation and 1v1 match lifecycle with one reserved arena per exact ladder. */
public final class DuelService implements Listener {
    private final BlazingPractice plugin;
    private final ArenaManager arenas;
    private final Map<UUID, Invite> invitesByTarget = new HashMap<>();
    private final Map<UUID, Match> matchesByPlayer = new HashMap<>();
    private final Set<UUID> activeMatchIds = new HashSet<>();
    private final Set<UUID> pendingLobbyRespawn = new HashSet<>();

    public DuelService(BlazingPractice plugin, ArenaManager arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    public boolean invite(Player sender, Player target, String ladderId) {
        if (sender.equals(target) || isInMatch(sender) || isInMatch(target)
                || (plugin.ffa() != null && (plugin.ffa().isActive(sender) || plugin.ffa().isActive(target)))) return false;
        var ladder = plugin.ladders().find(ladderId);
        if (ladder.isEmpty() || !ladder.get().enabled()) {
            sender.sendMessage("§cThat ladder is not available. Use /ladder list.");
            return false;
        }
        invitesByTarget.put(target.getUniqueId(), new Invite(sender.getUniqueId(), ladder.get().id(),
                System.currentTimeMillis() + 60_000L));
        sender.sendMessage("§a" + ladder.get().name() + " duel request sent to §f" + target.getName() + "§a.");
        target.sendMessage("§e" + sender.getName() + " challenged you for §f" + ladder.get().name()
                + "§e. Use §f/accept " + sender.getName() + " §eto accept (60s).");
        return true;
    }

    /** Returns the currently online challenger whose invitation the player can accept. */
    public List<String> pendingChallengerNames(Player target) {
        if (target == null) return List.of();
        Invite invite = invitesByTarget.get(target.getUniqueId());
        if (invite == null) return List.of();
        if (invite.expiresAt() < System.currentTimeMillis()) return List.of();
        Player challenger = Bukkit.getPlayer(invite.sender());
        return challenger != null && challenger.isOnline() ? List.of(challenger.getName()) : List.of();
    }

    public boolean accept(Player target, String challengerName) {
        Invite invite = invitesByTarget.get(target.getUniqueId());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
            invitesByTarget.remove(target.getUniqueId());
            target.sendMessage("§cYou have no pending duel request.");
            return false;
        }
        Player challenger = Bukkit.getPlayer(invite.sender());
        if (challenger == null || (challengerName != null && !challenger.getName().equalsIgnoreCase(challengerName))) {
            target.sendMessage("§cThat duel request is no longer available.");
            return false;
        }
        if ((plugin.ffa() != null && (plugin.ffa().isActive(challenger) || plugin.ffa().isActive(target)))
                || plugin.worldProfiles() == null
                || !plugin.worldProfiles().isPracticeWorld(challenger.getWorld())
                || !plugin.worldProfiles().isPracticeWorld(target.getWorld())) {
            target.sendMessage("§cBoth players must be in Practice mode (not active FFA) inside the shared PvP world before a duel can start.");
            return false;
        }

        boolean started = startMatch(challenger, target, invite.ladder(), false);
        if (started) {
            invitesByTarget.remove(target.getUniqueId());
        } else {
            target.sendMessage("§eYour duel request remains available until it expires.");
        }
        return started;
    }

    /**
     * Reserves a free arena for the exact ladder before either player is teleported.
     * All start paths (direct invites and ranked/unranked queues) use this method.
     */
    public boolean startMatch(Player first, Player second, String ladder, boolean ranked) {
        if (first == null || second == null || !first.isOnline() || !second.isOnline()
                || first.equals(second) || isInMatch(first) || isInMatch(second)
                || (plugin.ffa() != null && (plugin.ffa().isActive(first) || plugin.ffa().isActive(second)))
                || plugin.worldProfiles() == null
                || !plugin.worldProfiles().isPracticeWorld(first.getWorld())
                || !plugin.worldProfiles().isPracticeWorld(second.getWorld())) return false;

        var ladderDefinition = plugin.ladders().find(ladder);
        if (ladderDefinition.isEmpty() || !ladderDefinition.get().enabled()) {
            first.sendMessage("§cThat duel ladder is not enabled.");
            second.sendMessage("§cThat duel ladder is not enabled.");
            return false;
        }
        if (!arenas.worldGuardStatus().equals("enabled")) {
            String message = "§cWorldGuard is not enabled. Duel arenas cannot be validated until WorldEdit and WorldGuard are installed.";
            first.sendMessage(message);
            second.sendMessage(message);
            return false;
        }
        if (!arenas.hasReadyArena(ladder)) {
            String message = "§cNo enabled, valid arena is configured for " + ladder + ". Please contact an admin.";
            first.sendMessage(message);
            second.sendMessage(message);
            return false;
        }

        Optional<ArenaReservation> reservationResult = arenas.reserve(ladder);
        if (reservationResult.isEmpty()) {
            String message = "§eAll " + ladder + " arenas are currently busy. Try again shortly.";
            first.sendMessage(message);
            second.sendMessage(message);
            return false;
        }

        ArenaReservation reservation = reservationResult.get();
        Arena arena = reservation.arena();
        Location firstReturn = first.getLocation().clone();
        Location secondReturn = second.getLocation().clone();
        Match match = new Match(UUID.randomUUID(), first.getUniqueId(), second.getUniqueId(),
                reservation, ladderDefinition.get().id(), ranked);

        matchesByPlayer.put(first.getUniqueId(), match);
        matchesByPlayer.put(second.getUniqueId(), match);
        activeMatchIds.add(match.id());

        // Keep the established opposite-side spawn behavior: first -> spawn one, second -> spawn two.
        boolean firstMoved = first.teleport(arena.spawnOne().clone());
        boolean secondMoved = firstMoved && second.teleport(arena.spawnTwo().clone());
        if (!firstMoved || !secondMoved) {
            first.teleport(firstReturn);
            second.teleport(secondReturn);
            matchesByPlayer.remove(first.getUniqueId(), match);
            matchesByPlayer.remove(second.getUniqueId(), match);
            activeMatchIds.remove(match.id());
            arenas.release(reservation);
            if (plugin.protection() != null) {
                if (plugin.protection().isSafeWorld(first.getWorld().getName())) plugin.protection().protect(first);
                if (plugin.protection().isSafeWorld(second.getWorld().getName())) plugin.protection().protect(second);
            }
            first.sendMessage("§cThe duel could not start because an arena teleport was blocked. You remain protected.");
            second.sendMessage("§cThe duel could not start because an arena teleport was blocked. You remain protected.");
            return false;
        }

        if (plugin.protection() != null) {
            plugin.protection().release(first);
            plugin.protection().release(second);
        }
        first.setGameMode(GameMode.SURVIVAL);
        second.setGameMode(GameMode.SURVIVAL);
        first.setHealth(first.getMaxHealth());
        second.setHealth(second.getMaxHealth());
        first.setFoodLevel(20);
        second.setFoodLevel(20);
        first.setFireTicks(0);
        second.setFireTicks(0);
        first.sendMessage("§aMatch started against §f" + second.getName() + "§a in §f" + arena.name()
                + "§7 [" + ladder + "]");
        second.sendMessage("§aMatch started against §f" + first.getName() + "§a in §f" + arena.name()
                + "§7 [" + ladder + "]");
        return true;
    }

    public boolean isInMatch(Player player) {
        return matchesByPlayer.containsKey(player.getUniqueId());
    }

    /** Cancels an active match without recording a ranked win/loss; the arena is released. */
    public boolean cancelMatch(Player player, String reason) {
        if (player == null) return false;
        Match match = matchesByPlayer.get(player.getUniqueId());
        if (match == null) return false;
        finish(match, null, reason == null || reason.isBlank() ? "cancelled" : reason);
        return true;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player loser = event.getEntity();
        Match match = matchesByPlayer.get(loser.getUniqueId());
        if (match == null) return;
        UUID winnerId = match.other(loser.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> finish(match, winnerId, "defeat"));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!pendingLobbyRespawn.remove(playerId)) return;
        if (plugin.lobby() != null) {
            Location lobbyLocation = plugin.lobby().location();
            if (lobbyLocation != null) event.setRespawnLocation(lobbyLocation);
        }
        if (plugin.protection() != null) plugin.protection().protect(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        Match match = matchesByPlayer.get(quitter.getUniqueId());
        if (match != null) finish(match, match.other(quitter.getUniqueId()), "forfeit");
        if (plugin.parties() != null) plugin.parties().handleQuit(quitter);
        invitesByTarget.remove(quitter.getUniqueId());
        invitesByTarget.values().removeIf(invite -> invite.sender().equals(quitter.getUniqueId()));
    }

    private void finish(Match match, UUID winnerId, String reason) {
        // Death and quit can both fire close together; this guard releases each arena exactly once.
        if (!activeMatchIds.remove(match.id())) return;
        matchesByPlayer.remove(match.first(), match);
        matchesByPlayer.remove(match.second(), match);
        arenas.release(match.reservation());

        Player winner = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        Player first = Bukkit.getPlayer(match.first());
        Player second = Bukkit.getPlayer(match.second());
        if (winner != null) {
            winner.sendMessage("§aYou won the duel (" + reason + ").");
        } else {
            if (first != null && first.isOnline()) first.sendMessage("§eYour duel was cancelled (" + reason + ").");
            if (second != null && second.isOnline()) second.sendMessage("§eYour duel was cancelled (" + reason + ").");
        }

        if (plugin.protection() != null) {
            if (first != null && first.isOnline()) {
                if (plugin.lobby() != null && plugin.lobby().isSet()
                        && plugin.getConfig().getBoolean("lobby.return-after-match", true)) {
                    if (first.isDead()) {
                        pendingLobbyRespawn.add(first.getUniqueId());
                        plugin.protection().protect(first);
                    } else plugin.lobby().teleport(first);
                } else if (plugin.protection().isSafeWorld(first.getWorld().getName())) {
                    plugin.protection().protect(first);
                } else plugin.protection().release(first);
            }
            if (second != null && second.isOnline()) {
                if (plugin.lobby() != null && plugin.lobby().isSet()
                        && plugin.getConfig().getBoolean("lobby.return-after-match", true)) {
                    if (second.isDead()) {
                        pendingLobbyRespawn.add(second.getUniqueId());
                        plugin.protection().protect(second);
                    } else plugin.lobby().teleport(second);
                } else if (plugin.protection().isSafeWorld(second.getWorld().getName())) {
                    plugin.protection().protect(second);
                } else plugin.protection().release(second);
            }
        }
        if (winnerId != null) {
            if (first != null && first.isOnline() && !first.getUniqueId().equals(winnerId)) first.sendMessage("§cYou lost the duel.");
            if (second != null && second.isOnline() && !second.getUniqueId().equals(winnerId)) second.sendMessage("§cYou lost the duel.");
        }

        if (match.ranked() && winnerId != null) {
            plugin.database().recordRankedResult(winnerId, match.other(winnerId), match.ladder())
                    .exceptionally(error -> {
                        plugin.getLogger().warning("Could not save ranked result: " + error.getMessage());
                        return null;
                    });
            if (winner != null) winner.sendMessage("§6Ranked result recorded. Rating updates shortly.");
        }
    }

    /** Called during plugin shutdown. Reservations are runtime-only and are never persisted as busy. */
    public void shutdown() {
        Set<Match> matches = new HashSet<>(matchesByPlayer.values());
        for (Match match : matches) {
            if (activeMatchIds.remove(match.id())) arenas.release(match.reservation());
        }
        matchesByPlayer.clear();
        activeMatchIds.clear();
        invitesByTarget.clear();
        pendingLobbyRespawn.clear();
        arenas.clearReservations();
    }

    private record Invite(UUID sender, String ladder, long expiresAt) { }

    private record Match(UUID id, UUID first, UUID second, ArenaReservation reservation,
                         String ladder, boolean ranked) {
        UUID other(UUID player) {
            return first.equals(player) ? second : first;
        }
    }
}
