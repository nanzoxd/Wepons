package com.wepons;

import com.wepons.Weapon.Ability;
import com.wepons.Weapon.Eff;
import com.wepons.Weapon.Trigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Every weapon ability, cooldown, passive effect and particle aura. */
public final class Abilities implements Listener {

    private record LastHit(Entity entity, long time) {}

    private static final String RAVAGER_TAG = "wepons_ravager";

    private final Wepons plugin;
    private final WeaponRegistry reg;

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastFire = new HashMap<>();
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Map<UUID, Long> rootedUntil = new HashMap<>();
    private final Map<UUID, Long> novaUntil = new HashMap<>();
    private final Map<String, Long> ultUntil = new HashMap<>();
    private final Map<String, Integer> hitCounts = new HashMap<>();
    private final Map<UUID, UUID> ravagerOwner = new HashMap<>();
    private final Map<UUID, LastHit> lastAttack = new HashMap<>();
    private final Set<UUID> banLock = new HashSet<>();
    private final Map<UUID, Integer> charges = new HashMap<>();
    private final Set<UUID> internal = new HashSet<>();

    public Abilities(Wepons plugin) {
        this.plugin = plugin;
        this.reg = plugin.registry();
    }

    // ==================================================================
    // Background tasks
    // ==================================================================

    public void start() {
        // passive effects + holder tracking, once per second
        Bukkit.getScheduler().runTaskTimer(plugin, this::passiveTick, 20L, 20L);
        // particle aura around held weapons
        Bukkit.getScheduler().runTaskTimer(plugin, this::auraTick, 3L, 3L);
        // keep frozen players frozen, shatter when it ends
        Bukkit.getScheduler().runTaskTimer(plugin, this::frozenTick, 5L, 5L);
    }

    public void shutdown() {
        for (World world : Bukkit.getWorlds()) {
            for (Ravager r : world.getEntitiesByClass(Ravager.class)) {
                if (r.getScoreboardTags().contains(RAVAGER_TAG)) r.remove();
            }
        }
        ravagerOwner.clear();
    }

    private void passiveTick() {
        boolean glow = plugin.getConfig().getBoolean("glow-when-held", true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Weapon held = reg.of(p.getInventory().getItemInMainHand());
            if (held != null) {
                if (!ultActive(p, held.id)) {
                    for (Eff eff : held.held) applyPassive(p, eff);
                }
                if (glow) applyPassive(p, new Eff(PotionEffectType.GLOWING, 0));
            }

            Weapon worn = wornWeapon(p);
            if (worn != null) {
                for (Eff eff : worn.held) applyPassive(p, eff);
            }

            for (ItemStack item : p.getInventory().getContents()) {
                Weapon w = reg.of(item);
                if (w == null || w.carry.isEmpty()) continue;
                for (Eff eff : w.carry) applyPassive(p, eff);
                break;
            }

            plugin.holders().scan(p);
        }
    }

    private void applyPassive(Player p, Eff eff) {
        p.addPotionEffect(new PotionEffect(eff.type(), 60, eff.amp(), false, false, true));
    }

