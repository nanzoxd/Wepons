package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers who holds each weapon. Holders are saved to holders.yml, so a
 * player still shows up after logging off.
 */
public final class HolderManager {

    private final Wepons plugin;
    private final File file;
    private final Map<String, Map<UUID, String>> data = new HashMap<>();

    public HolderManager(Wepons plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holders.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection sec = yaml.getConfigurationSection(id);
            if (sec == null) continue;
            Map<UUID, String> map = new LinkedHashMap<>();
            for (String uuid : sec.getKeys(false)) {
                try {
                    map.put(UUID.fromString(uuid), sec.getString(uuid, "Unknown"));
                } catch (IllegalArgumentException ignored) {
                    // bad key, skip it
                }
            }
            data.put(id, map);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        data.forEach((id, map) -> map.forEach((uuid, name) -> yaml.set(id + "." + uuid, name)));
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save holders.yml: " + ex.getMessage());
        }
    }

    /** Updates the holder lists using what this player is carrying right now. */
    public void scan(Player p) {
        Set<String> carried = new HashSet<>();
        for (ItemStack item : p.getInventory().getContents()) {
            Weapon w = plugin.registry().of(item);
            if (w != null) carried.add(w.id);
        }

        boolean changed = false;
        for (Weapon w : plugin.registry().all()) {
            Map<UUID, String> map = data.computeIfAbsent(w.id, k -> new LinkedHashMap<>());
            if (carried.contains(w.id)) {
                String old = map.put(p.getUniqueId(), p.getName());
                if (!p.getName().equals(old)) changed = true;
            } else if (map.remove(p.getUniqueId()) != null) {
                changed = true;
            }
        }
        if (changed) save();
    }

    public Map<UUID, String> holdersOf(String weaponId) {
        return new LinkedHashMap<>(data.getOrDefault(weaponId, Map.of()));
    }

    public static boolean isOnline(UUID uuid) {
        return Bukkit.getPlayer(uuid) != null;
    }
}
