package com.wepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Both menus: /wepons (take a weapon) and /holder (see who has it). */
public final class WeponsGui implements InventoryHolder {

    public enum Type { WEAPONS, HOLDERS }

    public final Type type;
    private Inventory inventory;

    private WeponsGui(Type type) {
        this.type = type;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Seven items per row, centred, no filler. */
    private static int slot(int index) {
        return (index / 7) * 9 + 1 + (index % 7);
    }

    public static void open(Wepons plugin, Player player, Type type) {
        WeaponRegistry reg = plugin.registry();
        int count = reg.all().size();
        int rows = Math.max(1, (count + 6) / 7);

        WeponsGui gui = new WeponsGui(type);
        Component title = Component.text(type == Type.WEAPONS ? "Wepons" : "Holders", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true);
        gui.inventory = Bukkit.createInventory(gui, rows * 9, title);

        int i = 0;
        for (Weapon w : reg.all()) {
            ItemStack item = type == Type.WEAPONS
                    ? reg.create(w.id)
                    : reg.icon(w, holderLore(plugin, w));
            gui.inventory.setItem(slot(i++), item);
        }
        player.openInventory(gui.inventory);
    }

    private static List<Component> holderLore(Wepons plugin, Weapon w) {
        List<Component> lore = new ArrayList<>();
        Map<UUID, String> holders = plugin.holders().holdersOf(w.id);

        if (holders.isEmpty()) {
            lore.add(Component.text("No holder", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            return lore;
        }
        for (Map.Entry<UUID, String> entry : holders.entrySet()) {
            boolean online = HolderManager.isOnline(entry.getKey());
            String name = entry.getValue();
            if (!online) {
                String known = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (known != null) name = known;
            }
            lore.add(Component.text("\u25CF ", online ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(online ? "  Online" : "  Offline",
                            online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        }
        return lore;
    }
}
