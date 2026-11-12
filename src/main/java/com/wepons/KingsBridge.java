package com.wepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Link to the KingsSMP plugin (dev.notdqsher.kingssmp). Its items fill the "Kings SMP" tab of the menu.
 * Everything is done by reflection, so Wepons still loads and works when KingsSMP is not installed
 * (the tab is then just empty).
 *
 * Used KingsSMP API (all public static on dev.notdqsher.kingssmp.Items):
 *   all()                         -> Collection of Items.Def records (id, name, rarity, material, kind)
 *   create(String id)             -> ItemStack
 *   def(String id)                -> Items.Def
 *   give(Player, Items.Def, int)  -> gives the item the same way the /kings menu does
 *   id(ItemStack)                 -> item id or null
 */
public final class KingsBridge {

    private static final String PLUGIN = "KingsSMP";
    private static final String ITEMS = "dev.notdqsher.kingssmp.Items";

    private final Wepons plugin;
    private boolean warned;

    public KingsBridge(Wepons plugin) {
        this.plugin = plugin;
    }

    /** One KingsSMP item (only what the menu needs). */
    public record Entry(String id, Object def) {}

    private Class<?> itemsClass() throws ReflectiveOperationException {
        Plugin kings = Bukkit.getPluginManager().getPlugin(PLUGIN);
        if (kings == null || !kings.isEnabled()) throw new ClassNotFoundException(PLUGIN + " is not enabled");
        return Class.forName(ITEMS, true, kings.getClass().getClassLoader());
    }

    private void fail(Throwable t) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("Could not read the KingsSMP items (" + t + "). The Kings SMP tab will stay empty.");
    }

    public boolean available() {
        Plugin kings = Bukkit.getPluginManager().getPlugin(PLUGIN);
        return kings != null && kings.isEnabled();
    }

    /** Every KingsSMP item, in the order the plugin registered them. */
    public List<Entry> items() {
        if (!available()) return Collections.emptyList();
        try {
            Class<?> items = itemsClass();
            Object all = items.getMethod("all").invoke(null);
            List<Entry> out = new ArrayList<>();
            if (all instanceof Collection<?> c) {
                for (Object def : c) {
                    Method idM = def.getClass().getMethod("id");
                    out.add(new Entry((String) idM.invoke(def), def));
                }
            }
            return out;
        } catch (Throwable t) {
            fail(t);
            return Collections.emptyList();
        }
    }

    /** Fresh item for the menu icon, or null. */
    public ItemStack create(String id) {
        try {
            Object stack = itemsClass().getMethod("create", String.class).invoke(null, id);
            return stack instanceof ItemStack s ? s : null;
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    private static final TextColor TEXT = TextColor.fromHexString("#D1D5DB");

    private boolean restyleOn() {
        return plugin.getConfig().getBoolean("kings-smp.restyle-lore", false);
    }

    /** Fresh KingsSMP item in the Relique look (small-caps lore, bold name, no italics) when restyle-lore is on. */
    public ItemStack createStyled(String id) {
        ItemStack item = create(id);
        return item == null || !restyleOn() ? item : restyle(item);
    }

    /** Relique look for an existing item: small caps, no italics, soft grey text, bold name. */
    public ItemStack restyle(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (meta.hasDisplayName() && meta.displayName() != null) meta.displayName(style(meta.displayName(), true));
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> out = new ArrayList<>();
            for (Component line : lore) out.add(style(line, false));
            meta.lore(out);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Component style(Component c, boolean bold) {
        Component out = c.replaceText(TextReplacementConfig.builder()
                .match(Pattern.compile(".+"))
                .replacement((r, b) -> b.content(WeaponRegistry.sc(r.group())))
                .build());
        out = recolor(out).decoration(TextDecoration.ITALIC, false);
        return bold ? out.decoration(TextDecoration.BOLD, true) : out;
    }

    private static Component recolor(Component c) {
        TextColor col = c.color();
        if (col != null && (col.equals(NamedTextColor.GRAY) || col.equals(NamedTextColor.DARK_GRAY)
                || col.equals(NamedTextColor.WHITE))) c = c.color(TEXT);
        if (!c.children().isEmpty()) {
            List<Component> kids = new ArrayList<>();
            for (Component k : c.children()) kids.add(recolor(k));
            c = c.children(kids);
        }
        return c;
    }

    /** Gives the item. With restyle-lore on it hands out the restyled copy, otherwise KingsSMP's own give method. */
    public boolean give(Player player, String id) {
        if (restyleOn()) {
            ItemStack styled = createStyled(id);
            if (styled != null) {
                player.getInventory().addItem(styled).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                return true;
            }
        }
        try {
            Class<?> items = itemsClass();
            Object def = items.getMethod("def", String.class).invoke(null, id);
            if (def == null) return false;
            items.getMethod("give", Player.class, def.getClass(), int.class).invoke(null, player, def, 1);
            return true;
        } catch (Throwable t) {
            fail(t);
            ItemStack item = create(id);
            if (item == null) return false;
            player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            return true;
        }
    }

    /** KingsSMP item id of this stack, or null if it is not a KingsSMP item. */
    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !available()) return null;
        try {
            return (String) itemsClass().getMethod("id", ItemStack.class).invoke(null, item);
        } catch (Throwable t) {
            return null;
        }
    }
}
