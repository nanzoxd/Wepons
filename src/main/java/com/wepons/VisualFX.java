package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Visual and sound layer for every weapon: custom sweep arcs that follow the aim direction,
 * swing / hit / critical sounds, hit particles, ground impacts, ability animations and Level II flourishes.
 *
 * It only READS combat events (MONITOR priority, ignoreCancelled) and never changes damage, knockback
 * or any ability. Particles are short, capped and only drawn after a real swing / hit / ability use.
 */
public final class VisualFX implements Listener {

    enum Arc { WIDE, DIAG_DOWN, DIAG_UP, OVERHEAD, CRESCENT, CROSS, SPIRAL, WAVE, THRUST }

    enum Hit { SPARKS, FROST, FIRE, LIGHTNING, VOID, SCULK, BLOOM, WEB, GRAVITY, SOUL, GOLD, WIND, BLOOD, EARTH, WATER, SHIELD }

    private record Style(Color c1, Color c2, Arc arc, Particle trail, Hit hit, boolean swing, boolean heavy,
                         Sound swingS, float swingP, Sound hitS, float hitP,
                         Sound critS, float critP, Sound abilS, float abilP) {}

    private static final Vector UP = new Vector(0, 1, 0);

    private final Wepons plugin;
    private final WeaponRegistry reg;
    private final Map<String, Style> styles = new HashMap<>();
    private final Style fallback;

    private final Map<UUID, Long> lastSwing = new HashMap<>();
    private final Map<UUID, Long> lastArc = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();

    public VisualFX(Wepons plugin) {
        this.plugin = plugin;
        this.reg = plugin.registry();
        define();
        defineSweep();
        fallback = styles.get("dash");
    }

    // ---- how much sweep each weapon gets: 0 = none, 1 = light, 2 = full (arc + weapon model swing) ----

    private final Map<String, Integer> sweep = new HashMap<>();

    private void defineSweep() {
        for (String id : new String[]{"strength", "voidfang", "stormbreaker", "scythe", "lightningsword",
                "flame_blade", "ancient_blade", "dragonrend", "wither_symbiote"}) sweep.put(id, 2);
        for (String id : new String[]{"freeze", "speed", "dash", "flightsword", "tidebreaker", "omen", "bloom_blade",
                "fortune_blade", "impact", "cobwebaxe", "gravityaxe", "dashmace", "banhammer"}) sweep.put(id, 1);
    }

    private int sweepOf(Weapon w) {
        return sweep.getOrDefault(w.id, 0);
    }

    // ==================================================================
    // Style table: one entry per existing weapon
    // ==================================================================

    private void st(String id, String c1, String c2, Arc arc, Particle trail, Hit hit, boolean swing, boolean heavy,
                    Sound swingS, float swingP, Sound hitS, float hitP, Sound critS, float critP, Sound abilS, float abilP) {
        styles.put(id, new Style(Color.fromRGB(Integer.parseInt(c1.substring(1), 16)),
                Color.fromRGB(Integer.parseInt(c2.substring(1), 16)), arc, trail, hit, swing, heavy,
                swingS, swingP, hitS, hitP, critS, critP, abilS, abilP));
    }

