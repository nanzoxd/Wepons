package com.wepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Both menus: /wepons (take a weapon) and /holder (see who has it).
 * Six rows: top row = three tabs (Crazy Mythicals / Relique Weapons / Kings SMP), second row = black glass,
 * the four rows under it hold the weapons (up to 36, no pages).
 */
public final class WeponsGui implements InventoryHolder {

    public enum Type { WEAPONS, HOLDERS }

    public static final String CRAZY = "crazy";
    public static final String RELIQUE = "relique";
    public static final String KINGS = "kings";

    private static final int SIZE = 54;
    private static final int GLASS_FROM = 9;    // second row only
    private static final int GLASS_TO = 17;
    private static final int ITEMS_FROM = 18;   // rows 3-6
    private static final int MAX_ITEMS = SIZE - ITEMS_FROM;   // 36

    public final Type type;
    public final String category;
    public int crazySlot = 0;
    public int reliqueSlot = 4;
    public int kingsSlot = 8;
    /** Kings SMP tab: menu slot -> KingsSMP item id. */
    public final Map<Integer, String> kingsIds = new HashMap<>();
    private Inventory inventory;

    private WeponsGui(Type type, String category) {
        this.type = type;
        this.category = category;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack glass() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    private static ItemStack tab(Wepons plugin, String category, boolean active, int count) {
        String path;
        Material fallback;
        int fallbackCmd;
        Component name;
        switch (category) {
            case CRAZY -> {
                path = "gui.icons.crazy-mythicals";
                fallback = Material.NETHER_STAR;
                fallbackCmd = 9500;
                name = WeaponRegistry.gradient("Crazy Mythicals", "#FF8AF0", "#7C3AED");
            }
            case RELIQUE -> {
                path = "gui.icons.relique-weapons";
                fallback = Material.NETHERITE_SWORD;
                fallbackCmd = 9201;
                name = WeaponRegistry.gradient("Relique Weapons", "#FFF0A8", "#B45309");
            }
            default -> {
                path = "gui.icons.kings-smp";
                fallback = Material.NETHERITE_HELMET;
                fallbackCmd = 9124;
                name = WeaponRegistry.gradient("Kings SMP", "#FFF3A0", "#D97706");
            }
        }
        Material mat = Material.matchMaterial(plugin.getConfig().getString(path + ".material", fallback.name()));
        if (mat == null) mat = fallback;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.setCustomModelData(plugin.getConfig().getInt(path + ".custom-model-data", fallbackCmd));
        List<Component> lore = new ArrayList<>();
        boolean missing = category.equals(KINGS) && !plugin.kings().available();
        lore.add(Component.text(WeaponRegistry.sc(missing ? "KingsSMP plugin not found" : count + " weapons"),
                missing ? NamedTextColor.RED : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(WeaponRegistry.sc(active ? "Showing now" : "Click to open"),
                active ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        if (active) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static int slotSetting(Wepons plugin, String key, int def) {
        int v = plugin.getConfig().getInt("gui.tab-slots." + key, def);
        return v >= 0 && v < 9 ? v : def;
    }

    public static void open(Wepons plugin, Player player, Type type) {
        open(plugin, player, type, RELIQUE);
    }

    public static void open(Wepons plugin, Player player, Type type, String category) {
        WeaponRegistry reg = plugin.registry();
        KingsBridge kings = plugin.kings();
        if (!category.equals(CRAZY) && !category.equals(KINGS)) category = RELIQUE;

        WeponsGui gui = new WeponsGui(type, category);
        gui.crazySlot = slotSetting(plugin, "crazy-mythicals", 0);
        gui.reliqueSlot = slotSetting(plugin, "relique-weapons", 4);
        gui.kingsSlot = slotSetting(plugin, "kings-smp", 8);

        String title = (type == Type.WEAPONS ? "Wepons" : "Holders") + " - "
                + switch (category) {
                    case CRAZY -> "Crazy Mythicals";
                    case KINGS -> "Kings SMP";
                    default -> "Relique Weapons";
                };
        gui.inventory = Bukkit.createInventory(gui, SIZE, Component.text(title, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true));

        // glass: second row only
        ItemStack pane = glass();
        for (int i = GLASS_FROM; i <= GLASS_TO; i++) gui.inventory.setItem(i, pane.clone());

        List<KingsBridge.Entry> kingsItems = kings.items();
        int crazyCount = (int) reg.all().stream().filter(w -> w.category.equals(CRAZY)).count();
        int reliqueCount = (int) reg.all().stream().filter(w -> w.category.equals(RELIQUE)).count();
        gui.inventory.setItem(gui.crazySlot, tab(plugin, CRAZY, category.equals(CRAZY), crazyCount));
        gui.inventory.setItem(gui.reliqueSlot, tab(plugin, RELIQUE, category.equals(RELIQUE), reliqueCount));
        gui.inventory.setItem(gui.kingsSlot, tab(plugin, KINGS, category.equals(KINGS), kingsItems.size()));

        if (category.equals(KINGS)) {
            fillKings(plugin, gui, kingsItems);
        } else {
            final String cat = category;
            List<Weapon> list = reg.all().stream().filter(w -> w.category.equals(cat)).toList();
            for (int i = 0; i < MAX_ITEMS && i < list.size(); i++) {
                Weapon w = list.get(i);
                ItemStack item = type == Type.WEAPONS
                        ? reg.create(w.id)
                        : reg.icon(w, holderLore(plugin, w));
                gui.inventory.setItem(ITEMS_FROM + i, item);
            }
        }
        player.openInventory(gui.inventory);
    }

    /** Kings SMP tab: the KingsSMP plugin's own items. */
    private static void fillKings(Wepons plugin, WeponsGui gui, List<KingsBridge.Entry> items) {
        KingsBridge kings = plugin.kings();
        Map<String, List<Player>> held = gui.type == Type.HOLDERS ? onlineHolders(kings) : Map.of();
        for (int i = 0; i < MAX_ITEMS && i < items.size(); i++) {
            String id = items.get(i).id();
            ItemStack item = kings.createStyled(id);
            if (item == null) continue;
            if (gui.type == Type.HOLDERS) {
                ItemMeta meta = item.getItemMeta();
                meta.lore(onlineLore(held.get(id)));
                item.setItemMeta(meta);
            }
            int slot = ITEMS_FROM + i;
            gui.inventory.setItem(slot, item);
            gui.kingsIds.put(slot, id);
        }
    }

    /** KingsSMP item id -> online players carrying it (KingsSMP has no holder list of its own). */
    private static Map<String, List<Player>> onlineHolders(KingsBridge kings) {
        Map<String, List<Player>> out = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<String> seen = new HashSet<>();
            for (ItemStack stack : p.getInventory().getContents()) {
                String id = kings.idOf(stack);
                if (id != null && seen.add(id)) out.computeIfAbsent(id, k -> new ArrayList<>()).add(p);
            }
        }
        return out;
    }

    private static List<Component> onlineLore(List<Player> players) {
        List<Component> lore = new ArrayList<>();
        if (players == null || players.isEmpty()) {
            lore.add(Component.text(WeaponRegistry.sc("No holder online"), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            return lore;
        }
        for (Player p : players) {
            lore.add(Component.text("\u25CF ", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(p.getName(), NamedTextColor.WHITE))
                    .append(Component.text(WeaponRegistry.sc("  Online"), NamedTextColor.GREEN)));
        }
        return lore;
    }

    private static List<Component> holderLore(Wepons plugin, Weapon w) {
        List<Component> lore = new ArrayList<>();
        Map<UUID, String> holders = plugin.holders().holdersOf(w.id);

        if (holders.isEmpty()) {
            lore.add(Component.text(WeaponRegistry.sc("No holder"), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
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
                    .append(Component.text(WeaponRegistry.sc(online ? "  Online" : "  Offline"),
                            online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        }
        return lore;
    }
}
