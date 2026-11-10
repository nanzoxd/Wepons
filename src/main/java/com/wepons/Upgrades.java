package com.wepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Level II tasks. A weapon with a task (Weapon#upgrade) starts at Level I and reaches Level II when the
 * task is done: land hits, use its ability, or kill players. Level II = faster ability recharge
 * (config upgrades.level2-cooldown-multiplier) and stronger visual effects. Damage is never changed here.
 */
public final class Upgrades implements Listener {

    private final Wepons plugin;
    private final WeaponRegistry reg;

    public Upgrades(Wepons plugin) {
        this.plugin = plugin;
        this.reg = plugin.registry();
    }

    /** Counts hits for HITS tasks. Runs last and only reads the event, so damage is never touched. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity) || e.getEntity() instanceof ArmorStand) return;
        progress(p, Weapon.UpType.HITS);
    }

    /** Called after one of the weapon's abilities was used (ABILITY tasks). */
    public void onAbility(Player p, Weapon w) {
        if (w.upgrade != null && w.upgrade.type() == Weapon.UpType.ABILITY) progress(p, Weapon.UpType.ABILITY);
    }

    /** Called after a kill was counted on the weapon (KILLS tasks). */
    public void onKill(Player p, ItemStack item, Weapon w, int kills) {
        if (w.upgrade == null || w.upgrade.type() != Weapon.UpType.KILLS) return;
        if (reg.level(item) >= 2) return;
        if (kills >= reg.upgradeAmount(w)) levelUp(p, item, w);
    }

    private void progress(Player p, Weapon.UpType type) {
        ItemStack item = p.getInventory().getItemInMainHand();
        Weapon w = reg.of(item);
        if (w == null || w.upgrade == null || w.upgrade.type() != type) return;
        if (reg.level(item) >= 2) return;

        int need = reg.upgradeAmount(w);
        int prog = reg.progress(item) + 1;
        if (prog >= need) {
            reg.setUpgrade(item, 1, prog);
            levelUp(p, item, w);
        } else {
            // the lore is refreshed every 5 steps (and on level-up) so the held item is not rebuilt on every hit
            if (prog % 5 == 0) reg.setUpgrade(item, 1, prog);
            else storeQuietly(item, prog);
            p.sendActionBar(Component.text(w.name + " task ", w.color)
                    .append(Component.text(prog + "/" + need, NamedTextColor.GRAY)));
        }
        p.getInventory().setItemInMainHand(item);
    }

    /** Saves progress without rebuilding the lore. */
    private void storeQuietly(ItemStack item, int prog) {
        reg.setProgressOnly(item, prog);
    }

    private void levelUp(Player p, ItemStack item, Weapon w) {
        reg.setUpgrade(item, 2, reg.progress(item));
        p.getInventory().setItemInMainHand(item);
        p.sendActionBar(Component.text(w.name + " reached ", w.color)
                .append(Component.text("Level II", NamedTextColor.GOLD)));
        plugin.fx().levelUp(p, w);
    }
}