    private void define() {
        // ---- swords ----
        st("weaving", "#A78BFA", "#1F1B2E", Arc.DIAG_DOWN, Particle.WITCH, Hit.WEB, true, false,
                Sound.BLOCK_COBWEB_HIT, 1.4f, Sound.BLOCK_COBWEB_BREAK, 1.1f,
                Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, Sound.ENTITY_EVOKER_CAST_SPELL, 1.3f);
        st("resistance", "#9CA3AF", "#22D3EE", Arc.OVERHEAD, Particle.CRIT, Hit.SHIELD, true, true,
                Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, Sound.BLOCK_ANVIL_HIT, 1.5f,
                Sound.ITEM_SHIELD_BLOCK, 1.2f, Sound.ITEM_SHIELD_BLOCK, 0.7f);
        st("speed", "#22D3EE", "#FFFFFF", Arc.WIDE, Particle.CLOUD, Hit.WIND, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.8f, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.6f,
                Sound.ENTITY_PHANTOM_SWOOP, 1.8f, Sound.ENTITY_BREEZE_JUMP, 1.2f);
        st("invisibility", "#E5E7EB", "#6B7280", Arc.DIAG_UP, Particle.SMOKE, Hit.SOUL, true, false,
                Sound.ENTITY_ENDERMAN_TELEPORT, 1.9f, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.6f,
                Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.2f);
        st("regeneration", "#FF3B3B", "#22C55E", Arc.WAVE, Particle.HAPPY_VILLAGER, Hit.BLOOM, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.7f);
        st("saturation", "#FF8A1F", "#FDE047", Arc.CRESCENT, Particle.FLAME, Hit.GOLD, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, Sound.ENTITY_GENERIC_EAT, 1.0f);
        st("strength", "#EF4444", "#7F1D1D", Arc.CROSS, Particle.CRIT, Hit.BLOOD, true, true,
                Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, Sound.BLOCK_CHAIN_BREAK, 0.8f,
                Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, Sound.ENTITY_RAVAGER_ROAR, 1.2f);
        st("dash", "#38BDF8", "#FFFFFF", Arc.THRUST, Particle.SOUL_FIRE_FLAME, Hit.SPARKS, true, false,
                Sound.ITEM_TRIDENT_THROW, 1.7f, Sound.ITEM_TRIDENT_HIT, 1.6f,
                Sound.ITEM_TRIDENT_RIPTIDE_1, 1.5f, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f);
        st("voidfang", "#A78BFA", "#111827", Arc.DIAG_DOWN, Particle.PORTAL, Hit.VOID, true, false,
                Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f,
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f);
        st("stormbreaker", "#FACC15", "#38BDF8", Arc.SPIRAL, Particle.ELECTRIC_SPARK, Hit.LIGHTNING, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.9f,
                Sound.ITEM_TRIDENT_THUNDER, 1.6f, Sound.ITEM_TRIDENT_THUNDER, 1.2f);
        st("freeze", "#38E8E0", "#FFFFFF", Arc.WAVE, Particle.SNOWFLAKE, Hit.FROST, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, Sound.BLOCK_GLASS_BREAK, 1.6f,
                Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, Sound.BLOCK_GLASS_BREAK, 1.0f);
        st("scythe", "#C26A6A", "#3B0F2E", Arc.CRESCENT, Particle.SOUL, Hit.SOUL, true, true,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, Sound.ENTITY_VEX_HURT, 0.8f,
                Sound.ENTITY_VEX_CHARGE, 0.9f, Sound.ENTITY_VEX_CHARGE, 0.7f);
        st("flightsword", "#55FF55", "#FFFFFF", Arc.WIDE, Particle.CLOUD, Hit.WIND, true, false,
                Sound.ENTITY_PHANTOM_FLAP, 1.4f, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.3f,
                Sound.ENTITY_PHANTOM_SWOOP, 1.2f, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f);
        st("lightningsword", "#FFFF55", "#FFFFFF", Arc.DIAG_UP, Particle.ELECTRIC_SPARK, Hit.LIGHTNING, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f,
                Sound.ITEM_TRIDENT_THUNDER, 1.8f, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.8f);
        st("fortune_blade", "#FBBF24", "#FFFFFF", Arc.CRESCENT, Particle.FIREWORK, Hit.GOLD, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f,
                Sound.ENTITY_PLAYER_LEVELUP, 1.6f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
        st("flame_blade", "#FF4D1F", "#FDE047", Arc.DIAG_UP, Particle.FLAME, Hit.FIRE, true, false,
                Sound.ITEM_FIRECHARGE_USE, 1.4f, Sound.ITEM_FLINTANDSTEEL_USE, 1.2f,
                Sound.ENTITY_BLAZE_SHOOT, 1.3f, Sound.ITEM_FIRECHARGE_USE, 0.8f);
        st("bloom_blade", "#FF1FB4", "#FBCFE8", Arc.WAVE, Particle.CHERRY_LEAVES, Hit.BLOOM, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f,
                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.8f, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.8f);
        st("impact", "#E11D48", "#38BDF8", Arc.OVERHEAD, Particle.CLOUD, Hit.SHIELD, true, true,
                Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f,
                Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, Sound.BLOCK_BEACON_ACTIVATE, 1.4f);
        // ---- relic blades ----
        st("ancient_blade", "#22B8CF", "#0F172A", Arc.CROSS, Particle.SCULK_SOUL, Hit.SCULK, true, false,
                Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2f,
                Sound.ENTITY_WARDEN_HEARTBEAT, 1.3f, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f);
        st("dragonrend", "#D946EF", "#4C1D95", Arc.DIAG_UP, Particle.PORTAL, Hit.VOID, true, false,
                Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.7f,
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f);
        st("tidebreaker", "#3B82F6", "#E0F2FE", Arc.WAVE, Particle.SPLASH, Hit.WATER, true, false,
                Sound.ITEM_TRIDENT_THROW, 1.4f, Sound.ITEM_TRIDENT_HIT, 1.0f,
                Sound.ENTITY_GENERIC_SPLASH, 1.0f, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f);
        st("wither_symbiote", "#7C3AED", "#111827", Arc.DIAG_DOWN, Particle.SMOKE, Hit.SOUL, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, Sound.ENTITY_WITHER_HURT, 1.6f,
                Sound.ENTITY_WITHER_SHOOT, 1.6f, Sound.ENTITY_WITHER_AMBIENT, 1.4f);
        st("omen", "#0EA5A4", "#134E4A", Arc.THRUST, Particle.SOUL_FIRE_FLAME, Hit.SOUL, true, false,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, Sound.ITEM_TRIDENT_HIT, 1.2f,
                Sound.ENTITY_VEX_CHARGE, 1.4f, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f);
        // ---- axes, maces, tools, shield, bow, crown ----
        st("cobwebaxe", "#FF55FF", "#7C3AED", Arc.OVERHEAD, Particle.WITCH, Hit.WEB, true, true,
                Sound.BLOCK_COBWEB_HIT, 0.9f, Sound.BLOCK_WOOD_BREAK, 0.8f,
                Sound.BLOCK_COBWEB_BREAK, 0.9f, Sound.BLOCK_COBWEB_PLACE, 1.0f);
        st("gravityaxe", "#55FFFF", "#EC4899", Arc.OVERHEAD, Particle.REVERSE_PORTAL, Hit.GRAVITY, true, true,
                Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, Sound.BLOCK_ANVIL_LAND, 1.6f,
                Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f);
        st("dashmace", "#FF5555", "#FBBF24", Arc.OVERHEAD, Particle.FLAME, Hit.EARTH, true, true,
                Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, Sound.BLOCK_ANVIL_LAND, 0.9f,
                Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.7f, Sound.ENTITY_BLAZE_SHOOT, 0.8f);
        st("banhammer", "#FF4040", "#6B7280", Arc.OVERHEAD, Particle.CRIT, Hit.SHIELD, true, true,
                Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5f, Sound.ENTITY_IRON_GOLEM_HURT, 0.8f,
                Sound.BLOCK_ANVIL_LAND, 0.7f, Sound.BLOCK_BELL_USE, 0.7f);
        st("haste", "#FFD21F", "#F59E0B", Arc.OVERHEAD, Particle.CRIT, Hit.EARTH, false, true,
                Sound.BLOCK_STONE_BREAK, 1.0f, Sound.BLOCK_STONE_BREAK, 1.2f,
                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f);
        st("smelter", "#FF7A1A", "#FACC15", Arc.OVERHEAD, Particle.LAVA, Hit.FIRE, false, true,
                Sound.ITEM_FIRECHARGE_USE, 1.0f, Sound.ITEM_FLINTANDSTEEL_USE, 1.0f,
                Sound.ENTITY_BLAZE_SHOOT, 1.0f, Sound.ITEM_FIRECHARGE_USE, 0.9f);
        st("lumberjack", "#8B5A2B", "#16A34A", Arc.OVERHEAD, Particle.CRIT, Hit.EARTH, false, true,
                Sound.BLOCK_WOOD_BREAK, 0.9f, Sound.BLOCK_WOOD_BREAK, 0.7f,
                Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.2f, Sound.BLOCK_WOOD_BREAK, 0.6f);
        st("fireshield", "#F97316", "#FDE047", Arc.WIDE, Particle.FLAME, Hit.FIRE, false, false,
                Sound.ITEM_FIRECHARGE_USE, 1.2f, Sound.ITEM_FIRECHARGE_USE, 1.3f,
                Sound.ENTITY_BLAZE_SHOOT, 1.1f, Sound.ITEM_FIRECHARGE_USE, 0.8f);
        st("grapplebow", "#CC66CC", "#FFFFFF", Arc.THRUST, Particle.CRIT, Hit.SPARKS, false, false,
                Sound.ITEM_TRIDENT_THROW, 1.2f, Sound.ITEM_TRIDENT_HIT, 1.4f,
                Sound.ITEM_TRIDENT_RIPTIDE_1, 1.4f, Sound.ITEM_TRIDENT_THROW, 1.2f);
        st("monarchcrown", "#FFD700", "#FFFFFF", Arc.WIDE, Particle.WAX_ON, Hit.GOLD, false, false,
                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f,
                Sound.ENTITY_PLAYER_LEVELUP, 1.4f, Sound.BLOCK_BELL_USE, 1.1f);
    }

    // ==================================================================
    // Small helpers
    // ==================================================================

    private static void snd(World w, Location l, Sound s, float vol, float pitch) {
        w.playSound(l, s, SoundCategory.PLAYERS, vol, pitch);
    }

    private static void dot(World w, Location l, Style s, float size) {
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0,
                new Particle.DustTransition(s.c1(), s.c2(), size));
    }

