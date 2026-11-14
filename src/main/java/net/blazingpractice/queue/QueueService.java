package net.blazingpractice.queue;

import net.blazingpractice.BlazingPractice;
import net.blazingpractice.ladder.LadderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/** Matchmaking queues are isolated by both queue type and selected ladder. */
public final class QueueService implements Listener {
    private final BlazingPractice plugin;
    private final Map<String, LinkedHashMap<UUID, Long>> queues = new HashMap<>();
    private final Map<UUID, Long> arenaWaitNoticeAt = new HashMap<>();
    private int taskId = -1;

    public QueueService(BlazingPractice plugin) { this.plugin = plugin; }

    public void start() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::matchmake, 20L, 20L);
    }

    public void stop() {
        if (taskId >= 0) Bukkit.getScheduler().cancelTask(taskId);
        queues.clear();
        arenaWaitNoticeAt.clear();
    }

    public boolean join(Player player, String queueType, String ladderId) {
        String type = normalizeQueueType(queueType);
        if (type == null) {
            player.sendMessage("§cQueue type must be ranked or unranked.");
            return false;
        }
        Optional<LadderRegistry.Ladder> selected = plugin.ladders().find(ladderId);
        if (selected.isEmpty() || !selected.get().enabled() || !selected.get().queueEnabled()) {
            player.sendMessage("§cThat ladder is not available. Use /ladder list to see available ladders.");
            return false;
        }
        LadderRegistry.Ladder ladder = selected.get();
        if (type.equals("ranked") && !ladder.ranked() || type.equals("unranked") && !ladder.unranked()) {
            player.sendMessage("§c" + ladder.name() + " is not enabled for " + type + " matches.");
            return false;
        }
        if (plugin.duels().isInMatch(player) || (plugin.ffa() != null && plugin.ffa().isActive(player))) {
            player.sendMessage("§cLeave your current activity before joining a queue.");
            return false;
        }
        if (plugin.worldProfiles() == null || !plugin.worldProfiles().isPracticeWorld(player.getWorld())) {
            player.sendMessage("§cEnter Practice mode in the shared PvP world before joining a queue.");
            return false;
        }
        if (!plugin.arenas().worldGuardStatus().equals("enabled")) {
            player.sendMessage("§cWorldGuard is not enabled. Duel queues are unavailable until WorldEdit and WorldGuard are installed.");
            return false;
        }
        if (!plugin.arenas().hasReadyArena(ladder.id())) {
            player.sendMessage("§cNo enabled, valid duel arena is configured for " + ladder.id()
                    + ". Ask an admin to register a WorldGuard arena for this ladder.");
            return false;
        }
        leaveSilently(player.getUniqueId());
        String key = queueKey(type, ladder.id());
        LinkedHashMap<UUID, Long> queue = queues.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        queue.put(player.getUniqueId(), System.currentTimeMillis());
        if (plugin.protection() != null) plugin.protection().protect(player);
        player.sendMessage("§aSearching: §f" + type.toUpperCase(Locale.ROOT) + " " + ladder.name()
                + " §7| Waiting: §f" + queue.size() + " §7| You are protected until your match starts.");
        return true;
    }

    /** Compatibility overload deliberately refuses a generic queue without a chosen ladder. */
    public boolean join(Player player, String queueType) {
        player.sendMessage("§cChoose a ladder before queueing: §f/" + normalizeQueueType(queueType)
                + " <ladder> §7or open /practice.");
        return false;
    }

    public boolean leave(Player player) {
        boolean removed = leaveSilently(player.getUniqueId());
        if (removed) {
            player.sendMessage("§eYou left all matchmaking queues.");
            if (plugin.protection() != null && !plugin.protection().isSafeWorld(player.getWorld().getName())
                    && (plugin.ffa() == null || !plugin.ffa().isActive(player))
                    && (plugin.duels() == null || !plugin.duels().isInMatch(player))) {
                plugin.protection().release(player);
            }
        }
        return removed;
    }

    private boolean leaveSilently(UUID uuid) {
        boolean removed = false;
        for (Map<UUID, Long> queue : queues.values()) removed |= queue.remove(uuid) != null;
        arenaWaitNoticeAt.remove(uuid);
        return removed;
    }

    public boolean isQueued(Player player) {
        return queues.values().stream().anyMatch(queue -> queue.containsKey(player.getUniqueId()));
    }

    public int waiting(String queueType, String ladder) {
        String type = normalizeQueueType(queueType);
        if (type == null) return 0;
        return queues.getOrDefault(queueKey(type, ladder), new LinkedHashMap<>()).size();
    }

    public Map<String, Integer> snapshot() {
        Map<String, Integer> copy = new TreeMap<>();
        queues.forEach((key, value) -> copy.put(key.replace(':', ' '), value.size()));
        return Collections.unmodifiableMap(copy);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leaveSilently(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isQueued(player) && (plugin.worldProfiles() == null
                || !plugin.worldProfiles().isPracticeWorld(player.getWorld()))) {
            leave(player);
            player.sendMessage("§eYou were removed from the queue because you left Practice mode.");
        }
    }

    private void matchmake() {
        for (Map.Entry<String, LinkedHashMap<UUID, Long>> entry : queues.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            String[] parts = entry.getKey().split(":", 2);
            String queueType = parts[0];
            String ladder = parts.length > 1 ? parts[1] : "sword";

            // If configured arenas are all occupied, keep players in this exact ladder queue.
            // Never borrow another ladder's arena.
            if (!plugin.arenas().worldGuardStatus().equals("enabled") || !plugin.arenas().hasReadyArena(ladder)) {
                notifyArenaWait(entry.getValue().keySet(), ladder, false);
                continue;
            }
            if (!plugin.arenas().hasFreeArena(ladder)) {
                notifyArenaWait(entry.getValue().keySet(), ladder, true);
                continue;
            }

            List<UUID> candidates = new ArrayList<>(entry.getValue().keySet());
            for (int i = 0; i + 1 < candidates.size();) {
                if (!plugin.arenas().hasFreeArena(ladder)) {
                    notifyArenaWait(entry.getValue().keySet(), ladder, true);
                    break;
                }

                UUID firstId = candidates.get(i);
                UUID secondId = candidates.get(i + 1);
                Player first = Bukkit.getPlayer(firstId);
                Player second = Bukkit.getPlayer(secondId);
                if (!validCandidate(first)) {
                    entry.getValue().remove(firstId);
                    candidates.remove(i);
                    arenaWaitNoticeAt.remove(firstId);
                    continue;
                }
                if (!validCandidate(second)) {
                    entry.getValue().remove(secondId);
                    candidates.remove(i + 1);
                    arenaWaitNoticeAt.remove(secondId);
                    continue;
                }

                boolean started = plugin.duels().startMatch(first, second, ladder, queueType.equals("ranked"));
                if (started) {
                    entry.getValue().remove(firstId);
                    entry.getValue().remove(secondId);
                    arenaWaitNoticeAt.remove(firstId);
                    arenaWaitNoticeAt.remove(secondId);
                    candidates.remove(i + 1);
                    candidates.remove(i);
                } else if (!plugin.arenas().hasFreeArena(ladder)) {
                    notifyArenaWait(entry.getValue().keySet(), ladder, true);
                    break;
                } else {
                    i++;
                }
            }
        }
    }

    private void notifyArenaWait(Collection<UUID> playerIds, String ladder, boolean busy) {
        long now = System.currentTimeMillis();
        for (UUID playerId : playerIds) {
            long last = arenaWaitNoticeAt.getOrDefault(playerId, 0L);
            if (now - last < 30_000L) continue;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(busy
                        ? "§eAll " + ladder + " arenas are busy. You are still in the " + ladder + " queue."
                        : !plugin.arenas().worldGuardStatus().equals("enabled")
                        ? "§cWorldGuard is not enabled, so the " + ladder + " duel queue is paused."
                        : "§cThe " + ladder + " arena pool has no valid enabled arenas. Contact an admin.");
                arenaWaitNoticeAt.put(playerId, now);
            }
        }
    }

    private boolean validCandidate(Player player) {
        return player != null && player.isOnline() && !plugin.duels().isInMatch(player)
                && (plugin.ffa() == null || !plugin.ffa().isActive(player))
                && plugin.worldProfiles() != null && plugin.worldProfiles().isPracticeWorld(player.getWorld());
    }

    private String queueKey(String type, String ladder) {
        return type + ":" + ladder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private String normalizeQueueType(String input) {
        if (input == null) return null;
        String value = input.toLowerCase(Locale.ROOT);
        return value.equals("ranked") || value.equals("unranked") ? value : null;
    }
}
