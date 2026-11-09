package com.wepons;

import com.wepons.Weapon.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abilities of the Relique blades (Fortune, Flame, Bloom) and the Altar SMP S2 legendaries
 * (Ancient Blade, Dragonrend, Tidebreaker, Wither Symbiote, Omen). Cooldowns are handled by
 * {@link Abilities}; this class only does what each power does.
 */
@SuppressWarnings("deprecation")
public final class Relics implements Listener {

    private record Infection(LivingEntity victim, BukkitTask task) {}

    private final Wepons plugin;
    private final WeaponRegistry reg;

    // windows
    private final Map<UUID, Long> foodFreeUntil = new HashMap<>();
    private final Map<UUID, Long> luckyUntil = new HashMap<>();
    private final Map<UUID, Long> presenceUntil = new HashMap<>();
    private final Map<UUID, Long> noAbsorbUntil = new HashMap<>();
    private final Map<UUID, Long> slashFrom = new HashMap<>();
    private final Map<UUID, Long> slashUntil = new HashMap<>();
    private final Map<UUID, Long> slashWindow = new HashMap<>();
    private final Map<UUID, Long> infectArmedUntil = new HashMap<>();
    private final Map<UUID, Long> conjuringUntil = new HashMap<>();
    private final Map<UUID, Long> witherLockUntil = new HashMap<>();

    // state
    private final Map<UUID, Integer> bloomHits = new HashMap<>();
    private final Map<UUID, Infection> infections = new HashMap<>();
    private final Map<UUID, BukkitTask> rampages = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    private final NamespacedKey heartsKey;
    private final NamespacedKey capKey;
    private final NamespacedKey rampageKey;