    private static boolean isCrit(Player p) {
        return p.getFallDistance() > 0.0f && !p.isOnGround() && !p.isClimbing()
                && !p.isSprinting() && !p.isInWater() && p.getAttackCooldown() > 0.9f;
    }

    private Style style(Weapon w) {
        Style s = styles.get(w.id);
        return s != null ? s : fallback;
    }

    private static Particle.DustTransition transition(Style s, float size) {
        return new Particle.DustTransition(s.c1(), s.c2(), size);
    }

    // ==================================================================
    // Sweep arcs (follow the aim direction)
    // ==================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        Weapon w = reg.of(item);
        if (w == null) return;
        Style s = style(w);
        int sweepLvl = sweepOf(w);
        if (sweepLvl == 0) return;

        long now = System.currentTimeMillis();
        Long last = lastSwing.get(p.getUniqueId());
        if (last != null && now - last < 350) return;          // one arc per real swing
        if (p.getAttackCooldown() < 0.5f) return;               // no arcs for click spam
        if (p.getTargetEntity(4) == null && p.getTargetBlockExact(4) != null) return;  // mining, not attacking
        lastSwing.put(p.getUniqueId(), now);

        swingArc(p, s, isCrit(p), reg.level(item), sweepLvl, item);
    }

    private List<Vector> planar(double rollDeg, double spanDeg, double radius, int n, Vector f, Vector r, Vector u, double wave) {
        List<Vector> pts = new ArrayList<>(n);
        double roll = Math.toRadians(rollDeg);
        Vector side = r.clone().multiply(Math.cos(roll)).add(u.clone().multiply(Math.sin(roll)));
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double phi = Math.toRadians(spanDeg / 2.0 - spanDeg * t);
            Vector v = f.clone().multiply(radius * Math.cos(phi)).add(side.clone().multiply(radius * Math.sin(phi)));
            if (wave != 0) v.add(u.clone().multiply(wave * Math.sin(phi * 6.0)));
            pts.add(v);
        }
        return pts;
    }

    private List<Vector> shape(Arc arc, double radius, boolean crit, Vector f, Vector r, Vector u) {
        int n = crit ? 24 : 17;
        double span = crit ? 190 : 150;
        switch (arc) {
            case WIDE:
                return planar(0, span, radius, n, f, r, u, 0);
            case DIAG_DOWN:
                return planar(38, span, radius, n, f, r, u, 0);
            case DIAG_UP:
                return planar(-38, span, radius, n, f, r, u, 0);
            case OVERHEAD:
                return planar(90, crit ? 150 : 125, radius, n, f, r, u, 0);
            case CRESCENT: {
                List<Vector> pts = planar(8, span, radius, n, f, r, u, 0);
                pts.addAll(planar(8, span * 0.72, radius * 0.78, n - 5, f, r, u, 0));
                return pts;
            }
            case CROSS: {
                List<Vector> pts = planar(42, 130, radius, n - 3, f, r, u, 0);
                pts.addAll(planar(-42, 130, radius, n - 3, f, r, u, 0));
                return pts;
            }
            case WAVE:
                return planar(0, span, radius, n + 5, f, r, u, 0.28);
            case SPIRAL: {
                List<Vector> pts = new ArrayList<>(n + 6);
                for (int i = 0; i < n + 6; i++) {
                    double t = i / (double) (n + 5);
                    double a = t * Math.PI * 4.0;
                    double rad = 0.95 * (1.0 - 0.45 * t);
                    pts.add(f.clone().multiply(0.5 + radius * 1.2 * t)
                            .add(r.clone().multiply(Math.cos(a) * rad)).add(u.clone().multiply(Math.sin(a) * rad)));
                }
                return pts;
            }
            default: { // THRUST
                List<Vector> pts = new ArrayList<>(n + 8);
                for (int i = 0; i < n + 4; i++) {
                    double t = i / (double) (n + 3);
                    pts.add(f.clone().multiply(0.7 + (radius + 1.0) * t));
                }
                for (int i = 0; i < 8; i++) {            // ring at the tip
                    double a = i / 8.0 * Math.PI * 2.0;
                    pts.add(f.clone().multiply(radius + 1.7)
                            .add(r.clone().multiply(Math.cos(a) * 0.45)).add(u.clone().multiply(Math.sin(a) * 0.45)));
                }
                return pts;
            }
        }
    }

    private void swingArc(Player p, Style s, boolean crit, int level, int sweepLvl, ItemStack item) {
        lastArc.put(p.getUniqueId(), System.currentTimeMillis());
        Location eye = p.getEyeLocation();
        World w = p.getWorld();
        Vector f = eye.getDirection().normalize();
        Vector r = f.clone().crossProduct(UP);
        if (r.lengthSquared() < 1.0E-4) r = new Vector(1, 0, 0); else r.normalize();
        Vector u = r.clone().crossProduct(f).normalize();

        final Location origin = eye.clone().add(0, -0.45, 0).add(f.clone().multiply(0.2));
        double radius = (s.heavy() ? 2.9 : 2.6) + (crit ? 0.4 : 0) + (level >= 2 ? 0.25 : 0);
        final List<Vector> pts = shape(s.arc(), radius, crit, f, r, u);
        final int total = pts.size();
        final int steps = s.arc() == Arc.THRUST ? 3 : (s.heavy() ? 5 : 4);
        final float base = (sweepLvl >= 2 ? 1.0f : 0.8f) + (crit ? 0.3f : 0f);
        final boolean fancy = level >= 2;
        final boolean rich = sweepLvl >= 2;
        double density = Math.max(0.1, Math.min(1.5, plugin.getConfig().getDouble("visuals.sweep-density", 1.0)));
        final int stride = Math.max(1, (int) Math.round((rich ? 2 : 3) / density));

        // full sweeps also swing a ghost copy of the weapon's own model
        if (rich && plugin.getConfig().getBoolean("visuals.model-animations", true)) {
            plugin.displays().ghostSwing(p, item, crit);
        }

        // sound at the start of the swing; a deeper layer for criticals
        snd(w, origin, s.swingS(), crit ? 0.55f : 0.42f, crit ? s.swingP() * 0.88f : s.swingP());

        drawSlice(w, origin, pts, 0, total / steps, total, s, base, crit, fancy, stride, rich);
        new BukkitRunnable() {
            int k = 1;

            @Override
            public void run() {
                if (!p.isOnline() || k >= steps) {
                    cancel();
                    return;
                }
                int from = k * total / steps;
                int to = (k + 1) * total / steps;
                drawSlice(w, origin, pts, from, to, total, s, base, crit, fancy, stride, rich);
                if (k == steps - 1 && crit) snd(w, origin, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.25f, 1.7f);
                k++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void drawSlice(World w, Location origin, List<Vector> pts, int from, int to, int total,
                           Style s, float base, boolean crit, boolean fancy, int stride, boolean rich) {
        for (int i = from; i < to && i < total; i++) {
            if (i % stride != 0 && i != total - 1) continue;      // thinner arc: skip points
            Location l = origin.clone().add(pts.get(i));
            double t = i / (double) Math.max(1, total - 1);
            float size = (float) (base * (0.65 + 0.55 * Math.sin(Math.PI * t)));
            dot(w, l, s, size);
            if (rich && i % 4 == 0) w.spawnParticle(s.trail(), l, 1, 0.05, 0.05, 0.05, 0.01);
            if (rich && crit && i % 4 == 0) w.spawnParticle(Particle.CRIT, l, 2, 0.1, 0.1, 0.1, 0.2);
            if (fancy && i % 4 == 2) w.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
            if (i == total - 1 && rich) w.spawnParticle(Particle.END_ROD, l, 2, 0.08, 0.08, 0.08, 0.03);
        }
    }

    // ==================================================================
    // Hit effects
    // ==================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity v) || v instanceof ArmorStand) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        Weapon w = reg.of(item);
        if (w == null) return;
        Style s = style(w);

        long now = System.currentTimeMillis();
        Long last = lastHit.get(p.getUniqueId());
        if (last != null && now - last < 60) return;           // no duplicate bursts for one hit
        lastHit.put(p.getUniqueId(), now);

        boolean crit = isCrit(p);
        int level = reg.level(item);
        Location c = v.getLocation().add(0, v.getHeight() * 0.55, 0);
        Vector dir = v.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-6) dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-6) dir = new Vector(0, 0, 1);
        dir.normalize();

        impact(v, c, dir, s, crit, level);

        Long arc = lastArc.get(p.getUniqueId());
        int sweepLvl = sweepOf(w);
        if (sweepLvl > 0 && (arc == null || now - arc > 450)) swingArc(p, s, crit, level, sweepLvl, item);
    }

    private void impact(LivingEntity victim, Location c, Vector dir, Style s, boolean crit, int level) {
        World w = c.getWorld();
        float mult = crit ? 1.6f : 1.0f;

        // flash
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c, crit ? 10 : 5, 0.15, 0.15, 0.15, 0, transition(s, crit ? 1.6f : 1.1f));

        // slash mark across the target, tilted like the weapon's own arc
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        double tilt = s.arc() == Arc.DIAG_DOWN ? -0.7 : (s.arc() == Arc.DIAG_UP ? 0.7 : (s.arc() == Arc.OVERHEAD ? 4.0 : 0.0));
        for (int i = -3; i <= 3; i++) {
            Location l = c.clone().add(side.clone().multiply(i * 0.2 * (tilt > 3 ? 0.1 : 1.0)))
                    .add(0, i * 0.14 * (tilt > 3 ? 1.5 : tilt), 0);
            dot(w, l, s, 0.9f);
        }

        hitFx(w, c, s.hit(), mult, dir);

        if (level >= 2) w.spawnParticle(Particle.END_ROD, c, 6, 0.2, 0.2, 0.2, 0.08);

        if (crit) {
            w.spawnParticle(Particle.CRIT, c, 16, 0.3, 0.3, 0.3, 0.35);
            ring(w, c, 0.9, 10, s, 1.2f, Particle.ENCHANTED_HIT);
        }

        // ground impact for heavy weapons and criticals
        if (victim.isOnGround() && (crit || s.heavy())) groundShock(victim.getLocation(), crit ? 1.6 : 1.2);

        snd(w, c, s.hitS(), 0.5f, s.hitP());
        if (crit) snd(w, c, s.critS(), 0.45f, s.critP());
    }

    private void hitFx(World w, Location c, Hit hit, float m, Vector dir) {
        int n = (int) (10 * m);
        switch (hit) {
            case SPARKS:
                w.spawnParticle(Particle.CRIT, c, n, 0.2, 0.2, 0.2, 0.3);
                w.spawnParticle(Particle.ENCHANTED_HIT, c, n / 2, 0.25, 0.25, 0.25, 0.2);
                break;
            case FROST:
                w.spawnParticle(Particle.SNOWFLAKE, c, n + 4, 0.3, 0.3, 0.3, 0.05);
                w.spawnParticle(Particle.BLOCK, c, 8, 0.25, 0.25, 0.25, 0.1, Material.PACKED_ICE.createBlockData());
                break;
            case FIRE:
                w.spawnParticle(Particle.FLAME, c, n, 0.2, 0.2, 0.2, 0.06);
                w.spawnParticle(Particle.LAVA, c, 2, 0.2, 0.2, 0.2, 0);
                break;
            case LIGHTNING:
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, n + 4, 0.3, 0.3, 0.3, 0.4);
                break;
            case VOID:
                w.spawnParticle(Particle.PORTAL, c, n + 6, 0.35, 0.35, 0.35, 0.8);
                w.spawnParticle(Particle.REVERSE_PORTAL, c, 6, 0.3, 0.3, 0.3, 0.05);
                break;
            case SCULK:
                w.spawnParticle(Particle.SCULK_SOUL, c, 6, 0.25, 0.25, 0.25, 0.04);
                w.spawnParticle(Particle.SOUL, c, 4, 0.25, 0.25, 0.25, 0.03);
                break;
            case BLOOM:
                w.spawnParticle(Particle.CHERRY_LEAVES, c, n, 0.4, 0.4, 0.4, 0.02);
                w.spawnParticle(Particle.HAPPY_VILLAGER, c, 4, 0.3, 0.3, 0.3, 0);
                break;
            case WEB:
                w.spawnParticle(Particle.BLOCK, c, 8, 0.3, 0.3, 0.3, 0.05, Material.COBWEB.createBlockData());
                w.spawnParticle(Particle.WITCH, c, 4, 0.25, 0.25, 0.25, 0);
                break;
            case GRAVITY:
                w.spawnParticle(Particle.REVERSE_PORTAL, c, n + 4, 0.3, 0.1, 0.3, 0.05);
                w.spawnParticle(Particle.END_ROD, c, 4, 0.15, 0.1, 0.15, 0.1);
                break;
            case SOUL:
                w.spawnParticle(Particle.SOUL, c, 8, 0.25, 0.25, 0.25, 0.05);
                w.spawnParticle(Particle.SMOKE, c, 4, 0.2, 0.2, 0.2, 0.02);
                break;
            case GOLD:
                w.spawnParticle(Particle.WAX_ON, c, n, 0.3, 0.3, 0.3, 0);
                w.spawnParticle(Particle.FIREWORK, c, 4, 0.2, 0.2, 0.2, 0.05);
                break;
            case WIND:
                w.spawnParticle(Particle.CLOUD, c, n, 0.1, 0.1, 0.1, 0.12);
                break;
            case BLOOD:
                w.spawnParticle(Particle.BLOCK, c, n + 2, 0.25, 0.25, 0.25, 0.1, Material.REDSTONE_BLOCK.createBlockData());
                break;
            case EARTH:
                w.spawnParticle(Particle.BLOCK, c, n, 0.3, 0.3, 0.3, 0.1, Material.COBBLED_DEEPSLATE.createBlockData());
                break;
            case WATER:
                w.spawnParticle(Particle.SPLASH, c, n + 8, 0.35, 0.3, 0.35, 0.1);
                break;
            default: // SHIELD
                w.spawnParticle(Particle.CRIT, c, 6, 0.2, 0.2, 0.2, 0.25);
                w.spawnParticle(Particle.BLOCK, c, 6, 0.25, 0.25, 0.25, 0.05, Material.IRON_BLOCK.createBlockData());
                break;
        }
    }

    private void groundShock(Location at, double radius) {
        Block below = at.clone().subtract(0, 0.2, 0).getBlock();
        if (below.getType().isAir()) return;
        BlockData data = below.getBlockData();
        World w = at.getWorld();
        for (int i = 0; i < 12; i++) {
            double a = i / 12.0 * Math.PI * 2.0;
            Location l = at.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius);
            w.spawnParticle(Particle.BLOCK, l, 2, 0.1, 0.05, 0.1, 0.05, data);
        }
    }

    private void ring(World w, Location center, double radius, int points, Style s, float size, Particle extra) {
        for (int i = 0; i < points; i++) {
            double a = i / (double) points * Math.PI * 2.0;
            Location l = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            dot(w, l, s, size);
            if (extra != null && i % 3 == 0) w.spawnParticle(extra, l, 1, 0, 0, 0, 0);
        }
    }

    // ==================================================================
    // Ability visuals
    // ==================================================================

    /** Called right after an ability ran. 'from' is where the player stood before it ran. */
    public void ability(Player p, Weapon w, Weapon.Ability a, Location from) {
        Style s = style(w);
        World world = p.getWorld();
        Location at = p.getLocation();
        ItemStack held = p.getInventory().getItemInMainHand();
        int level = reg.level(held);
        float vol = 0.5f;
        final boolean models = plugin.getConfig().getBoolean("visuals.model-animations", true);

        switch (a.key()) {
            case "dash":
            case "flightdash":
            case "dashmacedash":
            case "grapplelaunch":
                snd(world, at, s.abilS(), vol, s.abilP());
                trail(p, s, 8);
                break;
            case "blink":
            case "vanishstep":
                portalLine(world, from, at, s);
                snd(world, from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
                snd(world, at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
                break;
            case "voidslash":
                beam(p, s, 10, false, Particle.END_ROD);
                if (models) plugin.displays().flyingModel(p, held, 8);
                snd(world, at, s.abilS(), vol, s.abilP());
                break;
            case "voidpull":
                beam(p, s, 10, true, Particle.PORTAL);
                snd(world, at, s.abilS(), vol, s.abilP() * 0.8f);
                break;
            case "lightning":
                lightningBeam(p, s);
                break;
            case "staticfield":
                expandingRing(at, 5.0, 8, s, Particle.ELECTRIC_SPARK);
                snd(world, at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 1.8f);
                break;
            case "frostnova":
                expandingRing(at, 6.0, 10, s, Particle.SNOWFLAKE);
                if (models) plugin.displays().iceSpikes(at, 6.0, 14);
                snd(world, at, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.2f);
                snd(world, at, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5f, 1.0f);
                break;
            case "gravitypulse":
                expandingRing(at, 8.0, 10, s, Particle.REVERSE_PORTAL);
                column(world, at, s, Particle.END_ROD, 6);
                if (models) plugin.displays().gravityOrbit(at.clone().add(0, 0.2, 0), 8, 40);
                snd(world, at, s.abilS(), 0.5f, s.abilP());
                break;
            case "bulwark":
                charge(p, s, 6);
                shieldRings(p, s);
                snd(world, at, Sound.ITEM_SHIELD_BLOCK, 0.6f, 0.8f);
                snd(world, at, Sound.BLOCK_ANVIL_LAND, 0.2f, 1.3f);
                break;
            case "windstep":
                spiralUp(p, s, Particle.CLOUD, 8);
                snd(world, at, s.abilS(), vol, s.abilP());
                break;
            case "berserk":
                charge(p, s, 6);
                expandingRing(at, 2.5, 6, s, Particle.CRIT);
                world.spawnParticle(Particle.ANGRY_VILLAGER, at.clone().add(0, 2.1, 0), 3, 0.3, 0.1, 0.3, 0);
                if (models) {
                    plugin.displays().orbitBlades(p, held, 3, 30);
                    plugin.displays().debris(at, Material.COBBLED_DEEPSLATE, 8, 2.5);
                }
                snd(world, at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5f, 0.6f);
                break;
            case "secondwind":
                spiralUp(p, s, Particle.HAPPY_VILLAGER, 10);
                world.spawnParticle(Particle.HEART, at.clone().add(0, 2, 0), 5, 0.4, 0.2, 0.4, 0);
                snd(world, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
                snd(world, at, Sound.ENTITY_PLAYER_LEVELUP, 0.25f, 1.7f);
                break;
            case "feast":
                spiralUp(p, s, Particle.FIREWORK, 8);
                snd(world, at, s.abilS(), 0.5f, s.abilP());
                break;
            case "impactbubble":
                expandingRing(at.clone().add(0, 0.9, 0), 2.5, 6, s, Particle.END_ROD);
                snd(world, at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.22f, 1.7f);
                break;
            case "royalreveal":
                expandingRing(at, 12.0, 12, s, Particle.WAX_ON);
                snd(world, at, s.abilS(), 0.6f, s.abilP());
                break;
            case "ravagerride":
                charge(p, s, 6);
                expandingRing(at, 4.0, 6, s, s.trail());
                if (models) plugin.displays().boneFangs(at, 4.0, 10);
                snd(world, at, s.abilS(), vol, s.abilP());
                break;
            case "minetoggle":
                break;
            default:   // relic and remaining abilities: weapon-coloured charge-up + burst
                charge(p, s, 6);
                expandingRing(at, 2.4, 5, s, s.trail());
                if (models && sweepOf(w) >= 2) plugin.displays().orbitBlades(p, held, 2, 16);
                snd(world, at, s.abilS(), vol, s.abilP());
                break;
        }

        if (level >= 2) {
            ring(world, at.clone().add(0, 0.1, 0), 1.5, 16, s, 1.4f, Particle.END_ROD);
            snd(world, at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 1.8f);
        }
    }

    /** Level II reached: rising helix and a soft level-up chime. */
    public void levelUp(Player p, Weapon w) {
        Style s = style(w);
        World world = p.getWorld();
        snd(world, p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        snd(world, p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.8f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= 20) {
                    if (p.isOnline()) world.spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 2.2, 0), 18, 0.3, 0.3, 0.3, 0.12);
                    cancel();
                    return;
                }
                Location base = p.getLocation();
                for (int k = 0; k < 2; k++) {
                    double a = t * 0.55 + k * Math.PI;
                    dot(world, base.clone().add(Math.cos(a) * 0.9, t * 0.11, Math.sin(a) * 0.9), s, 1.3f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- ability building blocks ----

    private void trail(Player p, Style s, int ticks) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    cancel();
                    return;
                }
                World w = p.getWorld();
                Location l = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.9 + i * 2.09;
                    dot(w, l.clone().add(Math.cos(a) * 0.5, Math.sin(a) * 0.5, Math.sin(a * 0.5) * 0.3), s, 1.2f);
                }
                w.spawnParticle(s.trail(), l, 2, 0.15, 0.2, 0.15, 0.02);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void portalLine(World w, Location from, Location to, Style s) {
        if (from == null || from.getWorld() != to.getWorld()) return;
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.5 || len > 40) return;
        d.normalize();
        int n = (int) Math.min(24, Math.max(6, len * 3));
        for (int i = 0; i <= n; i++) {
            Location l = from.clone().add(d.clone().multiply(len * i / n)).add(0, 1, 0);
            if (i % 2 == 0) w.spawnParticle(Particle.REVERSE_PORTAL, l, 2, 0.1, 0.2, 0.1, 0.02);
            else dot(w, l, s, 1.0f);
        }
        ring(w, from.clone().add(0, 0.1, 0), 0.9, 10, s, 1.2f, Particle.PORTAL);
        ring(w, to.clone().add(0, 0.1, 0), 0.9, 10, s, 1.2f, Particle.PORTAL);
    }

    private void beam(Player p, Style s, double range, boolean pull, Particle accent) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();
        RayTraceResult hit = w.rayTraceBlocks(eye, dir, range);
        double len = hit == null ? range : Math.max(1.0, hit.getHitPosition().distance(eye.toVector()));
        final Location start = eye.clone().add(0, -0.3, 0);
        final int n = (int) Math.max(4, len * 2);
        for (int i = 1; i <= n; i++) {
            Location l = start.clone().add(dir.clone().multiply(len * i / n));
            dot(w, l, s, 1.0f);
        }
        // a bright head runs along the beam (outwards, or back to the player when pulling)
        final double fl = len;
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 4) {
                    cancel();
                    return;
                }
                double d = pull ? fl * (1.0 - (t + 1) / 4.0) : fl * (t + 1) / 4.0;
                Location l = start.clone().add(dir.clone().multiply(d));
                w.spawnParticle(accent, l, 5, 0.12, 0.12, 0.12, 0.03);
                dot(w, l, s, 1.6f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void lightningBeam(Player p, Style s) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();
        RayTraceResult hit = w.rayTraceBlocks(eye, dir, 25);
        Vector end = hit == null ? eye.toVector().add(dir.clone().multiply(25)) : hit.getHitPosition();
        Location target = end.toLocation(w);
        Vector d = end.clone().subtract(eye.toVector());
        double len = d.length();
        if (len < 1) return;
        d.normalize();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = (int) Math.min(40, len * 2);
        for (int i = 1; i <= n; i++) {
            Location l = eye.clone().add(d.clone().multiply(len * i / n)).add(0, -0.3, 0);
            l.add((rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
            w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0, 0, 0, 0);
        }
        for (int y = 0; y < 10; y++) {
            w.spawnParticle(Particle.ELECTRIC_SPARK, target.clone().add(0, y * 0.8, 0), 2, 0.1, 0.1, 0.1, 0.02);
        }
        ring(w, target.clone().add(0, 0.1, 0), 1.5, 14, s, 1.3f, Particle.ELECTRIC_SPARK);
        if (plugin.getConfig().getBoolean("visuals.model-animations", true)) plugin.displays().lightningColumn(target);
        snd(w, target, Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.4f);
    }

    private void expandingRing(Location center, double maxR, int ticks, Style s, Particle extra) {
        final Location c = center.clone();
        World w = c.getWorld();
        new BukkitRunnable() {
            int t = 1;

            @Override
            public void run() {
                if (t > ticks) {
                    cancel();
                    return;
                }
                double r = maxR * t / ticks;
                int points = (int) Math.min(36, Math.max(10, r * 7));
                ring(w, c.clone().add(0, 0.15, 0), r, points, s, 1.2f, extra);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void column(World w, Location at, Style s, Particle particle, int height) {
        for (int y = 0; y < height; y++) {
            w.spawnParticle(particle, at.clone().add(0, y * 0.5, 0), 2, 0.3, 0.05, 0.3, 0.02);
        }
    }

    /** Motes that spiral in towards the player (a quick charge-up). */
    private void charge(Player p, Style s, int ticks) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    if (p.isOnline()) p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.05);
                    cancel();
                    return;
                }
                World w = p.getWorld();
                Location base = p.getLocation().add(0, 0.2 + t * 0.25, 0);
                double rad = 1.7 * (1.0 - t / (double) ticks) + 0.3;
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.8 + i * 2.09;
                    dot(w, base.clone().add(Math.cos(a) * rad, 0, Math.sin(a) * rad), s, 1.1f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void shieldRings(Player p, Style s) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= 8) {
                    cancel();
                    return;
                }
                World w = p.getWorld();
                Location base = p.getLocation();
                for (int ring = 0; ring < 3; ring++) {
                    double y = 0.3 + ring * 0.7;
                    for (int i = 0; i < 6; i++) {
                        double a = t * 0.4 + i * Math.PI / 3 + ring * 0.5;
                        dot(w, base.clone().add(Math.cos(a) * 1.1, y, Math.sin(a) * 1.1), s, 1.0f);
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spiralUp(Player p, Style s, Particle particle, int ticks) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    cancel();
                    return;
                }
                World w = p.getWorld();
                Location base = p.getLocation();
                for (int i = 0; i < 2; i++) {
                    double a = t * 0.7 + i * Math.PI;
                    Location l = base.clone().add(Math.cos(a) * 0.8, t * 0.22, Math.sin(a) * 0.8);
                    dot(w, l, s, 1.1f);
                    w.spawnParticle(particle, l, 1, 0.05, 0.05, 0.05, 0.01);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastSwing.remove(id);
        lastArc.remove(id);
        lastHit.remove(id);
    }
}
