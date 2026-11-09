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
import org.bukkit.configuration.ConfigurationSection;
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
    private final NamespacedKey killsKey;
    private final Map<String, Weapon> weapons = new LinkedHashMap<>();

    public WeaponRegistry(Wepons plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "weapon");
        this.killsKey = new NamespacedKey(plugin, "kills");
        register();
    }

    // ------------------------------------------------------------------
    // Weapon list
    // ------------------------------------------------------------------

    private void register() {
        // ---- passive swords ----
        blade("weaving", Material.NETHERITE_SWORD, "Weaving Sword", "#A855F7", 9101)
                .tag("Spin a web, then wait.")
                .aura(Particle.WITCH)
                .passive("Weaving while held.", e(PotionEffectType.WEAVING, 0))
                .proc("cobwebproc", "Web Snare", Trigger.HIT, 0, "30% chance to trap the target in cobwebs for 4s.");

        blade("resistance", Material.NETHERITE_SWORD, "Resistance Sword", "#9CA3AF", 9102)
                .tag("Stand firm. Nothing breaks you.")
                .aura(Particle.ASH)
                .passive("Resistance II while held.", e(PotionEffectType.RESISTANCE, 1))
                .one("bulwark", "Bulwark", 20, "Gain Resistance IV and Absorption II.");

        blade("speed", Material.NETHERITE_SWORD, "Speed Sword", "#22D3EE", 9103)
                .tag("Gone before the echo lands.")
                .aura(Particle.ELECTRIC_SPARK)
                .passive("Speed I while held.", e(PotionEffectType.SPEED, 0))
                .one("windstep", "Wind Step", 45, "Gain Speed II for 15s.");

        blade("invisibility", Material.NETHERITE_SWORD, "Invisible Sword", "#E5E7EB", 9104)
                .tag("You never saw it coming.")
                .aura(Particle.CLOUD)
                .passive("Invisibility while held.", e(PotionEffectType.INVISIBILITY, 0))
                .one("vanishstep", "Vanish Step", 16, "Blink forward and stay invisible.");

        blade("regeneration", Material.NETHERITE_SWORD, "Regen Sword", "#FF3B3B", 9105)
                .tag("Wounds close faster than they open.")
                .aura(Particle.HEART)
                .passive("Regeneration I while held.", e(PotionEffectType.REGENERATION, 0))
                .one("secondwind", "Second Wind", 25, "Heal 6 hearts and gain Regeneration III.");

        blade("saturation", Material.NETHERITE_SWORD, "Food Sword", "#FF8A1F", 9106)
                .tag("A feast in every swing.")
                .aura(Particle.FLAME)
                .passive("Never go hungry while held.", e(PotionEffectType.SATURATION, 0))
                .one("feast", "Feast", 20, "Refill hunger and saturation.");

        add("haste", Material.NETHERITE_PICKAXE, "Mining Pickaxe", "#FFD21F", 9107)
                .tag("Stone is only a suggestion.")
                .aura(Particle.END_ROD)
                .enchant(Enchantment.EFFICIENCY, 7)
                .enchant(Enchantment.FORTUNE, 3)
                .passive("Efficiency VII: mines blocks very fast.")
                .one("minetoggle", "Mine Mode", 0, "Switch between Fortune III and Silk Touch.");

        blade("strength", Material.NETHERITE_SWORD, "Strength Sword", "#EF4444", 9108)
                .tag("Might is its own argument.")
                .aura(Particle.ANGRY_VILLAGER)
                .passive("Strength I while held.", e(PotionEffectType.STRENGTH, 0))
                .one("berserk", "Berserk", 45, "Gain Strength II for 15s.");

        // ---- active swords ----
        blade("dash", Material.NETHERITE_SWORD, "Dash Sword", "#38BDF8", 9109)
                .tag("Distance is a choice.")
                .aura(Particle.SOUL_FIRE_FLAME)
                .one("dash", "Dash", 8, "Dash forward.")
                .two("blink", "Blink", 14, "Teleport forward through thin walls.");

        blade("voidfang", Material.NETHERITE_SWORD, "Void Sword", "#A78BFA", 9110)
                .tag("The void answers its master.")
                .aura(Particle.PORTAL)
                .one("voidslash", "Void Slash", 12, "Slash the enemy you look at.")
                .two("voidpull", "Void Pull", 16, "Pull the enemy you look at to you.");

        blade("stormbreaker", Material.NETHERITE_SWORD, "Storm Sword", "#FACC15", 9111)
                .tag("Thunder follows the blade.")
                .aura(Particle.END_ROD)
                .one("lightning", "Lightning Strike", 15, "Call lightning where you look.")
                .two("staticfield", "Static Field", 18, "Slow every enemy nearby.");

        blade("freeze", Material.NETHERITE_SWORD, "Frost Sword", "#38E8E0", 9112)
                .tag("Cold is the last thing they feel.")
                .aura(Particle.SNOWFLAKE)
                .proc("freeze", "Frost Strike", Trigger.HIT, 0, "Every 3rd hit freezes a player for 5s.")
                .one("frostnova", "Frost Nova", 20, "Freeze enemies within 6 blocks for 10s.");

        blade("scythe", Material.NETHERITE_SWORD, "Scythe", "#C26A6A", 9113)
                .tag("The harvest never ends.")
                .aura(Particle.CRIMSON_SPORE)
                .proc("summonfangs", "Fang Trap", Trigger.HIT, 30, "Every 5th hit traps the target in fangs for 3s.")
                .one("ravagerride", "Ravager Ride", 40, "Ride a ravager for 15s.");

        add("fireshield", Material.SHIELD, "Fire Shield", "#F97316", 9114)
                .tag("Strike me and burn.")
                .aura(Particle.FLAME)
                .proc("flamerebuke", "Flame Rebuke", Trigger.BLOCK, 0, "Attackers are launched back and set on fire.");

        // ---- utility weapons ----
        blade("flightsword", Material.NETHERITE_SWORD, "Flight Sword", "#55FF55", 9115)
                .tag("The sky is just another road.")
                .aura(Particle.HAPPY_VILLAGER)
                .one("flightdash", "Dash", 20, "Dash forward at high speed.");

        blade("lightningsword", Material.NETHERITE_SWORD, "Lightning Sword", "#FFFF55", 9116)
                .tag("Every strike calls the storm.")
                .aura(Particle.END_ROD)
                .proc("lightningproc", "Storm Edge", Trigger.HIT, 0, "30% chance to strike the target with lightning.");

        blade("cobwebaxe", Material.NETHERITE_AXE, "Web Axe", "#FF55FF", 9117)
                .tag("Nothing escapes the web.")
                .aura(Particle.ENCHANT)
                .proc("cobwebproc", "Web Snare", Trigger.HIT, 0, "30% chance to trap the target in cobwebs for 4s.");

        blade("gravityaxe", Material.NETHERITE_AXE, "Gravity Axe", "#55FFFF", 9118)
                .tag("Even the ground lets go.")
                .aura(Particle.ELECTRIC_SPARK)
                .one("gravitypulse", "Gravity Pulse", 10, "Lift and blind enemies within 8 blocks.");

        add("grapplebow", Material.BOW, "Grapple Bow", "#CC66CC", 9119)
                .tag("Pulled to the target, always.")
                .aura(Particle.WITCH)
                .enchant(Enchantment.POWER, 5)
                .enchant(Enchantment.INFINITY, 1)
                .one("grapplelaunch", "Grapple Launch", 10, "Launch yourself forward.");

        add("dashmace", Material.MACE, "Dash Mace", "#FF5555", 9120)
                .tag("Impact is everything.")
                .aura(Particle.FLAME)
                .enchant(Enchantment.DENSITY, 5)
                .enchant(Enchantment.WIND_BURST, 5)
                .one("dashmacedash", "Dash", 20, "Dash forward at very high speed.");

        add("banhammer", Material.MACE, "Ban Hammer", "#FF4040", 9121)
                .tag("The final verdict.")
                .aura(Particle.ANGRY_VILLAGER)
                .enchant(Enchantment.DENSITY, 3)
                .carry("Speed II, Strength II and Glowing while in your inventory.",
                        e(PotionEffectType.SPEED, 1), e(PotionEffectType.STRENGTH, 1),
                        e(PotionEffectType.GLOWING, 0), e(PotionEffectType.WEAVING, 0))
                .proc("banhammer", "Judgement", Trigger.HIT, 40, "Hit a player to ban them.");

        // ---- more tools ----
        add("smelter", Material.NETHERITE_PICKAXE, "Smelter Pickaxe", "#FF7A1A", 9122)
                .tag("Ore comes out finished.")
                .aura(Particle.FLAME)
                .enchant(Enchantment.EFFICIENCY, 7)
                .passive("Ores drop smelted with Fortune III. Other blocks drop as if Silk Touch.")
                .passive("Breaks 2 blocks at once.");

        blade("jaydentrident", Material.TRIDENT, "Jayden Trident", "#14B8A6", 9123)
                .tag("Rides the lightning.")
                .swordStats()
                .aura(Particle.DRIPPING_WATER)
                .proc("tridentstrike", "Sky Trident", Trigger.HIT, 0,
                        "Every 5th hit drops a trident from the sky for 4 hearts, a ground shockwave and a ring of fire.")
                .one("riptide", "Riptide Dash", 0,
                        "Dash like Riptide, no enchantment needed. Use it twice, then wait 60s.");

        // ---- crown (helmet) ----
        add("monarchcrown", Material.NETHERITE_HELMET, "Monarch Crown", "#FFD700", 9124)
                .kind("CROWN")
                .tag("Heavy is the head, mighty the king.")
                .worn()
                .aura(Particle.WAX_ON)
                .enchant(Enchantment.PROTECTION, 4)
                .passive("Deal 2x damage and gain Speed II while worn.", e(PotionEffectType.SPEED, 1))
                .one("royalreveal", "Royal Reveal", 60,
                        "Every player within 30 blocks glows for 10s. Press F with both hands empty.");

        // ---- Relique blades ----
        blade("fortune_blade", Material.NETHERITE_SWORD, "Fortune Blade", "#FBBF24", 9201)
                .tag("Luck favors the bold.")
                .aura(Particle.HAPPY_VILLAGER)
                .passive("Gain 2x XP while holding the blade.")
                .one("fortunefeast", "Endless Feast", 60, "Any food that you eat is not consumed.")
                .two("fortunerolls", "Lucky Strikes", 90, "Your hits gain random damage multipliers.");

        blade("flame_blade", Material.NETHERITE_SWORD, "Flame Blade", "#FF4D1F", 9202)
                .tag("Born in the heart of the fire.")
                .aura(Particle.FLAME)
                .passive("Infinite Fire Resistance while held.", e(PotionEffectType.FIRE_RESISTANCE, 0))
                .one("flamesweep", "Flame Sweep", 60, "Sets enemies on fire and knocks them away.")
                .two("flameblast", "Flame Explosion", 90, "Creates a powerful flame explosion.");

        blade("bloom_blade", Material.NETHERITE_SWORD, "Bloom Blade", "#FF1FB4", 9203)
                .tag("Life blooms where it cuts.")
                .aura(Particle.CHERRY_LEAVES)
                .passive("Every 10th hit heals you the damage dealt.")
                .one("bloomhearts", "Overgrowth", 60, "Temporarily raise yourself to 15 hearts.")
                .two("bloomwither", "Wilt", 90, "Cap nearby enemies' health temporarily.");

        // ---- Altar SMP S2 legendaries ----
        blade("ancient_blade", Material.NETHERITE_SWORD, "Ancient Blade", "#22B8CF", 9301)
                .tag("Carried by the Legendaries of the Deep Dark.")
                .aura(Particle.SCULK_SOUL)
                .passive("Deep Connection: +25% Speed while standing on any Sculk block.")
                .passive("Shift (2 kills): permanent Speed I while held.")
                .passive("Tightened Grip (4 kills): permanent Strength II while held.")
                .one("presence", "Presence", 45,
                        "(3 kills) After the shriek, you have 5s to hit a player. Marked players receive Darkness for 10s and cannot have Absorption.")
                .two("darkness", "Unyielding Darkness", 60,
                        "(5 kills) Launch upward, pause, then throw a spinning Sculk Catalyst that spreads Sculk and splits into Sonic Boom bursts.");

        blade("dragonrend", Material.NETHERITE_SWORD, "Dragonrend", "#D946EF", 9302)
                .tag("Forged from the Ender Dragon's heart.")
                .aura(Particle.REVERSE_PORTAL)
                .passive("Looting III and Sweeping Edge III.")
                .one("hypersonic", "Hyperonic Slash", 40,
                        "After a short delay, swing while looking at a player within 16 blocks to teleport to them and deal damage. Chain slashes, but the window halves each time.")
                .two("infinitevoid", "Infinite Void", 60,
                        "Place a Void Clock where you stand, drastically slowing players within 6 blocks. Lasts 10s.");

        blade("tidebreaker", Material.TRIDENT, "Tidebreaker", "#3B82F6", 9303)
                .tag("The ocean lives within the wielder.")
                .swordStats()
                .aura(Particle.DRIPPING_WATER)
                .passive("Permanent Water Breathing while held.", e(PotionEffectType.WATER_BREATHING, 0))
                .one("stormcall", "Stormcall", 40,
                        "Call a Storm where you look. It damages the two entities with the highest HP within 6 blocks every 0.5s for 10s.")
                .two("risingtide", "Rising Tide", 50,
                        "Looking up: blast up on a jet of water, then land with a tidal shockwave. Looking down: throw a wave that pulls players toward you.");

        blade("wither_symbiote", Material.NETHERITE_SWORD, "Wither Symbiote", "#7C3AED", 9304)
                .tag("It is ready to latch on.")
                .aura(Particle.SOUL)
                .passive("Immune to the Wither effect while held.")
                .one("infection", "Symbiote Infection", 40,
                        "Your next hit infects the target. When it ends they take damage and are weakened. Press again to detonate it early.")
                .two("rampage", "Symbiote Rampage", 120,
                        "20s: +3 hearts, heal 3 hearts, 50% Speed and lifesteal on hit, but you starve faster. Exiting leaves you Weak, Withered and 3 hearts down.");

        add("omen", spearMaterial(), "Omen", "#0EA5A4", 9305)
                .kind("SPEAR")
                .tag("Imbued with the power of the Trial Chambers.")
                .aura(Particle.TRIAL_OMEN)
                .enchant(Enchantment.SHARPNESS, 4)
                .enchant(Enchantment.LOOTING, 3)
                .one("vaultomen", "Vault Omen", 35,
                        "Throw a Vault Omen where you look for 7s, tethering players who enter within 3 blocks until it expires.")
                .two("conjuring", "Ominous Conjuring", 50,
                        "10s: your melee attacks throw Ominous Projectiles that deal AOE damage. More players within 16 blocks raise your Spell Level.");
    }

    private static Material spearMaterial() {
        Material m = Material.matchMaterial("NETHERITE_SPEAR");
        return m != null ? m : Material.NETHERITE_SWORD;
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
        return Component.text(sc(w.name), w.color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static final TextColor GOLD = TextColor.fromHexString("#FFD84A");
    private static final TextColor PASSIVE_HEAD = TextColor.fromHexString("#FFCFCA");
    private static final TextColor ABILITY_HEAD = TextColor.fromHexString("#FFF0DA");
    private static final TextColor AQUA = TextColor.fromHexString("#7DF9FF");
    private static final TextColor MYTHIC = TextColor.fromHexString("#B388FF");
    private static final TextColor TEXT = TextColor.fromHexString("#D1D5DB");

    private static Component styled(String text, TextColor color, boolean bold) {
        return Component.text(text, color)
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.ITALIC, false);
    }

    // Icons are vanilla item textures drawn inline with an object component (Minecraft 1.21.9+).
    // They come from the client's own item atlas, so every player sees them with or without a resource pack.
    private static final String I_PASSIVE = "item/nether_star";
    private static final String I_POWER = "item/echo_shard";
    private static final String I_COOLDOWN = "item/clock_00";
    private static final String I_KILLS = "item/bone";

    private static final String SMALL_CAPS = "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    /** Lowercase letters become small caps (a-z only, uppercase stays). Vanilla characters, no pack needed. */
    public static String sc(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) sb.append(c >= 'a' && c <= 'z' ? SMALL_CAPS.charAt(c - 'a') : c);
        return sb.toString();
    }

    /** Lore line: small caps text. */
    private static Component sstyled(String text, TextColor color, boolean bold) {
        return styled(sc(text), color, bold);
    }

    private static Component icon(String sprite) {
        return Component.object(net.kyori.adventure.text.object.ObjectContents.sprite(
                        net.kyori.adventure.key.Key.key("minecraft", "items"),
                        net.kyori.adventure.key.Key.key("minecraft", sprite)))
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component space() {
        return Component.text(" ").decoration(TextDecoration.ITALIC, false);
    }

    /** Compact, clean lore: one short line per passive/power, no repeated item name or cooldown clutter. */
    private List<Component> lore(Weapon w, int kills) {
        List<Component> lore = new ArrayList<>();

        // Keep the item name only once; the material kind is intentionally omitted.
        if (!w.tagline.isEmpty()) lore.add(sstyled(w.tagline, NamedTextColor.WHITE, true));
        lore.add(sstyled("(Mythic)", MYTHIC, false));
        if (kills >= 0) {
            lore.add(sstyled("Kills: " + kills, NamedTextColor.RED, false));
        }

        boolean hasPassives = w.abilities.stream().anyMatch(a -> !a.trigger().isPower());
        boolean hasPowers = w.abilities.stream().anyMatch(a -> a.trigger().isPower());

        if (hasPassives) {
            lore.add(Component.empty());
            lore.add(sstyled("PASSIVES", PASSIVE_HEAD, true));
            for (Weapon.Ability a : w.abilities) {
                if (a.trigger().isPower()) continue;
                String line = shortLore(a.name().equalsIgnoreCase("Passive") || a.name().equalsIgnoreCase("Carry")
                        ? a.desc() : a.name());
                if (!line.isBlank()) lore.add(sstyled("- " + line, TEXT, false));
            }
        }

        if (hasPowers) {
            lore.add(Component.empty());
            lore.add(sstyled("POWERS", AQUA, true));
            for (Weapon.Ability a : w.abilities) {
                if (!a.trigger().isPower()) continue;
                lore.add(sstyled("- " + shortLore(a.name()), TEXT, false));
            }
        }
        return lore;
    }

    /** Keep lore entries to a single readable line and remove trailing sentence clutter. */
    private static String shortLore(String text) {
        if (text == null) return "";
        String value = text.replaceAll("\\s+", " ").trim();
        int sentence = value.indexOf('.');
        if (sentence >= 0) value = value.substring(0, sentence).trim();
        if (value.length() > 34) value = value.substring(0, 31).trim() + "...";
        return value;
    }

    /** Custom model: the pack's custom_model_data and/or an item_model id, both set in config.yml (models:). */
    @SuppressWarnings("deprecation")
    private void applyModel(ItemMeta meta, Weapon w) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("models." + w.id);
        int cmd = sec != null ? sec.getInt("custom-model-data", w.cmd) : w.cmd;
        meta.setCustomModelData(cmd);
        String model = sec != null ? sec.getString("item-model", "") : "";
        if (model != null && !model.isBlank()) {
            NamespacedKey k = NamespacedKey.fromString(model.trim());
            if (k != null) meta.setItemModel(k);
        }
    }

    @SuppressWarnings("deprecation")
    public ItemStack create(String id) {
        Weapon w = get(id);
        if (w == null) return null;

        ItemStack item = new ItemStack(w.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title(w));
        meta.lore(lore(w, isProgressive(w) ? 0 : -1));
        meta.setUnbreakable(true);
        applyModel(meta, w);
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
    public ItemStack icon(Weapon w, List<Component> lore) {
        ItemStack item = new ItemStack(w.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title(w));
        meta.lore(lore);
        applyModel(meta, w);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    // Kill counter (Ancient Blade)
    // ------------------------------------------------------------------

    public boolean isProgressive(Weapon w) {
        return w.id.equals("ancient_blade");
    }

    public int kills(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
    }

    public void setKills(ItemStack item, int kills) {
        Weapon w = of(item);
        if (w == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, kills);
        meta.lore(lore(w, kills));
        item.setItemMeta(meta);
    }

    /**
     * Wearing the crown showed nothing without the resource pack (the item had no armor asset,
     * so it looked like it was floating). It now uses the vanilla netherite armor model, which
     * always renders on the head with or without the pack.
     */
    private static void wornNoArmorLayer(ItemMeta meta) {
        var eq = meta.getEquippable();
        eq.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);
        eq.setModel(NamespacedKey.minecraft("netherite"));
        meta.setEquippable(eq);
    }
}
