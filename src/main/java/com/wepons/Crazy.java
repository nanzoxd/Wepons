package com.wepons;

import com.wepons.Weapon.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * The nine Crazy Mythicals: Hourglass Dagger, Great Hammer, The Judge, Ghost Shield, Dragon Cannon, Cleaver,
 * Burgblade, Thornblade and Soul Staff. All numbers are in config.yml under "crazy:".
 *
 * Also owns the generic "protected" state (a protected player takes no damage but can still attack): the Ghost
 * Shield uses it, and so does the Impact Sword's glass box.
 *
 * Projectiles are display entities moved by one task each (see {@link Shot}); nothing here spawns a real projectile,
 * so nothing can be picked up, hit by other plugins' projectile events or left behind in the world.
 */
public final class Crazy implements Listener {

    /** Called when a shot ends. hit == null means it hit a wall or ran out of range. Return true to end the shot. */
    private interface Impact {
        boolean on(Shot shot, Location at, LivingEntity hit);
    }

    private record Wagon(Shot shot, LivingEntity[] carried) {}

    private record Burger(UUID owner, long until) {}

    /** Item models face "up"; this turns a blade so it points along the direction of flight. */
    private static Quaternionf pointForward() {
        return new Quaternionf().rotateX((float) Math.toRadians(90));
    }

    private final Wepons plugin;
    private final WeaponRegistry reg;

    private final Map<UUID, Long> protectUntil = new HashMap<>();
    private final Map<UUID, Long> lastDeflect = new HashMap<>();
    private final Set<UUID> silent = new HashSet<>();
    private final Map<UUID, Integer> ghostHits = new HashMap<>();
    private final Map<UUID, Integer> thornHits = new HashMap<>();
    private final Set<UUID> thornCharged = new HashSet<>();
    private final Map<UUID, Integer> souls = new HashMap<>();
    private final Map<UUID, Long> ultArmedUntil = new HashMap<>();
    private final Map<UUID, Long> noAbsorbUntil = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private final Map<UUID, Wagon> wagons = new HashMap<>();
    private final Map<UUID, Burger> burgers = new HashMap<>();       // victim -> burger
    private final Map<UUID, UUID> allies = new HashMap<>();          // mob -> owner
    private final Set<UUID> riders = new HashSet<>();
    private final Set<Shot> shots = new HashSet<>();

    public Crazy(Wepons plugin) {
        this.plugin = plugin;
        this.reg = plugin.registry();
    }

