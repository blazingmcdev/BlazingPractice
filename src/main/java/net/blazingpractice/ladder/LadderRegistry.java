package net.blazingpractice.ladder;

import net.blazingpractice.BlazingPractice;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads sanitized, editable ladder definitions from plugins/BlazingPractice/ladders/*.yml. */
public final class LadderRegistry {
    private final BlazingPractice plugin;
    private final File folder;
    private final Map<String, Ladder> ladders = new LinkedHashMap<>();

    public LadderRegistry(BlazingPractice plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "ladders");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the ladders directory: " + folder.getAbsolutePath());
        }
        saveBundledDefinitions();
        reload();
    }

    private void saveBundledDefinitions() {
        String[] defaults = {
                "archer.yml", "axe.yml", "battlerush.yml", "bedwars.yml", "boxing.yml", "bridges.yml",
                "builduhc.yml", "cart.yml", "creeper.yml", "crystal.yml", "diamondpotion.yml", "fireball.yml",
                "gapple.yml", "mace.yml", "mlgrush.yml", "netheritepotion.yml", "pearlfight.yml", "sg.yml",
                "skywars.yml", "soup.yml", "spearelytra.yml", "spearmace.yml", "spleef.yml", "sumo.yml",
                "sword.yml", "tntsumo.yml"
        };
        for (String name : defaults) {
            File target = new File(folder, name);
            if (!target.exists()) {
                try {
                    plugin.saveResource("ladders/" + name, false);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Bundled ladder definition is missing: " + name);
                }
            }
        }
    }

    public void reload() {
        ladders.clear();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            String name = yaml.getString("name", id);
            String type = yaml.getString("type", "BASIC").toUpperCase(Locale.ROOT);
            Material icon = Material.matchMaterial(yaml.getString("icon", "DIAMOND_SWORD"));
            if (icon == null || !icon.isItem()) icon = Material.DIAMOND_SWORD;
            Ladder ladder = new Ladder(id, name, type, icon, yaml.getBoolean("enabled", true),
                    yaml.getBoolean("queue-enabled", true), yaml.getBoolean("ranked", true),
                    yaml.getBoolean("unranked", true), file);
            ladders.put(id, ladder);
        }
    }

    public List<Ladder> all() {
        return List.copyOf(ladders.values());
    }

    public List<Ladder> available(String queueType) {
        boolean ranked = "ranked".equalsIgnoreCase(queueType);
        return ladders.values().stream()
                .filter(Ladder::enabled)
                .filter(Ladder::queueEnabled)
                .filter(ladder -> ranked ? ladder.ranked() : ladder.unranked())
                .toList();
    }

    public Optional<Ladder> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(ladders.get(id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "")));
    }

    public boolean setEnabled(String id, boolean enabled) {
        Optional<Ladder> found = find(id);
        if (found.isEmpty()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(found.get().file());
        yaml.set("enabled", enabled);
        try {
            yaml.save(found.get().file());
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update ladder " + id + ": " + exception.getMessage());
            return false;
        }
    }


    public boolean create(String rawId) {
        String id = normalize(rawId);
        if (id.isBlank() || ladders.containsKey(id)) return false;
        File target = new File(folder, id + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", toDisplayName(id));
        yaml.set("enabled", false);
        yaml.set("type", "BASIC");
        yaml.set("icon", "DIAMOND_SWORD");
        yaml.set("ranked", true);
        yaml.set("unranked", true);
        yaml.set("queue-enabled", true);
        yaml.set("description", "Custom BlazingPractice ladder. Configure its kit before enabling it for players.");
        try {
            yaml.save(target);
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create ladder " + id + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean cloneLadder(String sourceId, String newRawId) {
        Optional<Ladder> source = find(sourceId);
        String newId = normalize(newRawId);
        if (source.isEmpty() || newId.isBlank() || ladders.containsKey(newId)) return false;
        YamlConfiguration original = YamlConfiguration.loadConfiguration(source.get().file());
        original.set("name", toDisplayName(newId));
        original.set("enabled", false);
        try {
            original.save(new File(folder, newId + ".yml"));
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not clone ladder " + sourceId + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean delete(String id) {
        Optional<Ladder> found = find(id);
        if (found.isEmpty()) return false;
        boolean deleted = found.get().file().delete();
        if (deleted) reload();
        return deleted;
    }

    public boolean setIcon(String id, Material material) {
        Optional<Ladder> found = find(id);
        if (found.isEmpty() || material == null || !material.isItem()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(found.get().file());
        yaml.set("icon", material.name());
        try {
            yaml.save(found.get().file());
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update ladder icon " + id + ": " + exception.getMessage());
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private String toDisplayName(String value) {
        String[] words = value.replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public record Ladder(String id, String name, String type, Material icon, boolean enabled,
                         boolean queueEnabled, boolean ranked, boolean unranked, File file) { }
}
