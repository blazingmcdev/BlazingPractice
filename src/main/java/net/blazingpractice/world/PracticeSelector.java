package net.blazingpractice.world;

import net.blazingpractice.BlazingPractice;
import net.blazingpractice.ladder.LadderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.Locale;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/** Separate Practice hub controls and FFA in-game exit controls. */
public final class PracticeSelector implements Listener {
    private final BlazingPractice plugin;
    private final WorldProfileManager profiles;
    private final NamespacedKey actionKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey legacySelectorKey;
    private final File selectorFile;
    private YamlConfiguration selectorConfig;

    public PracticeSelector(BlazingPractice plugin, WorldProfileManager profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.actionKey = new NamespacedKey(plugin, "lobby_item_action");
        this.menuActionKey = new NamespacedKey(plugin, "selector_menu_action");
        this.legacySelectorKey = new NamespacedKey(plugin, "practice_selector");
        File selectorDirectory = new File(plugin.getDataFolder(), "selector");
        if (!selectorDirectory.exists() && !selectorDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create selector configuration folder: " + selectorDirectory);
        }
        this.selectorFile = new File(selectorDirectory, "selector.yml");
        this.selectorConfig = new YamlConfiguration();
        if (!selectorFile.exists()) {
            try {
                plugin.saveResource("selector/selector.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().severe("Missing bundled selector/selector.yml resource.");
            }
        }
        reloadSelectorConfig();
        profiles.setSelector(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        profiles.whenReady(event.getPlayer(), () -> ensureForWorld(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        profiles.whenReady(event.getPlayer(), () -> ensureForWorld(event.getPlayer()));
    }

    /** Reconciles plugin-owned controls after the destination world's inventory profile is loaded. */
    public void ensureForWorld(Player player) {
        if (player == null || !player.isOnline()) return;
        removeLegacySelectorItems(player);
        removeManagedItems(player);
        if (!plugin.getConfig().getBoolean("selector.enabled", true)) return;

        if (plugin.ffa() != null && plugin.ffa().isActive(player)) {
            addConfiguredItem(player, "ffa-leave", "FFA_LEAVE", Material.BARRIER, 8,
                    "&cLeave FFA &7(Right-Click)", List.of("&7Return to your previous Practice location"));
            return;
        }

        if (profiles.isLobbyWorld(player.getWorld())) {
            if (plugin.getConfig().getBoolean("selector.lobby-clean-inventory", true)) {
                // Lobby is a clean server selector area. The Survival profile has already
                // been saved by WorldProfileManager before this inventory is rebuilt.
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);
                player.getInventory().setItemInOffHand(null);
            }
            if (selectorConfig.getBoolean("selector-item.enabled", true)) {
                addSelectorItem(player);
            }
            return;
        }

        if (profiles.isPvpWorld(player.getWorld()) || profiles.isPracticeWorld(player.getWorld())) {
            addConfiguredItem(player, "practice", "PRACTICE_MENU", Material.IRON_SWORD, 0,
                    "&aPractice Queue &7(Right-Click)", List.of("&7Ranked and Unranked matchmaking"));
            addConfiguredItem(player, "ffa", "FFA_MENU", Material.GOLDEN_SWORD, 1,
                    "&6Join FFA &7(Right-Click)", List.of("&7Teleport to the FFA spawn in this world"));
            addSocialItems(player);
            return;
        }

        if (profiles.isFfaWorld(player.getWorld())) {
            addConfiguredItem(player, "ffa", "FFA_MENU", Material.GOLDEN_SWORD, 1,
                    "&6Join FFA &7(Right-Click)", List.of("&7Join FFA"));
        }
    }

    private void addSocialItems(Player player) {
        addConfiguredItem(player, "party-info", "PARTY_INFO", Material.PLAYER_HEAD, 6,
                "&bParty Information &7(Right-Click)", List.of("&7View your current party"));
        addConfiguredItem(player, "party-create", "PARTY_CREATE", Material.NAME_TAG, 7,
                "&dCreate Party &7(Right-Click)", List.of("&7Create a new party"));
        addConfiguredItem(player, "help", "HELP", Material.BOOK, 8,
                "&6Practice Help &7(Right-Click)", List.of("&7Show Practice commands"));
    }

    private void addConfiguredItem(Player player, String id, String action, Material fallback, int defaultSlot,
                                   String defaultName, List<String> defaultLore) {
        String path = "selector.hotbar." + id;
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) return;
        int slot = plugin.getConfig().getInt(path + ".slot", defaultSlot);
        if (slot < 0 || slot >= player.getInventory().getSize()) return;
        Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".material", fallback.name()));
        if (material == null || !material.isItem()) material = fallback;
        String displayName = plugin.getConfig().getString(path + ".name", defaultName);
        List<String> lore = plugin.getConfig().getStringList(path + ".lore");
        if (lore.isEmpty()) lore = defaultLore;
        ItemStack item = taggedItem(material, displayName, lore, actionKey, action);
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) {
            player.getInventory().setItem(slot, item);
        } else {
            // Do not erase a player's item to make room for a menu item.
            int free = player.getInventory().firstEmpty();
            if (free >= 0) player.getInventory().setItem(free, item);
            else player.sendMessage(color("&eYour inventory is full; there is no free slot for the " + id + " menu item."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action click = event.getAction();
        if (click != Action.RIGHT_CLICK_AIR && click != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();

        // Optional single configured wooden lobby block can open the same GUI.
        if (click == Action.RIGHT_CLICK_BLOCK && isConfiguredSelectorBlock(event.getClickedBlock())) {
            event.setCancelled(true);
            openServerSelector(player);
            return;
        }

        String action = getAction(event.getItem());
        if (action == null) return;
        // Process our tagged items even if another plugin cancelled the interaction first.
        event.setCancelled(true);

        if ("FFA_LEAVE".equals(action)) {
            if (plugin.ffa() != null) plugin.ffa().leave(player);
            return;
        }
        switch (action) {
            case "SERVER_SELECTOR" -> openServerSelector(player);
            case "PRACTICE_MENU" -> {
                if (profiles.isLobbyWorld(player.getWorld())) openLobbyModeMenu(player);
                else if (profiles.isPracticeWorld(player.getWorld())) openPracticeMenu(player);
                else player.sendMessage(color("&cPractice controls are not available in this world."));
            }
            case "FFA_MENU" -> {
                if (profiles.isLobbyWorld(player.getWorld())) openLobbyModeMenu(player);
                else if (profiles.isFfaWorld(player.getWorld())) openFfaMenu(player);
                else player.sendMessage(color("&cFFA is not configured for this world."));
            }
            case "SELECTOR" -> {
                if (profiles.isPracticeWorld(player.getWorld())) openPracticeMenu(player);
                else if (profiles.isLobbyWorld(player.getWorld())) openLobbyModeMenu(player);
                else player.sendMessage(color("&cPractice controls are not available in this world."));
            }
            case "PARTY_INFO" -> player.sendMessage(plugin.parties().info(player));
            case "PARTY_CREATE" -> player.sendMessage(plugin.parties().create(player)
                    ? color("&aParty created. Use /party invite <player> to invite friends.")
                    : color("&cYou are already in a party."));
            case "HELP" -> sendHelp(player);
            default -> player.sendMessage(color("&cThis Practice item has no action configured."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SelectorHolder holder)) return;
        // The selector is a controlled menu. Cancel all movement (including shift-click,
        // number-key swaps and offhand swaps) before resolving the selected destination.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        String action = resolveMenuAction(holder, event);
        if (action == null) return;

        switch (action) {
            case "RANKED" -> openLadderMenu(player, "ranked");
            case "UNRANKED" -> openLadderMenu(player, "unranked");
            case "PRIVATE_DUEL" -> openDuelTargetMenu(player);
            case "PARTY_HOME" -> {
                player.closeInventory();
                player.sendMessage(plugin.parties().info(player));
                player.sendMessage(color("&7Party commands: &f/party create, /party invite <player>, /party accept, /party leave"));
            }
            case "JOIN_FFA" -> openFfaMenu(player);
            case "CLOSE_MENU" -> player.closeInventory();
            case "ENTER_PRACTICE" -> {
                player.closeInventory();
                if (!profiles.teleportToPractice(player)) {
                    player.sendMessage(color("&cThe shared PvP world is not loaded. Ask an admin to run /bp world set pvp."));
                } else {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && profiles.isPracticeWorld(player.getWorld())) openPracticeMenu(player);
                    }, 6L);
                }
            }
            case "LEAVE_FFA" -> { player.closeInventory(); if (plugin.ffa() != null) plugin.ffa().leave(player); }
            case "BACK_PRACTICE" -> { if (profiles.isLobbyWorld(player.getWorld())) openLobbyModeMenu(player); else openPracticeMenu(player); }
            case "BACK_DUEL_TARGETS" -> openDuelTargetMenu(player);
            case "BACK_FFA" -> openFfaMenu(player);
            default -> {
                if (action.startsWith("SERVER_DESTINATION:")) {
                    String destination = action.substring("SERVER_DESTINATION:".length());
                    player.closeInventory();
                    if (plugin.npcs() != null) {
                        // Single source of truth: selector heads execute exactly the same
                        // action dispatcher as clicking the corresponding lobby NPC.
                        plugin.npcs().performAction(player, destination);
                    } else {
                        player.sendMessage(color("&cServer destination actions are unavailable. Restart BlazingPractice and check its startup log."));
                    }
                } else if (action.startsWith("FFA_MODE:")) {
                    String ladderId = action.substring("FFA_MODE:".length());
                    player.closeInventory();
                    if (plugin.ffa() != null) plugin.ffa().join(player, ladderId);
                } else if (action.startsWith("QUEUE:")) {
                    String[] parts = action.split(":", 3);
                    if (parts.length == 3) {
                        player.closeInventory();
                        joinQueue(player, parts[1], parts[2]);
                    }
                } else if (action.startsWith("DUEL_TARGET:")) {
                    String rawId = action.substring("DUEL_TARGET:".length());
                    try { openDuelLadderMenu(player, UUID.fromString(rawId)); }
                    catch (IllegalArgumentException exception) { player.sendMessage(color("&cThat player selection is invalid.")); }
                } else if (action.startsWith("DUEL_LADDER:")) {
                    String[] parts = action.split(":", 3);
                    if (parts.length == 3) {
                        try {
                            UUID targetId = UUID.fromString(parts[1]);
                            Player target = Bukkit.getPlayer(targetId);
                            player.closeInventory();
                            if (target == null || !target.isOnline()) player.sendMessage(color("&cThat player is no longer online."));
                            else if (!plugin.duels().invite(player, target, parts[2])) player.sendMessage(color("&cCould not send that duel request. Check player status and ladder availability."));
                        } catch (IllegalArgumentException exception) {
                            player.sendMessage(color("&cThat duel selection is invalid."));
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolve selector actions from PDC first. If another plugin strips item metadata,
     * use the configured slot and material in selector/selector.yml as a safe fallback.
     */
    private String resolveMenuAction(SelectorHolder holder, InventoryClickEvent event) {
        String action = getMenuAction(event.getCurrentItem());
        if (action != null) return action;
        if (!"server-selector".equals(holder.menu()) || event.getCurrentItem() == null
                || event.getCurrentItem().getType().isAir()) return null;
        ConfigurationSection buttons = selectorConfig.getConfigurationSection("menu.buttons");
        if (buttons == null) return null;
        for (String id : buttons.getKeys(false)) {
            ConfigurationSection button = buttons.getConfigurationSection(id);
            if (button == null || !button.getBoolean("enabled", true)) continue;
            if (button.getInt("slot", -1) != event.getRawSlot()) continue;
            Material configured = Material.matchMaterial(button.getString("material", "PLAYER_HEAD"));
            if (configured != null && configured == event.getCurrentItem().getType()) {
                return "SERVER_DESTINATION:" + id;
            }
        }
        return null;
    }

    /** Main server-selector GUI. Buttons are fully data-driven from plugins/BlazingPractice/selector/selector.yml. */
    public void openServerSelector(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!profiles.isLobbyWorld(player.getWorld())) {
            player.sendMessage(color("&cThe Server Selector is available in the main lobby."));
            return;
        }
        if (!selectorConfig.getBoolean("menu.enabled", true)) {
            player.sendMessage(color("&cThe server selector menu is disabled by an administrator."));
            return;
        }
        int size = selectorConfig.getInt("menu.size", 27);
        if (size < 9 || size > 54 || size % 9 != 0) size = 27;
        String title = selectorConfig.getString("menu.title", "&8Choose a Server");
        SelectorHolder holder = new SelectorHolder("server-selector");
        Inventory inventory = Bukkit.createInventory(holder, size, color(title));
        holder.setInventory(inventory);

        ConfigurationSection buttons = selectorConfig.getConfigurationSection("menu.buttons");
        if (buttons == null) {
            player.sendMessage(color("&cNo selector buttons are configured. Check selector/selector.yml."));
            return;
        }
        for (String id : buttons.getKeys(false)) {
            ConfigurationSection button = buttons.getConfigurationSection(id);
            if (button == null || !button.getBoolean("enabled", true)) continue;
            int slot = button.getInt("slot", -1);
            if (slot < 0 || slot >= size) {
                plugin.getLogger().warning("Selector button '" + id + "' has an invalid slot " + slot + ".");
                continue;
            }
            Material material = Material.matchMaterial(button.getString("material", "PLAYER_HEAD"));
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Selector button '" + id + "' has an invalid material.");
                continue;
            }
            String name = button.getString("name", "&f" + id);
            List<String> lore = button.getStringList("lore");
            ItemStack item = menuItem(material, name, "SERVER_DESTINATION:" + id, lore);
            if (material == Material.PLAYER_HEAD) applyHeadSkin(item, button.getString("skin-owner", ""));
            inventory.setItem(slot, item);
        }
        player.openInventory(inventory);
    }

    private void addSelectorItem(Player player) {
        ConfigurationSection itemConfig = selectorConfig.getConfigurationSection("selector-item");
        if (itemConfig == null || !itemConfig.getBoolean("enabled", true)) return;
        Material material = Material.matchMaterial(itemConfig.getString("material", "WOODEN_AXE"));
        if (material == null || !material.isItem()) material = Material.WOODEN_AXE;
        ItemStack item = taggedItem(material,
                itemConfig.getString("name", "&eServer Selector"),
                itemConfig.getStringList("lore"), actionKey, "SERVER_SELECTOR");
        int slot = itemConfig.getInt("slot", 0);
        if (slot < 0 || slot >= player.getInventory().getSize()) slot = 0;
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) player.getInventory().setItem(slot, item);
        else if (getAction(existing) == null) {
            int free = player.getInventory().firstEmpty();
            if (free >= 0) player.getInventory().setItem(free, item);
            else player.sendMessage(color("&eYour inventory is full; there is no free slot for the Server Selector."));
        }
    }

    private void applyHeadSkin(ItemStack item, String ownerName) {
        if (ownerName == null || ownerName.isBlank() || !(item.getItemMeta() instanceof SkullMeta skull)) return;
        skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName.trim()));
        item.setItemMeta(skull);
    }

    /** Executes the action configured for one selector button. NPC clicks call this same method. */
    public boolean performDestinationAction(Player player, String rawId) {
        if (player == null || !player.isOnline() || rawId == null) return false;
        String id = rawId.toLowerCase(Locale.ROOT).trim();
        ConfigurationSection button = selectorConfig.getConfigurationSection("menu.buttons." + id);
        if (button == null) return false;
        ConfigurationSection action = button.getConfigurationSection("action");
        if (action == null) {
            player.sendMessage(color("&cNo action configured for selector button '" + id + "'."));
            return true;
        }
        String type = action.getString("type", "message").toLowerCase(Locale.ROOT);
        switch (type) {
            case "command", "console_command" -> {
                String command = action.getString("command", action.getString("value", "")).trim();
                if (command.isBlank() || command.contains("\n") || command.contains("\r")) {
                    player.sendMessage(color("&cThe configured command for '" + id + "' is invalid."));
                    return true;
                }
                command = command.replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString())
                        .replace("{world}", player.getWorld().getName());
                if (command.startsWith("/")) command = command.substring(1);
                boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                if (!dispatched) plugin.getLogger().warning("Selector command returned false for button '" + id + "': " + command);
            }
            case "url" -> sendClickableUrl(player, action.getString("message", "Click to open the link"), action.getString("url", ""));
            case "links" -> sendConfiguredLinks(player, action);
            case "message" -> player.sendMessage(color(action.getString("message", action.getString("value", ""))));
            case "practice_menu" -> {
                if (profiles.isLobbyWorld(player.getWorld())) openLobbyModeMenu(player);
                else if (plugin.lobby() != null && plugin.lobby().teleport(player)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) openLobbyModeMenu(player); }, 6L);
                } else player.sendMessage(color("&cThe Practice lobby is not configured."));
            }
            default -> {
                player.sendMessage(color("&cUnknown selector action type '" + type + "' for '" + id + "'."));
                plugin.getLogger().warning("Unsupported selector action type '" + type + "' in selector/selector.yml for button '" + id + "'.");
            }
        }
        return true;
    }

    private void sendConfiguredLinks(Player player, ConfigurationSection action) {
        String message = action.getString("message", "Choose a link:");
        if (!message.isBlank()) player.sendMessage(color(message));
        for (Map<?, ?> raw : action.getMapList("links")) {
            Object labelValue = raw.get("label");
            Object urlValue = raw.get("url");
            String label = labelValue == null ? "Open link" : String.valueOf(labelValue);
            String url = urlValue == null ? "" : String.valueOf(urlValue);
            if (!isSafeUrl(url)) {
                plugin.getLogger().warning("Ignoring invalid URL in selector configuration for player click: " + url);
                continue;
            }
            player.sendMessage(Component.text("» " + label, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.openUrl(url)));
        }
    }

    private void sendClickableUrl(Player player, String message, String url) {
        if (!isSafeUrl(url)) {
            player.sendMessage(color("&cThis link is invalid. Ask an administrator to check selector/selector.yml."));
            return;
        }
        player.sendMessage(Component.text(message.isBlank() ? "Click to open link" : ChatColor.stripColor(color(message)), NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(url)));
    }

    private boolean isSafeUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean reloadSelectorConfig() {
        if (!selectorFile.exists()) return false;
        try {
            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(selectorFile);
            if (loaded.getConfigurationSection("menu.buttons") == null) {
                plugin.getLogger().warning("selector/selector.yml has no menu.buttons section.");
                return false;
            }
            this.selectorConfig = loaded;
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not load selector/selector.yml", exception);
            return false;
        }
    }

    /** Persists a URL edited via /bp npc url so the NPC and selector head stay synchronized. */
    public boolean setDestinationUrl(String rawId, String url) {
        if (rawId == null || !isSafeUrl(url)) return false;
        String id = rawId.toLowerCase(Locale.ROOT).trim();
        ConfigurationSection action = selectorConfig.getConfigurationSection("menu.buttons." + id + ".action");
        if (action == null) return false;
        String type = action.getString("type", "").toLowerCase(Locale.ROOT);
        if (type.equals("url")) {
            action.set("url", url);
        } else if (type.equals("links")) {
            List<Map<?, ?>> links = action.getMapList("links");
            if (links.isEmpty()) return false;
            List<Map<String, Object>> updated = new java.util.ArrayList<>();
            for (int i = 0; i < links.size(); i++) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : links.get(i).entrySet()) {
                    if (entry.getKey() != null) copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                if (i == 0) copy.put("url", url);
                updated.add(copy);
            }
            action.set("links", updated);
        } else {
            return false;
        }
        try {
            selectorConfig.save(selectorFile);
            return true;
        } catch (java.io.IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save selector URL change", exception);
            return false;
        }
    }

    /** Rebuilds the selector hotbar for online players after a configuration edit. */
    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            profiles.whenReady(player, () -> ensureForWorld(player));
        }
    }

    public boolean setSelectorBlock(org.bukkit.block.Block block) {
        if (block == null || block.getWorld() == null || !profiles.isLobbyWorld(block.getWorld())) return false;
        Material material = block.getType();
        String name = material.name();
        if (!(name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.equals("BAMBOO_BLOCK") || name.equals("MUSHROOM_STEM"))) return false;
        String path = "selector.block.";
        plugin.getConfig().set(path + "enabled", true);
        plugin.getConfig().set(path + "world", block.getWorld().getName());
        plugin.getConfig().set(path + "x", block.getX());
        plugin.getConfig().set(path + "y", block.getY());
        plugin.getConfig().set(path + "z", block.getZ());
        plugin.getConfig().set(path + "material", material.name());
        plugin.saveConfig();
        return true;
    }

    public void clearSelectorBlock() {
        plugin.getConfig().set("selector.block.enabled", false);
        plugin.getConfig().set("selector.block.world", null);
        plugin.getConfig().set("selector.block.x", null);
        plugin.getConfig().set("selector.block.y", null);
        plugin.getConfig().set("selector.block.z", null);
        plugin.getConfig().set("selector.block.material", null);
        plugin.saveConfig();
    }

    public String selectorBlockInfo() {
        if (!plugin.getConfig().getBoolean("selector.block.enabled", false)) return "not set";
        return plugin.getConfig().getString("selector.block.world", "?") + " @ "
                + plugin.getConfig().getInt("selector.block.x") + ", "
                + plugin.getConfig().getInt("selector.block.y") + ", "
                + plugin.getConfig().getInt("selector.block.z") + " ("
                + plugin.getConfig().getString("selector.block.material", "wood") + ")";
    }

    private boolean isConfiguredSelectorBlock(org.bukkit.block.Block block) {
        if (block == null || !plugin.getConfig().getBoolean("selector.block.enabled", false)) return false;
        return block.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString("selector.block.world", ""))
                && block.getX() == plugin.getConfig().getInt("selector.block.x")
                && block.getY() == plugin.getConfig().getInt("selector.block.y")
                && block.getZ() == plugin.getConfig().getInt("selector.block.z");
    }

    public void giveSelector(Player player) {
        if (player == null || !player.isOnline()) return;
        ConfigurationSection itemConfig = selectorConfig.getConfigurationSection("selector-item");
        Material material = itemConfig == null ? Material.WOODEN_AXE : Material.matchMaterial(itemConfig.getString("material", "WOODEN_AXE"));
        if (material == null || !material.isItem()) material = Material.WOODEN_AXE;
        ItemStack selector = taggedItem(material,
                itemConfig == null ? "&eServer Selector" : itemConfig.getString("name", "&eServer Selector"),
                itemConfig == null ? List.of("&7Right-click to open the server menu") : itemConfig.getStringList("lore"),
                actionKey, "SERVER_SELECTOR");
        int slot = itemConfig == null ? 0 : Math.max(0, Math.min(8, itemConfig.getInt("slot", 0)));
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) player.getInventory().setItem(slot, selector);
        else player.getInventory().addItem(selector);
        player.sendMessage(color("&aServer Selector given."));
    }

    public void openPracticeMenu(Player player) {
        if (plugin.ffa() != null && plugin.ffa().isActive(player)) {
            player.sendMessage(color("&cLeave FFA first using /ffa leave before opening Practice matchmaking."));
            return;
        }
        if (!profiles.isPracticeWorld(player.getWorld())) {
            player.sendMessage(color("&cEnter the configured shared PvP world from the BlazingPractice lobby first."));
            return;
        }
        SelectorHolder holder = new SelectorHolder("practice");
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&2Select Practice Match Type"));
        holder.setInventory(inventory);
        inventory.setItem(10, menuItem(Material.IRON_SWORD, "&cRanked Queue", "RANKED",
                List.of("&7Choose the exact ladder first", "&eClick to select a ranked kit")));
        inventory.setItem(13, menuItem(Material.WOODEN_SWORD, "&eUnranked Queue", "UNRANKED",
                List.of("&7Choose the exact ladder first", "&eClick to select an unranked kit")));
        inventory.setItem(16, menuItem(Material.DIAMOND_SWORD, "&bPrivate Duel", "PRIVATE_DUEL",
                List.of("&7Challenge an online player and choose a ladder", "&e/duel <online-player> [ladder]")));
        inventory.setItem(22, menuItem(Material.PLAYER_HEAD, "&dParty", "PARTY_HOME",
                List.of("&7Party information and commands", "&f/party help")));
        player.openInventory(inventory);
    }

    /** Opens an online-player picker for the private duel flow. */
    public void openDuelTargetMenu(Player player) {
        if (!profiles.isPracticeWorld(player.getWorld()) || (plugin.ffa() != null && plugin.ffa().isActive(player))
                || plugin.duels().isInMatch(player)) {
            player.sendMessage(color("&cEnter Practice mode and leave current matches before challenging someone."));
            return;
        }
        SelectorHolder holder = new SelectorHolder("duel-targets");
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&8Choose Duel Opponent"));
        holder.setInventory(inventory);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,46,47,48,49,50,51,52};
        int index = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || !target.getWorld().getUID().equals(player.getWorld().getUID())
                    || plugin.duels().isInMatch(target) || (plugin.ffa() != null && plugin.ffa().isActive(target))) continue;
            if (index >= slots.length) break;
            inventory.setItem(slots[index++], menuItem(Material.PLAYER_HEAD, "&e" + target.getName(),
                    "DUEL_TARGET:" + target.getUniqueId(),
                    List.of("&7Choose a ladder for this player", "&eClick to continue")));
        }
        if (index == 0) inventory.setItem(22, menuItem(Material.BARRIER, "&cNo duel opponents", "INFO",
                List.of("&7No other available players are in Practice.")));
        inventory.setItem(45, menuItem(Material.ARROW, "&cBack", "BACK_PRACTICE", List.of("&7Return to Practice match types")));
        player.openInventory(inventory);
    }

    /** Lets a player choose the ladder after selecting an online duel opponent. */
    public void openDuelLadderMenu(Player player, Player target) {
        if (target == null || !target.isOnline()) {
            player.sendMessage(color("&cThat player is not online."));
            return;
        }
        openDuelLadderMenu(player, target.getUniqueId());
    }

    public void openDuelLadderMenu(Player player, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || target.equals(player)
                || !profiles.isPracticeWorld(player.getWorld())
                || !target.getWorld().getUID().equals(player.getWorld().getUID())
                || plugin.duels().isInMatch(player) || plugin.duels().isInMatch(target)
                || (plugin.ffa() != null && (plugin.ffa().isActive(player) || plugin.ffa().isActive(target)))) {
            player.sendMessage(color("&cBoth players must be available in the same Practice world."));
            return;
        }
        List<LadderRegistry.Ladder> ladders = plugin.ladders().all().stream()
                .filter(LadderRegistry.Ladder::enabled).toList();
        if (ladders.isEmpty()) {
            player.sendMessage(color("&cNo duel ladders are enabled. Check /ladder list."));
            return;
        }
        SelectorHolder holder = new SelectorHolder("duel-ladders:" + targetId);
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&8Duel Kit: " + target.getName()));
        holder.setInventory(inventory);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,46,47,48,49,50,51,52};
        int index = 0;
        for (LadderRegistry.Ladder ladder : ladders) {
            if (index >= slots.length) break;
            inventory.setItem(slots[index++], menuItem(ladder.icon(), "&e" + ladder.name(),
                    "DUEL_LADDER:" + targetId + ":" + ladder.id(),
                    List.of("&7Opponent: &f" + target.getName(), "&7Kit: &f" + ladder.name(), "&eClick to send duel request")));
        }
        inventory.setItem(45, menuItem(Material.ARROW, "&cBack", "BACK_DUEL_TARGETS", List.of("&7Choose another opponent")));
        player.openInventory(inventory);
    }

    /** Opens a ladder-specific selector; selecting an item joins only that ladder's queue. */
    public void openLadderMenu(Player player, String queueType) {
        if (!profiles.isPracticeWorld(player.getWorld()) || (plugin.ffa() != null && plugin.ffa().isActive(player))) {
            player.sendMessage(color("&cYou must be in Practice mode to select a duel ladder."));
            return;
        }
        String type = "ranked".equalsIgnoreCase(queueType) ? "ranked" : "unranked";
        List<LadderRegistry.Ladder> ladders = plugin.ladders().available(type);
        if (ladders.isEmpty()) {
            player.sendMessage(color("&cNo ladders are enabled for " + type + " matchmaking. Ask an admin to check plugins/BlazingPractice/ladders."));
            return;
        }
        SelectorHolder holder = new SelectorHolder("ladder:" + type);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                color(type.equals("ranked") ? "&4Select Ranked Ladder" : "&6Select Unranked Ladder"));
        holder.setInventory(inventory);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,46,47,48,49,50,51,52};
        int index = 0;
        for (LadderRegistry.Ladder ladder : ladders) {
            if (index >= slots.length) break;
            String mode = type.toUpperCase(java.util.Locale.ROOT);
            inventory.setItem(slots[index++], menuItem(ladder.icon(), "&e" + ladder.name(),
                    "QUEUE:" + type + ":" + ladder.id(),
                    List.of("&7Type: &f" + ladder.type(), "&7Queue: &f" + mode,
                            "&cKit-specific gameplay is still being ported", "&eClick to enter this queue")));
        }
        inventory.setItem(45, menuItem(Material.ARROW, "&cBack", "BACK_PRACTICE", List.of("&7Return to match type selection")));
        inventory.setItem(53, menuItem(Material.BOOK, "&bLadder Selection", "INFO",
                List.of("&7Choose the game type you want", "&7to play before entering a queue.")));
        player.openInventory(inventory);
    }

    /** Shows enabled FFA ladders before a player is moved into the FFA arena. */
    public void openFfaMenu(Player player) {
        if (plugin.ffa() == null || !plugin.getConfig().getBoolean("ffa.enabled", true)) {
            player.sendMessage(color("&cFFA is currently unavailable."));
            return;
        }
        List<LadderRegistry.Ladder> modes = plugin.ladders().all().stream()
                .filter(LadderRegistry.Ladder::enabled)
                .toList();
        if (modes.isEmpty()) {
            player.sendMessage(color("&cThere are no enabled FFA modes. Ask an admin to enable ladders with /ladder enable <ladder>."));
            return;
        }
        SelectorHolder holder = new SelectorHolder("ffa-modes");
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&8Choose an FFA Mode"));
        holder.setInventory(inventory);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int index = 0;
        for (LadderRegistry.Ladder ladder : modes) {
            if (index >= slots.length) break;
            inventory.setItem(slots[index++], menuItem(ladder.icon(), "&6" + ladder.name() + " FFA",
                    "FFA_MODE:" + ladder.id(),
                    List.of("&7FFA mode: &f" + ladder.name(), "&7Choose this mode to enter FFA", "&eClick to play")));
        }
        inventory.setItem(45, menuItem(Material.ARROW, "&cBack", "BACK_PRACTICE",
                List.of("&7Return to Practice menu")));
        inventory.setItem(49, menuItem(Material.BARRIER, "&cClose", "CLOSE_MENU", List.of("&7Close this menu")));
        player.openInventory(inventory);
    }

    public void openLobbyModeMenu(Player player) {
        if (!profiles.isLobbyWorld(player.getWorld())) {
            player.sendMessage(color("&cReturn to the BlazingPractice lobby to choose a mode."));
            return;
        }
        SelectorHolder holder = new SelectorHolder("mode-selector");
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&8BlazingPractice Modes"));
        holder.setInventory(inventory);
        inventory.setItem(11, menuItem(Material.IRON_SWORD, "&aPractice", "ENTER_PRACTICE",
                List.of("&7Enter the shared PvP world", "&7Ranked and Unranked duels", "&eClick to continue")));
        inventory.setItem(15, menuItem(Material.GOLDEN_SWORD, "&6FFA", "JOIN_FFA",
                List.of("&7Join FFA at its own spawn point", "&7Open combat and FFA respawns", "&eClick to join")));
        player.openInventory(inventory);
    }

    private void joinQueue(Player player, String queueType, String ladder) {
        if (plugin.queues() == null || !plugin.queues().join(player, queueType, ladder)) {
            player.sendMessage(color("&cCould not join " + queueType + " " + ladder + ". Check ladder availability, Practice mode and a ready DUEL arena."));
        }
    }

    private ItemStack menuItem(Material material, String name, String action, List<String> lore) {
        return taggedItem(material, name, lore, menuActionKey, action);
    }

    private ItemStack taggedItem(Material material, String name, List<String> lore, NamespacedKey key, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(this::color).toList());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var data = item.getItemMeta().getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        if (action != null) return action;
        if (data.has(legacySelectorKey, PersistentDataType.BYTE)) return "SELECTOR";
        return null;
    }

    private String getMenuAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
    }

    private void removeLegacySelectorItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(legacySelectorKey, PersistentDataType.BYTE)) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) player.getInventory().setContents(contents);
    }

    private void removeManagedItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (getAction(contents[i]) != null) { contents[i] = null; changed = true; }
        }
        if (changed) player.getInventory().setContents(contents);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (getAction(offhand) != null) player.getInventory().setItemInOffHand(null);
    }

    private void sendHelp(Player player) {
        player.sendMessage(color("&aBlazingPractice mode controls"));
        player.sendMessage(color("&ePractice sword &7- Ranked and unranked matchmaking"));
        player.sendMessage(color("&6FFA sword &7- Join FFA at its spawn in the shared PvP world"));
        player.sendMessage(color("&e/ffa join [ladder]|leave|setspawn &7- FFA controls"));
        player.sendMessage(color("&e/duel <online-player> [ladder] &7- Challenge a player; Tab suggests online names and ladders"));
        player.sendMessage(color("&e/party create|invite <online-player>|accept|leave &7- Party commands"));
        player.sendMessage(color("&e/blazingpractice lobby &7- Return to the Practice lobby"));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final class SelectorHolder implements InventoryHolder {
        private final String menu;
        private Inventory inventory;
        private SelectorHolder(String menu) { this.menu = menu; }
        @SuppressWarnings("unused") public String menu() { return menu; }
        void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
