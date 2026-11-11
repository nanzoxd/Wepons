package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animated 3D effects built from display entities: a ghost copy of the weapon's own model swinging, ice spikes,
 * bone fangs rising from the ground, flying debris, orbiting blades and a flying model for beams.
 *
 * Every display is non-persistent, lives well under a second or two, is removed on a timer and is also removed
 * when the plugin shuts down. They are visual only: no hitboxes, no damage.
 */
public final class DisplayFX {

    private final Wepons plugin;
    private final Set<Display> live = new HashSet<>();

    public DisplayFX(Wepons plugin) {
        this.plugin = plugin;
    }

    /** Removes anything still alive (called on disable). */
    public void cleanup() {
        for (Display d : new HashSet<>(live)) d.remove();
        live.clear();
    }

    // ==================================================================
    // building blocks
    // ==================================================================

    private static Quaternionf rotZ(double deg) {
        return new Quaternionf().rotateZ((float) Math.toRadians(deg));
    }

    private static Quaternionf rotY(double deg) {
        return new Quaternionf().rotateY((float) Math.toRadians(deg));
    }

    /** A transformation that scales a block around its bottom centre (a block display's origin is its corner). */
    private static Transformation centred(float sx, float sy, float sz, Quaternionf rot, float lift) {
        Vector3f t = rot.transform(new Vector3f(-sx / 2f, 0f, -sz / 2f));
        t.y += lift;
        return new Transformation(t, rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private BlockDisplay block(Location at, Material m, Transformation start) {
        BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, x -> {
            x.setBlock(m.createBlockData());
            x.setTransformation(start);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(0.6f);
            x.setPersistent(false);
        });
        live.add(d);
        return d;
    }

    private ItemDisplay item(Location at, ItemStack stack, Transformation start) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, x -> {
            x.setItemStack(stack);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            x.setTransformation(start);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(0.6f);
            x.setPersistent(false);
        });
        live.add(d);
        return d;
    }

    /** Moves a display to a new transformation over 'duration' ticks, starting 'delay' ticks from now. */
    private void animate(Display d, long delay, int duration, Transformation end) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(duration);
            d.setTransformation(end);
        }, delay);
    }

    private void removeLater(Display d, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            d.remove();
            live.remove(d);
        }, ticks);
    }

    private static ItemStack single(ItemStack src) {
        ItemStack c = src.clone();
        c.setAmount(1);
        return c;
    }

    // ==================================================================
    // effects
    // ==================================================================

    /** A ghost copy of the held weapon swings across the screen (full-sweep weapons). */
    public void ghostSwing(Player p, ItemStack weapon, boolean crit) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection().normalize();
        Location at = eye.clone().add(f.clone().multiply(1.4)).add(0, -0.3, 0);
        at.setDirection(f);
        float size = crit ? 1.9f : 1.5f;
        ItemDisplay d = item(at, single(weapon), new Transformation(
                new Vector3f(), rotZ(-75), new Vector3f(size, size, size), new Quaternionf()));
        animate(d, 1L, 3, new Transformation(
                new Vector3f(), rotZ(75), new Vector3f(size, size, size), new Quaternionf()));
        animate(d, 5L, 2, new Transformation(
                new Vector3f(), rotZ(95), new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf()));
        removeLater(d, 8L);
    }

    /** Ice spikes burst out of the ground in two rings and shatter (Glacier Blade, Frost Nova). */
    public void iceSpikes(Location center, double radius, int count) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = i / (double) count * Math.PI * 2.0 + rnd.nextDouble() * 0.3;
            double r = (i % 2 == 0 ? 0.55 : 1.0) * radius;
            Location at = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Quaternionf rot = rotY(rnd.nextDouble() * 90);
            float h = (float) (1.3 + rnd.nextDouble() * 1.4);
            Material m = i % 3 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE;
            BlockDisplay d = block(at, m, centred(0.05f, 0.05f, 0.05f, rot, 0f));
            long delay = 1L + (i % 4);
            animate(d, delay, 3, centred(0.38f, h, 0.38f, rot, 0f));
            animate(d, delay + 12L, 4, centred(0.02f, 0.02f, 0.02f, rot, 0f));
            removeLater(d, delay + 18L);
        }
    }

    /** Bone fangs rise out of the ground and sink back (Reaper Scythe). */
    public void boneFangs(Location center, double radius, int count) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = i / (double) count * Math.PI * 2.0;
            double r = (i % 2 == 0 ? 0.5 : 1.0) * radius;
            Location at = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Quaternionf rot = rotY(rnd.nextDouble() * 90);
            float h = (float) (1.0 + rnd.nextDouble() * 0.8);
            BlockDisplay d = block(at, Material.BONE_BLOCK, centred(0.3f, h, 0.3f, rot, -h));
            long delay = 1L + (i % 3) * 2L;
            animate(d, delay, 3, centred(0.3f, h, 0.3f, rot, 0f));
            animate(d, delay + 10L, 4, centred(0.3f, h, 0.3f, rot, -h));
            removeLater(d, delay + 16L);
        }
    }

    /** Chunks of the given block fly out, spin and fall (Berserker Blade, heavy hits). */
    public void debris(Location center, Material m, int count, double power) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            Vector dir = new Vector(Math.cos(a), 0, Math.sin(a)).multiply(power * (0.5 + rnd.nextDouble() * 0.7));
            float s = (float) (0.2 + rnd.nextDouble() * 0.2);
            Quaternionf spin = new Quaternionf().rotateXYZ((float) (rnd.nextDouble() * 6), (float) (rnd.nextDouble() * 6), (float) (rnd.nextDouble() * 6));
            BlockDisplay d = block(center, m, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            animate(d, 1L, 5, new Transformation(new Vector3f((float) dir.getX(), 1.2f + (float) rnd.nextDouble(), (float) dir.getZ()),
                    spin, new Vector3f(s, s, s), new Quaternionf()));
            animate(d, 7L, 6, new Transformation(new Vector3f((float) dir.getX() * 1.3f, 0f, (float) dir.getZ() * 1.3f),
                    spin, new Vector3f(s * 0.5f, s * 0.5f, s * 0.5f), new Quaternionf()));
            removeLater(d, 15L);
        }
    }

    /** Ghost copies of the weapon orbit the player for a moment (Berserker Blade, relic abilities). */
    public void orbitBlades(Player p, ItemStack weapon, int count, int ticks) {
        final ItemDisplay[] blades = new ItemDisplay[count];
        for (int i = 0; i < count; i++) {
            blades[i] = item(p.getLocation().add(0, 1.1, 0), single(weapon), new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
            blades[i].setTeleportDuration(2);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    for (ItemDisplay d : blades) {
                        d.remove();
                        live.remove(d);
                    }
                    cancel();
                    return;
                }
                Location base = p.getLocation().add(0, 1.1, 0);
                for (int i = 0; i < blades.length; i++) {
                    double a = t * 0.45 + i * Math.PI * 2.0 / blades.length;
                    Location l = base.clone().add(Math.cos(a) * 1.7, Math.sin(t * 0.2 + i) * 0.25, Math.sin(a) * 1.7);
                    l.setYaw((float) Math.toDegrees(-a));
                    blades[i].teleport(l);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Small dark blocks circle a point and rise, pulling inwards (Graviton Axe, Gravity Pulse). */
    public void gravityOrbit(Location center, int count, int ticks) {
        final BlockDisplay[] rocks = new BlockDisplay[count];
        for (int i = 0; i < count; i++) {
            float s = 0.25f + (i % 3) * 0.08f;
            rocks[i] = block(center, i % 2 == 0 ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN,
                    new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            rocks[i].setTeleportDuration(2);
        }
        World w = center.getWorld();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks) {
                    for (BlockDisplay d : rocks) {
                        d.remove();
                        live.remove(d);
                    }
                    cancel();
                    return;
                }
                double pull = 1.0 - t / (double) ticks;
                for (int i = 0; i < rocks.length; i++) {
                    double a = t * 0.35 + i * Math.PI * 2.0 / rocks.length;
                    double r = 0.6 + 3.0 * pull;
                    rocks[i].teleport(new Location(w, center.getX() + Math.cos(a) * r,
                            center.getY() + 0.3 + t * 0.06 + (i % 3) * 0.3, center.getZ() + Math.sin(a) * r));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A bright column that flashes where lightning lands (Thunder Blade). */
    public void lightningColumn(Location target) {
        Location at = target.clone();
        BlockDisplay d = block(at, Material.WHITE_CONCRETE, centred(0.45f, 14f, 0.45f, new Quaternionf(), 0f));
        animate(d, 2L, 4, centred(0.04f, 14f, 0.04f, new Quaternionf(), 0f));
        removeLater(d, 8L);
    }

    /** A copy of the weapon spins forward along the look direction (Void Fang beam). */
    public void flyingModel(Player p, ItemStack weapon, double distance) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection().normalize();
        Location at = eye.clone().add(f.clone().multiply(0.8)).add(0, -0.3, 0);
        at.setDirection(f);
        ItemDisplay d = item(at, single(weapon), new Transformation(
                new Vector3f(), rotZ(-90), new Vector3f(1.4f, 1.4f, 1.4f), new Quaternionf()));
        animate(d, 1L, 5, new Transformation(
                new Vector3f(0f, 0f, (float) distance), rotZ(90), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
        removeLater(d, 8L);
    }
}
