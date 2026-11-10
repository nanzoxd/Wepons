package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * /wepons enchants: a dialog that lists every enchantment used on the weapons. Clicking one opens a second
 * dialog with its limit and which weapons carry it. Uses the vanilla /dialog command (Minecraft 1.21.6+),
 * so the buttons simply run /wepons enchants <name>.
 */
public final class EnchantDialogs {

    private static final String NAME = "#E9D5FF";
    private static final String GOLD = "#FFD84A";
    private static final String GRAY = "#9CA3AF";
    private static final String HEAD = "#C4B5FD";

    private final WeaponRegistry reg;

    public EnchantDialogs(Wepons plugin) {
        this.reg = plugin.registry();
    }

    // ---- list ----

    public void list(Player p) {
        List<String> buttons = new ArrayList<>();
        for (Enchantment e : reg.allEnchants()) {
            Map<Weapon, Integer> users = reg.weaponsWith(e);
            int best = 0;
            for (int lvl : users.values()) best = Math.max(best, lvl);
            String label = "{text:\"" + esc(WeaponRegistry.enchantName(e)) + " \",color:\"" + NAME + "\",extra:[{text:\""
                    + WeaponRegistry.roman(best) + "\",color:\"" + GOLD + "\"}]}";
            String tip = "{text:\"On " + users.size() + " weapon" + (users.size() == 1 ? "" : "s")
                    + ". Click to see the limit.\",color:\"" + GRAY + "\"}";
            buttons.add("{label:" + label + ",tooltip:" + tip + ",width:150,action:{type:\"minecraft:run_command\","
                    + "command:\"wepons enchants " + e.getKey().getKey() + "\"}}");
        }
        String snbt = "{type:\"minecraft:multi_action\",title:" + text("Weapon Enchantments", HEAD) + ",columns:2,"
                + "body:[{type:\"minecraft:plain_message\",contents:" + text("Every enchantment used on the weapons. Click one to see its limit.", GRAY) + "}],"
                + "actions:[" + String.join(",", buttons) + "],exit_action:{label:\"Close\"}}";
        show(p, snbt);
    }

    // ---- one enchantment ----

    public void detail(Player p, String key) {
        Enchantment found = null;
        for (Enchantment e : reg.allEnchants()) {
            if (e.getKey().getKey().equalsIgnoreCase(key.replace(' ', '_'))) found = e;
        }
        if (found == null) {
            p.sendMessage("No weapon uses that enchantment. Try /wepons enchants");
            return;
        }

        Map<Weapon, Integer> users = reg.weaponsWith(found);
        int best = 0;
        for (int lvl : users.values()) best = Math.max(best, lvl);

        // weapons grouped by level, highest level first
        Map<Integer, List<String>> byLevel = new TreeMap<>((a, b) -> b - a);
        for (Map.Entry<Weapon, Integer> en : users.entrySet()) {
            byLevel.computeIfAbsent(en.getValue(), k -> new ArrayList<>()).add(en.getKey().name);
        }

        List<String> body = new ArrayList<>();
        body.add(plain("Limit (vanilla): " + WeaponRegistry.roman(found.getMaxLevel()) + " (" + found.getMaxLevel() + ")", GOLD));
        body.add(plain("Highest on a weapon: " + WeaponRegistry.roman(best) + " (" + best + ")"
                + (best > found.getMaxLevel() ? " - above the vanilla limit" : ""), NAME));
        body.add(plain("Used on " + users.size() + " weapon" + (users.size() == 1 ? "" : "s") + ":", GRAY));
        for (Map.Entry<Integer, List<String>> en : byLevel.entrySet()) {
            body.add(plain(WeaponRegistry.roman(en.getKey()) + ": " + String.join(", ", en.getValue()), NAME));
        }

        String snbt = "{type:\"minecraft:notice\",title:" + text(WeaponRegistry.enchantName(found), HEAD) + ","
                + "body:[" + String.join(",", body) + "],"
                + "action:{label:\"Back\",action:{type:\"minecraft:run_command\",command:\"wepons enchants\"}}}";
        show(p, snbt);
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
