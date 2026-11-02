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

        if (gui.type != WeponsGui.Type.WEAPONS) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!p.hasPermission("wepons.use")) return;

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
