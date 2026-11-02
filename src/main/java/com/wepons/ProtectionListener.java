package com.wepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Weapons stay in player inventories: they can't be stored in chests, hoppers, etc. */
public final class ProtectionListener implements Listener {

    private final Wepons plugin;

    public ProtectionListener(Wepons plugin) {
        this.plugin = plugin;
    }

    private boolean isStorage(Inventory top) {
        InventoryType t = top.getType();
        if (t == InventoryType.CRAFTING || t == InventoryType.PLAYER || t == InventoryType.CREATIVE) return false;
        return !(top.getHolder() instanceof WeponsGui);
    }

    private void warn(Object who) {
        if (who instanceof Player p) {
            p.sendActionBar(Component.text("Weapons can't be stored.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isStorage(top)) return;

        WeaponRegistry reg = plugin.registry();
        boolean clickedTop = e.getClickedInventory() == top;

        // placing the cursor item into the container
        if (clickedTop && reg.isWeapon(e.getCursor())) {
            e.setCancelled(true);
            warn(e.getWhoClicked());
            return;
        }
        // shift-click from the player's inventory into the container
        if (!clickedTop && e.isShiftClick() && reg.isWeapon(e.getCurrentItem())) {
            e.setCancelled(true);
            warn(e.getWhoClicked());
            return;
        }
        // number key / offhand swap while hovering the container
        if (clickedTop && e.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
            if (reg.isWeapon(hotbar)) {
                e.setCancelled(true);
                warn(e.getWhoClicked());
                return;
            }
        }
        if (clickedTop && e.getClick() == ClickType.SWAP_OFFHAND
                && reg.isWeapon(e.getWhoClicked().getInventory().getItemInOffHand())) {
            e.setCancelled(true);
            warn(e.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isStorage(top)) return;
        if (!plugin.registry().isWeapon(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                warn(e.getWhoClicked());
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (plugin.registry().isWeapon(e.getItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.holders().scan(e.getPlayer());
    }
}
