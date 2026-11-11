package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * /wepons enchants: an editor dialog for the weapon in your hand. The first dialog has one button per enchantment
 * that can go on it (with its current level). Clicking a button opens a second dialog with a slider (0 = off up to the
 * limit) and an Apply button, which runs /wepons setenchant. Uses the vanilla /dialog command (Minecraft 1.21.6+).
 */
public final class EnchantDialogs {

    private static final String NAME = "#E9D5FF";
    private static final String GOLD = "#FFD84A";
    private static final String GRAY = "#9CA3AF";
    private static final String HEAD = "#C4B5FD";

    private final Wepons plugin;
    private final WeaponRegistry reg;

    public EnchantDialogs(Wepons plugin) {
        this.plugin = plugin;
        this.reg = plugin.registry();
    }

    // ---- lookup and limits ----

    public static Enchantment find(String key) {
        if (key == null) return null;
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase().replace(' ', '_')));
    }

    /** Highest level the slider offers: config enchant-editor.limits.<key>, else the vanilla max (never below what the weapon has). */
    public int limit(Enchantment e, Weapon w, int current) {
        int vanilla = e.getMaxLevel();
        int own = w.enchants.getOrDefault(e, 0);
        int base = Math.max(vanilla, Math.max(own, current));
        return Math.max(1, plugin.getConfig().getInt("enchant-editor.limits." + e.getKey().getKey(), base));
    }

    /** All enchantments the held weapon can carry (plus ones it already has), sorted by name. */
    public List<Enchantment> available(ItemStack item) {
        Map<String, Enchantment> map = new TreeMap<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            String k = e.getKey().getKey();
            if (k.contains("curse")) continue;
            if (e.canEnchantItem(item) || item.getEnchantmentLevel(e) > 0) map.put(k, e);
        }
        return new ArrayList<>(map.values());
    }

    private Weapon held(Player p) {
        Weapon w = reg.of(p.getInventory().getItemInMainHand());
        if (w == null) p.sendMessage("Hold one of the weapons in your main hand first.");
        return w;
    }

    // ---- dialog 1: every enchantment as a button ----

    public void list(Player p) {
        Weapon w = held(p);
        if (w == null) return;
        ItemStack item = p.getInventory().getItemInMainHand();

        List<String> buttons = new ArrayList<>();
        for (Enchantment e : available(item)) {
            int cur = item.getEnchantmentLevel(e);
            String state = cur > 0
                    ? "{text:\"" + cur + "\",color:\"" + GOLD + "\"}"
                    : "{text:\"off\",color:\"" + GRAY + "\"}";
            String label = "{text:\"" + esc(WeaponRegistry.enchantName(e)) + " \",color:\"" + NAME + "\",extra:[" + state + "]}";
            String tip = "{text:\"Limit " + limit(e, w, cur) + ". Click to change the level.\",color:\"" + GRAY + "\"}";
            buttons.add("{label:" + label + ",tooltip:" + tip + ",width:150,action:{type:\"minecraft:run_command\","
                    + "command:\"wepons enchants " + e.getKey().getKey() + "\"}}");
        }
        String snbt = "{type:\"minecraft:multi_action\",title:" + text(w.name + " - Enchantments", HEAD) + ",columns:2,"
                + "body:[{type:\"minecraft:plain_message\",contents:" + text("Pick an enchantment to set its level on this weapon.", GRAY) + "}],"
                + "actions:[" + String.join(",", buttons) + "],exit_action:{label:\"Close\"}}";
        show(p, snbt);
    }

    // ---- dialog 2: set the level of one enchantment ----

    public void detail(Player p, String key) {
        Weapon w = held(p);
        if (w == null) return;
        Enchantment e = find(key);
        if (e == null) {
            p.sendMessage("Unknown enchantment. Try /wepons enchants");
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        int cur = item.getEnchantmentLevel(e);
        int max = limit(e, w, cur);
        String k = e.getKey().getKey();

        List<String> body = new ArrayList<>();
        body.add(plain("Weapon: " + w.name, NAME));
        body.add(plain("Current: " + (cur > 0 ? cur : "off"), GOLD));
        body.add(plain("Limit: " + max + (max != e.getMaxLevel() ? " (vanilla " + e.getMaxLevel() + ")" : ""), GRAY));

        String input = "{type:\"minecraft:number_range\",key:\"level\",label:" + text("Level (0 removes it)", NAME)
                + ",start:0,end:" + max + ",initial:" + Math.min(cur, max) + ",step:1,width:250}";
        String snbt = "{type:\"minecraft:confirmation\",title:" + text(WeaponRegistry.enchantName(e), HEAD) + ","
                + "body:[" + String.join(",", body) + "],inputs:[" + input + "],"
                + "yes:{label:" + text("Apply", GOLD) + ",action:{type:\"minecraft:dynamic/run_command\",template:\"wepons setenchant " + k + " $(level)\"}},"
                + "no:{label:\"Back\",action:{type:\"minecraft:run_command\",command:\"wepons enchants\"}}}";
        show(p, snbt);
    }

    // ---- apply (runs from the Apply button) ----

    public void apply(Player p, String key, String rawLevel) {
        Weapon w = held(p);
        if (w == null) return;
        Enchantment e = find(key);
        if (e == null) return;

        int level;
        try {
            level = (int) Math.round(Double.parseDouble(rawLevel.replaceAll("[fFdD]$", "")));
        } catch (NumberFormatException ex) {
            p.sendMessage("Invalid level.");
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        int max = limit(e, w, item.getEnchantmentLevel(e));
        level = Math.max(0, Math.min(level, max));

        if (level == 0) item.removeEnchantment(e);
        else item.addUnsafeEnchantment(e, level);
        reg.refreshLore(item);
        p.getInventory().setItemInMainHand(item);

        p.sendActionBar(net.kyori.adventure.text.Component.text(
                WeaponRegistry.enchantName(e) + (level == 0 ? " removed" : " set to " + level), w.color));
        list(p);   // back to the list so you can keep editing
    }

    // ---- helpers ----

    private static String plain(String s, String color) {
        return "{type:\"minecraft:plain_message\",contents:" + text(s, color) + "}";
    }

    private static String text(String s, String color) {
        return "{text:\"" + esc(s) + "\",color:\"" + color + "\"}";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void show(Player p, String snbt) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dialog show " + p.getName() + " " + snbt);
    }
}
