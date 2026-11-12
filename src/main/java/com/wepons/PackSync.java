package com.wepons;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kings-SMP style tooltips (a different lore frame for every weapon) WITHOUT the purple/black
 * missing-texture box for players who do not have the pack.
 *
 * A tooltip_style whose sprites the client does not have draws the missing texture, so the style is only put on a
 * weapon while its holder has the pack. Everyone else keeps the plain vanilla tooltip (the models already fall back
 * to the vanilla netherite sword etc. because they only use custom_model_data).
 *
 * "Has the pack" = the client reported SUCCESSFULLY_LOADED for the server's pack, or the player ran /wepons pack on.
 * config.yml: tooltip-styles.mode = pack (default) | always | off.
 */
public final class PackSync implements Listener {

    private final Wepons plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> manual = new HashSet<>();
    private final File file;

    public PackSync(Wepons plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pack-users.yml");
        if (file.exists()) {
            for (String s : YamlConfiguration.loadConfiguration(file).getStringList("players")) {
                try {
                    manual.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /** Re-checks every online player once a second, so joins, pickups and chest moves all settle by themselves. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) sync(p);
        }, 40L, 20L);
    }

    private String mode() {
        return plugin.getConfig().getString("tooltip-styles.mode", "pack").toLowerCase(java.util.Locale.ROOT);
    }

    public boolean hasPack(Player p) {
        switch (mode()) {
            case "always": return true;
            case "off": return false;
            default: return loaded.contains(p.getUniqueId()) || manual.contains(p.getUniqueId());
        }
    }

    /** /wepons pack [on|off]: for players whose pack is installed by hand (the server never sends it). */
    public boolean toggleManual(Player p, Boolean value) {
        UUID id = p.getUniqueId();
        boolean on = value != null ? value : !manual.contains(id);
        if (on) manual.add(id);
        else manual.remove(id);
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("players", manual.stream().map(UUID::toString).toList());
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save pack-users.yml: " + ex.getMessage());
        }
        sync(p);
        return on;
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(id);
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL -> loaded.remove(id);
            default -> { }
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> sync(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }

    /** Puts the tooltip style on this player's weapons if they have the pack, takes it off if they don't. */
    public void sync(Player p) {
        boolean want = hasPack(p);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (apply(it, want)) inv.setItem(i, it);
        }
        ItemStack cursor = p.getItemOnCursor();
        if (apply(cursor, want)) p.setItemOnCursor(cursor);
    }

    private boolean apply(ItemStack item, boolean want) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Weapon w = plugin.registry().of(item);
        if (w == null) return false;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey current = meta.hasTooltipStyle() ? meta.getTooltipStyle() : null;
        NamespacedKey target = want ? plugin.registry().tooltipKey(w) : null;
        if (Objects.equals(current, target)) return false;
        meta.setTooltipStyle(target);
        item.setItemMeta(meta);
        return true;
    }

    /** Used by tab completion. */
    public static List<String> onOff() {
        return List.of("on", "off");
    }
}
