package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shows every weapon's ability cooldown as its durability bar (same idea as the Bucket of Greed).
 *
 * The bar fills as the cooldown runs down and disappears when the ability is ready. A weapon that is sneaked with
 * shows the Shift + F ability, otherwise the F ability. Real durability loss is blocked, so the bar is only ever a
 * cooldown. Every weapon in the inventory is updated (not just the held one), items from older versions are fixed
 * on the fly, and an item is only rewritten when its bar actually changes (13 pixels, so at most 13 changes per cooldown).
 */
public final class CooldownBar implements Listener {

    private static final int STEPS = 26;   // twice the bar width: smooth enough, few inventory updates

    private final Wepons plugin;

    public CooldownBar(Wepons plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    private void tick() {
        WeaponRegistry reg = plugin.registry();
        Abilities abilities = plugin.abilities();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            boolean sneak = p.isSneaking();
            int size = inv.getSize();   // storage + armor + offhand
            for (int slot = 0; slot < size; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType().isAir()) continue;
                Weapon w = reg.of(item);
                if (w == null) continue;

                double frac = abilities.cooldownFraction(p, w, sneak);
                int steps = frac <= 0 ? 0 : Math.max(1, (int) Math.ceil(frac * STEPS));
                int damage = steps * (WeaponRegistry.BAR_MAX / STEPS);

                ItemMeta meta = item.getItemMeta();
                if (!(meta instanceof Damageable d)) continue;
                boolean fix = meta.isUnbreakable() || !d.hasMaxDamage() || d.getMaxDamage() != WeaponRegistry.BAR_MAX;
                if (!fix && d.getDamage() == damage) continue;

                if (fix) WeaponRegistry.prepareBar(item, meta);
                d.setDamage(damage);
                item.setItemMeta(meta);
                inv.setItem(slot, item);
            }
        }
    }

    /** Weapons never wear out: the durability value belongs to the cooldown bar. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent e) {
        if (plugin.registry().isWeapon(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent e) {
        if (plugin.registry().isWeapon(e.getItem())) e.setCancelled(true);
    }
}