    private void auraTick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Weapon w = reg.of(p.getInventory().getItemInMainHand());
            if (w == null) w = wornWeapon(p);
            if (w == null) continue;
            for (int i = 0; i < 2; i++) {
                Location at = p.getLocation().add(r.nextDouble(-0.45, 0.45), r.nextDouble(0.1, 1.8), r.nextDouble(-0.45, 0.45));
                p.getWorld().spawnParticle(w.aura, at, 1, 0, 0, 0, 0);
            }
        }
    }

    private void frozenTick() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(frozenUntil.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                frozenUntil.remove(id);
                continue;
            }
            if (frozenUntil.get(id) > now) {
                p.setFreezeTicks(160);
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0);
            } else {
                frozenUntil.remove(id);
                p.setFreezeTicks(0);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.6f, 1.4f);
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.05);
                p.damage(2);
            }
        }
    }

    // ==================================================================
    // Cooldowns + firing
    // ==================================================================

    private static String key(Weapon w, Ability a) {
        return w.id + ":" + a.key();
    }

    private long remaining(Player p, String key) {
        Map<String, Long> map = cooldowns.get(p.getUniqueId());
        if (map == null) return 0;
        Long end = map.get(key);
        return end == null ? 0 : Math.max(0, end - System.currentTimeMillis());
    }

    private void startCooldown(Player p, Weapon w, Ability a) {
        startCooldown(p, w, a, a.cooldown());
    }

    private void startCooldown(Player p, Weapon w, Ability a, int seconds) {
        if (seconds <= 0) return;
        String key = key(w, a);
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(key, System.currentTimeMillis() + seconds * 1000L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || remaining(p, key) > 0) return;
            p.sendActionBar(Component.text(a.name() + " ", w.color).append(Component.text("ready", NamedTextColor.GRAY)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
        }, seconds * 20L);
    }

    private void warnCooldown(Player p, Weapon w, Ability a, long ms) {
        p.sendActionBar(Component.text(a.name() + " ", w.color)
                .append(Component.text(String.format("%.1fs", ms / 1000.0), NamedTextColor.GRAY)));
    }

    private void fire(Player p, Weapon w, Ability a) {
        long now = System.currentTimeMillis();
        Long last = lastFire.get(p.getUniqueId());
        if (last != null && now - last < 150) return;
        lastFire.put(p.getUniqueId(), now);

        long left = remaining(p, key(w, a));
        if (left > 0) {
            warnCooldown(p, w, a, left);
            return;
        }
        if (!run(p, w, a)) return;

        startCooldown(p, w, a);
        if (!a.key().equals("minetoggle") && !a.key().equals("riptide")) {
            p.sendActionBar(Component.text(a.name() + " ", w.color).append(Component.text("activated", NamedTextColor.GRAY)));
        }
    }

    private boolean run(Player p, Weapon w, Ability a) {
        return switch (a.key()) {
            case "dash" -> launch(p, 1.7, 0.15, Particle.SOUL_FIRE_FLAME, 1.5f);
            case "flightdash" -> launch(p, 2.5, 0.1, Particle.CLOUD, 1.4f);
            case "dashmacedash" -> launch(p, 3.5, 0.1, Particle.FLAME, 1.4f);
            case "grapplelaunch" -> launch(p, 3.0, 0.1, Particle.WITCH, 1.2f);
            case "blink" -> blink(p);
            case "voidslash" -> voidSlash(p);
            case "voidpull" -> voidPull(p);
            case "lightning" -> lightning(p);
            case "staticfield" -> staticField(p);
            case "frostnova" -> frostNova(p);
            case "bulwark" -> bulwark(p);
            case "windstep" -> surge(p, "speed", PotionEffectType.SPEED, Sound.ENTITY_BREEZE_JUMP, Particle.CLOUD);
            case "berserk" -> surge(p, "strength", PotionEffectType.STRENGTH, Sound.ENTITY_RAVAGER_ROAR, Particle.CRIT);
            case "vanishstep" -> vanishStep(p);
            case "secondwind" -> secondWind(p);
            case "feast" -> feast(p);
            case "minetoggle" -> mineToggle(p);
            case "gravitypulse" -> gravityPulse(p);
            case "ravagerride" -> ravagerRide(p);
            case "riptide" -> riptide(p, w, a);
            case "royalreveal" -> royalReveal(p);
            default -> false;
        };
    }

    // ==================================================================
    // Click triggers
    // ==================================================================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        // Frost Nova blocks wind charges
        if (item.getType() == Material.WIND_CHARGE && novaActive(p)) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("You can't use wind charges while frozen.", NamedTextColor.AQUA));
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.4f, 1.2f);
            return;
        }

        Weapon w = reg.of(item);
        if (w == null) return;
        for (Ability a : w.abilities) {
            boolean plain = a.trigger() == Trigger.RIGHT_CLICK;
            boolean shift = a.trigger() == Trigger.SHIFT_RIGHT && p.isSneaking();
            if (plain || shift) {
                e.setCancelled(true);
                e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                fire(p, w, a);
                return;
            }
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        for (Weapon w : new Weapon[]{reg.of(p.getInventory().getItemInMainHand()), wornWeapon(p)}) {
            if (w == null) continue;
            for (Ability a : w.abilities) {
                if (a.trigger() == Trigger.SHIFT_SWAP) {
                    e.setCancelled(true);
                    fire(p, w, a);
                    return;
                }
            }
        }
    }

    /** The weapon in the helmet slot, if it is a wearable one (Monarch Crown). */
    private Weapon wornWeapon(Player p) {
        Weapon w = reg.of(p.getInventory().getHelmet());
        return (w != null && w.worn) ? w : null;
    }

    // ==================================================================
    // Movement abilities
    // ==================================================================

    private boolean launch(Player p, double speed, double up, Particle trail, float pitch) {
        p.setVelocity(p.getLocation().getDirection().multiply(speed).add(new Vector(0, up, 0)));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, pitch);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 6 || !p.isOnline()) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(trail, p.getLocation().add(0, 1, 0), 4, 0.2, 0.3, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean blink(Player p) {
        Vector dir = p.getLocation().getDirection();
        dir.setY(Math.max(0, dir.getY()));
        if (dir.lengthSquared() < 0.0025) {
            p.sendActionBar(Component.text("Look forward to blink.", NamedTextColor.RED));
            return false;
        }
        dir.normalize();

        Location best = null;
        int run = 0;
        for (double d = 0.5; d <= 6; d += 0.5) {
            Location t = p.getLocation().add(dir.clone().multiply(d));
            boolean blocked = t.getBlock().getType().isSolid() || t.clone().add(0, 1, 0).getBlock().getType().isSolid();
            if (blocked) {
                if (++run > 4) break;
            } else {
                run = 0;
                best = t;
            }
        }
        if (best == null) {
            p.sendActionBar(Component.text("No space to blink.", NamedTextColor.RED));
            return false;
        }
        World w = p.getWorld();
        w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.3);
        p.teleport(best);
        w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.3);
        w.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.6f);
        return true;
    }

    private boolean vanishStep(Player p) {
        Vector dir = p.getLocation().getDirection();
        Location dest = p.getLocation().add(dir.clone().multiply(5));
        if (!isFree(dest)) dest = p.getLocation().add(dir.clone().multiply(2));
        if (!isFree(dest)) {
            p.sendActionBar(Component.text("No space to blink.", NamedTextColor.RED));
            return false;
        }
        World w = p.getWorld();
        w.spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
        p.teleport(dest);
        w.spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
        w.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.8f);
        give(p, PotionEffectType.INVISIBILITY, 60, 0);
        return true;
    }

    private boolean isFree(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
    }

    // ==================================================================
    // Combat abilities
    // ==================================================================

    private List<Player> nearbyEnemies(Player p, Location center, double radius) {
        List<Player> list = new ArrayList<>();
        for (Player o : center.getWorld().getPlayers()) {
            if (o == p || o.getGameMode() == GameMode.SPECTATOR) continue;
            if (o.getLocation().distanceSquared(center) <= radius * radius) list.add(o);
        }
        return list;
    }

    private boolean voidSlash(Player p) {
        int range = 10;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();

        Player target = null;
        double travel = range;
        if (p.getTargetEntity(range) instanceof Player t && t != p) {
            target = t;
            travel = p.getLocation().distance(t.getLocation());
        }
        for (double d = 0.5; d <= travel; d += 0.5) {
            w.spawnParticle(Particle.WITCH, eye.clone().add(dir.clone().multiply(d)), 2, 0.05, 0.05, 0.05, 0);
        }
        if (target != null) {
            target.damage(5, p);
            target.setVelocity(target.getVelocity().add(dir.clone().multiply(0.6)).add(new Vector(0, 0.2, 0)));
            w.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
            w.playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 1.6f);
        } else {
            w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.4f, 1.8f);
        }
        return true;
    }

    private boolean voidPull(Player p) {
        int range = 10;
        if (!(p.getTargetEntity(range) instanceof Player t) || t == p) {
            p.sendActionBar(Component.text("No target in sight.", NamedTextColor.RED));
            return false;
        }
        Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
        if (pull.lengthSquared() > 0) pull.normalize();
        t.setVelocity(pull.multiply(1.4).add(new Vector(0, 0.25, 0)));
        World w = p.getWorld();
        w.spawnParticle(Particle.REVERSE_PORTAL, t.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
        w.playSound(t.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.6f);
        return true;
    }

    private boolean lightning(Player p) {
        int range = 25;
        Block b = p.getTargetBlockExact(range);
        Location at = b != null
                ? b.getLocation().add(0.5, 1, 0.5)
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));

        World w = at.getWorld();
        w.strikeLightningEffect(at);
        w.spawnParticle(Particle.ELECTRIC_SPARK, at, 40, 0.5, 0.8, 0.5, 0.3);
        w.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1f);
        for (Player o : nearbyEnemies(p, at, 4)) o.damage(6, p);
        return true;
    }

    private boolean staticField(Player p) {
        Location at = p.getLocation();
        for (Player o : nearbyEnemies(p, at, 5)) {
            give(o, PotionEffectType.SLOWNESS, 60, 1);
            o.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, o.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
        }
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0), 40, 2, 0.8, 2, 0.1);
        p.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
        return true;
    }

    private boolean gravityPulse(Player p) {
        Location at = p.getLocation();
        for (Player o : nearbyEnemies(p, at, 8)) {
            give(o, PotionEffectType.LEVITATION, 100, 0);
            give(o, PotionEffectType.BLINDNESS, 60, 0);
        }
        p.getWorld().spawnParticle(Particle.WITCH, at.clone().add(0, 1, 0), 40, 3, 0.8, 3, 0.1);
        p.getWorld().playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.8f);
        return true;
    }

    private boolean frostNova(Player p) {
        Location at = p.getLocation();
        for (Player o : nearbyEnemies(p, at, 6)) {
            applyFreeze(o, 10);
            novaUntil.put(o.getUniqueId(), System.currentTimeMillis() + 10_000L);
            give(o, PotionEffectType.SLOWNESS, 200, 2);
            o.sendActionBar(Component.text("Frozen: slowed and wind charges disabled.", NamedTextColor.AQUA));
        }
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 80, 3, 0.8, 3, 0.05);
        p.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.9f, 0.8f);
        p.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.7f);
        return true;
    }

    private void applyFreeze(Player v, int seconds) {
        if (v.getGameMode() == GameMode.SPECTATOR) return;
        frozenUntil.put(v.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        v.setFreezeTicks(160);
        v.getWorld().playSound(v.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 0.8f, 0.8f);
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1f);
        v.getWorld().spawnParticle(Particle.SNOWFLAKE, v.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
        v.sendActionBar(Component.text("You are frozen", NamedTextColor.AQUA));
    }

    private boolean isFrozen(Player p) {
        Long end = frozenUntil.get(p.getUniqueId());
        return end != null && end > System.currentTimeMillis();
    }

    private boolean novaActive(Player p) {
        Long end = novaUntil.get(p.getUniqueId());
        return end != null && end > System.currentTimeMillis();
    }

    // ==================================================================
    // Self buffs
    // ==================================================================

    private void give(Player p, PotionEffectType type, int ticks, int amp) {
        p.addPotionEffect(new PotionEffect(type, ticks, amp, false, true, true));
    }

    private boolean ultActive(Player p, String weaponId) {
        Long end = ultUntil.get(p.getUniqueId() + ":" + weaponId);
        return end != null && end > System.currentTimeMillis();
    }


    // ==================================================================
    // Shadow Blade effects (copied from AltarSMP ShadowBladeListener)
    // Enderman teleport + player sweep sounds, SMOKE + CRIT + SWEEP_ATTACK particles
    // ==================================================================

    private void shadowSlash(Player p, Location at) {
        World w = at.getWorld();
        Location c = at.clone().add(0, 1, 0);
        w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        w.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        w.spawnParticle(Particle.SWEEP_ATTACK, c, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.SMOKE, c, 20, 0.3, 0.5, 0.3, 0.05);
    }

    /** AltarSMP sweep hit: one SWEEP_ATTACK particle on the target and the player sweep sound (pitch 1.5). */
    private void sweepHit(LivingEntity victim) {
        if (!plugin.getConfig().getBoolean("sweep-hit-effects", true)) return;
        World w = victim.getWorld();
        w.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        w.spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
    }

    private void shadowCloak(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        w.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        w.spawnParticle(Particle.SMOKE, l.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        w.spawnParticle(Particle.SWEEP_ATTACK, l.clone().add(0, 1, 0), 3, 0.5, 0.2, 0.5, 0);
    }

    private boolean bulwark(Player p) {
        shadowCloak(p);
        give(p, PotionEffectType.RESISTANCE, 80, 3);
        give(p, PotionEffectType.ABSORPTION, 160, 1);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0);
        return true;
    }

    private boolean surge(Player p, String weaponId, PotionEffectType type, Sound sound, Particle particle) {
        ultUntil.put(p.getUniqueId() + ":" + weaponId, System.currentTimeMillis() + 15_000L);
        give(p, type, 300, 1);
        p.getWorld().playSound(p.getLocation(), sound, 0.6f, 1.2f);
        p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.1);
        return true;
    }

    private boolean secondWind(Player p) {
        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + 12));
        give(p, PotionEffectType.REGENERATION, 60, 2);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.2, 0), 10, 0.4, 0.5, 0.4, 0);
        return true;
    }

    private boolean feast(Player p) {
        p.setFoodLevel(20);
        p.setSaturation(20f);
        give(p, PotionEffectType.REGENERATION, 80, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.7f, 1f);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.2, 0), 15, 0.4, 0.5, 0.4, 0,
                new Particle.DustOptions(Color.ORANGE, 1.2f));
        return true;
    }

    private boolean mineToggle(Player p) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        ItemMeta meta = tool.getItemMeta();
        if (meta == null) return false;

        boolean silk = meta.hasEnchant(Enchantment.SILK_TOUCH);
        if (silk) {
            meta.removeEnchant(Enchantment.SILK_TOUCH);
            meta.addEnchant(Enchantment.FORTUNE, 3, true);
        } else {
            meta.removeEnchant(Enchantment.FORTUNE);
            meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
        }
        tool.setItemMeta(meta);

        p.sendActionBar(Component.text(silk ? "Fortune III" : "Silk Touch", NamedTextColor.GOLD)
                .append(Component.text(" enabled", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, silk ? 1.2f : 0.8f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.2, 0), 10, 0.3, 0.4, 0.3, 0.02);
        return true;
    }

    // ==================================================================
    // Ravager Ride
    // ==================================================================

    private boolean ravagerRide(Player p) {
        Entity look = p.getTargetEntity(30);
        if (look != null && !(look instanceof Ravager) && look != p) {
            lastAttack.put(p.getUniqueId(), new LastHit(look, System.currentTimeMillis()));
        }

        Ravager r = p.getWorld().spawn(p.getLocation(), Ravager.class, ravager -> {
            ravager.addScoreboardTag(RAVAGER_TAG);
            ravager.setPersistent(false);
            ravager.setRemoveWhenFarAway(true);
        });
        ravagerOwner.put(r.getUniqueId(), p.getUniqueId());
        r.addPassenger(p);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.9f, 1f);
        p.getWorld().spawnParticle(Particle.CRIMSON_SPORE, p.getLocation().add(0, 1, 0), 30, 0.8, 0.8, 0.8, 0);
        p.sendActionBar(Component.text("Ravager summoned - 15s", NamedTextColor.GRAY));

        new BukkitRunnable() {
            int ticks = 0;

            private void end() {
                cancel();
                ravagerOwner.remove(r.getUniqueId());
                if (r.isValid()) {
                    r.getWorld().spawnParticle(Particle.POOF, r.getLocation().add(0, 1, 0), 20, 0.6, 0.6, 0.6, 0.02);
                    r.remove();
                }
            }

            @Override
            public void run() {
                if (++ticks > 300 || !r.isValid() || !p.isOnline()) {
                    end();
                    return;
                }
                if (!r.getPassengers().contains(p)) {
                    if (ticks < 20) r.addPassenger(p);
                    else {
                        end();
                        return;
                    }
                }

                // chase the last thing the rider hit, otherwise steer where they look
                LastHit hit = lastAttack.get(p.getUniqueId());
                LivingEntity chase = null;
                if (hit != null && System.currentTimeMillis() - hit.time() <= 30_000L
                        && hit.entity() instanceof LivingEntity le && le.isValid()
                        && le.getWorld() == r.getWorld() && le.getLocation().distance(r.getLocation()) < 4) {
                    chase = le;
                }
                r.setTarget(chase);
                if (chase != null) return;

                Location pl = p.getLocation();
                r.setRotation(pl.getYaw(), 0f);
                if (pl.getPitch() < 60) {
                    Vector v = pl.getDirection().setY(0);
                    if (v.lengthSquared() > 0) {
                        v.normalize();
                        double y = r.getVelocity().getY();
                        if (r.getLocation().add(v).getBlock().getType().isSolid()) y = 0.5;
                        r.setVelocity(new Vector(v.getX() * 0.45, y, v.getZ() * 0.45));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (ravagerOwner.containsKey(e.getEntity().getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    // ==================================================================
    // On-hit / on-block abilities
    // ==================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player v && isFrozen(v)) {
            e.setDamage(e.getDamage() * 1.2);
            v.getWorld().playSound(v.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.4f, 1.6f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        // a summoned ravager never hurts its rider
        if (e.getDamager() instanceof Ravager r) {
            UUID owner = ravagerOwner.get(r.getUniqueId());
            if (owner != null && owner.equals(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
                return;
            }
        }

        // Fire Shield: attacker gets launched and set on fire
        if (e.getEntity() instanceof Player blocker && e.getDamager() instanceof Player hitter && blocker.isBlocking()) {
            Weapon main = reg.of(blocker.getInventory().getItemInMainHand());
            Weapon off = reg.of(blocker.getInventory().getItemInOffHand());
            if ((main != null && main.id.equals("fireshield")) || (off != null && off.id.equals("fireshield"))) {
                rebuke(blocker, hitter);
            }
        }

        if (!(e.getDamager() instanceof Player attacker)) return;
        if (internal.contains(attacker.getUniqueId())) return;

        // Monarch Crown: 2x damage while worn
        Weapon crown = wornWeapon(attacker);
        if (crown != null && crown.id.equals("monarchcrown")) {
            e.setDamage(e.getDamage() * 2);
        }

        if (e.getEntity() != attacker && !(e.getEntity() instanceof Ravager)) {
            lastAttack.put(attacker.getUniqueId(), new LastHit(e.getEntity(), System.currentTimeMillis()));
        }

        Weapon w = reg.of(attacker.getInventory().getItemInMainHand());
        if (w == null || !(e.getEntity() instanceof LivingEntity victim)) return;
        if (w.id.equals("resistance")) shadowSlash(attacker, victim.getLocation());
        else sweepHit(victim);
        for (Ability a : w.abilities) {
            if (a.trigger() != Trigger.HIT) continue;
            switch (a.key()) {
                case "cobwebproc" -> cobweb(victim);
                case "lightningproc" -> lightningProc(victim);
                case "summonfangs" -> fangTrap(attacker, w, a, victim);
                case "freeze" -> frostStrike(attacker, victim);
                case "banhammer" -> banHammer(attacker, w, a, victim);
                case "tridentstrike" -> skyTrident(attacker, w, victim);
                default -> { }
            }
        }
    }

    private void rebuke(Player blocker, Player attacker) {
        if (blocker.getLocation().distance(attacker.getLocation()) > 6) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!attacker.isOnline()) return;
            Vector dir = attacker.getLocation().toVector().subtract(blocker.getLocation().toVector());
            if (dir.lengthSquared() > 0) dir.normalize();
            attacker.setVelocity(dir.multiply(1.3).add(new Vector(0, 0.35, 0)));
            attacker.setFireTicks(80);
            attacker.getWorld().spawnParticle(Particle.FLAME, attacker.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1f);
            blocker.playSound(blocker.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1f);
        }, 1L);
    }

    private void cobweb(LivingEntity v) {
        if (ThreadLocalRandom.current().nextInt(100) >= 30) return;
        Block b = v.getLocation().getBlock();
        if (!b.getType().isAir()) return;
        b.setType(Material.COBWEB);
        v.getWorld().playSound(v.getLocation(), Sound.BLOCK_COBWEB_PLACE, 0.7f, 1f);
        v.getWorld().spawnParticle(Particle.WITCH, v.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
        }, 80L);
    }

    private void lightningProc(LivingEntity v) {
        if (ThreadLocalRandom.current().nextInt(100) >= 30) return;
        v.getWorld().strikeLightning(v.getLocation());
    }

    private void fangTrap(Player p, Weapon w, Ability a, LivingEntity victim) {
        if (remaining(p, key(w, a)) > 0) return;
        int count = hitCounts.merge(p.getUniqueId() + ":scythe", 1, Integer::sum);
        if (count < 5) return;
        hitCounts.put(p.getUniqueId() + ":scythe", 0);
        startCooldown(p, w, a);
        p.sendActionBar(Component.text(a.name() + " ", w.color).append(Component.text("activated", NamedTextColor.GRAY)));

        victim.setVelocity(new Vector(0, 0, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 7, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 128, false, false, true));
        root(victim, 60); // really pinned for 3 seconds, knockback and jumping included
        if (victim instanceof Player vp) vp.sendActionBar(Component.text("Trapped in fangs", w.color));

        Location at = victim.getLocation();
        World world = at.getWorld();
        world.spawn(at, EvokerFangs.class, f -> f.setOwner(p));
        for (int k = 0; k < 6; k++) {
            double ang = Math.toRadians(k * 60);
            Location ring = at.clone().add(Math.sin(ang) * 1.5, 0, Math.cos(ang) * 1.5);
            world.spawn(ring, EvokerFangs.class, f -> f.setOwner(p));
        }
        world.playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 15 || !victim.isValid()) {
                    cancel();
                    return;
                }
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 2.2, 0), 4, 0.2, 0.1, 0.2, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /** Pins the target in place for the given ticks: no walking, no jumping, no pearl or chorus escapes. */
    private void root(LivingEntity v, int ticks) {
        if (v instanceof Player pl) {
            rootedUntil.put(pl.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        } else if (v instanceof org.bukkit.entity.Mob m && m.hasAI()) {
            m.setAI(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.isValid()) m.setAI(true);
            }, ticks);
        }
    }

    private boolean isRooted(Player p) {
        Long end = rootedUntil.get(p.getUniqueId());
        if (end == null) return false;
        if (end <= System.currentTimeMillis()) {
            rootedUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRootedMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isRooted(p)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        // looking around and falling are still allowed
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && to.getY() <= from.getY()) return;
        Location pinned = from.clone();
        pinned.setYaw(to.getYaw());
        pinned.setPitch(to.getPitch());
        if (to.getY() < from.getY()) pinned.setY(to.getY());
        e.setTo(pinned);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRootedTeleport(PlayerTeleportEvent e) {
        if (!isRooted(e.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            e.setCancelled(true);
        }
    }

    private void frostStrike(Player p, LivingEntity victim) {
        if (!(victim instanceof Player v) || v == p) return;
        int count = hitCounts.merge(p.getUniqueId() + ":freeze", 1, Integer::sum);
        if (count < 3) {
            p.sendActionBar(Component.text("Frost hit " + count + "/3", NamedTextColor.AQUA));
            return;
        }
        hitCounts.put(p.getUniqueId() + ":freeze", 0);
        applyFreeze(v, 5);
        p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.3f);
        p.sendActionBar(Component.text("Freeze triggered", NamedTextColor.AQUA));
    }

    private void banHammer(Player p, Weapon w, Ability a, LivingEntity victim) {
        if (!plugin.getConfig().getBoolean("ban-hammer-enabled", true)) return;
        if (!(victim instanceof Player v) || v == p || v.isBanned()) return;

        long left = remaining(p, key(w, a));
        if (left > 0) {
            warnCooldown(p, w, a, left);
            return;
        }
        if (!banLock.add(v.getUniqueId())) return;

        String name = v.getName();
        startCooldown(p, w, a);
        p.setCooldown(Material.MACE, a.cooldown() * 20);
        p.sendActionBar(Component.text(a.name() + " ", w.color).append(Component.text("used", NamedTextColor.GRAY)));

        World world = v.getWorld();
        world.strikeLightningEffect(v.getLocation());
        world.playSound(v.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
        world.playSound(v.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
        world.playSound(v.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 1.2f);
        Bukkit.broadcast(Component.text("[Wepons] " + name + " was judged by the Ban Hammer!", NamedTextColor.RED));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + name + " Banned by Ban Hammer");
            banLock.remove(v.getUniqueId());
        }, 2L);
    }

    // ==================================================================
    // Jayden Trident + Monarch Crown
    // ==================================================================

    /** Riptide-style dash: two uses back to back, then a 60 second cooldown. */
    private boolean riptide(Player p, Weapon w, Ability a) {
        int used = charges.merge(p.getUniqueId(), 1, Integer::sum);

        p.setVelocity(p.getLocation().getDirection().multiply(2.2).add(new Vector(0, 0.15, 0)));
        World world = p.getWorld();
        world.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
        world.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 1.6f);

        // sonic booms left behind along the path
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 8 || !p.isOnline()) {
                    cancel();
                    return;
                }
                if (ticks % 2 == 0) {
                    p.getWorld().spawnParticle(Particle.SONIC_BOOM, p.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.8, 0), 3, 0.2, 0.2, 0.2, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        if (used >= 2) {
            charges.remove(p.getUniqueId());
            startCooldown(p, w, a, 60);
            p.sendActionBar(Component.text("Riptide Dash ", w.color).append(Component.text("2/2 - 60s cooldown", NamedTextColor.GRAY)));
        } else {
            p.sendActionBar(Component.text("Riptide Dash ", w.color).append(Component.text("1/2", NamedTextColor.GRAY)));
        }
        return true;
    }

    /** Every 5th hit: a trident falls on the target, 4 hearts of damage and a ring of fire. */
    private void skyTrident(Player p, Weapon w, LivingEntity victim) {
        String key = p.getUniqueId() + ":trident";
        int count = hitCounts.merge(key, 1, Integer::sum);
        if (count < 5) {
            p.sendActionBar(Component.text("Sky Trident " + count + "/5", w.color));
            return;
        }
        hitCounts.put(key, 0);

        World world = victim.getWorld();
        // Falling trident: uses the pack's falling_trident model (custom_model_data 9125),
        // stays point-down and faces the viewer, drops with an End Rod / Enchant trail
        // like the Paladin Axe slam, then hits with a dirt shockwave in impact().
        ItemStack tridentItem = new ItemStack(Material.TRIDENT);
        ItemMeta tm = tridentItem.getItemMeta();
        tm.setCustomModelData(9125);
        tridentItem.setItemMeta(tm);

        final double height = 12.0;
        final int fallTicks = 20; // 1 second, slow enough to see
        ItemDisplay trident = world.spawn(victim.getLocation().add(0, height, 0), ItemDisplay.class, d -> {
            d.setItemStack(tridentItem);
            d.setPersistent(false);
            d.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            d.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    new org.joml.AxisAngle4f(0, 0, 0, 1),
                    new org.joml.Vector3f(1.6f, 1.6f, 1.6f),
                    new org.joml.AxisAngle4f(0, 0, 0, 1)));
            d.setTeleportDuration(2);
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            d.setViewRange(2.0f);
        });
        world.playSound(victim.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 1.2f);
        world.playSound(victim.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 0.7f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                if (!victim.isValid()) {
                    trident.remove();
                    cancel();
                    return;
                }
                // accelerates like a real drop: slow start, fast finish
                double progress = Math.min(1.0, (t * t) / (double) (fallTicks * fallTicks));
                Location ground = victim.getLocation();
                Location at = ground.clone().add(0, height * (1.0 - progress) + 1.8, 0);
                trident.teleport(at);

                // glowing column marking the fall
                for (double y = 0; y < height * (1.0 - progress) + 1; y += 1.0) {
                    world.spawnParticle(Particle.END_ROD, ground.clone().add(0, y + 0.5, 0), 1, 0.1, 0.05, 0.1, 0);
                }
                world.spawnParticle(Particle.ENCHANT, at, 10, 0.4, 0.4, 0.4, 0.5);

                if (t >= fallTicks) {
                    trident.remove();
                    cancel();
                    impact(p, victim);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void impact(Player p, LivingEntity victim) {
        Location at = victim.getLocation();
        World world = at.getWorld();

        internal.add(p.getUniqueId());
        try {
            victim.damage(8, p); // 4 hearts
        } finally {
            internal.remove(p.getUniqueId());
        }

        world.playSound(at, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.8f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.3f);
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1, 0), 1);
        shockwave(p, victim, at);
        world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 0.4f, 1.6f);

        for (Entity en : world.getNearbyEntities(at, 3, 2, 3)) {
            if (en instanceof LivingEntity le && le != p) le.setFireTicks(100);
        }

        // ring of fire, burns for about 2 seconds
        new BukkitRunnable() {
            int pulses = 0;

            @Override
            public void run() {
                if (pulses++ >= 10) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 32; i++) {
                    double ang = Math.PI * 2 * i / 32;
                    Location ring = at.clone().add(Math.cos(ang) * 3, 0.2, Math.sin(ang) * 3);
                    world.spawnParticle(Particle.FLAME, ring, 1, 0.05, 0.1, 0.05, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /**
     * AltarSMP Paladin Axe "Earthshatter" shockwave: an orange ring expands 0.8 blocks every 2 ticks up to
     * 8 blocks while chunks of the ground fly up; everything caught by the ring takes 8 damage and is launched.
     */
    private void shockwave(Player owner, LivingEntity victim, Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0.5, 0.5, 0);
        Material under = center.clone().subtract(0, 0.5, 0).getBlock().getType();
        final org.bukkit.block.data.BlockData ground =
                (under.isBlock() && under.isSolid() ? under : Material.DIRT).createBlockData();
        final Particle.DustOptions dust = new Particle.DustOptions(Color.ORANGE, 1f);

        new BukkitRunnable() {
            double radius = 0;

            @Override
            public void run() {
                if (radius >= 8.0) {
                    cancel();
                    return;
                }
                radius += 0.8;
                for (int i = 0; i < 30; i++) {
                    double a = Math.PI * 2 * i / 30;
                    world.spawnParticle(Particle.DUST, center.getX() + radius * Math.cos(a), center.getY(),
                            center.getZ() + radius * Math.sin(a), 1, 0, 0, 0, 0, dust);
                }
                for (int i = 0; i < 20; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double r = radius * Math.random();
                    Location loc = new Location(world, center.getX() + r * Math.cos(a), center.getY(),
                            center.getZ() + r * Math.sin(a));
                    world.spawnParticle(Particle.BLOCK, loc, 2, 0.1, 0.1, 0.1, 0, ground);
                }
                for (Entity en : world.getNearbyEntities(center, radius, 3, radius)) {
                    if (!(en instanceof LivingEntity le) || le == owner || le == victim
                            || le instanceof org.bukkit.entity.ArmorStand) continue;
                    double d = le.getLocation().distance(center);
                    if (d < radius - 1 || d > radius + 0.5) continue;
                    internal.add(owner.getUniqueId());
                    try {
                        le.damage(8, owner);
                    } finally {
                        internal.remove(owner.getUniqueId());
                    }
                    le.setVelocity(le.getVelocity().add(new Vector(0, 0.8, 0)));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Every other player within 30 blocks glows for 10 seconds. */
    private boolean royalReveal(Player p) {
        Location at = p.getLocation();
        for (Player o : nearbyEnemies(p, at, 30)) {
            give(o, PotionEffectType.GLOWING, 200, 0);
            o.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, o.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.2);
            o.playSound(o.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.2f);
        }
        World world = p.getWorld();
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        world.playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 1.5f);
        for (int i = 0; i < 40; i++) {
            double ang = Math.PI * 2 * i / 40;
            world.spawnParticle(Particle.WAX_ON, at.clone().add(Math.cos(ang) * 3, 1, Math.sin(ang) * 3), 2, 0.1, 0.3, 0.1, 0);
        }
        return true;
    }

    // ==================================================================
    // Cleanup
    // ==================================================================

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        frozenUntil.remove(id);
        novaUntil.remove(id);
        e.getEntity().setFreezeTicks(0);
        rootedUntil.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        frozenUntil.remove(id);
        novaUntil.remove(id);
        banLock.remove(id);
        rootedUntil.remove(id);
        lastFire.remove(id);
        hitCounts.remove(id + ":scythe");
        hitCounts.remove(id + ":freeze");
    }
}
