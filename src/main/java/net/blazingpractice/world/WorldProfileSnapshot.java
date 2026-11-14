package net.blazingpractice.world;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Serializable snapshot of the player state that the world-profile setting isolates. */
public final class WorldProfileSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack[] enderChestContents;
    private final ItemStack offhand;
    private final int heldSlot;
    private final int level;
    private final float experience;
    private final int totalExperience;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final List<EffectData> effects;

    private WorldProfileSnapshot(ItemStack[] storageContents, ItemStack[] armorContents, ItemStack[] enderChestContents, ItemStack offhand,
                                 int heldSlot, int level, float experience, int totalExperience,
                                 double health, int foodLevel, float saturation, float exhaustion,
                                 List<EffectData> effects) {
        this.storageContents = cloneItems(storageContents);
        this.armorContents = cloneItems(armorContents);
        this.enderChestContents = cloneItems(enderChestContents);
        this.offhand = offhand == null ? null : offhand.clone();
        this.heldSlot = heldSlot;
        this.level = level;
        this.experience = experience;
        this.totalExperience = totalExperience;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.effects = List.copyOf(effects);
    }

    public static WorldProfileSnapshot capture(Player player) {
        List<EffectData> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(new EffectData(effect.getType().getName(), effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
        return new WorldProfileSnapshot(player.getInventory().getStorageContents(),
                player.getInventory().getArmorContents(), player.getEnderChest().getContents(), player.getInventory().getItemInOffHand(),
                player.getInventory().getHeldItemSlot(), player.getLevel(), player.getExp(),
                player.getTotalExperience(), player.getHealth(), player.getFoodLevel(),
                player.getSaturation(), player.getExhaustion(), effects);
    }

    public static WorldProfileSnapshot empty() {
        return new WorldProfileSnapshot(new ItemStack[36], new ItemStack[4], new ItemStack[27], null, 0,
                0, 0.0f, 0, 20.0, 20, 5.0f, 0.0f, List.of());
    }

    public void apply(Player player) {
        player.getInventory().setStorageContents(cloneItems(storageContents));
        player.getInventory().setArmorContents(cloneItems(armorContents));
        player.getEnderChest().setContents(cloneItems(enderChestContents));
        player.getInventory().setItemInOffHand(offhand == null ? null : offhand.clone());
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));

        player.setLevel(level);
        player.setExp(Math.max(0.0f, Math.min(1.0f, experience)));
        player.setTotalExperience(Math.max(0, totalExperience));
        player.setFoodLevel(Math.max(0, Math.min(20, foodLevel)));
        player.setSaturation(Math.max(0.0f, saturation));
        player.setExhaustion(Math.max(0.0f, exhaustion));
        player.setHealth(Math.max(0.1, Math.min(player.getMaxHealth(), health)));

        for (PotionEffect current : player.getActivePotionEffects()) player.removePotionEffect(current.getType());
        for (EffectData effect : effects) {
            PotionEffectType type = PotionEffectType.getByName(effect.typeName);
            if (type != null && effect.duration > 0) {
                player.addPotionEffect(new PotionEffect(type, effect.duration, effect.amplifier,
                        effect.ambient, effect.particles, effect.icon));
            }
        }
    }

    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(this);
            output.flush();
            return bytes.toByteArray();
        }
    }

    public static WorldProfileSnapshot deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = input.readObject();
            if (!(value instanceof WorldProfileSnapshot snapshot)) {
                throw new IOException("Unexpected world profile snapshot type");
            }
            return snapshot;
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? null : items[i].clone();
        return copy;
    }

    private static final class EffectData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String typeName;
        private final int duration;
        private final int amplifier;
        private final boolean ambient;
        private final boolean particles;
        private final boolean icon;

        private EffectData(String typeName, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
            this.typeName = typeName;
            this.duration = duration;
            this.amplifier = amplifier;
            this.ambient = ambient;
            this.particles = particles;
            this.icon = icon;
        }
    }
}
