package com.wepons;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class GuiListener implements Listener {

    private final Wepons plugin;

    public GuiListener(Wepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof WeponsGui gui)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        // tabs (both menus)
        int raw = e.getRawSlot();
        String goCat = null;
        if (raw == gui.crazySlot) goCat = WeponsGui.CRAZY;
        else if (raw == gui.reliqueSlot) goCat = WeponsGui.RELIQUE;
        else if (raw == gui.kingsSlot) goCat = WeponsGui.KINGS;
        if (goCat != null) {
            final String cat = goCat;
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            plugin.getServer().getScheduler().runTask(plugin, () -> WeponsGui.open(plugin, p, gui.type, cat));
            return;
        }

        if (gui.type != WeponsGui.Type.WEAPONS) return;
        if (!p.hasPermission("wepons.use")) return;

        // Kings SMP tab: items come from the KingsSMP plugin
        String kingsId = gui.kingsIds.get(raw);
        if (kingsId != null) {
            if (plugin.kings().give(p, kingsId)) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                ItemStack shown = e.getCurrentItem();
                net.kyori.adventure.text.Component name = shown != null && shown.hasItemMeta()
                        && shown.getItemMeta().hasDisplayName()
                        ? shown.getItemMeta().displayName()
                        : net.kyori.adventure.text.Component.text(kingsId);
                p.sendActionBar(net.kyori.adventure.text.Component.text("Received ",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY).append(name));
            }
            return;
        }

        Weapon w = plugin.registry().of(e.getCurrentItem());
        if (w == null) return;

        ItemStack item = plugin.registry().create(w.id);
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        left.values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        p.sendActionBar(net.kyori.adventure.text.Component.text("Received ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(plugin.registry().title(w)));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof WeponsGui) e.setCancelled(true);
    }
}