    public Relics(Wepons plugin) {
        this.plugin = plugin;
        this.reg = plugin.registry();
        this.heartsKey = new NamespacedKey(plugin, "bloom_hearts");
        this.capKey = new NamespacedKey(plugin, "bloom_cap");
        this.rampageKey = new NamespacedKey(plugin, "rampage_hearts");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private double num(String key, double def) {
        return plugin.getConfig().getDouble("tuning." + key, def);
    }

    private static boolean active(Map<UUID, Long> map, Player p) {
        Long end = map.get(p.getUniqueId());
        return end != null && end > System.currentTimeMillis();
    }

    private static void window(Map<UUID, Long> map, Player p, long millis) {
        map.put(p.getUniqueId(), System.currentTimeMillis() + millis);
    }

    private void give(LivingEntity e, PotionEffectType type, int ticks, int amp) {
        e.addPotionEffect(new PotionEffect(type, ticks, amp, false, true, true));
    }

    private void hurt(Player owner, LivingEntity victim, double damage) {
        busy.add(owner.getUniqueId());
        try {
            victim.damage(damage, owner);
        } finally {
            busy.remove(owner.getUniqueId());
        }
    }

    private void heal(Player p, double amount) {
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }

    private List<LivingEntity> enemies(Player p, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Vector c = center.toVector();
        for (Entity en : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || le == p || le instanceof ArmorStand || le.isDead()) continue;
            if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) continue;
            if (le.getBoundingBox().getCenter().distanceSquared(c) > radius * radius) continue;
            out.add(le);
        }
        return out;
    }

    private void knock(Location from, LivingEntity target, double strength, double up) {
        Vector v = target.getLocation().toVector().subtract(from.toVector());
        v.setY(0);
        if (v.lengthSquared() > 0) v.normalize();
        target.setVelocity(v.multiply(strength).setY(up));
    }

    private void ring(World w, Location c, double r, int points, Particle particle) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(particle, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private void bar(Player p, String text, net.kyori.adventure.text.format.TextColor color) {
        p.sendActionBar(Component.text(text, color));
    }

    private Location lookTarget(Player p, double range) {
        Block b = p.getTargetBlockExact((int) range);
        if (b != null) return b.getLocation().add(0.5, 1, 0.5);
        return p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(range));
    }

    private boolean isFree(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private ItemDisplay display(Location at, Material mat, float scale) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(mat));
            d.setPersistent(false);
            d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            d.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    new org.joml.AxisAngle4f(0, 0, 0, 1),
                    new org.joml.Vector3f(scale, scale, scale),
                    new org.joml.AxisAngle4f(0, 0, 0, 1)));
            d.setTeleportDuration(1);
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        });
    }

    private void setModifier(LivingEntity e, NamespacedKey key, double amount) {
        AttributeInstance inst = e.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        dropModifier(e, key);
        inst.addTransientModifier(new AttributeModifier(key, amount,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private void dropModifier(LivingEntity e, NamespacedKey key) {
        AttributeInstance inst = e.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(key)) inst.removeModifier(m);
        }
    }

    private boolean locked(Player p, String path, int def) {
        if (!plugin.getConfig().getBoolean("ancient-blade.progression", true)) return false;
        int need = plugin.getConfig().getInt("ancient-blade." + path, def);
        int kills = reg.kills(p.getInventory().getItemInMainHand());
        if (kills >= need) return false;
        bar(p, "Locked: needs " + need + " kills (" + kills + "/" + need + ")", NamedTextColor.RED);
        p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3f, 0.6f);
        return true;
    }

    private boolean unlocked(Player p, String path, int def) {
        if (!plugin.getConfig().getBoolean("ancient-blade.progression", true)) return true;
        return reg.kills(p.getInventory().getItemInMainHand()) >= plugin.getConfig().getInt("ancient-blade." + path, def);
    }

    // ==================================================================
    // Entry points used by Abilities
    // ==================================================================

    /** Called before the cooldown check. Returns true if the press was fully handled here. */
    public boolean preempt(Player p, Weapon w, Ability a) {
        if (a.key().equals("infection") && infections.containsKey(p.getUniqueId())) {
            detonate(p);
            return true;
        }
        return false;
    }

    /** Runs a power. Returning true starts its cooldown. */
    public boolean run(Player p, Weapon w, Ability a) {
        return switch (a.key()) {
            case "fortunefeast" -> fortuneFeast(p);
            case "fortunerolls" -> fortuneRolls(p);
            case "flamesweep" -> flameSweep(p);
            case "flameblast" -> flameBlast(p);
            case "bloomhearts" -> bloomHearts(p);
            case "bloomwither" -> bloomWilt(p);
            case "presence" -> presence(p);
            case "darkness" -> unyieldingDarkness(p);
            case "hypersonic" -> hyperonicSlash(p);
            case "infinitevoid" -> infiniteVoid(p);
            case "stormcall" -> stormcall(p);
            case "risingtide" -> risingTide(p);
            case "infection" -> infectionArm(p);
            case "rampage" -> rampage(p);
            case "vaultomen" -> vaultOmen(p);
            case "conjuring" -> conjuring(p);
            default -> false;
        };
    }

    /** Called once a second for every online player. */
    public void tick(Player p) {
        Weapon w = reg.of(p.getInventory().getItemInMainHand());
        long now = System.currentTimeMillis();

        if (w != null && w.id.equals("ancient_blade")) {
            Location feet = p.getLocation();
            boolean sculk = feet.getBlock().getType().name().contains("SCULK")
                    || feet.clone().subtract(0, 0.2, 0).getBlock().getType().name().contains("SCULK");
            if (sculk || unlocked(p, "shift-kills", 2)) give(p, PotionEffectType.SPEED, 60, 0);
            if (unlocked(p, "grip-kills", 4)) give(p, PotionEffectType.STRENGTH, 60, 1);
        }

        if (w != null && w.id.equals("wither_symbiote")) {
            Long lock = witherLockUntil.get(p.getUniqueId());
            if (lock == null || lock < now) p.removePotionEffect(PotionEffectType.WITHER);
        }

        Long noAbsorb = noAbsorbUntil.get(p.getUniqueId());
        if (noAbsorb != null) {
            if (noAbsorb > now) {
                p.setAbsorptionAmount(0);
                p.removePotionEffect(PotionEffectType.ABSORPTION);
            } else {
                noAbsorbUntil.remove(p.getUniqueId());
            }
        }
    }

    /** Called for every hit made with a weapon in the main hand. */
    public void onHit(Player attacker, Weapon w, LivingEntity victim, EntityDamageByEntityEvent e) {
        if (busy.contains(attacker.getUniqueId())) return;
        UUID id = attacker.getUniqueId();

        // Symbiote Rampage: lifesteal
        if (rampages.containsKey(id)) heal(attacker, 1.0);

        switch (w.id) {
            case "fortune_blade" -> {
                if (!active(luckyUntil, attacker)) return;
                double[] pool = {1.5, 2.0, 2.5, 3.0, 4.0};
                double m = pool[ThreadLocalRandom.current().nextInt(pool.length)];
                e.setDamage(e.getDamage() * m);
                bar(attacker, "Lucky Strike x" + m, NamedTextColor.GOLD);
                World world = victim.getWorld();
                world.spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), (int) (m * 6), 0.3, 0.5, 0.3, 0.2);
                world.spawnParticle(Particle.HAPPY_VILLAGER, victim.getLocation().add(0, 1.5, 0), 6, 0.3, 0.3, 0.3, 0);
                world.playSound(victim.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, (float) (0.8 + m / 4));
            }
            case "bloom_blade" -> {
                int count = bloomHits.merge(id, 1, Integer::sum);
                if (count % 10 != 0) return;
                heal(attacker, e.getFinalDamage());
                World world = attacker.getWorld();
                world.spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.4, 0), 8, 0.4, 0.4, 0.4, 0);
                world.spawnParticle(Particle.CHERRY_LEAVES, attacker.getLocation().add(0, 1.2, 0), 20, 0.5, 0.6, 0.5, 0.02);
                world.playSound(attacker.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
                bar(attacker, "Bloom: healed " + String.format("%.1f", e.getFinalDamage() / 2) + " hearts", NamedTextColor.LIGHT_PURPLE);
            }
            case "ancient_blade" -> {
                if (!active(presenceUntil, attacker) || !(victim instanceof Player target)) return;
                presenceUntil.remove(id);
                give(target, PotionEffectType.DARKNESS, 200, 0);
                window(noAbsorbUntil, target, 10_000);
                target.setAbsorptionAmount(0);
                target.removePotionEffect(PotionEffectType.ABSORPTION);
                hurt(attacker, target, num("presence-damage", 2.0));
                World world = target.getWorld();
                world.spawnParticle(Particle.SCULK_SOUL, target.getLocation().add(0, 1, 0), 25, 0.4, 0.7, 0.4, 0.05);
                world.playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f);
                bar(attacker, "Presence: target marked", NamedTextColor.AQUA);
            }
            case "wither_symbiote" -> {
                if (!active(infectArmedUntil, attacker)) return;
                infectArmedUntil.remove(id);
                infect(attacker, victim);
            }
            case "omen" -> {
                if (!active(conjuringUntil, attacker)) return;
                conjuringHit(attacker, victim);
            }
            default -> { }
        }
    }

    // ==================================================================
    // Listeners
    // ==================================================================

    /** Fortune Blade passive: 2x XP while holding it. */
    @EventHandler
    public void onExp(PlayerExpChangeEvent e) {
        Weapon w = reg.of(e.getPlayer().getInventory().getItemInMainHand());
        if (w == null || !w.id.equals("fortune_blade") || e.getAmount() <= 0) return;
        e.setAmount(e.getAmount() * 2);
        e.getPlayer().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getPlayer().getLocation().add(0, 1.2, 0), 4, 0.3, 0.3, 0.3, 0);
    }

    /** Fortune Blade ability 1: the food you eat is not consumed. */
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!active(foodFreeUntil, p) || !e.getItem().getType().isEdible()) return;

        boolean main = p.getInventory().getItemInMainHand().isSimilar(e.getItem());
        int slot = p.getInventory().getHeldItemSlot();
        ItemStack before = (main ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand()).clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (main) p.getInventory().setItem(slot, before);
            else p.getInventory().setItemInOffHand(before);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 6, 0.3, 0.3, 0.3, 0);
        });
    }

    /** Dragonrend Hyperonic Slash: swing while the window is open. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Long until = slashUntil.get(id);
        if (until == null) return;
        long now = System.currentTimeMillis();
        Weapon w = reg.of(p.getInventory().getItemInMainHand());
        if (until < now || w == null || !w.id.equals("dragonrend")) {
            if (until < now) endSlash(id);
            return;
        }
        if (now < slashFrom.getOrDefault(id, 0L)) return;

        Entity look = p.getTargetEntity(16);
        if (!(look instanceof LivingEntity target) || target == p) return;

        Vector behind = target.getLocation().getDirection().setY(0);
        if (behind.lengthSquared() > 0) behind.normalize();
        Location dest = target.getLocation().subtract(behind.multiply(1.6));
        if (!isFree(dest)) dest = target.getLocation();
        dest.setDirection(target.getLocation().toVector().subtract(dest.toVector()));

        World world = p.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
        for (double d = 0.5; d < p.getLocation().distance(dest); d += 0.5) {
            world.spawnParticle(Particle.PORTAL, p.getLocation().add(dest.toVector().subtract(p.getLocation().toVector()).normalize().multiply(d)).add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0);
        }
        p.teleport(dest);
        world.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0);
        world.spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
        world.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
        world.playSound(dest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        hurt(p, target, num("slash-damage", 10.0));

        long next = slashWindow.getOrDefault(id, 5000L) / 2;
        if (next < 600) {
            endSlash(id);
            bar(p, "Hyperonic Slash ended", NamedTextColor.LIGHT_PURPLE);
        } else {
            slashWindow.put(id, next);
            slashUntil.put(id, now + next);
            bar(p, String.format("Slash again: %.1fs", next / 1000.0), NamedTextColor.LIGHT_PURPLE);
        }
    }

    private void endSlash(UUID id) {
        slashUntil.remove(id);
        slashFrom.remove(id);
        slashWindow.remove(id);
    }

    /** Ancient Blade: every player kill counts. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        cleanup(dead);

        Player killer = dead.getKiller();
        if (killer == null || killer == dead) return;
        ItemStack item = killer.getInventory().getItemInMainHand();
        Weapon w = reg.of(item);
        if (w == null || !w.id.equals("ancient_blade")) return;

        int kills = reg.kills(item) + 1;
        reg.setKills(item, kills);
        killer.getInventory().setItemInMainHand(item);
        killer.getWorld().spawnParticle(Particle.SCULK_SOUL, dead.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
        killer.playSound(killer.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
        bar(killer, "Ancient Blade kills: " + kills, NamedTextColor.AQUA);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer());
    }

    private void cleanup(Player p) {
        UUID id = p.getUniqueId();
        for (Map<UUID, Long> m : List.of(foodFreeUntil, luckyUntil, presenceUntil, noAbsorbUntil, slashFrom,
                slashUntil, slashWindow, infectArmedUntil, conjuringUntil, witherLockUntil)) {
            m.remove(id);
        }
        bloomHits.remove(id);
        Infection inf = infections.remove(id);
        if (inf != null) inf.task().cancel();
        BukkitTask r = rampages.remove(id);
        if (r != null) r.cancel();
        dropModifier(p, heartsKey);
        dropModifier(p, rampageKey);
    }

    // ==================================================================
    // Fortune Blade
    // ==================================================================

    private boolean fortuneFeast(Player p) {
        window(foodFreeUntil, p, 30_000);
        World w = p.getWorld();
        w.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 25, 0.5, 0.7, 0.5, 0);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.2);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
        bar(p, "Endless Feast: food is not consumed for 30s", NamedTextColor.GOLD);
        return true;
    }

    private boolean fortuneRolls(Player p) {
        window(luckyUntil, p, 15_000);
        World w = p.getWorld();
        w.spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 40, 0.5, 0.7, 0.5, 0.8);
        w.spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1, 0), 20, 0.5, 0.7, 0.5, 0);
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        bar(p, "Lucky Strikes: random multipliers for 15s", NamedTextColor.GOLD);
        return true;
    }

    // ==================================================================
    // Flame Blade
    // ==================================================================

    private boolean flameSweep(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
        dir.normalize();
        World w = p.getWorld();

        for (LivingEntity le : enemies(p, p.getLocation().add(0, 1, 0), 4.5)) {
            Vector to = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (to.lengthSquared() > 0 && to.normalize().dot(dir) < 0.2) continue;
            hurt(p, le, num("flame-sweep-damage", 6.0));
            le.setFireTicks(100);
            Vector kb = dir.clone().multiply(1.9).setY(0.45);
            le.setVelocity(kb);
            w.spawnParticle(Particle.FLAME, le.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
        }
        // visible arc
        for (int deg = -70; deg <= 70; deg += 10) {
            Vector v = dir.clone().rotateAroundY(Math.toRadians(deg));
            for (double r = 1.5; r <= 4; r += 0.75) {
                Location at = p.getLocation().add(0, 1.1, 0).add(v.clone().multiply(r));
                w.spawnParticle(Particle.FLAME, at, 1, 0.05, 0.05, 0.05, 0.01);
            }
            if (deg % 40 == 0) {
                w.spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1.1, 0).add(v.multiply(2.5)), 1, 0, 0, 0, 0);
            }
        }
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        return true;
    }

    private boolean flameBlast(Player p) {
        Location center = p.getLocation();
        World w = center.getWorld();
        double radius = num("flame-blast-radius", 7.0);

        w.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 1);
        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        w.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.5f);
        w.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);

        for (LivingEntity le : enemies(p, center.clone().add(0, 1, 0), radius)) {
            hurt(p, le, num("flame-blast-damage", 10.0));
            le.setFireTicks(120);
            knock(center, le, 1.6, 0.6);
        }
        new BukkitRunnable() {
            double r = 0.5;

            @Override
            public void run() {
                if (r > radius || !p.isOnline()) {
                    cancel();
                    return;
                }
                ring(w, center.clone().add(0, 0.2, 0), r, (int) (r * 10) + 10, Particle.FLAME);
                ring(w, center.clone().add(0, 0.6, 0), r * 0.8, (int) (r * 6) + 6, Particle.LAVA);
                w.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, 0.5, 0), 4, r * 0.4, 0.3, r * 0.4, 0.02);
                r += 0.9;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ==================================================================
    // Bloom Blade
    // ==================================================================

    private boolean bloomHearts(Player p) {
        setModifier(p, heartsKey, 10.0); // 10 hp = 5 hearts, 20 -> 30 = 15 hearts
        p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), p.getHealth() + 10));
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        bar(p, "Overgrowth: 15 hearts for 15s", NamedTextColor.LIGHT_PURPLE);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 300 || !p.isOnline() || p.isDead()) {
                    cancel();
                    dropModifier(p, heartsKey);
                    return;
                }
                if (t % 4 == 0) {
                    w.spawnParticle(Particle.CHERRY_LEAVES, p.getLocation().add(0, 1, 0), 3, 0.5, 0.8, 0.5, 0.01);
                    if (t % 20 == 0) w.spawnParticle(Particle.HEART, p.getLocation().add(0, 2.1, 0), 1, 0.2, 0.1, 0.2, 0);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    private boolean bloomWilt(Player p) {
        double capHp = num("wilt-health-cap", 10.0);
        World w = p.getWorld();
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity le : enemies(p, p.getLocation().add(0, 1, 0), 8)) {
            AttributeInstance inst = le.getAttribute(Attribute.MAX_HEALTH);
            if (inst == null) continue;
            dropModifier(le, capKey);
            double max = inst.getValue();
            if (max > capHp) setModifier(le, capKey, capHp - max);
            hit.add(le);
            w.spawnParticle(Particle.ASH, le.getLocation().add(0, 1, 0), 25, 0.4, 0.7, 0.4, 0.02);
            w.spawnParticle(Particle.CHERRY_LEAVES, le.getLocation().add(0, 1.6, 0), 12, 0.4, 0.4, 0.4, 0.02);
            if (le instanceof Player pl) bar(pl, "Wilted: max health capped", NamedTextColor.LIGHT_PURPLE);
        }
        ring(w, p.getLocation().add(0, 0.2, 0), 8, 60, Particle.CHERRY_LEAVES);
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 1.6f);
        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.6f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity le : hit) dropModifier(le, capKey);
        }, 200L);
        return true;
    }

    // ==================================================================
    // Ancient Blade
    // ==================================================================

    private boolean presence(Player p) {
        if (locked(p, "presence-kills", 3)) return false;
        window(presenceUntil, p, 5_000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.2f, 0.9f);
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.2f);
        for (int i = 0; i < 3; i++) {
            final double r = 1 + i;
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    ring(w, p.getLocation().add(0, 0.2, 0), r + 1, 30, Particle.SCULK_CHARGE_POP), i * 4L);
        }
        bar(p, "Presence: hit a player within 5s", NamedTextColor.AQUA);
        return true;
    }

    private boolean unyieldingDarkness(Player p) {
        if (locked(p, "darkness-kills", 5)) return false;
        World w = p.getWorld();
        p.setVelocity(new Vector(0, 1.3, 0));
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
        w.spawnParticle(Particle.SCULK_SOUL, p.getLocation(), 30, 0.4, 0.2, 0.4, 0.05);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > 14) {
                    cancel();
                    if (p.isOnline()) throwCatalyst(p);
                    return;
                }
                w.spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0.02);
                if (t > 6) { // pause in the air
                    p.setVelocity(new Vector(0, 0.04, 0));
                    p.setFallDistance(0);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void throwCatalyst(Player p) {
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location at = p.getEyeLocation().add(dir.clone().multiply(1.5));
        ItemDisplay catalyst = display(at, Material.SCULK_CATALYST, 1.4f);
        w.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.4f);

        new BukkitRunnable() {
            int t = 0;
            float spin = 0;

            @Override
            public void run() {
                t++;
                at.add(dir.clone().multiply(1.3));
                spin += 40;
                catalyst.teleport(at);
                catalyst.setRotation(spin, spin / 2);
                w.spawnParticle(Particle.SCULK_SOUL, at, 3, 0.2, 0.2, 0.2, 0.01);
                w.spawnParticle(Particle.SCULK_CHARGE_POP, at, 2, 0.3, 0.3, 0.3, 0);

                boolean hitBlock = !at.getBlock().isPassable();
                boolean hitEntity = !enemies(p, at, 1.6).isEmpty();
                if (hitBlock || hitEntity || t > 24 || !p.isOnline()) {
                    cancel();
                    catalyst.remove();
                    if (p.isOnline()) catalystImpact(p, at.clone().subtract(dir.clone().multiply(hitBlock ? 1.3 : 0)));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void catalystImpact(Player p, Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.SCULK_SOUL, at, 60, 1.2, 0.8, 1.2, 0.08);
        w.spawnParticle(Particle.EXPLOSION, at, 1);
        w.playSound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.5f, 0.7f);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        for (double r = 1; r <= 6; r += 1.5) ring(w, at.clone().add(0, 0.1, 0), r, (int) (r * 10), Particle.SCULK_CHARGE_POP);

        for (LivingEntity le : enemies(p, at, 6)) hurt(p, le, num("darkness-damage", 12.0));

        // splits into sonic boom bursts
        final double split = num("darkness-split-damage", 3.0);
        for (int i = 0; i < 6; i++) {
            double ang = Math.PI * 2 * i / 6;
            Location burst = at.clone().add(Math.cos(ang) * 4, 0.5, Math.sin(ang) * 4);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                w.spawnParticle(Particle.SONIC_BOOM, burst, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.SCULK_SOUL, burst, 10, 0.3, 0.3, 0.3, 0.03);
                w.playSound(burst, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.6f);
                if (p.isOnline()) for (LivingEntity le : enemies(p, burst, 1.6)) hurt(p, le, split);
            }, 6L + i * 2L);
        }
    }

    // ==================================================================
    // Dragonrend
    // ==================================================================

    private boolean hyperonicSlash(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        slashWindow.put(id, 5000L);
        slashFrom.put(id, now + 500);
        slashUntil.put(id, now + 500 + 5000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.7f);
        w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 50, 0.4, 0.8, 0.4, 0.8);
        bar(p, "Hyperonic Slash: swing at a player within 16 blocks", NamedTextColor.LIGHT_PURPLE);
        return true;
    }

    private boolean infiniteVoid(Player p) {
        Location center = p.getLocation().clone();
        World w = center.getWorld();
        double radius = num("void-radius", 6.0);
        ItemDisplay clock = display(center.clone().add(0, 1.6, 0), Material.CLOCK, 3f);
        w.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
        w.playSound(center, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.6f, 0.6f);

        new BukkitRunnable() {
            int t = 0;
            float spin = 0;

            @Override
            public void run() {
                if (t >= 200 || !p.isOnline()) {
                    cancel();
                    clock.remove();
                    w.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 1, 0), 60, radius / 2, 0.8, radius / 2, 0.3);
                    return;
                }
                spin += 18;
                clock.setRotation(spin, 0);
                ring(w, center.clone().add(0, 0.15, 0), radius, 48, Particle.REVERSE_PORTAL);
                if (t % 4 == 0) {
                    ring(w, center.clone().add(0, 1.0, 0), radius * (0.3 + 0.7 * ((t % 40) / 40.0)), 24, Particle.PORTAL);
                }
                if (t % 10 == 0) {
                    for (LivingEntity le : enemies(p, center.clone().add(0, 1, 0), radius)) {
                        give(le, PotionEffectType.SLOWNESS, 30, 3);
                        give(le, PotionEffectType.MINING_FATIGUE, 30, 1);
                    }
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    // ==================================================================
    // Tidebreaker
    // ==================================================================

    private boolean stormcall(Player p) {
        Location center = lookTarget(p, 30);
        World w = center.getWorld();
        double radius = num("storm-radius", 6.0);
        double dmg = num("stormcall-damage", 3.0);
        w.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 0.8f);
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.1f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 20 || !p.isOnline()) {
                    cancel();
                    return;
                }
                t++;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 14; i++) {
                    Location c = center.clone().add(r.nextDouble(-radius, radius), 8 + r.nextDouble(2), r.nextDouble(-radius, radius));
                    w.spawnParticle(Particle.CLOUD, c, 2, 0.4, 0.1, 0.4, 0);
                    w.spawnParticle(Particle.FALLING_WATER, c, 2, 0.3, 0.1, 0.3, 0);
                }
                ring(w, center.clone().add(0, 0.2, 0), radius, 40, Particle.DRIPPING_WATER);

                List<LivingEntity> targets = enemies(p, center, radius);
                targets.sort(Comparator.comparingDouble(LivingEntity::getHealth).reversed());
                for (int i = 0; i < Math.min(2, targets.size()); i++) {
                    LivingEntity le = targets.get(i);
                    w.strikeLightningEffect(le.getLocation());
                    w.spawnParticle(Particle.ELECTRIC_SPARK, le.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.2);
                    hurt(p, le, dmg);
                }
                if (t % 4 == 0) w.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.4f);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    private boolean risingTide(Player p) {
        World w = p.getWorld();
        if (p.getLocation().getPitch() <= -30) {
            // jet of water, then a tidal shockwave on landing
            p.setVelocity(new Vector(0, 1.7, 0));
            w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.8f);
            w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    if (!p.isOnline() || t++ > 100) {
                        cancel();
                        return;
                    }
                    Location l = p.getLocation();
                    w.spawnParticle(Particle.BUBBLE_COLUMN_UP, l, 8, 0.3, 0.3, 0.3, 0.1);
                    w.spawnParticle(Particle.SPLASH, l.clone().add(0, 0.5, 0), 10, 0.4, 0.5, 0.4, 0.2);
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -1; y <= 2; y++) {
                            for (int z = -2; z <= 2; z++) {
                                Block b = l.clone().add(x, y, z).getBlock();
                                if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
                            }
                        }
                    }
                    if (t > 8 && p.isOnGround()) {
                        cancel();
                        tidalShockwave(p);
                    }
                }
            }.runTaskTimer(plugin, 2L, 1L);
        } else {
            // long wave that pulls enemies toward you
            Location start = p.getLocation().add(0, 0.5, 0);
            Vector dir = p.getEyeLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
            final Vector flat = dir.normalize();
            w.playSound(start, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.7f);
            new BukkitRunnable() {
                int t = 0;
                final Location at = start.clone();

                @Override
                public void run() {
                    if (!p.isOnline() || t++ > 24) {
                        cancel();
                        return;
                    }
                    at.add(flat.clone().multiply(0.9));
                    Vector side = new Vector(-flat.getZ(), 0, flat.getX());
                    for (double s = -2; s <= 2; s += 0.5) {
                        Location c = at.clone().add(side.clone().multiply(s));
                        w.spawnParticle(Particle.SPLASH, c, 3, 0.1, 0.3, 0.1, 0.1);
                        w.spawnParticle(Particle.BUBBLE_POP, c.clone().add(0, 0.8, 0), 1, 0.1, 0.2, 0.1, 0);
                        w.spawnParticle(Particle.FALLING_WATER, c.clone().add(0, 1.3, 0), 1, 0.1, 0.1, 0.1, 0);
                    }
                    for (LivingEntity le : enemies(p, at.clone().add(0, 1, 0), 2.6)) {
                        Vector pull = p.getLocation().toVector().subtract(le.getLocation().toVector());
                        pull.setY(0);
                        if (pull.lengthSquared() > 0) pull.normalize();
                        le.setVelocity(pull.multiply(1.1).setY(0.25));
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        return true;
    }

    private void tidalShockwave(Player p) {
        Location center = p.getLocation();
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.6f);
        w.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.3f);
        for (LivingEntity le : enemies(p, center.clone().add(0, 1, 0), 6)) {
            hurt(p, le, num("tide-damage", 4.0));
            knock(center, le, 1.8, 0.5);
        }
        new BukkitRunnable() {
            double r = 0.5;

            @Override
            public void run() {
                if (r > 7) {
                    cancel();
                    return;
                }
                ring(w, center.clone().add(0, 0.3, 0), r, (int) (r * 10) + 8, Particle.SPLASH);
                ring(w, center.clone().add(0, 0.6, 0), r, (int) (r * 6) + 6, Particle.BUBBLE_POP);
                r += 0.9;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================================================================
    // Wither Symbiote
    // ==================================================================

    private boolean infectionArm(Player p) {
        window(infectArmedUntil, p, 10_000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.6f);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1.2, 0), 25, 0.4, 0.5, 0.4, 0.03);
        bar(p, "Infection ready: your next hit infects", NamedTextColor.DARK_PURPLE);
        return true;
    }

    private void infect(Player owner, LivingEntity victim) {
        UUID id = owner.getUniqueId();
        Infection old = infections.remove(id);
        if (old != null) old.task().cancel();

        hurt(owner, victim, num("infection-initial-damage", 2.0));
        World w = victim.getWorld();
        w.playSound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 0.7f);
        bar(owner, "Infected! Press F again to detonate", NamedTextColor.DARK_PURPLE);
        if (victim instanceof Player vp) bar(vp, "You are infected", NamedTextColor.DARK_PURPLE);

        BukkitTask task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!victim.isValid() || !owner.isOnline() || t >= 160) {
                    if (infections.containsKey(id) && infections.get(id).victim() == victim) {
                        if (owner.isOnline() && victim.isValid()) detonate(owner);
                        else infections.remove(id);
                    }
                    cancel();
                    return;
                }
                t += 4;
                w.spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 3, 0.3, 0.6, 0.3, 0.01);
                w.spawnParticle(Particle.SMOKE, victim.getLocation().add(0, 1.4, 0), 2, 0.3, 0.5, 0.3, 0.01);
            }
        }.runTaskTimer(plugin, 4L, 4L);
        infections.put(id, new Infection(victim, task));
    }

    private void detonate(Player owner) {
        Infection inf = infections.remove(owner.getUniqueId());
        if (inf == null) return;
        inf.task().cancel();
        LivingEntity v = inf.victim();
        if (!v.isValid()) return;
        World w = v.getWorld();
        Location at = v.getLocation().add(0, 1, 0);
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 50, 0.5, 0.8, 0.5, 0.15);
        w.spawnParticle(Particle.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
        w.spawnParticle(Particle.SQUID_INK, at, 30, 0.5, 0.8, 0.5, 0.1);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        w.playSound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 1.2f);
        give(v, PotionEffectType.WEAKNESS, 200, 0);
        hurt(owner, v, num("infection-detonation-damage", 8.0));
    }

    private boolean rampage(Player p) {
        UUID id = p.getUniqueId();
        World w = p.getWorld();
        setModifier(p, rampageKey, 6.0); // +3 hearts
        heal(p, 6.0);
        give(p, PotionEffectType.SPEED, 400, 1);
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.5f);
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.8f, 1.2f);
        bar(p, "Rampage!", NamedTextColor.DARK_PURPLE);

        BukkitTask task = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 400 || !p.isOnline() || p.isDead()) {
                    cancel();
                    endRampage(p, t >= 400 && p.isOnline() && !p.isDead());
                    return;
                }
                t += 4;
                Location l = p.getLocation();
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.clone().add(0, 1, 0), 4, 0.4, 0.8, 0.4, 0.02);
                w.spawnParticle(Particle.SQUID_INK, l.clone().add(0, 1.2, 0), 2, 0.4, 0.6, 0.4, 0.01);
                p.setExhaustion(p.getExhaustion() + 0.25f);
                for (int x = -3; x <= 3; x++) {
                    for (int y = -1; y <= 3; y++) {
                        for (int z = -3; z <= 3; z++) {
                            Block b = l.clone().add(x, y, z).getBlock();
                            if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);
        rampages.put(id, task);
        return true;
    }

    private void endRampage(Player p, boolean natural) {
        rampages.remove(p.getUniqueId());
        dropModifier(p, rampageKey);
        if (!natural || !p.isOnline()) return;
        give(p, PotionEffectType.WEAKNESS, 300, 0);
        give(p, PotionEffectType.WITHER, 100, 0);
        window(witherLockUntil, p, 8_000);
        p.setHealth(Math.max(1.0, p.getHealth() - 6.0));
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.6f);
        bar(p, "Rampage ended: you are drained", NamedTextColor.GRAY);
    }

    // ==================================================================
    // Omen
    // ==================================================================

    private boolean vaultOmen(Player p) {
        Location center = lookTarget(p, 24);
        World w = center.getWorld();
        ItemDisplay key = display(center.clone().add(0, 1.2, 0), Material.OMINOUS_TRIAL_KEY, 1.6f);
        w.playSound(center, Sound.BLOCK_VAULT_ACTIVATE, 1.2f, 0.6f);
        w.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.6f);
        Set<UUID> tethered = new HashSet<>();

        new BukkitRunnable() {
            int t = 0;
            float spin = 0;

            @Override
            public void run() {
                if (t >= 140 || !p.isOnline()) {
                    cancel();
                    key.remove();
                    w.spawnParticle(Particle.TRIAL_OMEN, center.clone().add(0, 1, 0), 30, 1, 0.8, 1, 0.05);
                    return;
                }
                spin += 14;
                key.setRotation(spin, 0);
                ring(w, center.clone().add(0, 0.15, 0), 3, 30, Particle.SOUL_FIRE_FLAME);
                w.spawnParticle(Particle.TRIAL_OMEN, center.clone().add(0, 1.2, 0), 2, 0.3, 0.3, 0.3, 0);

                for (LivingEntity le : enemies(p, center.clone().add(0, 1, 0), 12)) {
                    boolean inside = le.getLocation().distance(center) <= 3;
                    if (inside && le instanceof Player) {
                        if (tethered.add(le.getUniqueId())) {
                            le.getWorld().playSound(le.getLocation(), Sound.BLOCK_VAULT_INSERT_ITEM, 1f, 0.7f);
                            bar((Player) le, "Tethered by the Vault Omen", NamedTextColor.DARK_AQUA);
                        }
                    }
                    if (tethered.contains(le.getUniqueId()) && le.getLocation().distance(center) > 3) {
                        Vector pull = center.toVector().subtract(le.getLocation().toVector()).setY(0.1);
                        if (pull.lengthSquared() > 0) pull.normalize();
                        le.setVelocity(pull.multiply(0.55));
                        Location from = center.clone().add(0, 1, 0);
                        Vector line = le.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
                        double len = line.length();
                        if (len > 0) {
                            line.normalize();
                            for (double d = 0; d < len; d += 0.7) {
                                w.spawnParticle(Particle.VAULT_CONNECTION, from.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    private boolean conjuring(Player p) {
        window(conjuringUntil, p, 10_000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.BLOCK_VAULT_ACTIVATE, 1f, 0.8f);
        bar(p, "Ominous Conjuring: your hits throw omens for 10s", NamedTextColor.DARK_AQUA);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 200 || !p.isOnline() || !active(conjuringUntil, p)) {
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.TRIAL_OMEN, p.getLocation().add(0, 1, 0), 3, 0.5, 0.8, 0.5, 0);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 0.2, 0), 2, 0.5, 0.1, 0.5, 0.01);
                t += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
        return true;
    }

    private void conjuringHit(Player attacker, LivingEntity victim) {
        int others = 0;
        for (Player o : attacker.getWorld().getPlayers()) {
            if (o != attacker && o.getGameMode() != GameMode.SPECTATOR
                    && o.getLocation().distanceSquared(attacker.getLocation()) <= 16 * 16) others++;
        }
        int level = others <= 1 ? 1 : others <= 4 ? 2 : 3;
        World w = victim.getWorld();
        Location at = victim.getLocation().add(0, 1, 0);

        // projectile trail from the attacker to the target
        Vector line = at.toVector().subtract(attacker.getEyeLocation().toVector());
        double len = line.length();
        if (len > 0) {
            line.normalize();
            for (double d = 0.5; d < len; d += 0.5) {
                Location pt = attacker.getEyeLocation().add(line.clone().multiply(d));
                w.spawnParticle(Particle.TRIAL_OMEN, pt, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        w.spawnParticle(Particle.OMINOUS_SPAWNING, at, 30, 0.8, 0.8, 0.8, 0.1);
        ring(w, victim.getLocation().add(0, 0.2, 0), 2.5 + level * 0.5, 30, Particle.SOUL_FIRE_FLAME);
        w.playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.8f);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);

        for (LivingEntity le : enemies(attacker, at, 2.5 + level * 0.5)) {
            if (le == victim) continue;
            hurt(attacker, le, num("conjuring-damage", 6.0));
        }
        if (level >= 2 && victim instanceof Player vp && vp.isBlocking()) {
            vp.clearActiveItem();
            vp.setCooldown(Material.SHIELD, 100);
        }
        if (level == 1) {
            give(attacker, PotionEffectType.SPEED, 20, 1);
        } else if (level == 3 && !attacker.isSneaking()) {
            Vector pull = at.toVector().subtract(attacker.getLocation().toVector()).setY(0.1);
            if (pull.lengthSquared() > 0) attacker.setVelocity(pull.normalize().multiply(0.9));
        }
        bar(attacker, "Spell Level " + level, NamedTextColor.DARK_AQUA);
    }
}
