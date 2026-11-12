package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
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
 * Animated 3D effects built from display entities: ghost weapon swings, dash afterimages, ice spikes and bone fangs
 * (tapered, stacked blocks), debris and a blood ring on the ground, orbiting blades, a rope for pulls, and the
 * Impact Bubble.
 *
 * Everything is non-persistent, short-lived, removed on a timer and removed again when the plugin shuts down.
 * Models that would sit in the owner's face (ghost weapon, orbiting blades, afterimages, flying model) are hidden from
 * the owner and still seen by everyone else. Visual only: no hitboxes, no damage.
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

    private static Transformation scaled(float s) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }

    /** Scales a block around its bottom centre (a block display's origin is its corner). */
    private static Transformation centred(float sx, float sy, float sz, Quaternionf rot, float lift) {
        Vector3f t = rot.transform(new Vector3f(-sx / 2f, 0f, -sz / 2f));
        t.y += lift;
        return new Transformation(t, rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /** A cube of the given size whose CENTRE is the display's position. */
    private static Transformation cube(float size, Quaternionf rot) {
        Vector3f t = rot.transform(new Vector3f(-size / 2f, -size / 2f, -size / 2f));
        return new Transformation(t, rot, new Vector3f(size, size, size), new Quaternionf());
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

    private ItemDisplay item(Location at, ItemStack stack, Transformation start, Player hideFrom) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, x -> {
            x.setItemStack(stack);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            x.setTransformation(start);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(0.6f);
            x.setPersistent(false);
        });
        if (hideFrom != null) hideFrom.hideEntity(plugin, d);   // never in the owner's face
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

    private void removeNow(Display d) {
        d.remove();
        live.remove(d);
    }

    private static ItemStack single(ItemStack src) {
        ItemStack c = src.clone();
        c.setAmount(1);
        return c;
    }

    // ==================================================================
    // weapon model effects
    // ==================================================================

    /** A ghost copy of the held weapon swings across, with two fading smear copies. Hidden from the swinger. */
    public void ghostSwing(Player p, ItemStack weapon, boolean crit) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection().normalize();
        Location at = eye.clone().add(f.clone().multiply(2.0)).add(0, -0.5, 0);
        at.setDirection(f);
        float size = crit ? 2.2f : 1.8f;
        for (int i = 0; i < 3; i++) {
            float sc = size * (1.0f - i * 0.18f);
            double from = -80 - i * 14;
            double to = 80 - i * 22;
            ItemDisplay d = item(at, single(weapon), new Transformation(
                    new Vector3f(), rotZ(from), new Vector3f(sc, sc, sc), new Quaternionf()), p);
            animate(d, 1L + i, 3, new Transformation(
                    new Vector3f(), rotZ(to), new Vector3f(sc, sc, sc), new Quaternionf()));
            animate(d, 5L + i, 2, new Transformation(
                    new Vector3f(), rotZ(to + 15), new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf()));
            removeLater(d, 8L + i);
        }
    }

    /** Ghost copies of the weapon trail behind a dash and shrink away (Rift Blade, dashes). Hidden from the dasher. */
    public void dashImages(Player p, ItemStack weapon, int ticks) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    cancel();
                    return;
                }
                Vector v = p.getVelocity();
                Location l = p.getLocation().add(0, 0.95, 0);
                if (v.lengthSquared() > 0.01) l.setDirection(v);
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                if (t % 2 == 0) {
                    ItemDisplay d = item(l, single(weapon), new Transformation(
                            new Vector3f(), rotZ(rnd.nextDouble(-50, 50)), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()), p);
                    animate(d, 1L, 6, new Transformation(
                            new Vector3f(), rotZ(rnd.nextDouble(-90, 90)), new Vector3f(0.1f, 0.1f, 0.1f), new Quaternionf()));
                    removeLater(d, 8L);
                }
                // thin speed streaks along the direction of travel
                for (int k = 0; k < 2; k++) {
                    Location s = l.clone().add(rnd.nextDouble(-0.6, 0.6), rnd.nextDouble(-0.5, 0.7), rnd.nextDouble(-0.6, 0.6));
                    BlockDisplay line = block(s, Material.WHITE_CONCRETE,
                            centred(0.04f, 0.04f, 1.6f, new Quaternionf(), 0f));
                    line.setTransformation(streak(1.6f));
                    animate(line, 1L, 4, streak(0.05f));
                    removeLater(line, 6L);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A thin box along the display's forward (+z) direction, centred on its position. */
    private static Transformation streak(float len) {
        return new Transformation(new Vector3f(-0.02f, -0.02f, -len / 2f), new Quaternionf(),
                new Vector3f(0.04f, 0.04f, len), new Quaternionf());
    }

    /** One fading copy of the weapon at a spot (the start of a Blink). */
    public void afterimage(Location at, ItemStack weapon, Player owner) {
        Location l = at.clone().add(0, 0.95, 0);
        ItemDisplay d = item(l, single(weapon), new Transformation(
                new Vector3f(), rotZ(-20), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()), null);
        animate(d, 1L, 8, new Transformation(
                new Vector3f(), rotZ(40), new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf()));
        removeLater(d, 11L);
    }

    /** Ghost copies of the weapon orbit the player for a moment. Hidden from the owner. */
    public void orbitBlades(Player p, ItemStack weapon, int count, int ticks) {
        final ItemDisplay[] blades = new ItemDisplay[count];
        for (int i = 0; i < count; i++) {
            blades[i] = item(p.getLocation().add(0, 1.1, 0), single(weapon), new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()), p);
            blades[i].setTeleportDuration(2);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    for (ItemDisplay d : blades) removeNow(d);
                    cancel();
                    return;
                }
                Location base = p.getLocation().add(0, 1.1, 0);
                for (int i = 0; i < blades.length; i++) {
                    double a = t * 0.45 + i * Math.PI * 2.0 / blades.length;
                    Location l = base.clone().add(Math.cos(a) * 1.8, Math.sin(t * 0.2 + i) * 0.3, Math.sin(a) * 1.8);
                    l.setYaw((float) Math.toDegrees(-a));
                    blades[i].teleport(l);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A copy of the weapon spins forward along the look direction (Void Fang beam). Hidden from the caster. */
    public void flyingModel(Player p, ItemStack weapon, double distance) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection().normalize();
        Location at = eye.clone().add(f.clone().multiply(1.0)).add(0, -0.4, 0);
        at.setDirection(f);
        ItemDisplay d = item(at, single(weapon), new Transformation(
                new Vector3f(), rotZ(-90), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()), p);
        animate(d, 1L, 5, new Transformation(
                new Vector3f(0f, 0f, (float) distance), rotZ(90), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
        removeLater(d, 8L);
    }

    // ==================================================================
    // ground and block effects
    // ==================================================================

    /** One tapered spike: three stacked blocks that get thinner towards the tip, rising out of the ground. */
    private void spike(Location base, Material[] mats, float width, float height, Quaternionf rot,
                       long delay, int hold, boolean fromBelow) {
        float seg = height / 3f;
        for (int i = 0; i < 3; i++) {
            float w = width * (1.0f - i * 0.3f);
            float lift = i * seg;
            float hidden = fromBelow ? lift - height : lift;
            BlockDisplay d = block(base, mats[i], centred(w, seg * 1.05f, w, rot, hidden));
            if (!fromBelow) d.setTransformation(centred(0.02f, 0.02f, 0.02f, rot, lift));
            animate(d, delay + i, 3, centred(w, seg * 1.05f, w, rot, lift));
            animate(d, delay + hold, 4, fromBelow
                    ? centred(w, seg * 1.05f, w, rot, lift - height)
                    : centred(0.02f, 0.02f, 0.02f, rot, lift));
            removeLater(d, delay + hold + 8L);
        }
    }

    /** A ring (two rings) of tapered spikes made of the given blocks; fromBelow = rise out of the ground. */
    public void spikes(Location center, double radius, int count, Material[] mats, float width,
                       float minH, float maxH, boolean fromBelow, int hold) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Material[] three = {mats[0], mats[Math.min(1, mats.length - 1)], mats[Math.min(2, mats.length - 1)]};
        for (int i = 0; i < count; i++) {
            double a = i / (double) count * Math.PI * 2.0 + rnd.nextDouble() * 0.3;
            double r = (i % 2 == 0 ? 0.55 : 1.0) * radius;
            Location at = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            spike(at, three, width, (float) (minH + rnd.nextDouble() * (maxH - minH)), rotY(rnd.nextDouble() * 90),
                    1L + (i % 4), hold, fromBelow);
        }
    }

    /** Ice spikes burst out of the ground in two rings and shatter (Glacier Blade, Frost Nova). */
    public void iceSpikes(Location center, double radius, int count) {
        spikes(center, radius, count, new Material[]{Material.PACKED_ICE, Material.BLUE_ICE, Material.ICE},
                0.5f, 1.4f, 2.9f, false, 12);
    }

    /** Bone fangs rise out of the ground and sink back (Reaper Scythe). */
    public void boneFangs(Location center, double radius, int count) {
        spikes(center, radius, count, new Material[]{Material.BONE_BLOCK, Material.BONE_BLOCK, Material.WHITE_CONCRETE},
                0.45f, 1.1f, 2.0f, true, 10);
    }

    /** Big glass cubes burst out, tumble in the air and fade (the cubes in the reference footage). */
    public void tumbleCubes(Location center, Material glass, int count, float size, int ticks) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = i / (double) count * Math.PI * 2.0 + rnd.nextDouble();
            double r = 1.2 + rnd.nextDouble() * 1.4;
            Location at = center.clone().add(Math.cos(a) * r, 1.2 + rnd.nextDouble() * 1.2, Math.sin(a) * r);
            float sz = size * (0.7f + (float) rnd.nextDouble() * 0.6f);
            Quaternionf r0 = new Quaternionf().rotateXYZ((float) rnd.nextDouble() * 3f, (float) rnd.nextDouble() * 3f, 0f);
            Quaternionf r1 = new Quaternionf().rotateXYZ((float) rnd.nextDouble() * 6f, (float) rnd.nextDouble() * 6f, (float) rnd.nextDouble() * 6f);
            BlockDisplay d = block(at, glass, cube(0.05f, r0));
            d.setTeleportDuration(1);
            animate(d, 1L, 5, cube(sz, r0));
            animate(d, 6L, ticks - 10, cube(sz, r1));
            animate(d, ticks - 4L, 4, cube(0.05f, r1));
            removeLater(d, ticks + 2L);
        }
    }

    /** Cubes spiral upwards in a tornado (wind and water weapons). */
    public void tornado(Location center, Material[] mats, int layers, int ticks) {
        final int per = 2;
        final BlockDisplay[] cubes = new BlockDisplay[layers * per];
        for (int i = 0; i < cubes.length; i++) {
            cubes[i] = block(center, mats[i % mats.length], scaled(0.28f + (i % 3) * 0.07f));
            cubes[i].setTeleportDuration(2);
        }
        World w = center.getWorld();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks) {
                    for (BlockDisplay d : cubes) removeNow(d);
                    cancel();
                    return;
                }
                double fade = Math.min(1.0, (ticks - t) / 6.0);
                for (int i = 0; i < cubes.length; i++) {
                    int layer = i / per;
                    double a = t * 0.5 + layer * 0.9 + (i % per) * Math.PI;
                    double r = (0.5 + layer * 0.28) * fade;
                    cubes[i].teleport(new Location(w, center.getX() + Math.cos(a) * r,
                            center.getY() + 0.2 + layer * 0.55 * Math.min(1.0, t / 6.0), center.getZ() + Math.sin(a) * r));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Cubes of the given blocks circle the player for a moment (visible to everyone, even the owner). */
    public void orbitCubes(Player p, Material[] mats, int count, double radius, int ticks) {
        final BlockDisplay[] cubes = new BlockDisplay[count];
        for (int i = 0; i < count; i++) {
            cubes[i] = block(p.getLocation().add(0, 1, 0), mats[i % mats.length], scaled(0.35f));
            cubes[i].setTeleportDuration(2);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    for (BlockDisplay d : cubes) removeNow(d);
                    cancel();
                    return;
                }
                Location base = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < cubes.length; i++) {
                    double a = t * 0.4 + i * Math.PI * 2.0 / cubes.length;
                    cubes[i].teleport(base.clone().add(Math.cos(a) * radius, Math.sin(t * 0.25 + i) * 0.4, Math.sin(a) * radius));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A flat wave of glass travels forward from the player (slash wave). */
    public void slashWave(Player p, Material glass, Material core) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection().setY(0);
        if (f.lengthSquared() < 1.0E-6) return;
        f.normalize();
        Location at = p.getLocation().add(f.clone().multiply(2.0)).add(0, 0.9, 0);
        at.setDirection(f);
        Quaternionf tilt = new Quaternionf().rotateZ((float) Math.toRadians(-18));
        for (int i = 0; i < 2; i++) {
            float len = i == 0 ? 3.2f : 2.2f;
            BlockDisplay d = block(at, i == 0 ? glass : core, new Transformation(
                    tilt.transform(new Vector3f(-len / 2f, 0f, 0f)), tilt, new Vector3f(len, 0.06f, 0.5f), new Quaternionf()));
            d.setTransformation(new Transformation(new Vector3f(-len / 2f, 0f, 0f), tilt, new Vector3f(len, 0.06f, 0.5f), new Quaternionf()));
            animate(d, 1L, 6, new Transformation(new Vector3f(-len / 2f, 0f, 6.0f), tilt, new Vector3f(len * 1.2f, 0.02f, 0.2f), new Quaternionf()));
            removeLater(d, 9L);
        }
    }

    /** A small burst of cubes where a hit lands; a criticals pops a bigger glass cube too. */
    public void hitBurst(Location c, Material[] chunks, Material glass, boolean crit) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = crit ? 6 : 3;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            Vector dir = new Vector(Math.cos(a), 0, Math.sin(a)).multiply(0.9 + rnd.nextDouble() * 0.8);
            float s = (float) (0.12 + rnd.nextDouble() * 0.14);
            Quaternionf spin = new Quaternionf().rotateXYZ((float) (rnd.nextDouble() * 6), (float) (rnd.nextDouble() * 6), 0f);
            BlockDisplay d = block(c, chunks[i % chunks.length], scaled(0.01f));
            animate(d, 1L, 4, new Transformation(new Vector3f((float) dir.getX(), 0.5f + (float) rnd.nextDouble() * 0.6f, (float) dir.getZ()),
                    spin, new Vector3f(s, s, s), new Quaternionf()));
            animate(d, 5L, 5, new Transformation(new Vector3f((float) dir.getX() * 1.2f, -0.4f, (float) dir.getZ() * 1.2f),
                    spin, new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            removeLater(d, 12L);
        }
        if (crit) {
            Quaternionf r0 = new Quaternionf().rotateXYZ(0.6f, 0.8f, 0f);
            Quaternionf r1 = new Quaternionf().rotateXYZ(2.4f, 3.0f, 1.0f);
            BlockDisplay d = block(c, glass, cube(0.05f, r0));
            animate(d, 1L, 3, cube(0.9f, r0));
            animate(d, 4L, 6, cube(0.05f, r1));
            removeLater(d, 12L);
        }
    }

    /** Chunks of the given blocks fly out, spin and fall (Berserker Blade, heavy hits). */
    public void debris(Location center, Material[] mats, int count, double power) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            Vector dir = new Vector(Math.cos(a), 0, Math.sin(a)).multiply(power * (0.5 + rnd.nextDouble() * 0.7));
            float s = (float) (0.22 + rnd.nextDouble() * 0.22);
            Quaternionf spin = new Quaternionf().rotateXYZ((float) (rnd.nextDouble() * 6), (float) (rnd.nextDouble() * 6), (float) (rnd.nextDouble() * 6));
            BlockDisplay d = block(center.clone().add(0, 0.2, 0), mats[i % mats.length], scaled(0.01f));
            animate(d, 1L, 5, new Transformation(new Vector3f((float) dir.getX(), 1.3f + (float) rnd.nextDouble(), (float) dir.getZ()),
                    spin, new Vector3f(s, s, s), new Quaternionf()));
            animate(d, 7L, 6, new Transformation(new Vector3f((float) dir.getX() * 1.3f, 0f, (float) dir.getZ() * 1.3f),
                    spin, new Vector3f(s * 0.5f, s * 0.5f, s * 0.5f), new Quaternionf()));
            removeLater(d, 15L);
        }
    }

    /** A flat ring of tiles on the ground with a few splatters inside (the blood circle of the Berserker Blade). */
    public void groundRing(Location center, double radius, Material tile, int ring, int splatter, int holdTicks) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location ground = center.clone();
        ground.setY(Math.floor(center.getY()) + 0.02);
        for (int i = 0; i < ring + splatter; i++) {
            double a;
            double r;
            float size;
            if (i < ring) {
                a = i / (double) ring * Math.PI * 2.0;
                r = radius;
                size = 0.9f;
            } else {
                a = rnd.nextDouble() * Math.PI * 2.0;
                r = rnd.nextDouble() * radius * 0.85;
                size = (float) (0.5 + rnd.nextDouble() * 0.5);
            }
            Location at = ground.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockDisplay d = block(at, tile, new Transformation(new Vector3f(-size / 2f, 0f, -size / 2f),
                    new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            long delay = 1L + (long) (r / radius * 4.0);
            animate(d, delay, 2, new Transformation(new Vector3f(-size / 2f, 0f, -size / 2f),
                    new Quaternionf(), new Vector3f(size, 0.03f, size), new Quaternionf()));
            animate(d, delay + holdTicks, 5, new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            removeLater(d, delay + holdTicks + 8L);
        }
    }

    /** Small dark blocks circle a point and rise, pulling inwards (Graviton Axe, Gravity Pulse). */
    public void gravityOrbit(Location center, int count, int ticks) {
        final BlockDisplay[] rocks = new BlockDisplay[count];
        for (int i = 0; i < count; i++) {
            rocks[i] = block(center, i % 2 == 0 ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN,
                    scaled(0.28f + (i % 3) * 0.1f));
            rocks[i].setTeleportDuration(2);
        }
        World w = center.getWorld();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks) {
                    for (BlockDisplay d : rocks) removeNow(d);
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
        BlockDisplay d = block(at, Material.WHITE_CONCRETE, centred(0.5f, 14f, 0.5f, new Quaternionf(), 0f));
        animate(d, 2L, 4, centred(0.04f, 14f, 0.04f, new Quaternionf(), 0f));
        removeLater(d, 8L);
    }

    // ==================================================================
    // rope and bubble
    // ==================================================================

    /** A rope of linked segments from the player to the target while it is pulled in. */
    public void rope(Player p, LivingEntity target, Material mat, int ticks) {
        final int n = 10;
        final BlockDisplay[] seg = new BlockDisplay[n];
        for (int i = 0; i < n; i++) {
            seg[i] = block(p.getLocation().add(0, 1.2, 0), i % 3 == 2 ? Material.BLACK_CONCRETE : mat, scaled(0.1f));
            seg[i].setTeleportDuration(1);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !target.isValid() || t >= ticks) {
                    for (BlockDisplay d : seg) removeNow(d);
                    cancel();
                    return;
                }
                Location a = p.getLocation().add(0, 1.2, 0);
                Location b = target.getLocation().add(0, target.getHeight() * 0.6, 0);
                if (a.getWorld() != b.getWorld()) {
                    for (BlockDisplay d : seg) removeNow(d);
                    cancel();
                    return;
                }
                double sag = Math.min(0.5, a.distance(b) * 0.06);
                Vector[] pt = new Vector[n + 1];
                for (int i = 0; i <= n; i++) {
                    double u = i / (double) n;
                    Vector v = a.toVector().multiply(1 - u).add(b.toVector().multiply(u));
                    v.setY(v.getY() - Math.sin(Math.PI * u) * sag);
                    pt[i] = v;
                }
                for (int i = 0; i < n; i++) {
                    Vector dir = pt[i + 1].clone().subtract(pt[i]);
                    float len = (float) dir.length();
                    if (len < 1.0E-4) continue;
                    dir.normalize();
                    Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                            new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
                    Vector3f off = q.transform(new Vector3f(-0.05f, -0.05f, 0f));
                    seg[i].teleport(pt[i].toLocation(a.getWorld()));
                    seg[i].setInterpolationDelay(0);
                    seg[i].setInterpolationDuration(1);
                    seg[i].setTransformation(new Transformation(off, q, new Vector3f(0.1f, 0.1f, len * 1.15f), new Quaternionf()));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * The Impact Bubble: two nested glass cubes of the given colour that follow the player, spin slowly in
     * opposite directions, pulse, and shrink away when the time is up.
     */
    public void bubble(Player p, int ticks, double radius, Material outerMat, Material innerMat) {
        final float outer = (float) (radius * 2.0 * 0.9);
        final float inner = outer * 0.72f;
        final BlockDisplay a = block(p.getLocation().add(0, 1, 0), outerMat, cube(0.1f, new Quaternionf()));
        final BlockDisplay b = block(p.getLocation().add(0, 1, 0), innerMat, cube(0.1f, new Quaternionf()));
        a.setTeleportDuration(1);
        b.setTeleportDuration(1);
        animate(a, 1L, 6, cube(outer, new Quaternionf().rotateY(0.8f).rotateX(0.6f)));
        animate(b, 1L, 6, cube(inner, new Quaternionf().rotateY(-0.8f).rotateZ(0.6f)));

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= ticks) {
                    animate(a, 0L, 5, cube(0.05f, new Quaternionf().rotateY(2f)));
                    animate(b, 0L, 5, cube(0.05f, new Quaternionf().rotateY(-2f)));
                    removeLater(a, 7L);
                    removeLater(b, 7L);
                    cancel();
                    return;
                }
                Location c = p.getLocation().add(0, 1, 0);
                a.teleport(c);
                b.teleport(c);
                if (t % 10 == 6) {
                    float spin = t * 0.07f;
                    float pulse = 1.0f + 0.04f * (float) Math.sin(t * 0.3);
                    animate(a, 0L, 10, cube(outer * pulse, new Quaternionf().rotateY(0.8f + spin).rotateX(0.6f)));
                    animate(b, 0L, 10, cube(inner * pulse, new Quaternionf().rotateY(-0.8f - spin).rotateZ(0.6f)));
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ==================================================================
    // custom set pieces
    // ==================================================================

    /** A thin box between two points: pops in after 'delay' ticks and shrinks away after 'hold' more. */
    private void line(Location a, Location b, Material m, float w, long delay, int hold) {
        Vector d = b.toVector().subtract(a.toVector());
        float len = (float) d.length();
        if (len < 1.0E-3f) return;
        d.normalize();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ()));
        Transformation full = new Transformation(q.transform(new Vector3f(-w / 2f, -w / 2f, 0f)), q,
                new Vector3f(w, w, len), new Quaternionf());
        Transformation tiny = new Transformation(new Vector3f(), q, new Vector3f(0.001f, 0.001f, len), new Quaternionf());
        Location at = new Location(a.getWorld(), a.getX(), a.getY(), a.getZ());
        BlockDisplay disp = block(at, m, tiny);
        animate(disp, delay + 1, 2, full);
        animate(disp, delay + hold, 3, tiny);
        removeLater(disp, delay + hold + 6L);
    }

    private static Location pt(World w, double x, double y, double z) {
        return new Location(w, x, y, z);
    }

    /** A spider web drawn on the ground: spokes and three rings (Spider Blade, Widow's Axe). */
    public void web(Location center, double radius, Material strand) {
        World w = center.getWorld();
        double y = Math.floor(center.getY()) + 0.05;
        Location c = pt(w, center.getX(), y, center.getZ());
        int spokes = 8;
        Location[][] ring = new Location[3][spokes];
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < spokes; i++) {
                double a = i * Math.PI * 2.0 / spokes;
                double r = radius * (k + 1) / 3.0;
                ring[k][i] = pt(w, c.getX() + Math.cos(a) * r, y, c.getZ() + Math.sin(a) * r);
            }
        }
        for (int i = 0; i < spokes; i++) line(c, ring[2][i], strand, 0.07f, 0, 26);
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < spokes; i++) line(ring[k][i], ring[k][(i + 1) % spokes], strand, 0.06f, 1 + k * 2L, 24 - k * 2);
        }
    }

    private static Transformation panelT(float s) {
        return new Transformation(new Vector3f(-0.6f * s, 0f, -0.03f), new Quaternionf(),
                new Vector3f(1.2f * s, 1.7f * s, 0.06f), new Quaternionf());
    }

    /** Glass shield plates stand in a ring around the player and slowly turn (Guardian Blade). */
    public void plates(Player p, Material glass, int count, double radius, int ticks) {
        final BlockDisplay[] pl = new BlockDisplay[count];
        for (int i = 0; i < count; i++) {
            pl[i] = block(p.getLocation().add(0, 0.1, 0), glass, panelT(0.05f));
            pl[i].setTeleportDuration(2);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    for (BlockDisplay d : pl) removeNow(d);
                    cancel();
                    return;
                }
                float s = (float) Math.min(Math.min(1.0, t / 4.0), Math.min(1.0, (ticks - t) / 5.0));
                Location base = p.getLocation().add(0, 0.1, 0);
                for (int i = 0; i < count; i++) {
                    double a = t * 0.12 + i * Math.PI * 2.0 / count;
                    Location l = base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                    l.setYaw((float) Math.toDegrees(a) - 90f);
                    l.setPitch(0f);
                    pl[i].teleport(l);
                    pl[i].setInterpolationDelay(0);
                    pl[i].setInterpolationDuration(1);
                    pl[i].setTransformation(panelT(s));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Two strands of cubes spiral up around the player (Mender's Blade). */
    public void helix(Player p, Material[] mats, int ticks) {
        final int per = 8;
        final BlockDisplay[] c = new BlockDisplay[per * 2];
        for (int i = 0; i < c.length; i++) {
            c[i] = block(p.getLocation(), mats[i % mats.length], scaled(0.17f));
            c[i].setTeleportDuration(2);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    for (BlockDisplay d : c) removeNow(d);
                    cancel();
                    return;
                }
                Location base = p.getLocation();
                double fade = Math.min(1.0, (ticks - t) / 6.0);
                for (int i = 0; i < c.length; i++) {
                    int k = i % per;
                    int strand = i / per;
                    double a = t * 0.3 + k * 0.55 + strand * Math.PI;
                    double y = Math.min(k * 0.28, t * 0.18);
                    c[i].teleport(base.clone().add(Math.cos(a) * 0.9 * fade, y, Math.sin(a) * 0.9 * fade));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Small cubes drift upwards like embers and shrink away. */
    public void embers(Location center, Material[] mats, int count, int ticks) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location at = center.clone().add(rnd.nextDouble(-1.6, 1.6), 0.1, rnd.nextDouble(-1.6, 1.6));
            float s = (float) (0.1 + rnd.nextDouble() * 0.12);
            BlockDisplay d = block(at, mats[i % mats.length], scaled(0.01f));
            long delay = 1L + rnd.nextInt(6);
            animate(d, delay, 3, scaled(s));
            animate(d, delay + 3, ticks, new Transformation(
                    new Vector3f((float) rnd.nextDouble(-0.8, 0.8), (float) (2.2 + rnd.nextDouble() * 1.5), (float) rnd.nextDouble(-0.8, 0.8)),
                    new Quaternionf(), new Vector3f(0.02f, 0.02f, 0.02f), new Quaternionf()));
            removeLater(d, delay + ticks + 6L);
        }
    }

    /** Items rain down, spin and vanish (food, gold, leaves, petals). */
    public void itemRain(Location center, Material[] items, int count, double radius) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location at = center.clone().add(rnd.nextDouble(-radius, radius), 5.0, rnd.nextDouble(-radius, radius));
            ItemDisplay d = item(at, new ItemStack(items[i % items.length]), scaled(0.75f), null);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            long delay = 1L + rnd.nextInt(8);
            animate(d, delay, 7, new Transformation(new Vector3f(0f, -4.8f, 0f), rotY(160), new Vector3f(0.75f, 0.75f, 0.75f), new Quaternionf()));
            animate(d, delay + 14, 4, new Transformation(new Vector3f(0f, -4.8f, 0f), rotY(320), new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf()));
            removeLater(d, delay + 22L);
        }
    }

    /** Glowing cracks run out of the impact point across the ground (Quake and Forge pickaxes). */
    public void fissure(Location center, double len, Material rock, Material glow) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World w = center.getWorld();
        double y = Math.floor(center.getY()) + 0.04;
        int rays = 5;
        for (int r = 0; r < rays; r++) {
            double a = r * Math.PI * 2.0 / rays + rnd.nextDouble() * 0.5;
            Location prev = pt(w, center.getX(), y, center.getZ());
            for (int sgm = 0; sgm < 3; sgm++) {
                a += rnd.nextDouble(-0.4, 0.4);
                double step = len / 3.0;
                Location next = pt(w, prev.getX() + Math.cos(a) * step, y, prev.getZ() + Math.sin(a) * step);
                line(prev, next, rock, 0.3f, sgm * 2L, 22);
                line(pt(w, prev.getX(), y + 0.06, prev.getZ()), pt(w, next.getX(), y + 0.06, next.getZ()), glow, 0.14f, sgm * 2L, 22);
                prev = next;
            }
        }
    }

    /** A tall slit opens in the air and closes (Rift Blade, Phantom Blade). */
    public void rift(Location at, Material glass, Material core, float yaw) {
        Location l = new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), yaw, 0f);
        BlockDisplay a = block(l, glass, centred(0.1f, 0.1f, 0.02f, new Quaternionf(), 0f));
        BlockDisplay b = block(l, core, centred(0.1f, 0.1f, 0.01f, new Quaternionf(), 0f));
        animate(a, 1L, 4, centred(1.1f, 2.6f, 0.2f, new Quaternionf(), 0f));
        animate(b, 1L, 4, centred(0.9f, 2.5f, 0.06f, new Quaternionf(), 0f));
        animate(a, 12L, 4, centred(0.05f, 0.1f, 0.02f, new Quaternionf(), 0f));
        animate(b, 12L, 4, centred(0.05f, 0.1f, 0.01f, new Quaternionf(), 0f));
        removeLater(a, 18L);
        removeLater(b, 18L);
    }

    /** A jagged bolt from the sky with a side branch (Thunder Blade, Stormbreaker). */
    public void bolt(Location ground, Material core, Material glow, double spread) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World w = ground.getWorld();
        int segs = 7;
        Location[] pts = new Location[segs + 1];
        pts[0] = pt(w, ground.getX() + rnd.nextDouble(-1, 1), ground.getY() + 13, ground.getZ() + rnd.nextDouble(-1, 1));
        for (int i = 1; i <= segs; i++) {
            double u = i / (double) segs;
            pts[i] = i == segs ? pt(w, ground.getX(), ground.getY(), ground.getZ())
                    : pt(w, ground.getX() + rnd.nextDouble(-spread, spread) * (1 - u), ground.getY() + 13 * (1 - u),
                    ground.getZ() + rnd.nextDouble(-spread, spread) * (1 - u));
        }
        for (int i = 0; i < segs; i++) {
            line(pts[i], pts[i + 1], glow, 0.34f, 0, 3);
            line(pts[i], pts[i + 1], core, 0.11f, 0, 3);
        }
        Location f0 = pts[3];
        Location f1 = pt(w, f0.getX() + rnd.nextDouble(-2.5, 2.5), f0.getY() - 2.5, f0.getZ() + rnd.nextDouble(-2.5, 2.5));
        line(f0, f1, core, 0.09f, 0, 3);
    }

    /** Feathered wings of glass flap behind the player (Sky Blade). */
    public void wings(Player p, Material feather, int ticks) {
        final int rows = 5;
        final BlockDisplay[] panels = new BlockDisplay[rows * 2];
        for (int i = 0; i < panels.length; i++) {
            panels[i] = block(p.getLocation().add(0, 1.3, 0), feather, scaled(0.01f));
            panels[i].setTeleportDuration(1);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    for (BlockDisplay d : panels) removeNow(d);
                    cancel();
                    return;
                }
                float s = (float) Math.min(Math.min(1.0, t / 5.0), Math.min(1.0, (ticks - t) / 5.0));
                double flap = Math.sin(t * 0.55) * 0.6;
                Location base = p.getLocation();
                Vector face = base.getDirection().setY(0);
                if (face.lengthSquared() > 1.0E-6) face.normalize();
                Location shoulder = base.clone().add(0, 1.35, 0).subtract(face.multiply(0.25));
                shoulder.setPitch(0f);
                for (int side = 0; side < 2; side++) {
                    for (int k = 0; k < rows; k++) {
                        int idx = side * rows + k;
                        float len = (float) ((0.9 + k * 0.28) * s);
                        double spread = 0.45 + k * 0.12;
                        Quaternionf q = side == 0
                                ? new Quaternionf().rotateY((float) spread).rotateZ((float) flap)
                                : new Quaternionf().rotateY((float) (Math.PI - spread)).rotateZ((float) flap);
                        Vector3f t3 = q.transform(new Vector3f(0f, 0f, -0.225f * s));
                        t3.y -= k * 0.16f;
                        panels[idx].teleport(shoulder);
                        panels[idx].setInterpolationDelay(0);
                        panels[idx].setInterpolationDuration(1);
                        panels[idx].setTransformation(new Transformation(t3, q, new Vector3f(len, 0.05f, 0.45f * s), new Quaternionf()));
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A big spinning block falls in at an angle with a trail and smashes into the ground (Comet Mace). */
    public void meteor(Location target, Material core, Material[] chunks, Material tile) {
        Location start = target.clone().add(4, 14, 3);
        BlockDisplay m = block(start, core, cube(1.8f, new Quaternionf()));
        m.setTeleportDuration(1);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                double u = t / 7.0;
                if (u >= 1.0) {
                    m.teleport(target);
                    debris(target, chunks, 12, 3.0);
                    groundRing(target, 4.0, tile, 22, 10, 22);
                    animate(m, 1L, 3, cube(0.05f, new Quaternionf()));
                    removeLater(m, 6L);
                    cancel();
                    return;
                }
                Location l = start.clone().add(target.toVector().subtract(start.toVector()).multiply(u));
                m.teleport(l);
                m.setInterpolationDelay(0);
                m.setInterpolationDuration(1);
                m.setTransformation(cube(1.8f, new Quaternionf().rotateXYZ((float) (t * 0.7), (float) (t * 0.5), (float) (t * 0.3))));
                BlockDisplay tr = block(l, chunks[t % chunks.length], cube(0.7f, new Quaternionf()));
                animate(tr, 1L, 6, cube(0.05f, new Quaternionf()));
                removeLater(tr, 8L);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** A heavy block (an anvil for the Doom Hammer) drops from above and slams down. */
    public void drop(Location target, Material m, float size, Material[] chunks, Material tile) {
        BlockDisplay d = block(target, m, centred(size, size, size, new Quaternionf(), 9f));
        animate(d, 1L, 4, centred(size, size, size, new Quaternionf(), 0f));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            debris(target, chunks, 10, 2.8);
            groundRing(target, 4.0, tile, 22, 8, 20);
        }, 6L);
        animate(d, 16L, 4, centred(0.05f, 0.05f, 0.05f, new Quaternionf(), 0f));
        removeLater(d, 22L);
    }

    /** The crown floats above the head, spinning, with rays of light (Monarch Crown). */
    public void halo(Player p, ItemStack crown, Material ray, int ticks) {
        final ItemDisplay d = item(p.getLocation().add(0, 2.7, 0), single(crown), scaled(0.05f), null);
        d.setTeleportDuration(1);
        animate(d, 1L, 5, scaled(1.6f));
        final int n = 8;
        final BlockDisplay[] rays = new BlockDisplay[n];
        for (int i = 0; i < n; i++) {
            rays[i] = block(p.getLocation().add(0, 2.7, 0), ray, scaled(0.01f));
            rays[i].setTeleportDuration(1);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t >= ticks) {
                    removeNow(d);
                    for (BlockDisplay r : rays) removeNow(r);
                    cancel();
                    return;
                }
                float s = (float) Math.min(Math.min(1.0, t / 5.0), Math.min(1.0, (ticks - t) / 5.0));
                Location c = p.getLocation().add(0, 2.7 + 0.1 * Math.sin(t * 0.2), 0);
                d.teleport(c);
                if (t % 8 == 0) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(8);
                    d.setTransformation(new Transformation(new Vector3f(), rotY(t * 21.0), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
                }
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2.0 / n + t * 0.08;
                    Quaternionf q = new Quaternionf().rotateY((float) a).rotateZ((float) Math.toRadians(-38));
                    Vector3f off = q.transform(new Vector3f(-0.025f, 0f, -0.025f));
                    rays[i].teleport(c);
                    rays[i].setInterpolationDelay(0);
                    rays[i].setInterpolationDuration(1);
                    rays[i].setTransformation(new Transformation(off, q, new Vector3f(0.05f, 1.1f * s, 0.05f), new Quaternionf()));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Sonar pulses roll outwards across the ground (Ancient Blade). */
    public void sonar(Location center, double maxR, int pulses, Material tile) {
        World w = center.getWorld();
        double y = Math.floor(center.getY()) + 0.03;
        for (int pulse = 0; pulse < pulses; pulse++) {
            long delay = pulse * 6L;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2.0 / 14.0;
                Location at = pt(w, center.getX(), y, center.getZ());
                Transformation flat = new Transformation(new Vector3f(-0.25f, 0f, -0.25f), new Quaternionf(),
                        new Vector3f(0.5f, 0.04f, 0.5f), new Quaternionf());
                BlockDisplay d = block(at, tile, new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
                animate(d, delay + 1, 1, flat);
                Vector3f out = new Vector3f((float) (Math.cos(a) * maxR) - 0.2f, 0f, (float) (Math.sin(a) * maxR) - 0.2f);
                animate(d, delay + 2, 6, new Transformation(out, new Quaternionf(), new Vector3f(0.4f, 0.03f, 0.4f), new Quaternionf()));
                animate(d, delay + 8, 3, new Transformation(out, new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
                removeLater(d, delay + 14L);
            }
        }
    }

    /** A snake of cubes weaves forward, each segment following the one before (Dragonrend, Tidebreaker). */
    public void serpent(Player p, Material[] mats, int length, int ticks) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection().normalize();
        Vector side = f.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-4) side = new Vector(1, 0, 0); else side.normalize();
        final Vector sideV = side;
        final Location start = p.getLocation().add(0, 1.0, 0).add(f.clone().multiply(2.2));
        final BlockDisplay[] segs = new BlockDisplay[length];
        for (int j = 0; j < length; j++) {
            segs[j] = block(start, mats[j % mats.length], cube((float) (0.75 - j * 0.045), new Quaternionf()));
            segs[j].setTeleportDuration(1);
        }
        final int total = ticks + length * 2 + 2;
        final Location[] hist = new Location[total];
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= total) {
                    for (BlockDisplay d : segs) removeNow(d);
                    cancel();
                    return;
                }
                if (t < ticks) {
                    hist[t] = start.clone().add(f.clone().multiply(t * 1.0))
                            .add(sideV.clone().multiply(Math.sin(t * 0.55) * 0.9))
                            .add(0, Math.cos(t * 0.4) * 0.35, 0);
                } else {
                    hist[t] = hist[ticks - 1];
                }
                for (int j = 0; j < segs.length; j++) {
                    int idx = Math.max(0, t - j * 2);
                    segs[j].teleport(hist[idx]);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The weapon is thrown flat, spins out along a curve and flies back (Reaper Scythe). Hidden from the thrower on the way back. */
    public void boomerang(Player p, ItemStack weapon, double dist, int outTicks) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection();
        f.setY(Math.max(-0.2, Math.min(0.2, f.getY())));
        f.normalize();
        final Vector fwd = f;
        final Vector side = new Vector(-f.getZ(), 0, f.getX());
        final Location start = p.getLocation().add(0, 1.2, 0).add(f.clone().multiply(1.6));
        final Location apex = start.clone().add(f.clone().multiply(dist));
        final ItemDisplay d = item(start, single(weapon), new Transformation(new Vector3f(),
                new Quaternionf().rotateX((float) Math.toRadians(90)), new Vector3f(1.7f, 1.7f, 1.7f), new Quaternionf()), null);
        d.setTeleportDuration(1);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                int total = outTicks * 2;
                if (!p.isOnline() || t >= total) {
                    removeNow(d);
                    cancel();
                    return;
                }
                if (t == outTicks) p.hideEntity(plugin, d);
                Location pos;
                if (t < outTicks) {
                    double u = (t + 1) / (double) outTicks;
                    pos = start.clone().add(fwd.clone().multiply(dist * u)).add(side.clone().multiply(Math.sin(Math.PI * u) * 1.8));
                } else {
                    double r = (t - outTicks + 1) / (double) outTicks;
                    Location chest = p.getLocation().add(0, 1.0, 0);
                    pos = apex.clone().multiply(1 - r).add(chest.clone().multiply(r));
                    pos.add(side.clone().multiply(Math.sin(Math.PI * (1 - r)) * 1.2));
                }
                pos.setYaw((float) (t * 55.0));
                pos.setPitch(0f);
                d.teleport(pos);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Copies of the weapon fall point-down from the sky and stab into the ground (Omen). */
    public void weaponRain(Location center, ItemStack weapon, int count, double radius) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location at = center.clone().add(rnd.nextDouble(-radius, radius), 6.5, rnd.nextDouble(-radius, radius));
            Quaternionf down = rotZ(180);
            ItemDisplay d = item(at, single(weapon), new Transformation(new Vector3f(), down, new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()), null);
            long delay = 1L + rnd.nextInt(8);
            animate(d, delay, 4, new Transformation(new Vector3f(0f, -6.0f, 0f), down, new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
            animate(d, delay + 16, 4, new Transformation(new Vector3f(0f, -6.0f, 0f), down, new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf()));
            removeLater(d, delay + 24L);
        }
    }

    /** A lotus of glass petals opens on the ground and closes again (Blossom Blade). */
    public void bloom(Location center, Material outer, Material inner) {
        World w = center.getWorld();
        Location base = pt(w, center.getX(), Math.floor(center.getY()) + 0.05, center.getZ());
        float[] tilt = {25f, 45f, 65f};
        float[] lens = {1.5f, 1.2f, 0.9f};
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI * 2.0 / 8.0 + layer * 0.4;
                Quaternionf closed = new Quaternionf().rotateY((float) a).rotateX((float) Math.toRadians(-85));
                Quaternionf open = new Quaternionf().rotateY((float) a).rotateX((float) Math.toRadians(-tilt[layer]));
                BlockDisplay d = block(base, layer == 0 ? outer : inner, petal(closed, lens[layer] * 0.6f));
                animate(d, 1L + layer * 2L, 6, petal(open, lens[layer]));
                animate(d, 22L, 6, petal(closed, lens[layer] * 0.3f));
                removeLater(d, 32L);
            }
        }
    }

    private static Transformation petal(Quaternionf q, float len) {
        Vector3f t = q.transform(new Vector3f(-0.25f, 0f, 0f));
        return new Transformation(t, q, new Vector3f(0.5f, 0.05f, len), new Quaternionf());
    }

    /** A dark spinning core with rocks circling it, then it collapses (Void Fang, Graviton Axe). */
    public void singularity(Location center, int ticks) {
        Location c = center.clone().add(0, 1.4, 0);
        Quaternionf r0 = new Quaternionf().rotateXYZ(0.3f, 0.5f, 0f);
        BlockDisplay core = block(c, Material.BLACK_CONCRETE, cube(0.1f, r0));
        animate(core, 1L, Math.max(2, ticks / 2), cube(1.1f, new Quaternionf().rotateXYZ(3f, 4f, 2f)));
        animate(core, Math.max(2, ticks - 5), 5, cube(0.05f, new Quaternionf().rotateXYZ(6f, 7f, 4f)));
        removeLater(core, ticks + 2L);
        gravityOrbit(center.clone().add(0, 0.2, 0), 8, ticks);
    }
}