    // ==================================================================
    // lifecycle
    // ==================================================================

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            protectUntil.entrySet().removeIf(en -> en.getValue() <= now);
            ultArmedUntil.entrySet().removeIf(en -> en.getValue() <= now);
            noFallUntil.entrySet().removeIf(en -> en.getValue() <= now);
            // the curse: no Absorption
            noAbsorbUntil.entrySet().removeIf(en -> {
                Entity ent = Bukkit.getEntity(en.getKey());
                if (en.getValue() > now && ent instanceof LivingEntity le && le.isValid()) {
                    le.setAbsorptionAmount(0);
                    le.removePotionEffect(PotionEffectType.ABSORPTION);
                    return false;
                }
                return true;
            });
        }, 5L, 5L);

        // allied mobs (Dragon Cannon bite) hunt the nearest enemy of their owner
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, UUID> en : new ArrayList<>(allies.entrySet())) {
                Entity ent = Bukkit.getEntity(en.getKey());
                Player owner = Bukkit.getPlayer(en.getValue());
                if (!(ent instanceof Mob m) || !m.isValid() || owner == null || !owner.isOnline()) {
                    allies.remove(en.getKey());
                    continue;
                }
                LivingEntity best = null;
                double bd = 18 * 18;
                for (LivingEntity le : enemies(owner, m.getLocation(), 18)) {
                    if (le == m || !(le instanceof Player || le instanceof org.bukkit.entity.Monster)) continue;
                    double d = le.getLocation().distanceSquared(m.getLocation());
                    if (d < bd) {
                        bd = d;
                        best = le;
                    }
                }
                m.setTarget(best);
            }
        }, 20L, 20L);
    }

    /** Plugin disable: give flying riders their gravity back and stop every shot. */
    public void shutdown() {
        for (UUID id : new HashSet<>(riders)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setGravity(true);
        }
        riders.clear();
        for (Shot s : new ArrayList<>(shots)) s.end();
        shots.clear();
        wagons.clear();
        burgers.clear();
        allies.clear();
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private double num(String path, double def) {
        return plugin.getConfig().getDouble("crazy." + path, def);
    }

    private boolean fxOn() {
        return plugin.getConfig().getBoolean("visuals.model-animations", true);
    }

    public boolean isSilent(Player p) {
        return silent.contains(p.getUniqueId());
    }

    public boolean isProtected(Player p) {
        Long end = protectUntil.get(p.getUniqueId());
        return end != null && end > System.currentTimeMillis();
    }

    /** The player takes no damage for 'ms' milliseconds (attacking is still allowed). */
    public void protect(Player p, long ms) {
        long end = System.currentTimeMillis() + ms;
        protectUntil.merge(p.getUniqueId(), end, Math::max);
    }

    public void unprotect(Player p) {
        protectUntil.remove(p.getUniqueId());
    }

    private List<LivingEntity> enemies(Player owner, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        Vector cv = c.toVector();
        for (Entity en : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (!(en instanceof LivingEntity le) || le == owner || le instanceof ArmorStand || le.isDead() || !le.isValid()) continue;
            if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) continue;
            if (owner != null && owner.getUniqueId().equals(allies.get(le.getUniqueId()))) continue;
            if (le.getBoundingBox().getCenter().distanceSquared(cv) > r * r) continue;
            out.add(le);
        }
        return out;
    }

    /** Damage that ignores armor (config trueDamage: false makes it normal damage). Never re-triggers weapon procs. */
    private void trueDamage(Player owner, LivingEntity v, double amount) {
        if (v.isDead() || !v.isValid()) return;
        silent.add(owner.getUniqueId());
        try {
            if (!plugin.getConfig().getBoolean("trueDamage", true)) {
                v.damage(amount, owner);
                return;
            }
            v.damage(0.01, owner);    // hurt animation, kill credit and protection checks
        } finally {
            silent.remove(owner.getUniqueId());
        }
        if (v.isDead() || (v instanceof Player pl && isProtected(pl))) return;
        v.setHealth(Math.max(0.0, v.getHealth() - amount));
    }

    /** Ordinary damage (armor applies) without re-triggering procs. */
    private void plainDamage(Player owner, LivingEntity v, double amount) {
        silent.add(owner.getUniqueId());
        try {
            v.damage(amount, owner);
        } finally {
            silent.remove(owner.getUniqueId());
        }
    }

    private void knock(Location from, LivingEntity t, double strength, double up) {
        Vector v = t.getLocation().toVector().subtract(from.toVector());
        v.setY(0);
        if (v.lengthSquared() < 1.0E-6) v = new Vector(ThreadLocalRandom.current().nextDouble() - 0.5, 0, ThreadLocalRandom.current().nextDouble() - 0.5);
        t.setVelocity(v.normalize().multiply(strength).setY(up));
    }

    private void stunShield(LivingEntity v, int ticks) {
        if (v instanceof Player vp && (vp.isBlocking() || vp.getCooldown(Material.SHIELD) > 0)) {
            vp.setCooldown(Material.SHIELD, ticks);
            vp.getWorld().playSound(vp.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.9f);
            vp.sendActionBar(Component.text("Your shield is stunned", NamedTextColor.RED));
        }
    }

    private Location muzzle(Player p) {
        return p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.8)).subtract(0, 0.25, 0);
    }

    private Location hand(Player p) {
        return p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6)).subtract(0, 0.4, 0);
    }

    private static void sound(World w, Location at, String key, float vol, float pitch) {
        w.playSound(at, key, vol, pitch);   // resource-pack sound (silent without the pack)
    }

    private void noFall(Player p, long ms) {
        noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    // ==================================================================
    // projectile engine
    // ==================================================================

    /** One flying display entity. Moves in small steps (no tunnelling), stops on blocks and entities. */
    private final class Shot extends BukkitRunnable {
        final Player owner;
        final ItemDisplay disp;
        final Vector vel;
        final double gravity;
        final double radius;
        final int maxTicks;
        final Impact cb;
        final Particle trail;
        final float spin;
        final char axis;
        final Quaternionf base;
        final float scale;
        final Set<UUID> ignore = new HashSet<>();
        final Location pos;
        Consumer<Shot> perTick;
        boolean phase;      // flies through everything (a hammer coming back)
        boolean done;
        int ticks;
        float angle;

        Shot(Player owner, ItemStack model, float scale, Quaternionf base, char axis, float spin, Location from, Vector vel,
             double gravity, double radius, int maxTicks, Particle trail, Impact cb) {
            this.owner = owner;
            this.vel = vel;
            this.gravity = gravity;
            this.radius = radius;
            this.maxTicks = maxTicks;
            this.cb = cb;
            this.trail = trail;
            this.spin = spin;
            this.axis = axis;
            this.base = base;
            this.scale = scale;
            this.pos = from.clone();
            this.disp = plugin.customFx().item(from, model, scale);
            this.disp.setTeleportDuration(1);
            shots.add(this);
        }

        private LivingEntity firstHit() {
            for (Entity en : pos.getWorld().getNearbyEntities(pos, radius, radius, radius)) {
                if (!(en instanceof LivingEntity le) || le == owner || le instanceof ArmorStand || le.isDead() || !le.isValid()) continue;
                if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) continue;
                if (ignore.contains(le.getUniqueId())) continue;
                if (owner.getUniqueId().equals(allies.get(le.getUniqueId()))) continue;
                return le;
            }
            return null;
        }

        void end() {
            if (done) return;
            done = true;
            shots.remove(this);
            try {
                cancel();
            } catch (IllegalStateException ignored) {
                // never scheduled
            }
            plugin.customFx().kill(disp);
        }

        /** Ends the shot right now as if it had hit a wall. */
        void detonate() {
            if (done) return;
            cb.on(this, pos.clone(), null);
            end();
        }

        @Override
        public void run() {
            if (done) {
                cancel();
                return;
            }
            if (!owner.isOnline()) {
                end();
                return;
            }
            int steps = Math.max(1, (int) Math.ceil(vel.length() / 0.4));
            Vector step = vel.clone().multiply(1.0 / steps);
            for (int i = 0; i < steps; i++) {
                pos.add(step);
                if (phase) continue;
                if (!pos.getBlock().isPassable()) {
                    pos.subtract(step);
                    cb.on(this, pos.clone(), null);
                    end();
                    return;
                }
                LivingEntity hit = firstHit();
                if (hit != null) {
                    ignore.add(hit.getUniqueId());
                    if (cb.on(this, pos.clone(), hit)) {
                        end();
                        return;
                    }
                }
            }
            vel.setY(vel.getY() - gravity);
            if (trail != null) pos.getWorld().spawnParticle(trail, pos, 2, 0.05, 0.05, 0.05, 0.0);
            if (perTick != null) perTick.accept(this);
            if (done) return;

            Location at = pos.clone();
            if (vel.lengthSquared() > 1.0E-6) at.setDirection(vel);
            disp.teleport(at);
            angle += spin;
            Quaternionf r = axis == 'y'
                    ? new Quaternionf().rotateY((float) Math.toRadians(angle))
                    : new Quaternionf().rotateX((float) Math.toRadians(angle));
            plugin.customFx().morph(disp, 1, new Transformation(new Vector3f(), new Quaternionf(base),
                    new Vector3f(scale, scale, scale), r));

            if (++ticks >= maxTicks) {
                cb.on(this, pos.clone(), null);
                end();
            }
        }
    }

    private Shot launch(Player owner, ItemStack model, float scale, Quaternionf base, char axis, float spin, Location from,
                        Vector vel, double gravity, double radius, int maxTicks, Particle trail, Impact cb) {
        Shot s = new Shot(owner, model, scale, base, axis, spin, from, vel, gravity, radius, maxTicks, trail, cb);
        s.runTaskTimer(plugin, 1L, 1L);
        return s;
    }

    /** A weapon flying back to its owner (visual only, flies through everything). */
    private void flyBack(Player p, Location from, ItemStack model, float scale, char axis, float spin) {
        Shot s = launch(p, model, scale, pointForward(), axis, spin, from, new Vector(0, 0, 0), 0, 0.1, 45, null,
                (sh, at, hit) -> true);
        s.phase = true;
        s.perTick = sh -> {
            Location target = p.getLocation().add(0, 1.2, 0);
            Vector to = target.toVector().subtract(sh.pos.toVector());
            if (to.lengthSquared() < 2.25) {
                sh.end();
                return;
            }
            sh.vel.copy(to.normalize().multiply(1.8));
        };
    }

    // ==================================================================
    // run / preempt
    // ==================================================================

    /** Pressing the Cleaver's wagon key again explodes the wagon by hand. */
    public boolean preempt(Player p, Weapon w, Ability a) {
        if (a.key().equals("cleaverwagon")) {
            Wagon wagon = wagons.get(p.getUniqueId());
            if (wagon != null) {
                wagon.shot().detonate();
                return true;
            }
        }
        return false;
    }

    public boolean run(Player p, Weapon w, Ability a) {
        return switch (a.key()) {
            case "timewarp" -> timeWarp(p);
            case "hourglassthrow" -> hourglassThrow(p);
            case "hammerleap" -> hammerLeap(p);
            case "hammerthrow" -> hammerThrow(p);
            case "judgethrow" -> judgeThrow(p);
            case "judgerain" -> judgeRain(p);
            case "ghostform" -> ghostForm(p);
            case "ghostthrow" -> ghostThrow(p);
            case "dragonshot" -> dragonShot(p);
            case "dragonride" -> dragonRide(p);
            case "cleaverswing" -> cleaverSwing(p);
            case "cleaverwagon" -> cleaverWagon(p);
            case "burgify" -> burgify(p);
            case "burgershot" -> burgerShot(p);
            case "thornspike" -> thornSpike(p);
            case "thornvines" -> thornVines(p);
            case "soulseye" -> soulEye(p);
            case "soulsand" -> soulSand(p);
            default -> false;
        };
    }

    // ==================================================================
    // Hourglass Dagger
    // ==================================================================

    private boolean timeWarp(Player p) {
        int ticks = (int) (num("hourglass.warp-seconds", 15) * 20);
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, 9, false, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, plugin.getConfig().getInt("crazy.hourglass.speed-amplifier", 2), false, true, true));
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        sound(w, p.getLocation(), "cm:time", 1f, 1f);
        w.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.5, 0.9, 0.5, 0.15);
        if (fxOn()) {
            CustomFX fx = plugin.customFx();
            fx.followItem(p, CustomFX.vfx("hourglass"), 0.9f, 2.5, ticks, 14f, () -> p.isOnline() && !p.isDead());
            fx.orbit(p, new Material[]{Material.YELLOW_STAINED_GLASS, Material.BLACK_CONCRETE, Material.ORANGE_STAINED_GLASS, Material.WHITE_CONCRETE},
                    8, 1.3, 0.2f, ticks, 7.0, 1.0);
            plugin.displays().groundRing(p.getLocation(), 3.0, Material.YELLOW_CONCRETE, 24, 6, 20);
        }
        return true;
    }

    private boolean hourglassThrow(Player p) {
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        double power = num("hourglass.throw-power", 2) * 0.75;
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 1.2f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
        launch(p, CustomFX.vfx("hourglass"), 1.4f, new Quaternionf(), 'y', 30f, from, dir.clone().multiply(power),
                num("hourglass.gravity", 0.025), 1.0, 45, Particle.END_ROD, (sh, at, hit) -> {
                    freezeField(p, at);
                    return true;
                });
        return true;
    }

    private void freezeField(Player p, Location at) {
        World w = at.getWorld();
        double radius = num("hourglass.radius", 5);
        int ticks = (int) (num("hourglass.freeze-seconds", 3) * 20);
        w.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
        w.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
        sound(w, at, "cm:time", 1f, 0.8f);
        w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 60, radius / 3, 0.8, radius / 3, 0.1);
        CustomFX fx = plugin.customFx();
        if (fxOn()) {
            fx.clockCircle(at, radius, ticks + 20, CustomFX.TIME_CLOCK, CustomFX.vfx("hourglass"), 2.2f);
            plugin.displays().tumbleCubes(at.clone().add(0, 1, 0), Material.YELLOW_STAINED_GLASS, 5, 1.2f, 20);
        }
        for (LivingEntity le : enemies(p, at.clone().add(0, 1, 0), radius)) {
            plugin.abilities().root(le, ticks);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, true));
            if (le instanceof Player vp) vp.sendActionBar(Component.text("Frozen in time", NamedTextColor.GOLD));
            if (fxOn()) {
                fx.shell(le, Material.YELLOW_STAINED_GLASS, null, 1.2f, 2.3f, 1.2f, ticks, false, 0.95f);
                fx.chains(le, ticks);
            }
        }
    }

    // ==================================================================
    // Great Hammer
    // ==================================================================

    private boolean hammerThrow(Player p) {
        ItemStack model = reg.create("great_hammer");
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 0.7f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        launch(p, model, 1.7f, pointForward(), 'x', 40f, from, dir.clone().multiply(1.7), 0.01, 1.2, 22, Particle.CRIT, (sh, at, hit) -> {
            World w = at.getWorld();
            if (hit != null) {
                trueDamage(p, hit, num("hammer.throw-damage", 8));
                stunShield(hit, (int) (num("hammer.shield-stun-seconds", 5) * 20));
                Vector pull = p.getLocation().toVector().subtract(hit.getLocation().toVector());
                if (pull.lengthSquared() > 1.0E-6) hit.setVelocity(pull.normalize().multiply(1.5).setY(0.35));
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                w.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
                if (fxOn()) {
                    plugin.displays().debris(at, new Material[]{Material.GREEN_CONCRETE, Material.MOSS_BLOCK, Material.EMERALD_BLOCK}, 8, 2.0);
                    plugin.displays().tumbleCubes(at.clone().add(0, 1, 0), Material.LIME_STAINED_GLASS, 3, 1.1f, 16);
                }
                return true;
            }
            // miss: it flies back and the cooldown resets
            plugin.abilities().resetCooldown(p, "great_hammer", "hammerthrow");
            p.sendActionBar(Component.text("Hammer returned: cooldown reset", NamedTextColor.GREEN));
            flyBack(p, at, model, 1.7f, 'x', 40f);
            return true;
        });
        return true;
    }

    private boolean hammerLeap(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 1);
        p.setVelocity(dir.normalize().multiply(1.25).setY(0.95));
        noFall(p, 8000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 0.7f);
        w.spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.5, 0.1, 0.5, 0.05);

        final int spinTicks = (int) (num("hammer.spin-seconds", 3) * 20);
        final double spinDamage = num("hammer.spin-damage", 3);
        final double landDamage = num("hammer.land-damage", 6);
        final double landRadius = num("hammer.land-radius", 4.5);

        // three hammers spin around the player
        final CustomFX fx = plugin.customFx();
        final List<ItemDisplay> hammers = new ArrayList<>();
        if (fxOn()) {
            ItemStack model = reg.create("great_hammer");
            for (int i = 0; i < 3; i++) hammers.add(fx.item(p.getLocation().add(0, 1, 0), model, 1.6f));
        }
        new BukkitRunnable() {
            int t = 0;
            boolean landed = false;
            final Map<UUID, Integer> lastHit = new HashMap<>();

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= spinTicks) {
                    fx.collapse(hammers, 5);
                    cancel();
                    return;
                }
                Location o = p.getLocation().add(0, 1.0, 0);
                for (int i = 0; i < hammers.size(); i++) {
                    double a = Math.toRadians(t * 24.0 + i * 120.0);
                    ItemDisplay h = hammers.get(i);
                    Location at = o.clone().add(Math.cos(a) * 2.0, 0.3 * Math.sin(t * 0.3 + i), Math.sin(a) * 2.0);
                    at.setYaw((float) Math.toDegrees(-a));
                    h.teleport(at);
                    if (t % 2 == 0) {
                        fx.morph(h, 2, new Transformation(new Vector3f(), new Quaternionf(pointForward()), new Vector3f(1.6f, 1.6f, 1.6f),
                                new Quaternionf().rotateX((float) Math.toRadians(t * 40.0))));
                    }
                }
                if (t % 5 == 0) {
                    for (LivingEntity le : enemies(p, o, 2.7)) {
                        Integer last = lastHit.get(le.getUniqueId());
                        if (last != null && t - last < 10) continue;
                        lastHit.put(le.getUniqueId(), t);
                        plainDamage(p, le, spinDamage);
                        knock(p.getLocation(), le, 0.7, 0.3);
                        w.spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.2);
                    }
                }
                // landing: the impact
                if (!landed && t >= 5 && p.getVelocity().getY() <= 0.05
                        && !p.getLocation().subtract(0, 0.15, 0).getBlock().isPassable()) {
                    landed = true;
                    Location at = p.getLocation();
                    w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                    w.playSound(at, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
                    w.spawnParticle(Particle.EXPLOSION, at, 3, 1.0, 0.1, 1.0, 0);
                    for (LivingEntity le : enemies(p, at, landRadius)) {
                        trueDamage(p, le, landDamage);
                        stunShield(le, (int) (num("hammer.shield-stun-seconds", 5) * 20));
                        knock(at, le, 1.1, 0.5);
                    }
                    if (fxOn()) {
                        plugin.displays().groundRing(at, landRadius, Material.LIME_CONCRETE, 32, 14, 26);
                        plugin.displays().debris(at, new Material[]{Material.GREEN_CONCRETE, Material.MOSS_BLOCK, Material.DEEPSLATE}, 14, 3.0);
                        plugin.displays().tumbleCubes(at.clone().add(0, 0.6, 0), Material.LIME_STAINED_GLASS, 5, 1.3f, 22);
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ==================================================================
    // The Judge
    // ==================================================================

    private boolean judgeThrow(Player p) {
        ItemStack model = reg.create("judge");
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 1.0f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
        launch(p, model, 2.0f, pointForward(), 'y', 0f, from, dir.clone().multiply(2.4), 0.02, 1.0, 30, Particle.ELECTRIC_SPARK, (sh, at, hit) -> {
            World w = at.getWorld();
            w.playSound(at, Sound.ITEM_TRIDENT_HIT, 1f, 0.7f);
            w.spawnParticle(Particle.ELECTRIC_SPARK, at, 30, 0.4, 0.4, 0.4, 0.3);
            if (hit != null) {
                trueDamage(p, hit, num("judge.throw-damage", 6));
                stunShield(hit, (int) (num("judge.shield-stun-seconds", 5) * 20));
                w.strikeLightningEffect(hit.getLocation());
                if (fxOn()) plugin.displays().tumbleCubes(at.clone().add(0, 1, 0), Material.CYAN_STAINED_GLASS, 4, 1.1f, 16);
                return true;
            }
            flyBack(p, at, model, 2.0f, 'y', 0f);
            return true;
        });
        return true;
    }

    private boolean judgeRain(Player p) {
        final Location c = p.getLocation().clone();
        final World w = c.getWorld();
        final double radius = num("judge.rain-radius", 10);
        final double damage = num("judge.rain-damage", 6);
        final ItemStack model = reg.create("judge");
        final CustomFX fx = plugin.customFx();
        final int count = 26;

        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
        w.playSound(c, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.0f);
        if (fxOn()) {
            fx.orbit(p, new Material[]{Material.CYAN_STAINED_GLASS, Material.PRISMARINE, Material.LIGHT_BLUE_STAINED_GLASS, Material.DARK_PRISMARINE},
                    6, 1.7, 0.3f, 70, 9.0, 3.6);
            plugin.displays().groundRing(c, Math.min(radius, 6.0), Material.CYAN_CONCRETE, 30, 10, 60);
        }
        final Map<UUID, Integer> lastHit = new HashMap<>();
        for (int i = 0; i < count; i++) {
            final int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double ang = r.nextDouble(Math.PI * 2);
                double dist = radius * Math.sqrt(r.nextDouble());
                Location ground = new Location(w, c.getX() + Math.cos(ang) * dist, c.getY(), c.getZ() + Math.sin(ang) * dist);
                for (int up = 0; up < 4 && !ground.getBlock().isPassable(); up++) ground.add(0, 1, 0);
                final Location start = ground.clone().add(0, 12, 0);
                final ItemDisplay d = fx.item(start, model, 2.2f);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) Math.PI), new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
                fx.morphLater(d, 1L, 6, new Transformation(new Vector3f(0, -12f, 0), new Quaternionf().rotateX((float) Math.PI), new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    w.playSound(ground, Sound.ITEM_TRIDENT_HIT_GROUND, 0.7f, 0.8f);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, ground, 18, 0.4, 0.2, 0.4, 0.25);
                    if (n % 4 == 0) w.strikeLightningEffect(ground);
                    for (LivingEntity le : enemies(p, ground.clone().add(0, 1, 0), 1.9)) {
                        Integer last = lastHit.get(le.getUniqueId());
                        if (last != null && n - last < 4) continue;
                        lastHit.put(le.getUniqueId(), n);
                        trueDamage(p, le, damage);
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> fx.collapse(List.of(d), 5), 8L);
                }, 8L);
            }, 6L + i * 2L);
        }
        return true;
    }

    // ==================================================================
    // Ghost Shield
    // ==================================================================

    private void ghost(Player p, int ticks) {
        protect(p, ticks * 50L);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 0.5f);
        w.spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 30, 0.5, 0.9, 0.5, 0.05);
        if (fxOn()) {
            CustomFX fx = plugin.customFx();
            fx.shell(p, Material.WHITE_STAINED_GLASS, null, 1.5f, 2.4f, 1.5f, ticks, false, 0.9f);
            fx.orbit(p, new Material[]{Material.LIGHT_GRAY_STAINED_GLASS, Material.GRAY_CONCRETE}, 6, 1.1, 0.22f, ticks, 8.0, 1.0);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= ticks) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 2, 0.4, 0.8, 0.4, 0.01);
                t += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private boolean ghostForm(Player p) {
        ghost(p, (int) (num("ghost.form-seconds", 10) * 20));
        p.sendActionBar(Component.text("Ghost Form: nothing can hurt you", NamedTextColor.GRAY));
        return true;
    }

    private boolean ghostThrow(Player p) {
        ItemStack model = reg.create("ghost_shield");
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 1.4f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1.5f);
        launch(p, model, 1.8f, new Quaternionf(), 'y', 35f, from, dir.clone().multiply(1.7), 0.01, 1.1, 30, Particle.SOUL, (sh, at, hit) -> {
            World w = at.getWorld();
            w.spawnParticle(Particle.SOUL, at, 25, 0.4, 0.4, 0.4, 0.05);
            if (hit != null) {
                trueDamage(p, hit, num("ghost.throw-damage", 8));
                hit.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
                w.playSound(at, Sound.ITEM_SHIELD_BREAK, 1f, 0.6f);
                if (fxOn()) plugin.displays().tumbleCubes(at.clone().add(0, 1, 0), Material.WHITE_STAINED_GLASS, 4, 1.0f, 16);
                return true;
            }
            flyBack(p, at, model, 1.8f, 'y', 35f);
            return true;
        });
        return true;
    }

    // ==================================================================
    // Dragon Cannon
    // ==================================================================

    /** A big blast: damage with falloff, knockback, cubes and a ground ring. No block damage unless configured. */
    private void blast(Player owner, Location at, double radius, double damage, Material glass, Material[] chunks, Material tile) {
        World w = at.getWorld();
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
        w.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 0.8f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.DRAGON_BREATH, at, 60, radius / 2, 0.5, radius / 2, 0.05);
        for (LivingEntity le : enemies(owner, at.clone().add(0, 0.5, 0), radius)) {
            double d = le.getLocation().distance(at);
            trueDamage(owner, le, Math.max(2.0, damage * (1.0 - d / (radius * 1.4))));
            knock(at, le, 1.2, 0.6);
        }
        if (plugin.getConfig().getBoolean("crazy.dragon.explosion-breaks-blocks", false)) {
            w.createExplosion(at, (float) radius, false, true, owner);
        }
        if (fxOn()) {
            plugin.displays().tumbleCubes(at.clone().add(0, 0.8, 0), glass, 6, 1.5f, 24);
            plugin.displays().debris(at, chunks, 16, 3.2);
            plugin.displays().groundRing(at, radius, tile, 34, 16, 28);
        }
    }

    private boolean dragonShot(Player p) {
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        final double power = num("dragon.explosion-power", 4);
        final double damage = num("dragon.bite-damage", 6);
        sound(p.getWorld(), p.getLocation(), "cm:spawn", 1f, 1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.3f);
        launch(p, new ItemStack(Material.DRAGON_HEAD), 2.2f, new Quaternionf(), 'y', 0f, from, dir.clone().multiply(1.5), 0.0, 1.3, 40,
                Particle.DRAGON_BREATH, (sh, at, hit) -> {
                    if (hit == null) {   // wall, ground or out of range
                        blast(p, at, power, damage, Material.PURPLE_STAINED_GLASS,
                                new Material[]{Material.PURPLE_CONCRETE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN}, Material.PURPLE_CONCRETE);
                        return true;
                    }
                    sound(at.getWorld(), at, "cm:bite", 1f, 1f);
                    at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                    if (fxOn()) plugin.customFx().bite(hit.getLocation());
                    trueDamage(p, hit, damage);
                    if (hit instanceof Player vp) {
                        int ticks = (int) (num("dragon.stun-seconds", 10) * 20);
                        List<String> names = plugin.getConfig().getStringList("crazy.dragon.disabled-items");
                        if (names.isEmpty()) names = List.of("GOLDEN_APPLE", "COBWEB", "WATER_BUCKET", "EXPERIENCE_BOTTLE");
                        for (String n : names) {
                            Material m = Material.matchMaterial(n);
                            if (m != null) vp.setCooldown(m, ticks);
                        }
                        vp.sendActionBar(Component.text("The dragon stunned your utilities", NamedTextColor.DARK_PURPLE));
                    } else if (hit instanceof Mob mob) {
                        controlMob(p, mob);
                    }
                    return true;
                });
        return true;
    }

    /** A bitten mob fights for the owner for a while, then dies. */
    private void controlMob(Player owner, Mob mob) {
        int ticks = (int) (num("dragon.control-seconds", 30) * 20);
        allies.put(mob.getUniqueId(), owner.getUniqueId());
        mob.setTarget(null);
        mob.setGlowing(true);
        mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, mob.getHeight() + 0.3, 0), 6, 0.3, 0.2, 0.3, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (allies.remove(mob.getUniqueId()) != null && mob.isValid()) {
                mob.getWorld().spawnParticle(Particle.SOUL, mob.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.05);
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.6f);
                mob.setGlowing(false);
                mob.setHealth(0);
            }
        }, ticks);
    }

    private boolean dragonRide(Player p) {
        final UUID id = p.getUniqueId();
        if (!riders.add(id)) return false;
        final int ticks = (int) (num("dragon.ride-seconds", 5) * 20);
        final CustomFX fx = plugin.customFx();
        final List<org.bukkit.entity.Display> parts = new ArrayList<>();
        final ItemDisplay head;
        final org.bukkit.entity.BlockDisplay[] body = new org.bukkit.entity.BlockDisplay[5];
        final org.bukkit.entity.BlockDisplay[] wings = new org.bukkit.entity.BlockDisplay[2];
        if (fxOn()) {
            head = fx.item(p.getLocation(), new ItemStack(Material.DRAGON_HEAD), 2.8f);
            parts.add(head);
            for (int i = 0; i < body.length; i++) {
                float s = 1.1f - i * 0.15f;
                body[i] = fx.block(p.getLocation(), i % 2 == 0 ? Material.BLACK_CONCRETE : Material.PURPLE_CONCRETE,
                        CustomFX.boxT(s, s, s, 0, 0, 0, new Quaternionf()));
                parts.add(body[i]);
            }
            for (int i = 0; i < wings.length; i++) {
                wings[i] = fx.block(p.getLocation(), Material.PURPLE_STAINED_GLASS, CustomFX.boxT(2.2f, 0.06f, 1.2f, 0, 0, 0, new Quaternionf()));
                parts.add(wings[i]);
            }
        } else {
            head = null;
        }
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.8f);
        sound(w, p.getLocation(), "cm:spawn", 1f, 0.8f);
        p.setGravity(false);

        new BukkitRunnable() {
            int t = 0;
            final Deque<Location> trail = new ArrayDeque<>();
            final Set<UUID> hurt = new HashSet<>();

            private void finish() {
                cancel();
                riders.remove(id);
                if (p.isOnline()) {
                    p.setGravity(true);
                    p.setFallDistance(0f);
                    noFall(p, 4000);
                }
                fx.collapse(parts, 6);
            }

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= ticks) {
                    finish();
                    return;
                }
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                p.setVelocity(dir.clone().multiply(1.1));
                p.setFallDistance(0f);
                trail.addFirst(p.getLocation().clone());
                if (trail.size() > 40) trail.removeLast();

                if (head != null) {
                    Location hl = eye.clone().add(dir.clone().multiply(1.5)).subtract(0, 1.4, 0);
                    hl.setDirection(dir);
                    head.teleport(hl);
                    List<Location> list = new ArrayList<>(trail);
                    for (int i = 0; i < body.length; i++) {
                        Location seg = list.get(Math.min(list.size() - 1, (i + 1) * 4)).clone().subtract(0, 0.5, 0);
                        body[i].teleport(seg);
                    }
                    double flap = Math.sin(t * 0.5) * 32.0;
                    for (int i = 0; i < wings.length; i++) {
                        float sign = i == 0 ? -1f : 1f;
                        Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(sign * flap));
                        Vector3f off = q.transform(new Vector3f(sign * 1.1f, 0f, 0f));
                        Location wl = p.getLocation().add(0, -0.2, 0);
                        wl.setYaw(p.getLocation().getYaw());
                        wl.setPitch(0f);
                        wings[i].teleport(wl);
                        fx.morph(wings[i], 1, CustomFX.boxT(2.2f, 0.06f, 1.2f, off.x + sign * 0.3f, off.y, off.z, q));
                    }
                }
                p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 0.5, 0), 4, 0.4, 0.2, 0.4, 0.01);
                if (t % 5 == 0) {
                    for (LivingEntity le : enemies(p, p.getLocation().add(0, 1, 0), 2.6)) {
                        if (!hurt.add(le.getUniqueId())) continue;
                        plainDamage(p, le, 3);
                        knock(p.getLocation(), le, 1.0, 0.5);
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ==================================================================
    // Cleaver
    // ==================================================================

    private boolean cleaverSwing(Player p) {
        double radius = num("cleaver.swing-radius", 3);
        double damage = num("cleaver.swing-damage", 8);
        int stun = (int) (num("cleaver.stun-seconds", 20) * 20);
        World w = p.getWorld();
        sound(w, p.getLocation(), "cm:axe_slash", 1f, 1f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.5f);
        w.playSound(p.getLocation(), Sound.ITEM_AXE_SCRAPE, 1f, 0.6f);
        if (fxOn()) {
            plugin.customFx().arc(p, Material.ORANGE_STAINED_GLASS, Material.RED_CONCRETE, radius * 0.9, 170, 11);
            plugin.customFx().arc(p, Material.YELLOW_STAINED_GLASS, Material.ORANGE_CONCRETE, radius * 0.6, 150, 9);
        }
        Vector look = p.getLocation().getDirection().setY(0);
        boolean useCone = look.lengthSquared() > 1.0E-4;
        if (useCone) look.normalize();
        for (LivingEntity le : enemies(p, p.getLocation().add(0, 1, 0), radius + 0.5)) {
            if (useCone) {
                Vector to = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (to.lengthSquared() > 1.0E-6 && to.normalize().dot(look) < 0.0) continue;
            }
            trueDamage(p, le, damage);
            stunShield(le, stun);
            if (le instanceof Player vp && stun > 0) vp.setCooldown(Material.SHIELD, stun);   // even a lowered shield stays disabled
            knock(p.getLocation(), le, 0.9, 0.35);
            w.spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 18, 0.3, 0.5, 0.3, 0.3);
        }
        return true;
    }

    private boolean cleaverWagon(Player p) {
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        final LivingEntity[] carried = new LivingEntity[1];
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 0.8f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_MINECART_INSIDE, 1f, 0.8f);
        final double radius = num("cleaver.wagon-radius", 3);
        final double hitDamage = num("cleaver.wagon-hit-damage", 3);
        Shot s = launch(p, CustomFX.vfx("wagon"), 2.2f, new Quaternionf(), 'y', 0f, from, dir.clone().multiply(1.25), 0.0, 1.4, 70,
                Particle.SMOKE, (sh, at, hit) -> {
                    if (hit != null) {
                        if (carried[0] == null) {
                            carried[0] = hit;
                            trueDamage(p, hit, hitDamage);
                            at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
                            if (hit instanceof Player vp) vp.sendActionBar(Component.text("Strapped to the wagon", NamedTextColor.GOLD));
                        }
                        return false;   // the wagon keeps rolling with it
                    }
                    wagons.remove(p.getUniqueId());
                    blast(p, at, radius, hitDamage, Material.ORANGE_STAINED_GLASS,
                            new Material[]{Material.ORANGE_CONCRETE, Material.RED_CONCRETE, Material.MAGMA_BLOCK}, Material.ORANGE_CONCRETE);
                    return true;
                });
        s.perTick = sh -> {
            LivingEntity c = carried[0];
            if (c != null && c.isValid() && !c.isDead()) {
                Location t = sh.pos.clone().add(0, 0.3, 0);
                t.setYaw(c.getLocation().getYaw());
                t.setPitch(c.getLocation().getPitch());
                c.teleport(t);
                c.setVelocity(new Vector(0, 0, 0));
            }
        };
        wagons.put(p.getUniqueId(), new Wagon(s, carried));
        return true;
    }

    // ==================================================================
    // Burgblade
    // ==================================================================

    private boolean burgify(Player p) {
        Entity t = p.getTargetEntity(15);
        if (!(t instanceof LivingEntity victim) || victim instanceof ArmorStand || victim == p) {
            p.sendActionBar(Component.text("No target in sight.", NamedTextColor.RED));
            return false;
        }
        final int ticks = (int) (num("burgblade.burgify-seconds", 5) * 20);
        final UUID vid = victim.getUniqueId();
        burgers.put(vid, new Burger(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, true));
        World w = victim.getWorld();
        w.playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 0.6f);
        sound(w, victim.getLocation(), "cm:burger_throw", 1f, 1f);
        w.spawnParticle(Particle.POOF, victim.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
        if (victim instanceof Player vp) vp.sendActionBar(Component.text("You are a burger!", NamedTextColor.GOLD));
        if (fxOn()) {
            plugin.customFx().followItem(victim, CustomFX.vfx("burger"), 2.6f, 0.9, ticks, 0f, () -> burgers.containsKey(vid));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (burgers.remove(vid) != null && victim.isValid()) {
                victim.removePotionEffect(PotionEffectType.INVISIBILITY);
                victim.getWorld().spawnParticle(Particle.POOF, victim.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.05);
            }
        }, ticks + 1L);
        return true;
    }

    private boolean burgerShot(Player p) {
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        sound(p.getWorld(), p.getLocation(), "cm:burger_throw", 1f, 1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);
        launch(p, CustomFX.vfx("burger"), 1.6f, new Quaternionf(), 'x', 25f, from, dir.clone().multiply(1.4), 0.015, 1.1, 40,
                Particle.CRIT, (sh, at, hit) -> {
                    World w = at.getWorld();
                    w.playSound(at, Sound.ENTITY_GENERIC_EAT, 1f, 0.8f);
                    w.spawnParticle(Particle.DUST, at, 20, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.4f));
                    if (hit != null) {
                        trueDamage(p, hit, num("burgblade.shot-damage", 7));
                        hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (num("burgblade.slow-seconds", 4) * 20), 2, false, true, true));
                        if (hit instanceof Player vp) vp.setFoodLevel(vp.getFoodLevel() / 2);
                    }
                    return true;
                });
        return true;
    }

    private void burgerEat(Player p) {
        long left = plugin.abilities().remainingMs(p, "burgblade", "burgereat");
        if (left > 0) {
            p.sendActionBar(Component.text("Burger Feast " + String.format("%.1fs", left / 1000.0), NamedTextColor.GRAY));
            return;
        }
        plugin.abilities().startCooldownMs(p, "burgblade", "burgereat", (int) num("burgblade.cooldown-eat", 20));
        World w = p.getWorld();
        if (fxOn()) plugin.customFx().followItem(p, CustomFX.vfx("burger"), 0.8f, 1.9, 24, 20f, () -> p.isOnline() && !p.isDead());
        for (int i = 0; i < 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.9f, 0.9f), i * 5L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            int ticks = (int) (num("burgblade.eat-seconds", 4) * 20);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 1, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 1, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0, false, true, true));
            w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
            w.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.2, 0), 15, 0.4, 0.5, 0.4, 0);
        }, 22L);
    }

    // ==================================================================
    // Thornblade
    // ==================================================================

    private boolean thornSpike(Player p) {
        Block b = p.getTargetBlockExact(30);
        Location at = b != null ? b.getLocation().add(0.5, 1, 0.5)
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(8));
        final int ticks = (int) (num("thornblade.spike-seconds", 5) * 20);
        final double dmg = num("thornblade.spike-damage", 1);
        final CustomFX fx = plugin.customFx();
        World w = at.getWorld();
        w.playSound(at, Sound.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 1f, 0.5f);
        w.playSound(at, Sound.BLOCK_GRASS_BREAK, 1f, 0.6f);
        w.spawnParticle(Particle.HAPPY_VILLAGER, at, 25, 0.6, 0.2, 0.6, 0);

        ItemDisplay spike = null;
        if (fxOn()) {
            spike = fx.item(at.clone().add(0, 0.6, 0), CustomFX.vfx("spikes"), 0.01f);
            final ItemDisplay sp = spike;
            fx.morphLater(sp, 1L, 5, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.6f, 2.6f, 2.6f), new Quaternionf()));
            plugin.displays().spikes(at, 1.7, 6, new Material[]{Material.MOSS_BLOCK, Material.GREEN_CONCRETE, Material.LIME_CONCRETE},
                    0.3f, 0.8f, 1.6f, true, Math.min(ticks, 100));
            plugin.displays().groundRing(at, 2.2, Material.GREEN_CONCRETE, 22, 8, 40);
        }
        final ItemDisplay held = spike;
        final Location center = at.clone().add(0, 1, 0);
        new BukkitRunnable() {
            int t = 0;
            final Map<UUID, Integer> last = new HashMap<>();

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    if (held != null) fx.collapse(List.of(held), 6);
                    cancel();
                    return;
                }
                if (t % 5 == 0) {
                    for (LivingEntity le : enemies(p, center, 2.0)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true, true));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true));
                        Integer l = last.get(le.getUniqueId());
                        if (l == null || t - l >= 10) {
                            last.put(le.getUniqueId(), t);
                            trueDamage(p, le, dmg);
                        }
                    }
                    w.spawnParticle(Particle.HAPPY_VILLAGER, center, 3, 0.8, 0.5, 0.8, 0);
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private boolean thornVines(Player p) {
        Location from = hand(p);
        Vector dir = p.getEyeLocation().getDirection();
        final CustomFX fx = plugin.customFx();
        final CustomFX.Link rope = fx.link(from, from, Material.GREEN_CONCRETE, 0.1f);
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 1.5f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_VINE_STEP, 1f, 0.6f);
        Shot s = launch(p, CustomFX.vfx("vines"), 1.4f, new Quaternionf(), 'y', 0f, from, dir.clone().multiply(1.9), 0.0, 0.9, 18,
                Particle.HAPPY_VILLAGER, (sh, at, hit) -> {
                    if (hit != null) {
                        reel(p, hit, null, rope);
                        return true;
                    }
                    Vector n = sh.vel.clone();
                    boolean wall = n.lengthSquared() > 1.0E-6
                            && !at.clone().add(n.normalize().multiply(0.6)).getBlock().isPassable();
                    if (wall) reel(p, null, at.clone(), rope);
                    else rope.remove();
                    return true;
                });
        s.perTick = sh -> rope.update(hand(p), sh.pos);
        return true;
    }

    /** Pulls an enemy to the owner (entity != null) or the owner to a block (anchor != null). Sneak lets go. */
    private void reel(Player p, LivingEntity entity, Location anchor, CustomFX.Link rope) {
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                boolean lost = !p.isOnline() || p.isDead() || p.isSneaking() || t >= 60
                        || (entity != null && (!entity.isValid() || entity.isDead()));
                Location target = entity != null ? entity.getLocation().add(0, 1, 0) : anchor;
                if (!lost) {
                    Location me = p.getLocation().add(0, 1, 0);
                    Vector to = entity != null
                            ? me.toVector().subtract(target.toVector())
                            : target.toVector().subtract(me.toVector());
                    if (to.length() < 2.2) lost = true;
                    else {
                        Vector v = to.normalize().multiply(0.95);
                        if (entity != null) {
                            v.setY(v.getY() * 0.5 + 0.1);
                            entity.setVelocity(v);
                        } else {
                            p.setVelocity(v);
                            p.setFallDistance(0f);
                        }
                    }
                }
                if (lost) {
                    rope.remove();
                    if (entity == null && p.isOnline()) noFall(p, 3000);
                    cancel();
                    return;
                }
                rope.update(hand(p), target);
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ==================================================================
    // Soul Staff
    // ==================================================================

    private void addSoul(Player p) {
        int n = Math.min(5, souls.merge(p.getUniqueId(), 1, Integer::sum));
        souls.put(p.getUniqueId(), n);
        p.sendActionBar(Component.text("Souls " + "●".repeat(n) + "○".repeat(5 - n)
                + (n >= 5 ? "  Shift + Left-click: Ultimate" : ""), NamedTextColor.AQUA));
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 1.0f + n * 0.1f);
    }

    private boolean soulEye(Player p) {
        final double range = num("soulstaff.range", 50);
        final double dmg = num("soulstaff.eye-damage", 2);
        final CustomFX fx = plugin.customFx();
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.2f);
        w.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 1.4f);
        addSoul(p);
        final ItemDisplay eye = fxOn()
                ? fx.followItem(p, CustomFX.vfx("eye"), 1.8f, 2.9, 80, 0f, () -> p.isOnline() && !p.isDead())
                : null;
        if (fxOn()) {
            fx.orbit(p, new Material[]{Material.SOUL_SAND, Material.LIGHT_BLUE_STAINED_GLASS, Material.BLACK_CONCRETE},
                    6, 1.4, 0.2f, 80, 6.0, 2.9);
        }
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.isDead()) return;
                Location origin = eye != null && eye.isValid() ? eye.getLocation() : p.getLocation().add(0, 2.9, 0);
                LivingEntity best = null;
                double bd = Double.MAX_VALUE;
                for (LivingEntity le : enemies(p, origin, range)) {
                    double d = le.getLocation().distanceSquared(origin);
                    if (d < bd) {
                        bd = d;
                        best = le;
                    }
                }
                if (best == null) return;
                final LivingEntity target = best;
                w.playSound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 1.8f);
                trueDamage(p, target, dmg);
                steal(p, target);
                if (fxOn()) {
                    final CustomFX.Link core = fx.link(origin, target.getEyeLocation(), Material.LIGHT_BLUE_CONCRETE, 0.08f);
                    final CustomFX.Link glow = fx.link(origin, target.getEyeLocation(), Material.CYAN_STAINED_GLASS, 0.26f);
                    new BukkitRunnable() {
                        int t = 0;

                        @Override
                        public void run() {
                            if (t >= 6 || !target.isValid()) {
                                core.remove();
                                glow.remove();
                                cancel();
                                return;
                            }
                            Location o = eye != null && eye.isValid() ? eye.getLocation() : origin;
                            core.update(o, target.getEyeLocation());
                            glow.update(o, target.getEyeLocation());
                            t++;
                        }
                    }.runTaskTimer(plugin, 1L, 1L);
                }
            }, 14L + i * 14L);
        }
        return true;
    }

    /** Each beam steals 1 XP, 1 golden apple and 1 cobweb. */
    private void steal(Player owner, LivingEntity v) {
        if (!(v instanceof Player vp)) return;
        if (vp.getTotalExperience() > 0) {
            vp.setTotalExperience(vp.getTotalExperience() - 1);
            owner.giveExp(1);
        }
        for (Material m : new Material[]{Material.GOLDEN_APPLE, Material.COBWEB}) {
            Map<Integer, ItemStack> left = vp.getInventory().removeItem(new ItemStack(m, 1));
            if (left.isEmpty()) {
                for (ItemStack extra : owner.getInventory().addItem(new ItemStack(m, 1)).values()) {
                    owner.getWorld().dropItemNaturally(owner.getLocation(), extra);
                }
            }
        }
    }

    private boolean soulSand(Player p) {
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        final double dmg = num("soulstaff.sand-damage", 10);
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 0.6f);
        p.getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.6f);
        addSoul(p);
        launch(p, new ItemStack(Material.SOUL_SAND), 0.9f, new Quaternionf(), 'x', 25f, from, dir.clone().multiply(1.5), 0.01, 1.1, 40,
                Particle.SOUL, (sh, at, hit) -> {
                    World w = at.getWorld();
                    w.spawnParticle(Particle.SOUL, at, 25, 0.4, 0.4, 0.4, 0.06);
                    w.playSound(at, Sound.BLOCK_SOUL_SAND_BREAK, 1f, 0.6f);
                    if (hit != null) {
                        trueDamage(p, hit, dmg);
                        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                        p.setHealth(Math.min(max, p.getHealth() + dmg));
                        w.spawnParticle(Particle.HEART, p.getLocation().add(0, 1.6, 0), 6, 0.4, 0.3, 0.4, 0);
                        if (fxOn()) plugin.displays().tumbleCubes(at.clone().add(0, 1, 0), Material.LIGHT_BLUE_STAINED_GLASS, 4, 1.0f, 16);
                    }
                    return true;
                });
        return true;
    }

    private void curse(Player owner, LivingEntity v) {
        int slow = (int) (num("soulstaff.curse-slow-seconds", 90) * 20);
        double dark = num("soulstaff.curse-darkness-seconds", -1);
        v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 0, false, true, true));
        v.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, dark < 0 ? PotionEffect.INFINITE_DURATION : (int) (dark * 20), 0, false, true, true));
        noAbsorbUntil.put(v.getUniqueId(), System.currentTimeMillis() + (long) (num("soulstaff.curse-absorption-block-seconds", 20) * 1000));
        v.setAbsorptionAmount(0);
        v.removePotionEffect(PotionEffectType.ABSORPTION);
        World w = v.getWorld();
        w.playSound(v.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        w.spawnParticle(Particle.SOUL, v.getLocation().add(0, 1, 0), 50, 0.5, 0.9, 0.5, 0.1);
        if (v instanceof Player vp) vp.sendActionBar(Component.text("You are cursed", NamedTextColor.DARK_PURPLE));
        owner.sendActionBar(Component.text("Curse delivered", NamedTextColor.AQUA));
        if (fxOn()) {
            plugin.customFx().chains(v, 80);
            plugin.customFx().shell(v, Material.BLACK_STAINED_GLASS, Material.SOUL_SAND, 1.5f, 2.4f, 1.5f, 80, true, 0.95f);
        }
    }

    // ==================================================================
    // events
    // ==================================================================

    /** Protected players (Ghost Shield, Impact box) take no damage. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProtected(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.KILL
                || c == EntityDamageEvent.DamageCause.SUICIDE) return;

        if (c == EntityDamageEvent.DamageCause.FALL) {
            Long end = noFallUntil.get(v.getUniqueId());
            if (end != null && end > System.currentTimeMillis()) {
                e.setCancelled(true);
                return;
            }
        }
        if (!isProtected(v)) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastDeflect.get(v.getUniqueId());
        if (last == null || now - last > 400) {
            lastDeflect.put(v.getUniqueId(), now);
            v.getWorld().spawnParticle(Particle.END_ROD, v.getLocation().add(0, 1, 0), 8, 0.5, 0.8, 0.5, 0.05);
            v.getWorld().playSound(v.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
        }
    }

    /** Hits taken: the Ghost Shield counts to 10, the Thornblade charges every 5th. Allied mobs spare their owner. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Mob m && e.getEntity() instanceof Player owner
                && owner.getUniqueId().equals(allies.get(m.getUniqueId()))) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getEntity() instanceof Player v)) return;
        Weapon main = reg.of(v.getInventory().getItemInMainHand());
        Weapon off = reg.of(v.getInventory().getItemInOffHand());

        boolean shield = (main != null && main.id.equals("ghost_shield")) || (off != null && off.id.equals("ghost_shield"));
        if (shield && !isProtected(v)) {
            int n = ghostHits.merge(v.getUniqueId(), 1, Integer::sum);
            if (n >= 10) {
                ghostHits.put(v.getUniqueId(), 0);
                e.setCancelled(true);       // the 10th hit passes through you
                ghost(v, 20);
                v.sendActionBar(Component.text("Ghost!", NamedTextColor.WHITE));
                return;
            }
            v.sendActionBar(Component.text("Phase " + n + "/10", NamedTextColor.GRAY));
        }
        if (main != null && main.id.equals("thornblade")) {
            int n = thornHits.merge(v.getUniqueId(), 1, Integer::sum);
            if (n >= 5) {
                thornHits.put(v.getUniqueId(), 0);
                thornCharged.add(v.getUniqueId());
                v.getWorld().playSound(v.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
                v.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, v.getLocation().add(0, 1, 0), 20, 0.4, 0.7, 0.4, 0);
                v.sendActionBar(Component.text("Thorns charged", NamedTextColor.GREEN));
            }
        }
    }

    /** Called by Abilities for every hit with a Crazy Mythicals weapon in the main hand. */
    public void onHit(Player attacker, Weapon w, LivingEntity victim, EntityDamageByEntityEvent e) {
        UUID id = attacker.getUniqueId();
        switch (w.id) {
            case "great_hammer" -> stunShield(victim, (int) (num("hammer.shield-stun-seconds", 5) * 20));
            case "judge" -> stunShield(victim, (int) (num("judge.shield-stun-seconds", 5) * 20));
            case "cleaver" -> stunShield(victim, (int) (num("cleaver.passive-stun-seconds", 10) * 20));
            case "thornblade" -> {
                if (thornCharged.remove(id)) {
                    e.setDamage(e.getDamage() + 3.0);   // 1.5 hearts
                    World world = victim.getWorld();
                    world.spawnParticle(Particle.HAPPY_VILLAGER, victim.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0);
                    world.playSound(victim.getLocation(), Sound.BLOCK_GRASS_BREAK, 1f, 0.6f);
                    if (fxOn()) plugin.displays().spikes(victim.getLocation(), 1.0, 4, new Material[]{Material.MOSS_BLOCK, Material.GREEN_CONCRETE, Material.LIME_CONCRETE},
                            0.25f, 0.8f, 1.4f, true, 12);
                }
            }
            case "burgblade" -> {
                Burger b = burgers.get(victim.getUniqueId());
                if (b != null && b.owner().equals(id) && b.until() > System.currentTimeMillis()) {
                    e.setCancelled(true);
                    int dmg = ThreadLocalRandom.current().nextInt(1, 11);
                    trueDamage(attacker, victim, dmg);
                    if (victim instanceof Player vp) {
                        vp.setSaturation(0f);
                        vp.setFoodLevel(Math.max(0, vp.getFoodLevel() - 2));
                    }
                    World world = victim.getWorld();
                    sound(world, victim.getLocation(), "cm:bite", 1f, 1.2f);
                    world.playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 0.7f);
                    world.spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 18, 0.4, 0.6, 0.4, 0,
                            new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.3f));
                }
            }
            case "soulstaff" -> {
                Long armed = ultArmedUntil.get(id);
                if (armed != null && armed > System.currentTimeMillis()) {
                    ultArmedUntil.remove(id);
                    curse(attacker, victim);
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Weapon w = reg.of(p.getInventory().getItemInMainHand());
        if (w == null || !w.category.equals("crazy")) return;

        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;

        if (right) {
            switch (w.id) {
                case "judge" -> e.setUseItemInHand(Event.Result.DENY);    // F throws it, never the vanilla throw
                case "dragon_cannon" -> {
                    e.setUseItemInHand(Event.Result.DENY);                // never loads like a crossbow
                    dragonBall(p);
                }
                case "burgblade" -> burgerEat(p);
                default -> { }
            }
        } else if (left && w.id.equals("soulstaff") && p.isSneaking()) {
            int n = souls.getOrDefault(p.getUniqueId(), 0);
            if (n < 5) {
                p.sendActionBar(Component.text("Souls " + n + "/5", NamedTextColor.GRAY));
                return;
            }
            souls.put(p.getUniqueId(), 0);
            ultArmedUntil.put(p.getUniqueId(), System.currentTimeMillis() + 30_000L);
            World world = p.getWorld();
            world.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.5f);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 60, 0.6, 1.0, 0.6, 0.08);
            p.sendActionBar(Component.text("Ultimate armed: hit someone to curse them", NamedTextColor.AQUA));
            if (fxOn()) {
                plugin.customFx().shell(p, Material.CYAN_STAINED_GLASS, Material.BLACK_CONCRETE, 2.0f, 2.8f, 2.0f, 40, true, 0.9f);
                plugin.customFx().orbit(p, new Material[]{Material.SOUL_SAND, Material.SOUL_SOIL}, 8, 1.6, 0.25f, 60, 8.0, 1.0);
            }
        }
    }

    private void dragonBall(Player p) {
        long left = plugin.abilities().remainingMs(p, "dragon_cannon", "dragonball");
        if (left > 0) {
            p.sendActionBar(Component.text("Dragon Ball " + String.format("%.1fs", left / 1000.0), NamedTextColor.GRAY));
            return;
        }
        plugin.abilities().startCooldownMs(p, "dragon_cannon", "dragonball", (int) num("dragon.cooldown-ball", 5));
        Location from = muzzle(p);
        Vector dir = p.getEyeLocation().getDirection();
        final double dmg = num("dragon.ball-damage", 4);
        sound(p.getWorld(), p.getLocation(), "cm:shoot", 1f, 1.1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        launch(p, CustomFX.vfx("fireball"), 1.6f, new Quaternionf(), 'y', 0f, from, dir.clone().multiply(1.8), 0.0, 0.9, 40,
                Particle.DRAGON_BREATH, (sh, at, hit) -> {
                    World w = at.getWorld();
                    w.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1.2f);
                    w.spawnParticle(Particle.DRAGON_BREATH, at, 40, 0.5, 0.5, 0.5, 0.05);
                    if (hit != null) {
                        trueDamage(p, hit, dmg);
                        hit.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
                        if (fxOn()) plugin.displays().tumbleCubes(at.clone().add(0, 1, 0), Material.PURPLE_STAINED_GLASS, 3, 1.0f, 14);
                    }
                    return true;
                });
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        UUID owner = allies.get(e.getEntity().getUniqueId());
        if (owner != null && e.getTarget() != null && owner.equals(e.getTarget().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        allies.remove(e.getEntity().getUniqueId());
        burgers.remove(e.getEntity().getUniqueId());
        noAbsorbUntil.remove(e.getEntity().getUniqueId());
        Player killer = e.getEntity().getKiller();
        if (killer != null && e.getEntity() instanceof org.bukkit.entity.AbstractSkeleton) {
            Weapon w = reg.of(killer.getInventory().getItemInMainHand());
            if (w != null && w.id.equals("soulstaff")) addSoul(killer);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        forget(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (riders.remove(p.getUniqueId())) p.setGravity(true);
        forget(p.getUniqueId());
        ghostHits.remove(p.getUniqueId());
        thornHits.remove(p.getUniqueId());
        souls.remove(p.getUniqueId());
        wagons.remove(p.getUniqueId());
    }

    private void forget(UUID id) {
        protectUntil.remove(id);
        thornCharged.remove(id);
        ultArmedUntil.remove(id);
        noAbsorbUntil.remove(id);
        noFallUntil.remove(id);
        burgers.remove(id);
        lastDeflect.remove(id);
    }
}
