package com.wepons;

import com.wepons.Weapon.Eff;
import com.wepons.Weapon.Trigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All weapons, plus building and identifying their items. */
public final class WeaponRegistry {

    private final Wepons plugin;
    private final NamespacedKey key;
    private final Map<String, Weapon> weapons = new LinkedHashMap<>();

    public WeaponRegistry(Wepons plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "weapon");
        register();
    }

    // ------------------------------------------------------------------
    // Weapon list
    // ------------------------------------------------------------------

    private void register() {
        // ---- passive swords ----
        blade("weaving", Material.NETHERITE_SWORD, "Weaving Sword", "#A855F7", 9101)
                .aura(Particle.WITCH)
                .passive("Weaving", "Weaving while held.", e(PotionEffectType.WEAVING, 0))
                .ability("cobwebproc", "Web Snare", Trigger.HIT, 0, "30% chance to trap the target in cobwebs for 4s.");

        blade("resistance", Material.NETHERITE_SWORD, "Resistance Sword", "#9CA3AF", 9102)
                .aura(Particle.ASH)
                .passive("Resistance II", "Take less damage while held.", e(PotionEffectType.RESISTANCE, 1))
                .ability("bulwark", "Bulwark", Trigger.SHIFT_RIGHT, 20, "Gain Resistance IV and Absorption II.");

        blade("speed", Material.NETHERITE_SWORD, "Speed Sword", "#22D3EE", 9103)
                .aura(Particle.ELECTRIC_SPARK)
                .passive("Speed I", "Move faster while held.", e(PotionEffectType.SPEED, 0))
                .ability("windstep", "Wind Step", Trigger.SHIFT_RIGHT, 45, "Gain Speed II for 15s.");

        blade("invisibility", Material.NETHERITE_SWORD, "Invisible Sword", "#E5E7EB", 9104)
                .aura(Particle.CLOUD)
                .passive("Invisibility", "Become invisible while held.", e(PotionEffectType.INVISIBILITY, 0))
                .ability("vanishstep", "Vanish Step", Trigger.SHIFT_SWAP, 16, "Blink forward and stay invisible.");

        blade("regeneration", Material.NETHERITE_SWORD, "Regen Sword", "#FF3B3B", 9105)
                .aura(Particle.HEART)
                .passive("Regeneration I", "Slowly heal while held.", e(PotionEffectType.REGENERATION, 0))
                .ability("secondwind", "Second Wind", Trigger.SHIFT_RIGHT, 25, "Heal 6 hearts and gain Regeneration III.");

        blade("saturation", Material.NETHERITE_SWORD, "Food Sword", "#FF8A1F", 9106)
                .aura(Particle.FLAME)
                .passive("Saturation", "Never go hungry while held.", e(PotionEffectType.SATURATION, 0))
                .ability("feast", "Feast", Trigger.SHIFT_RIGHT, 20, "Refill hunger and saturation.");

        add("haste", Material.NETHERITE_PICKAXE, "Mining Pickaxe", "#FFD21F", 9107)
                .aura(Particle.END_ROD)
                .enchant(Enchantment.EFFICIENCY, 7)
                .enchant(Enchantment.FORTUNE, 3)
                .passive("Efficiency VII", "Mines blocks very fast.")
                .ability("minetoggle", "Mine Mode", Trigger.SHIFT_RIGHT, 0, "Switch between Fortune III and Silk Touch.");

        blade("strength", Material.NETHERITE_SWORD, "Strength Sword", "#EF4444", 9108)
                .aura(Particle.ANGRY_VILLAGER)
                .passive("Strength I", "Hit harder while held.", e(PotionEffectType.STRENGTH, 0))
                .ability("berserk", "Berserk", Trigger.SHIFT_RIGHT, 45, "Gain Strength II for 15s.");

        // ---- active swords ----
        blade("dash", Material.NETHERITE_SWORD, "Dash Sword", "#38BDF8", 9109)
                .aura(Particle.SOUL_FIRE_FLAME)
                .ability("dash", "Dash", Trigger.SHIFT_RIGHT, 8, "Dash forward.")
                .ability("blink", "Blink", Trigger.SHIFT_SWAP, 14, "Teleport forward through thin walls.");

        blade("voidfang", Material.NETHERITE_SWORD, "Void Sword", "#A78BFA", 9110)
                .aura(Particle.PORTAL)
                .ability("voidslash", "Void Slash", Trigger.SHIFT_RIGHT, 12, "Slash the enemy you look at.")
                .ability("voidpull", "Void Pull", Trigger.SHIFT_SWAP, 16, "Pull the enemy you look at to you.");

        blade("stormbreaker", Material.NETHERITE_SWORD, "Storm Sword", "#FACC15", 9111)
                .aura(Particle.END_ROD)
                .ability("lightning", "Lightning Strike", Trigger.SHIFT_RIGHT, 15, "Call lightning where you look.")
                .ability("staticfield", "Static Field", Trigger.SHIFT_SWAP, 18, "Slow every enemy nearby.");

        blade("freeze", Material.NETHERITE_SWORD, "Frost Sword", "#38E8E0", 9112)
                .aura(Particle.SNOWFLAKE)
                .ability("freeze", "Frost Strike", Trigger.HIT, 0, "Every 3rd hit freezes a player for 5s.")
                .ability("frostnova", "Frost Nova", Trigger.SHIFT_SWAP, 20, "Freeze enemies within 6 blocks for 10s.");

        blade("scythe", Material.NETHERITE_SWORD, "Scythe", "#C26A6A", 9113)
                .aura(Particle.CRIMSON_SPORE)
                .ability("summonfangs", "Fang Trap", Trigger.HIT, 30, "Every 5th hit traps the target in fangs for 3s.")
                .ability("ravagerride", "Ravager Ride", Trigger.SHIFT_SWAP, 40, "Ride a ravager for 15s.");

        add("fireshield", Material.SHIELD, "Fire Shield", "#F97316", 9114)
                .aura(Particle.FLAME)
                .ability("flamerebuke", "Flame Rebuke", Trigger.BLOCK, 0, "Attackers are launched back and set on fire.");

        // ---- utility weapons ----
        blade("flightsword", Material.NETHERITE_SWORD, "Flight Sword", "#55FF55", 9115)
                .aura(Particle.HAPPY_VILLAGER)
                .ability("flightdash", "Dash", Trigger.SHIFT_RIGHT, 20, "Dash forward at high speed.");

        blade("lightningsword", Material.NETHERITE_SWORD, "Lightning Sword", "#FFFF55", 9116)
                .aura(Particle.END_ROD)
                .ability("lightningproc", "Storm Edge", Trigger.HIT, 0, "30% chance to strike the target with lightning.");

        blade("cobwebaxe", Material.NETHERITE_AXE, "Web Axe", "#FF55FF", 9117)
                .aura(Particle.ENCHANT)
                .ability("cobwebproc", "Web Snare", Trigger.HIT, 0, "30% chance to trap the target in cobwebs for 4s.");

        blade("gravityaxe", Material.NETHERITE_AXE, "Gravity Axe", "#55FFFF", 9118)
                .aura(Particle.ELECTRIC_SPARK)
                .ability("gravitypulse", "Gravity Pulse", Trigger.SHIFT_RIGHT, 10, "Lift and blind enemies within 8 blocks.");

        add("grapplebow", Material.BOW, "Grapple Bow", "#CC66CC", 9119)
                .aura(Particle.WITCH)
                .enchant(Enchantment.POWER, 5)
                .enchant(Enchantment.INFINITY, 1)
                .ability("grapplelaunch", "Grapple Launch", Trigger.SHIFT_RIGHT, 10, "Launch yourself forward.");

        add("dashmace", Material.MACE, "Dash Mace", "#FF5555", 9120)
                .aura(Particle.FLAME)
                .enchant(Enchantment.DENSITY, 5)
                .enchant(Enchantment.WIND_BURST, 5)
                .ability("dashmacedash", "Dash", Trigger.SHIFT_RIGHT, 20, "Dash forward at very high speed.");

        add("banhammer", Material.MACE, "Ban Hammer", "#FF4040", 9121)
                .aura(Particle.ANGRY_VILLAGER)
                .enchant(Enchantment.DENSITY, 3)
                .carry("Carry Bonus", "Speed II, Strength II and Glowing while in your inventory.",
                        e(PotionEffectType.SPEED, 1), e(PotionEffectType.STRENGTH, 1),
                        e(PotionEffectType.GLOWING, 0), e(PotionEffectType.WEAVING, 0))
                .ability("banhammer", "Judgement", Trigger.HIT, 40, "Hit a player to ban them.");

        // ---- new weapons ----
        add("smelter", Material.NETHERITE_PICKAXE, "Smelter Pickaxe", "#FF7A1A", 9122)
                .aura(Particle.FLAME)
                .enchant(Enchantment.EFFICIENCY, 7)
                .passive("Auto Smelt", "Ores drop smelted with Fortune III. Other blocks drop as if Silk Touch.")
                .passive("Wide Mine", "Breaks 2 blocks at once.");

        blade("jaydentrident", Material.TRIDENT, "Jayden Trident", "#14B8A6", 9123)
                .swordStats()
                .aura(Particle.DRIPPING_WATER)
                .ability("riptide", "Riptide Dash", Trigger.RIGHT_CLICK, 0,
                        "Dash like Riptide, no enchantment needed. Use it twice with no cooldown, then wait 60s.")
                .ability("tridentstrike", "Sky Trident", Trigger.HIT, 0,
                        "Every 5th hit drops a trident from the sky for 4 hearts and a ring of fire.");

        add("monarchcrown", Material.GOLDEN_HELMET, "Monarch Crown", "#FFD700", 9124)
                .worn()
                .aura(Particle.WAX_ON)
                .enchant(Enchantment.PROTECTION, 4)
                .passive("Royal Might", "Deal 2x damage and gain Speed II while worn.", e(PotionEffectType.SPEED, 1))
                .ability("royalreveal", "Royal Reveal", Trigger.SHIFT_SWAP, 60, "Every player within 30 blocks glows for 10s.");
    }

    private Weapon add(String id, Material m, String name, String hex, int cmd) {
        Weapon w = new Weapon(id, m, name, hex, cmd);
        weapons.put(id, w);
        return w;
    }

    private Weapon blade(String id, Material m, String name, String hex, int cmd) {
        return add(id, m, name, hex, cmd)
                .enchant(Enchantment.SHARPNESS, 4)
                .enchant(Enchantment.LOOTING, 3)
                .enchant(Enchantment.SWEEPING_EDGE, 3);
    }

    private static Eff e(PotionEffectType type, int amp) {
        return new Eff(type, amp);
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    public Collection<Weapon> all() {
        return weapons.values();
    }

    public Weapon get(String id) {
        return id == null ? null : weapons.get(id);
    }

    /** The weapon this item is, or null. Only the hidden tag counts, never the name. */
    public Weapon of(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return get(id);
    }

    public boolean isWeapon(ItemStack item) {
        return of(item) != null;
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    public Component title(Weapon w) {
        return Component.text(w.name, w.color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> lore(Weapon w) {
        List<Component> lore = new ArrayList<>();
        boolean first = true;
        for (Weapon.Ability a : w.abilities) {
            if (!first) lore.add(Component.empty());
            first = false;

            lore.add(Component.text(a.name(), w.color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("  " + a.trigger().label, NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false)));

            for (String l : wrap(a.desc(), 32)) lore.add(line(l, NamedTextColor.GRAY));
            if (a.cooldown() > 0) lore.add(line(a.cooldown() + "s Cooldown", NamedTextColor.DARK_GRAY));
        }
        return lore;
    }

    @SuppressWarnings("deprecation")
    public ItemStack create(String id) {
        Weapon w = get(id);
        if (w == null) return null;

        ItemStack item = new ItemStack(w.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title(w));
        meta.lore(lore(w));
        meta.setUnbreakable(true);
        meta.setCustomModelData(w.cmd);
        if (w.worn) wornNoArmorLayer(meta);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        w.enchants.forEach((ench, lvl) -> meta.addEnchant(ench, lvl, true));
        if (w.swordStats) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, "sword_damage"), 7.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, "sword_speed"), -2.4,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, w.id);
        item.setItemMeta(meta);
        return item;
    }

    /** Display-only icon (no weapon tag) with custom lore, used by /holder. */
    @SuppressWarnings("deprecation")
    public ItemStack icon(Weapon w, List<Component> lore) {
        ItemStack item = new ItemStack(w.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title(w));
        meta.lore(lore);
        meta.setCustomModelData(w.cmd);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Golden helmet showed the vanilla gold armor when worn because the item still had the gold
     * armor asset. Equippable with no asset id = no armor layer, so the game draws the item's own
     * model (the crown, via its "head" display) on the player's head instead.
     */
    private static void wornNoArmorLayer(ItemMeta meta) {
        var eq = meta.getEquippable();
        eq.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);
        eq.setModel(null);
        meta.setEquippable(eq);
    }
}
