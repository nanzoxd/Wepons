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
import java.util.TreeMap;

/** All weapons, plus building and identifying their items. */
public final class WeaponRegistry {

    private final Wepons plugin;
    private final NamespacedKey key;
    private final NamespacedKey killsKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey progKey;
    private final Map<String, Weapon> weapons = new LinkedHashMap<>();

    public WeaponRegistry(Wepons plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "weapon");
        this.killsKey = new NamespacedKey(plugin, "kills");
        this.levelKey = new NamespacedKey(plugin, "level");
        this.progKey = new NamespacedKey(plugin, "uprog");
        register();
    }

    // ------------------------------------------------------------------
    // Weapon list
    // ------------------------------------------------------------------

    private void register() {
        // ---- passive swords ----
        blade("weaving", Material.NETHERITE_SWORD, "Spider Blade", "#A855F7", 9101)
                .upgrade(Weapon.UpType.KILLS, 4, "Kill 4 players")
                .tag("Spin a web, then wait.")
                .aura(Particle.WITCH)
                .passive("Weaving while held.", e(PotionEffectType.WEAVING, 0))
                .proc("cobwebproc", "Cobweb", Trigger.HIT, 0, "30% chance to trap the target in cobwebs for 4s.")
                .unlock("cobwebproc", "weaving-sword", 2);

        blade("resistance", Material.NETHERITE_SWORD, "Guardian Blade", "#9CA3AF", 9102)
                .tag("Stand firm. Nothing breaks you.")
                .aura(Particle.ASH)
                .passive("Resistance II while held.", e(PotionEffectType.RESISTANCE, 1))
                .one("bulwark", "Resistance IV", 20, "Gain Resistance IV for 4s and Absorption II for 8s.");

        blade("speed", Material.NETHERITE_SWORD, "Gale Blade", "#22D3EE", 9103)
                .tag("Gone before the echo lands.")
                .aura(Particle.ELECTRIC_SPARK)
                .passive("Speed I while held.", e(PotionEffectType.SPEED, 0))
                .one("windstep", "Speed II", 45, "Gain Speed II for 15s.");

        blade("invisibility", Material.NETHERITE_SWORD, "Phantom Blade", "#E5E7EB", 9104)
                .tag("You never saw it coming.")
                .aura(Particle.CLOUD)
                .passive("Invisibility while held.", e(PotionEffectType.INVISIBILITY, 0))
                .one("vanishstep", "Blink", 16, "Teleport 5 blocks forward (2 if blocked) and turn invisible for 3s.");

        blade("regeneration", Material.NETHERITE_SWORD, "Mender's Blade", "#FF3B3B", 9105)
                .tag("Wounds close faster than they open.")
                .aura(Particle.HEART)
                .passive("Regeneration I while held.", e(PotionEffectType.REGENERATION, 0))
                .one("secondwind", "Heal", 25, "Heal 6 hearts and gain Regeneration III for 3s.");

        blade("saturation", Material.NETHERITE_SWORD, "Feast Blade", "#FF8A1F", 9106)
                .tag("A feast in every swing.")
                .aura(Particle.FLAME)
                .passive("Never go hungry while held.", e(PotionEffectType.SATURATION, 0))
                .one("feast", "Refill Food", 20, "Refill hunger and saturation, plus Regeneration I for 4s.");

        add("haste", Material.NETHERITE_PICKAXE, "Quake Pickaxe", "#FFD21F", 9107)
                .tag("Stone is only a suggestion.")
                .aura(Particle.END_ROD)
                .enchant(Enchantment.EFFICIENCY, 7)
                .enchant(Enchantment.FORTUNE, 3)
                .passive("Efficiency VII: mines blocks very fast.")
                .one("minetoggle", "Mine Mode", 0, "Switch the pickaxe between Fortune III and Silk Touch.");

        blade("strength", Material.NETHERITE_SWORD, "Berserker Blade", "#EF4444", 9108)
                .upgrade(Weapon.UpType.KILLS, 4, "Kill 4 players")
                .tag("Might is its own argument.")
                .aura(Particle.ANGRY_VILLAGER)
                .passive("Strength I while held.", e(PotionEffectType.STRENGTH, 0))
                .one("berserk", "Strength II", 45, "Gain Strength II for 15s.")
                .unlock("berserk", "strength-sword", 2);

        // ---- active swords ----
        blade("dash", Material.NETHERITE_SWORD, "Rift Blade", "#38BDF8", 9109)
                .tag("Distance is a choice.")
                .aura(Particle.SOUL_FIRE_FLAME)
                .one("dash", "Dash", 8, "Launch yourself forward in a quick dash.")
                .two("blink", "Blink", 14, "Teleport up to 6 blocks forward, even through thin walls.");

        blade("voidfang", Material.NETHERITE_SWORD, "Void Fang", "#A78BFA", 9110)
                .tag("The void answers its master.")
                .aura(Particle.PORTAL)
                .one("voidslash", "Void Slash", 12, "Slash the player you look at (up to 10 blocks) for 2.5 hearts.")
                .two("voidpull", "Void Pull", 16, "Pull the player you look at (up to 10 blocks) to you.");

        blade("stormbreaker", Material.NETHERITE_SWORD, "Stormbreaker", "#FACC15", 9111)
                .tag("Thunder follows the blade.")
                .aura(Particle.END_ROD)
                .one("lightning", "Lightning Strike", 15, "Call lightning where you look. Players near it take 3 hearts.")
                .two("staticfield", "Static Field", 18, "Slow every player within 5 blocks (Slowness II, 3s).");

        blade("freeze", Material.NETHERITE_SWORD, "Glacier Blade", "#38E8E0", 9112)
                .tag("Cold is the last thing they feel.")
                .aura(Particle.SNOWFLAKE)
                .proc("freeze", "Frost Strike", Trigger.HIT, 0, "Every 3rd hit on a player freezes them for 5s.")
                .one("frostnova", "Frost Nova", 20, "Freeze players within 6 blocks for 10s and slow them.");

        blade("scythe", Material.NETHERITE_SWORD, "Reaper Scythe", "#C26A6A", 9113)
                .tag("The harvest never ends.")
                .aura(Particle.CRIMSON_SPORE)
                .proc("summonfangs", "Fang Trap", Trigger.HIT, 30, "Every 5th hit traps the target in fangs for 3s.")
                .one("ravagerride", "Ravager Ride", 40, "Ride a ravager for 15s.");

        add("fireshield", Material.SHIELD, "Phoenix Shield", "#F97316", 9114)
                .tag("Strike me and burn.")
                .aura(Particle.FLAME)
                .proc("flamerebuke", "Flame Rebuke", Trigger.BLOCK, 0, "While blocking, attackers are launched back and set on fire for 4s.");

        // ---- utility weapons ----
        blade("flightsword", Material.NETHERITE_SWORD, "Sky Blade", "#55FF55", 9115)
                .tag("The sky is just another road.")
                .aura(Particle.HAPPY_VILLAGER)
                .one("flightdash", "Dash", 20, "Dash forward at high speed.");

        blade("lightningsword", Material.NETHERITE_SWORD, "Thunder Blade", "#FFFF55", 9116)
                .tag("Every strike calls the storm.")
                .aura(Particle.END_ROD)
                .proc("lightningproc", "Storm Edge", Trigger.HIT, 0, "30% chance to strike the target with lightning.");

        blade("cobwebaxe", Material.NETHERITE_AXE, "Widow's Axe", "#FF55FF", 9117)
                .enchant(Enchantment.EFFICIENCY, 5)
                .tag("Nothing escapes the web.")
                .aura(Particle.ENCHANT)
                .proc("cobwebproc", "Cobweb", Trigger.HIT, 0, "30% chance to trap the target in cobwebs for 4s.");

        blade("gravityaxe", Material.NETHERITE_AXE, "Graviton Axe", "#55FFFF", 9118)
                .enchant(Enchantment.EFFICIENCY, 5)
                .tag("Even the ground lets go.")
                .aura(Particle.ELECTRIC_SPARK)
                .one("gravitypulse", "Gravity Pulse", 10, "Lift players within 8 blocks for 5s and blind them for 3s.");

        add("grapplebow", Material.BOW, "Hookshot Bow", "#CC66CC", 9119)
                .tag("Pulled to the target, always.")
                .aura(Particle.WITCH)
                .enchant(Enchantment.POWER, 5)
                .enchant(Enchantment.INFINITY, 1)
                .one("grapplelaunch", "Grapple Launch", 10, "Launch yourself forward.");

        add("dashmace", Material.MACE, "Comet Mace", "#FF5555", 9120)
                .tag("Impact is everything.")
                .aura(Particle.FLAME)
                .enchant(Enchantment.DENSITY, 5)
                .enchant(Enchantment.WIND_BURST, 5)
                .one("dashmacedash", "Dash", 20, "Dash forward at very high speed.");

        add("banhammer", Material.MACE, "Doom Hammer", "#FF4040", 9121)
                .tag("The final verdict.")
                .aura(Particle.ANGRY_VILLAGER)
                .enchant(Enchantment.DENSITY, 3)
                .carry("Speed II, Strength II and Glowing while in your inventory.",
                        e(PotionEffectType.SPEED, 1), e(PotionEffectType.STRENGTH, 1),
                        e(PotionEffectType.GLOWING, 0), e(PotionEffectType.WEAVING, 0))
                .proc("banhammer", "Judgement", Trigger.HIT, 40, "Hit a player to ban them.");

        // ---- more tools ----
        add("smelter", Material.NETHERITE_PICKAXE, "Forge Pickaxe", "#FF7A1A", 9122)
                .tag("Ore comes out finished.")
                .aura(Particle.FLAME)
                .enchant(Enchantment.EFFICIENCY, 7)
                .passive("Ores drop smelted with Fortune III. Other blocks drop as if Silk Touch.")
                .passive("Breaks 2 blocks at once.");

        add("lumberjack", Material.NETHERITE_AXE, "Timber Axe", "#8B5A2B", 9126)
                .enchant(Enchantment.SHARPNESS, 4)
                .tag("One swing, the whole tree.")
                .aura(Particle.HAPPY_VILLAGER)
                .enchant(Enchantment.EFFICIENCY, 5)
                .passive("Efficiency V: chops logs very fast.")
                .passive("Chops a whole natural tree in one swing. Logs placed by players (houses, builds) are not affected.");

        blade("impact", Material.NETHERITE_SWORD, "Impact Sword", "#E11D48", 9127)
                .tag("Nothing gets past the bubble.")
                .passive("Take 20% less damage while holding the sword.")
                .one("impactbubble", "Impact Bubble", 60,
                        "Wrap yourself in a bubble for 15s. Players who touch it are knocked back.")
                .unlockStreak("impactbubble", "impact-sword", 2);

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
        blade("fortune_blade", Material.NETHERITE_SWORD, "Gilded Blade", "#FBBF24", 9201)
                .tag("Luck favors the bold.")
                .aura(Particle.HAPPY_VILLAGER)
                .passive("Gain 2x XP while holding the blade.")
                .one("fortunefeast", "Endless Feast", 60, "Any food that you eat is not consumed.")
                .two("fortunerolls", "Lucky Strikes", 90, "Your hits gain random damage multipliers.");

        blade("flame_blade", Material.NETHERITE_SWORD, "Inferno Blade", "#FF4D1F", 9202)
                .tag("Born in the heart of the fire.")
                .aura(Particle.FLAME)
                .passive("Infinite Fire Resistance while held.", e(PotionEffectType.FIRE_RESISTANCE, 0))
                .one("flamesweep", "Flame Sweep", 60, "Sets enemies on fire and knocks them away.")
                .two("flameblast", "Flame Explosion", 90, "Creates a powerful flame explosion.");

        blade("bloom_blade", Material.NETHERITE_SWORD, "Blossom Blade", "#FF1FB4", 9203)
                .tag("Life blooms where it cuts.")
                .aura(Particle.CHERRY_LEAVES)
                .passive("Every 10th hit heals you the damage dealt.")
                .one("bloomhearts", "Overgrowth", 60, "Temporarily raise yourself to 15 hearts.")
                .two("bloomwither", "Wilt", 90, "Cap nearby enemies' health temporarily.");

        // ---- Altar SMP S2 legendaries ----
        blade("ancient_blade", Material.NETHERITE_SWORD, "Ancient Blade", "#22B8CF", 9301)
                .tag("Carried by the Legendaries of the Deep Dark.")
                .aura(Particle.SCULK_SOUL)
                .passive("Speed boost while standing on Sculk.")
                .passive("(2 kills) Permanent Speed I while held.")
                .passive("(4 kills) Permanent Strength II while held.")
                .one("presence", "Presence", 45,
                        "Shriek, then hit a player within 5s to mark them: Darkness for 10s and no Absorption. Unlocks at 3 kills.")
                .two("darkness", "Unyielding Darkness", 60,
                        "Launch up, then throw a Sculk Catalyst that spreads Sculk and splits into sonic booms. Unlocks at 5 kills.");

        blade("dragonrend", Material.NETHERITE_SWORD, "Dragonrend", "#D946EF", 9302)
                .tag("Forged from the Ender Dragon's heart.")
                .aura(Particle.REVERSE_PORTAL)
                .passive("Looting III and Sweeping Edge III.")
                .one("hypersonic", "Hyperonic Slash", 40,
                        "Swing at a player within 16 blocks to teleport to them and hit for 5 hearts. Chain slashes; the window halves each time.")
                .two("infinitevoid", "Infinite Void", 60,
                        "Place a Void Clock that heavily slows players within 6 blocks for 10s.");

        blade("tidebreaker", Material.TRIDENT, "Tidebreaker", "#3B82F6", 9303)
                .enchant(Enchantment.LOYALTY, 3)      // thrown Tidebreaker flies back to you
                .enchant(Enchantment.IMPALING, 5)
                .enchant(Enchantment.CHANNELING, 1)
                .tag("The ocean lives within the wielder.")
                .swordStats()
                .aura(Particle.DRIPPING_WATER)
                .passive("Permanent Water Breathing while held.", e(PotionEffectType.WATER_BREATHING, 0))
                .one("stormcall", "Stormcall", 40,
                        "Call a storm where you look. It hurts the 2 highest-HP targets within 6 blocks every 0.5s for 10s.")
                .two("risingtide", "Rising Tide", 50,
                        "Look up: jet upward, then land with a shockwave. Look down: throw a wave that pulls players to you.");

        blade("wither_symbiote", Material.NETHERITE_SWORD, "Wither Symbiote", "#7C3AED", 9304)
                .tag("It is ready to latch on.")
                .aura(Particle.SOUL)
                .passive("Immune to the Wither effect while held.")
                .one("infection", "Symbiote Infection", 40,
                        "Your next hit infects the target (damage and Weakness when it ends). Press again to detonate early.")
                .two("rampage", "Symbiote Rampage", 120,
                        "20s of +3 hearts, speed and lifesteal, but faster hunger. Afterwards you are Weak, Withered and 3 hearts down.");

        add("omen", spearMaterial(), "Omen", "#0EA5A4", 9305)
                .kind("SPEAR")
                .tag("Imbued with the power of the Trial Chambers.")
                .aura(Particle.TRIAL_OMEN)
                .enchant(Enchantment.SHARPNESS, 4)
                .enchant(Enchantment.LOOTING, 3)
                .one("vaultomen", "Vault Omen", 35,
                        "Throw a Vault Omen for 7s that tethers players within 3 blocks.")
                .two("conjuring", "Ominous Conjuring", 50,
                        "For 10s your hits throw AOE projectiles. More players within 16 blocks raise the spell level.");
        // ---- Crazy Mythicals ----
        add("hourglass_dagger", Material.NETHERITE_SWORD, "Hourglass Dagger", "#FCD05C", 9401)
                .crazy(CrazyLore.HOURGLASS_DAGGER)
                .aura(Particle.END_ROD)
                .passive("Speed II.", e(PotionEffectType.SPEED, 1));

        add("great_hammer", Material.MACE, "Great Hammer", "#34B532", 9402)
                .crazy(CrazyLore.GREAT_HAMMER)
                .aura(Particle.HAPPY_VILLAGER);

        add("judge", Material.TRIDENT, "The Judge", "#55FFFF", 9403)
                .crazy(CrazyLore.JUDGE)
                .reach(0.5)
                .aura(Particle.ELECTRIC_SPARK);

        add("ghost_shield", Material.SHIELD, "Ghost Shield", "#AAAAAA", 9404)
                .crazy(CrazyLore.GHOST_SHIELD)
                .aura(Particle.SOUL);

        add("dragon_cannon", Material.CROSSBOW, "Dragon Cannon", "#AA00AA", 9405)
                .crazy(CrazyLore.DRAGON_CANNON)
                .aura(Particle.DRAGON_BREATH);

        add("cleaver", Material.NETHERITE_AXE, "Cleaver", "#D97706", 9406)
                .crazy(CrazyLore.CLEAVER)
                .aura(Particle.CRIT);

        add("burgblade", Material.NETHERITE_SWORD, "Burgblade", "#F59E0B", 9407)
                .crazy(CrazyLore.BURGBLADE)
                .aura(Particle.FLAME);

        add("thornblade", Material.NETHERITE_SWORD, "Thornblade", "#22C55E", 9408)
                .crazy(CrazyLore.THORNBLADE)
                .aura(Particle.HAPPY_VILLAGER);

        add("soulstaff", Material.BLAZE_ROD, "Soul Staff", "#7DD3FC", 9409)
                .crazy(CrazyLore.SOULSTAFF)
                .aura(Particle.SOUL_FIRE_FLAME);
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

    /** Tooltip style id: wepons:<weapon id>. The pack has wepons:tooltip/<id>_background and _frame sprites for it. */
    public NamespacedKey tooltipKey(Weapon w) {
        return new NamespacedKey(plugin, w.id);
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

    /** Kings SMP style name gradient per weapon: {from, to}, blended letter by letter. Weapons not listed keep one colour. */
    private static final Map<String, String[]> GRADIENTS = new LinkedHashMap<>();
    static {
        g("weaving", "#D8B4FE", "#6D28D9");        g("resistance", "#E5E7EB", "#4B5563");
        g("speed", "#A5F3FC", "#0E7490");          g("invisibility", "#FFFFFF", "#6B7280");
        g("regeneration", "#FF8A8A", "#B91C1C");   g("saturation", "#FFD08A", "#C2410C");
        g("haste", "#FFF08A", "#D97706");          g("strength", "#FCA5A5", "#7F1D1D");
        g("dash", "#BAE6FD", "#1D4ED8");           g("voidfang", "#E0D4FF", "#4C1D95");
        g("stormbreaker", "#FFF7AE", "#CA8A04");   g("freeze", "#CFFFFB", "#0E8F9A");
        g("scythe", "#F0B4B4", "#7A2E2E");         g("fireshield", "#FFD27A", "#C2260C");
        g("flightsword", "#D4FFB0", "#15803D");    g("lightningsword", "#FFFFFF", "#EAB308");
        g("cobwebaxe", "#FFC2FF", "#A21CAF");      g("gravityaxe", "#C8FFFF", "#1E88C8");
        g("grapplebow", "#F0B0F0", "#7E2A7E");     g("dashmace", "#FFC0A0", "#B91C1C");
        g("banhammer", "#FF9A9A", "#7F0F0F");      g("smelter", "#FFC86B", "#D4380D");
        g("lumberjack", "#D9A066", "#4A2E12");     g("impact", "#FF8FA8", "#881337");
        g("monarchcrown", "#FFF3A0", "#B8860B");   g("fortune_blade", "#FFF0A8", "#B45309");
        g("flame_blade", "#FFD060", "#B91C1C");    g("bloom_blade", "#FFC2EC", "#9D174D");
        g("ancient_blade", "#A5F3FC", "#0B4F6C");  g("dragonrend", "#F5B5FF", "#6B21A8");
        g("tidebreaker", "#93E0FF", "#1E3A8A");    g("wither_symbiote", "#C4B5FD", "#2E1065");
        g("omen", "#7FF0E4", "#0B5E63");
        g("hourglass_dagger", "#FFF1A8", "#C28A00");  g("great_hammer", "#9BFF8F", "#1B6B1A");
        g("judge", "#CFFFFF", "#0E7490");             g("ghost_shield", "#F3F4F6", "#4B5563");
        g("dragon_cannon", "#F0A0FF", "#5B0F7A");     g("cleaver", "#FBBF24", "#92400E");
        g("burgblade", "#FDE68A", "#B45309");         g("thornblade", "#BBF7D0", "#166534");
        g("soulstaff", "#E0F7FF", "#1E6FA8");
    }

    private static void g(String id, String from, String to) {
        GRADIENTS.put(id, new String[]{from, to});
    }

    /** Bold small-caps name, one colour per letter from the weapon's gradient (same font as before). */
    public Component title(Weapon w) {
        String text = sc(w.name);
        String[] g = GRADIENTS.get(w.id);
        if (g == null) {
            return Component.text(text, w.color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false);
        }
        TextColor from = TextColor.fromHexString(g[0]);
        TextColor to = TextColor.fromHexString(g[1]);
        net.kyori.adventure.text.TextComponent.Builder out = Component.text();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n > 1 ? i / (float) (n - 1) : 0f;
            out.append(Component.text(String.valueOf(text.charAt(i)), TextColor.lerp(t, from, to)));
        }
        return out.decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    private static final TextColor GOLD = TextColor.fromHexString("#FFD84A");
    private static final TextColor ENCH_HEAD = TextColor.fromHexString("#C4B5FD");
    private static final TextColor ENCH_NAME = TextColor.fromHexString("#E9D5FF");
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

    /** Clean lore: tagline, passives, then abilities (key, what it does, cooldown). All small caps. */
    private List<Component> lore(Weapon w, int kills) {
        return lore(w, kills, 1, 0);
    }

    private List<Component> lore(Weapon w, int kills, int level, int prog) {
        return lore(w, kills, level, prog, w.enchants);
    }

    private List<Component> lore(Weapon w, int kills, int level, int prog, Map<Enchantment, Integer> ench) {
        if (w.rawLore != null) return crazyLore(w);
        List<Component> lore = new ArrayList<>();

        if (!w.tagline.isEmpty()) lore.add(sstyled(w.tagline, NamedTextColor.WHITE, true));
        lore.add(sstyled("(Mythic)", MYTHIC, false));
        if (kills >= 0) {
            lore.add(sstyled("Kills: " + kills, NamedTextColor.RED, false));
        }
        if (w.upgrade != null) {
            if (level >= 2) {
                lore.add(sstyled("Level II", GOLD, true));
                int pct = (int) Math.round((1.0 - cooldownMultiplier()) * 100);
                wrapped(lore, "", "Abilities recharge " + pct + "% faster. Stronger effects.", TEXT);
            } else {
                int need = upgradeAmount(w);
                int shown = w.upgrade.type() == Weapon.UpType.KILLS ? Math.max(0, kills) : prog;
                lore.add(sstyled("Level I", TEXT, false));
                wrapped(lore, "Task: ", w.upgrade.desc() + " (" + Math.min(shown, need) + "/" + need + ")", GOLD);
            }
        }

        boolean hasPassives = w.abilities.stream().anyMatch(a -> !a.trigger().isPower());
        boolean hasAbilities = w.abilities.stream().anyMatch(a -> a.trigger().isPower());

        if (hasPassives) {
            lore.add(Component.empty());
            lore.add(sstyled("passive", PASSIVE_HEAD, true));
            for (Weapon.Ability a : w.abilities) {
                if (!a.trigger().isPower()) entry(lore, w, a, kills);
            }
        }

        if (hasAbilities) {
            lore.add(Component.empty());
            lore.add(sstyled("ability", AQUA, true));
            for (Weapon.Ability a : w.abilities) {
                if (a.trigger().isPower()) entry(lore, w, a, kills);
            }
        }

        if (!ench.isEmpty()) {
            lore.add(Component.empty());
            lore.add(sstyled("enchants", ENCH_HEAD, true));
            for (Map.Entry<Enchantment, Integer> en : ench.entrySet()) lore.add(enchLine(en));
        }
        return lore;
    }

    private static final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer LEGACY =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                    .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static final java.util.regex.Pattern LEGACY_CODES =
            java.util.regex.Pattern.compile("&(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])");

    /**
     * Crazy Mythicals lore in the Relique layout: (Mythic), then a "passive" block and an "ability" block with
     * "- name" lines and wrapped small-caps text. The text itself still comes from CrazyLore (original & lines).
     */
    private List<Component> crazyLore(Weapon w) {
        List<String> passives = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<List<String>> descs = new ArrayList<>();
        int mode = 0;   // 0 = before any header, 1 = ability block, 2 = passives block
        for (String raw : w.rawLore) {
            String line = LEGACY_CODES.matcher(raw).replaceAll("").trim();
            if (line.isEmpty()) continue;
            if (line.indexOf('\u2693') >= 0) { mode = 2; continue; }
            if (line.startsWith("--") && line.endsWith("--") && line.length() > 4) {
                names.add(line.substring(2, line.length() - 2).trim());
                descs.add(new ArrayList<>());
                mode = 1;
                continue;
            }
            boolean bullet = line.startsWith(">");
            String text = bullet ? line.substring(1).trim() : line;
            List<String> target = mode == 2 ? passives : (mode == 1 ? descs.get(descs.size() - 1) : null);
            if (target == null) continue;
            if (bullet || target.isEmpty() || text.startsWith("-")) target.add(text);
            else target.set(target.size() - 1, target.get(target.size() - 1) + " " + text);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(sstyled("(Mythic)", MYTHIC, false));
        if (!passives.isEmpty()) {
            lore.add(Component.empty());
            lore.add(sstyled("passive", PASSIVE_HEAD, true));
            for (String t : passives) wrapped(lore, t.startsWith("-") ? "  " : "- ", t, TEXT);
        }
        if (!names.isEmpty()) {
            lore.add(Component.empty());
            lore.add(sstyled("ability", AQUA, true));
            for (int i = 0; i < names.size(); i++) {
                lore.add(sstyled("- " + names.get(i), ABILITY_HEAD, false));
                for (String t : descs.get(i)) wrapped(lore, "  ", t, TEXT);
            }
        }
        return lore;
    }

    /** Crazy Mythicals lore: the original & colour-coded lines, never italic. */
    private static List<Component> rawLore(Weapon w) {
        List<Component> out = new ArrayList<>();
        for (String line : w.rawLore) out.add(LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        return out;
    }

    /** Menu helper: small-caps text blended letter by letter from one hex colour to another, bold. */
    public static Component gradient(String text, String fromHex, String toHex) {
        String t = sc(text);
        TextColor from = TextColor.fromHexString(fromHex);
        TextColor to = TextColor.fromHexString(toHex);
        net.kyori.adventure.text.TextComponent.Builder out = Component.text();
        int n = t.length();
        for (int i = 0; i < n; i++) {
            float f = n > 1 ? i / (float) (n - 1) : 0f;
            out.append(Component.text(String.valueOf(t.charAt(i)), TextColor.lerp(f, from, to)));
        }
        return out.decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false).build();
    }

    /** One enchantment per line in short form, e.g. "Sharp 4". A level at or above the vanilla maximum is gold. */
    private static Component enchLine(Map.Entry<Enchantment, Integer> en) {
        Enchantment e = en.getKey();
        int lvl = en.getValue();
        TextColor lvlColor = lvl >= e.getMaxLevel() ? GOLD : TEXT;
        return styled(enchantShort(e) + " ", ENCH_NAME, false)
                .append(styled(String.valueOf(lvl), lvlColor, false));
    }

    /** Short lore name: Sharpness -> Sharp, Looting -> Loot, Sweeping Edge -> Sweep. */
    public static String enchantShort(Enchantment e) {
        switch (e.getKey().getKey()) {
            case "sharpness": return "Sharp";
            case "looting": return "Loot";
            case "sweeping_edge": return "Sweep";
            case "efficiency": return "Eff";
            case "fortune": return "Fort";
            case "wind_burst": return "Wind";
            case "protection": return "Prot";
            case "unbreaking": return "Unb";
            case "silk_touch": return "Silk";
            case "density": return "Dens";
            case "loyalty": return "Loyal";
            case "impaling": return "Impale";
            case "channeling": return "Chan";
            default: {
                String n = enchantName(e).split(" ")[0];
                return n.length() > 5 ? n.substring(0, 5) : n;
            }
        }
    }

    // ---- enchantment info (lore and the /wepons enchants dialogs) ----

    /** "sweeping_edge" -> "Sweeping Edge". */
    public static String enchantName(Enchantment e) {
        StringBuilder sb = new StringBuilder();
        for (String part : e.getKey().getKey().split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String roman(int n) {
        if (n <= 0 || n > 39) return String.valueOf(n);
        String[] r = {"X", "IX", "V", "IV", "I"};
        int[] v = {10, 9, 5, 4, 1};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                sb.append(r[i]);
                n -= v[i];
            }
        }
        return sb.toString();
    }

    /** Every enchantment that appears on at least one weapon, sorted by name. */
    public List<Enchantment> allEnchants() {
        Map<String, Enchantment> map = new TreeMap<>();
        for (Weapon w : all()) {
            for (Enchantment e : w.enchants.keySet()) map.putIfAbsent(e.getKey().getKey(), e);
        }
        return new ArrayList<>(map.values());
    }

    /** Weapons that carry this enchantment, with the level they have. */
    public Map<Weapon, Integer> weaponsWith(Enchantment e) {
        Map<Weapon, Integer> out = new LinkedHashMap<>();
        for (Weapon w : all()) {
            Integer lvl = w.enchants.get(e);
            if (lvl != null) out.put(w, lvl);
        }
        return out;
    }

    /** One passive or ability: name (with its key), a short description, cooldown and unlock info. */
    private void entry(List<Component> lore, Weapon w, Weapon.Ability a, int kills) {
        boolean plain = a.name().equals("Passive") || a.name().equals("Carry");
        if (plain) {
            wrapped(lore, "- ", a.desc(), TEXT);
        } else {
            String head = a.trigger().isPower() ? a.name() + " [" + a.trigger().key + "]" : a.name();
            lore.add(sstyled("- " + head, ABILITY_HEAD, false));
            wrapped(lore, "  ", a.desc(), TEXT);
        }
        if (a.trigger().isPower()) {
            String cd = a.cooldown() > 0 ? a.cooldown() + "s" : "none";
            lore.add(sstyled("  Cooldown: " + cd, GOLD, false));
        } else if (a.cooldown() > 0) {
            lore.add(sstyled("  Cooldown: " + a.cooldown() + "s", GOLD, false));
        }

        Weapon.Unlock u = w.unlocks.get(a.key());
        if (u != null && kills >= 0) {
            int need = unlockKills(u);
            if (kills >= need) lore.add(sstyled("  Unlocked", NamedTextColor.GREEN, false));
            else lore.add(sstyled("  Unlock: " + need + (u.streak() ? " kills in a row" : " kills"), NamedTextColor.RED, false));
        }
    }

    /** Wraps a description onto several short lore lines; the first gets the prefix, the rest are indented. */
    private static void wrapped(List<Component> lore, String prefix, String text, TextColor color) {
        String indent = " ".repeat(prefix.length());
        boolean first = true;
        for (String part : wrap(text, 40)) {
            lore.add(sstyled((first ? prefix : (prefix.equals("- ") ? "  " : indent)) + part, color, false));
            first = false;
        }
    }

    /** Kill streak weapons (Impact Sword): dying before the unlock resets the kills. */
    public void resetStreak(ItemStack item) {
        Weapon w = of(item);
        if (w == null) return;
        for (Weapon.Unlock u : w.unlocks.values()) {
            if (!u.streak()) continue;
            int kills = kills(item);
            if (kills > 0 && kills < unlockKills(u)) setKills(item, 0);
        }
    }

    /** Kills needed for an unlock (config kill-unlocks.<path>). */
    public int unlockKills(Weapon.Unlock u) {
        return plugin.getConfig().getInt("kill-unlocks." + u.path(), u.def());
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
        meta.lore(lore(w, isProgressive(w) ? 0 : -1, 1, 0));
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
        if (w.reach > 0) {
            meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                    new NamespacedKey(plugin, "reach"), w.reach,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, new AttributeModifier(
                    new NamespacedKey(plugin, "block_reach"), w.reach,
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
    // Kill counter (Ancient Blade, Strength Sword, Weaving Sword)
    // ------------------------------------------------------------------

    public boolean isProgressive(Weapon w) {
        return w.id.equals("ancient_blade") || !w.unlocks.isEmpty()
                || (w.upgrade != null && w.upgrade.type() == Weapon.UpType.KILLS);
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
        meta.lore(lore(w, kills, level(item), progress(item), orderedEnchants(w, item)));
        item.setItemMeta(meta);
    }

    // ---- Level II (task upgrade) ----

    public int level(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    public int progress(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0);
    }

    /** Task amount for Level II (config upgrades.<id> overrides the default). */
    public int upgradeAmount(Weapon w) {
        return plugin.getConfig().getInt("upgrades." + w.id, w.upgrade == null ? 0 : w.upgrade.amount());
    }

    /** Cooldown multiplier at Level II (config upgrades.level2-cooldown-multiplier). */
    public double cooldownMultiplier() {
        return plugin.getConfig().getDouble("upgrades.level2-cooldown-multiplier", 0.8);
    }

    /** Stores task progress only (no lore rebuild). */
    public void setProgressOnly(ItemStack item, int prog) {
        if (of(item) == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
        item.setItemMeta(meta);
    }

    public void setUpgrade(ItemStack item, int level, int prog) {
        Weapon w = of(item);
        if (w == null) return;
        int kills = isProgressive(w) ? kills(item) : -1;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
        meta.lore(lore(w, kills, level, prog, orderedEnchants(w, item)));
        item.setItemMeta(meta);
    }

    /** Rebuilds the lore from the item's real state (kills, level, task progress, enchantments). */
    public void refreshLore(ItemStack item) {
        Weapon w = of(item);
        if (w == null) return;
        int kills = isProgressive(w) ? kills(item) : -1;
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore(w, kills, level(item), progress(item), orderedEnchants(w, item)));
        item.setItemMeta(meta);
    }

    /** The item's enchantments: the weapon's own order first, then any others by name. */
    private Map<Enchantment, Integer> orderedEnchants(Weapon w, ItemStack item) {
        Map<Enchantment, Integer> have = item.getEnchantments();
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        for (Enchantment e : w.enchants.keySet()) {
            if (have.containsKey(e)) out.put(e, have.get(e));
        }
        Map<String, Enchantment> rest = new TreeMap<>();
        for (Enchantment e : have.keySet()) {
            if (!out.containsKey(e)) rest.put(e.getKey().getKey(), e);
        }
        for (Enchantment e : rest.values()) out.put(e, have.get(e));
        return out;
    }

    /**
     * Wearing the crown shows the crown model itself (resource pack: items/netherite_helmet.json,
     * model number 9124, "head" display). The item is wearable in the head slot but has no armor
     * asset, so the plain netherite armor layer (the "normal white helmet") is not drawn over it.
     */
    private static void wornNoArmorLayer(ItemMeta meta) {
        var eq = meta.getEquippable();
        eq.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);
        eq.setModel(null);
        meta.setEquippable(eq);
    }
}
