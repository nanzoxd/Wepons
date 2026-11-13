package com.wepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Display-entity set pieces built for the weapon abilities: the Impact glass box, the clock circles (Dragonrend's
 * Infinite Void and the Hourglass Dagger's time field), the dark web on a cobweb, glass shells, orbiting cubes, chains
 * and a few helpers (follow-items, beams, slash arcs, a dragon bite).
 *
 * Rules every effect here follows:
 *  - every display is non-persistent and registered in {@link #live}, so {@link #cleanup()} removes whatever is left on disable;
 *  - every effect has a hard tick limit and also ends when the entity it follows dies, leaves or becomes invalid;
 *  - displays are only spawned once per effect, then moved/morphed with interpolation (no per-tick spawning);
 *  - nothing here has a hitbox or does damage, it is all visual.
 */
public final class CustomFX implements Listener {

    private final Wepons plugin;
    private final Set<Display> live = new HashSet<>();
    private final Map<Block, List<Display>> webs = new HashMap<>();

    public CustomFX(Wepons plugin) {
        this.plugin = plugin;
    }

    /** Removes everything that is still alive (plugin disable). */
    public void cleanup() {
        for (Display d : new HashSet<>(live)) d.remove();
        live.clear();
        webs.clear();
    }

    // ==================================================================
    // primitives
    // ==================================================================

    /** A resource-pack VFX model (assets/cm/items/item/vfx/<name>.json) on a plain item. */
    public static ItemStack vfx(String name) {
        ItemStack s = new ItemStack(Material.PAPER);
        ItemMeta m = s.getItemMeta();
        NamespacedKey k = NamespacedKey.fromString("cm:item/vfx/" + name);
        if (k != null) m.setItemModel(k);
        s.setItemMeta(m);
        return s;
    }

    /** Chain block: renamed in newer versions, so look the name up. */
    private static final Material CHAIN = firstMaterial("IRON_CHAIN", "CHAIN", "IRON_BARS");

    private static Material firstMaterial(String... names) {
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) return m;
        }
        return Material.IRON_BARS;
    }

    /** A box of the given size whose centre is the display's position (+ an offset), rotated around that centre. */
    static Transformation boxT(float sx, float sy, float sz, float ox, float oy, float oz, Quaternionf rot) {
        Vector3f t = rot.transform(new Vector3f(-sx / 2f, -sy / 2f, -sz / 2f));
        t.add(ox, oy, oz);
        return new Transformation(t, new Quaternionf(rot), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    static Quaternionf rotY(double deg) {
        return new Quaternionf().rotateY((float) Math.toRadians(deg));
    }

    BlockDisplay block(Location at, Material m, Transformation start) {
        BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, x -> {
            x.setBlock(m.createBlockData());
            x.setTransformation(start);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(0.7f);
            x.setTeleportDuration(2);
            x.setPersistent(false);
        });
        live.add(d);
        return d;
    }

    ItemDisplay item(Location at, ItemStack stack, float scale) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, x -> {
            x.setItemStack(stack);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(0.7f);
            x.setTeleportDuration(2);
            x.setPersistent(false);
        });
        live.add(d);
        return d;
    }

    TextDisplay text(Location at, String text, NamedTextColor color, float scale) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, x -> {
            x.text(Component.text(text, color));
            x.setBillboard(Display.Billboard.CENTER);
            x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(0.5f);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            x.setPersistent(false);
        });
        live.add(d);
        return d;
    }

    /** Changes a display to a new transformation over 'ticks' ticks. */
    void morph(Display d, int ticks, Transformation t) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(t);
    }

    void morphLater(Display d, long delay, int ticks, Transformation t) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> morph(d, ticks, t), delay);
    }

    public void kill(Display d) {
        if (d == null) return;
        d.remove();
        live.remove(d);
    }

    /** Shrinks everything towards its own centre over 'ticks' ticks, then removes it. */
    void collapse(Collection<? extends Display> parts, int ticks) {
        List<Display> copy = new ArrayList<>(parts);
        for (Display d : copy) {
            if (!d.isValid()) continue;
            Transformation t = d.getTransformation();
            morph(d, ticks, new Transformation(new Vector3f(t.getTranslation()).mul(0.02f), t.getLeftRotation(),
                    new Vector3f(0.01f, 0.01f, 0.01f), t.getRightRotation()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Display d : copy) kill(d);
        }, ticks + 2L);
    }

    private void killLater(Collection<? extends Display> parts, long ticks) {
        List<Display> copy = new ArrayList<>(parts);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Display d : copy) kill(d);
        }, ticks);
    }

    // ==================================================================
    // beams
    // ==================================================================

    /** A thin glowing bar between two points that can be re-aimed every tick (vines, soul beams). */
    public final class Link {
        private final BlockDisplay d;
        private final float width;

        Link(Location a, Location b, Material m, float width) {
            this.width = width;
            this.d = block(a, m, shape(a, b));
            d.setTeleportDuration(1);
        }

        private Transformation shape(Location a, Location b) {
            Vector dir = b.toVector().subtract(a.toVector());
            float len = (float) dir.length();
            if (len < 1.0E-3f) {
                return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
            }
            dir.normalize();
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
            Vector3f t = q.transform(new Vector3f(-width / 2f, -width / 2f, 0f));
            return new Transformation(t, q, new Vector3f(width, width, len), new Quaternionf());
        }

        public void update(Location a, Location b) {
            if (!d.isValid()) return;
            d.teleport(a);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(1);
            d.setTransformation(shape(a, b));
        }

        public void remove() {
            kill(d);
        }
    }

    public Link link(Location a, Location b, Material m, float width) {
        return new Link(a, b, m, width);
    }

    // ==================================================================
    // Impact Sword: transparent glass box
    // ==================================================================

    /**
     * A square glass box with a defined frame around the player. It follows the player, pulses gently, opens
     * and closes with a short animation and is removed after 'ticks' (the same time the protection lasts).
     */
    public void glassBox(Player p, int ticks, float size) {
        final float h = size / 2f;
        final float bar = 0.08f;
        final float lift = size / 2f - 0.5f;
        final Quaternionf none = new Quaternionf();
        final Location c = p.getLocation().add(0, lift, 0);

        final List<Display> parts = new ArrayList<>();
        final List<Transformation> full = new ArrayList<>();

        full.add(boxT(size, size, size, 0, 0, 0, none));
        parts.add(block(c, Material.RED_STAINED_GLASS, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, none)));
        float[] s = {-h, h};
        for (float a : s) {
            for (float b : s) {
                full.add(boxT(size + bar, bar, bar, 0, a, b, none));     // edges along X
                full.add(boxT(bar, size + bar, bar, a, 0, b, none));     // edges along Y
                full.add(boxT(bar, bar, size + bar, a, b, 0, none));     // edges along Z
            }
        }
        for (int i = 1; i < full.size(); i++) {
            parts.add(block(c, Material.BLACK_CONCRETE, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, none)));
        }
        for (float a : s) {
            for (float b : s) {
                for (float d : s) {
                    full.add(boxT(0.2f, 0.2f, 0.2f, a, b, d, none));     // corner studs
                    parts.add(block(c, Material.RED_CONCRETE, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, none)));
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < parts.size(); i++) morph(parts.get(i), 6, full.get(i));
        }, 1L);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || t >= ticks) {
                    collapse(parts, 6);
                    cancel();
                    return;
                }
                Location at = p.getLocation().add(0, lift, 0);
                for (Display d : parts) d.teleport(at);
                if (t % 30 == 10) {   // slow breathing of the glass
                    morph(parts.get(0), 15, boxT(size * 1.03f, size * 1.03f, size * 1.03f, 0, 0, 0, none));
                } else if (t % 30 == 25) {
                    morph(parts.get(0), 15, full.get(0));
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    // ==================================================================
    // Clock circles (Dragonrend Infinite Void, Hourglass Dagger field)
    // ==================================================================

    /** Colours of a clock circle. */
    public record ClockPalette(Material ring, Material inner, Material marks, Material hourHand, Material minuteHand,
                               Material secondHand, Material hub, Material glass, NamedTextColor numerals) {}

    public static final ClockPalette VOID_CLOCK = new ClockPalette(Material.MAGENTA_CONCRETE, Material.PURPLE_CONCRETE,
            Material.WHITE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE,
            Material.BLACK_CONCRETE, Material.MAGENTA_STAINED_GLASS, NamedTextColor.LIGHT_PURPLE);

    public static final ClockPalette TIME_CLOCK = new ClockPalette(Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE,
            Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE,
            Material.BLACK_CONCRETE, Material.YELLOW_STAINED_GLASS, NamedTextColor.GOLD);

    /** A flat bar lying on the ground between two points (relative to 'base'). */
    private BlockDisplay flatBar(Location base, double x0, double z0, double x1, double z1, float w, float hgt, Material m) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        Quaternionf q = new Quaternionf().rotateY((float) Math.atan2(dx, dz));
        Location at = base.clone().add((x0 + x1) / 2.0, 0, (z0 + z1) / 2.0);
        return block(at, m, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, q));
    }

    private Transformation flatBarT(double x0, double z0, double x1, double z1, float w, float hgt) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        Quaternionf q = new Quaternionf().rotateY((float) Math.atan2(dx, dz));
        return boxT(w, hgt, len + 0.02f, 0, hgt / 2f, 0, q);
    }

    /** One hand of the clock: pivots on the centre, points along +Z rotated by 'deg' (0 = north / 12 o'clock). */
    private Transformation handT(float len, float w, double deg) {
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-deg + 180.0));
        // the bar runs from the pivot outwards: its centre is len/2 away along the rotated +Z axis
        Vector3f off = q.transform(new Vector3f(0f, 0f, len / 2f));
        return boxT(w, 0.08f, len, off.x, 0.1f, off.z, q);
    }

    /**
     * A clock drawn on the ground: two rings, 12 tick marks, four roman numerals, a hub, three hands turning
     * (the hour hand runs backwards), a ring of gears and a floating centrepiece. Lasts 'ticks', then folds away.
     */
    public void clockCircle(Location center, double radius, int ticks, ClockPalette pal, ItemStack centrepiece, float centreScale) {
        final World w = center.getWorld();
        final Location base = new Location(w, center.getX(), Math.floor(center.getY()) + 0.06, center.getZ());
        final List<Display> all = new ArrayList<>();
        final List<Transformation> targets = new ArrayList<>();
        final List<BlockDisplay> statics = new ArrayList<>();

        // rings
        int seg = 24;
        for (int ringNo = 0; ringNo < 2; ringNo++) {
            double r = ringNo == 0 ? radius : radius * 0.72;
            Material mat = ringNo == 0 ? pal.ring() : pal.inner();
            float width = ringNo == 0 ? 0.22f : 0.12f;
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg;
                double a1 = Math.PI * 2 * (i + 1) / seg;
                double x0 = Math.sin(a0) * r, z0 = -Math.cos(a0) * r, x1 = Math.sin(a1) * r, z1 = -Math.cos(a1) * r;
                BlockDisplay d = flatBar(base, x0, z0, x1, z1, width, 0.05f, mat);
                statics.add(d);
                targets.add(flatBarT(x0, z0, x1, z1, width, 0.05f));
            }
        }
        // tick marks
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30.0);
            boolean quarter = k % 3 == 0;
            double r0 = radius * (quarter ? 0.78 : 0.86);
            double r1 = radius * 0.95;
            double x0 = Math.sin(a) * r0, z0 = -Math.cos(a) * r0, x1 = Math.sin(a) * r1, z1 = -Math.cos(a) * r1;
            statics.add(flatBar(base, x0, z0, x1, z1, 0.1f, 0.06f, pal.marks()));
            targets.add(flatBarT(x0, z0, x1, z1, quarter ? 0.2f : 0.1f, 0.06f));
        }
        all.addAll(statics);
        for (int i = 0; i < statics.size(); i++) {
            final BlockDisplay d = statics.get(i);
            final Transformation tt = targets.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> morph(d, 8, tt), 1L + (i % 24) / 3);
        }

        // numerals at 12, 3, 6, 9
        String[] roman = {"XII", "III", "VI", "IX"};
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(k * 90.0);
            Location at = base.clone().add(Math.sin(a) * radius * 0.6, 0.55, -Math.cos(a) * radius * 0.6);
            all.add(text(at, roman[k], pal.numerals(), 1.3f));
        }

        // hub + hands
        final BlockDisplay hub = block(base, pal.hub(), boxT(0.5f, 0.2f, 0.5f, 0, 0.1f, 0, new Quaternionf()));
        all.add(hub);
        final BlockDisplay hour = block(base, pal.hourHand(), handT(0.5f * (float) radius, 0.22f, 0));
        final BlockDisplay minute = block(base, pal.minuteHand(), handT(0.78f * (float) radius, 0.14f, 0));
        final BlockDisplay second = block(base, pal.secondHand(), handT(0.9f * (float) radius, 0.07f, 0));
        all.add(hour);
        all.add(minute);
        all.add(second);

        // gears: small glass cubes round a dark core orbiting inside the inner ring
        final int gearCount = 8;
        final BlockDisplay[] gears = new BlockDisplay[gearCount];
        for (int i = 0; i < gearCount; i++) {
            gears[i] = block(base.clone().add(0, 0.5, 0), i % 2 == 0 ? pal.glass() : pal.hub(),
                    boxT(0.3f, 0.3f, 0.3f, 0, 0, 0, new Quaternionf()));
            all.add(gears[i]);
        }

        // floating centrepiece
        final ItemDisplay piece = item(base.clone().add(0, 2.2, 0), centrepiece, 0.01f);
        all.add(piece);
        Bukkit.getScheduler().runTaskLater(plugin, () -> morph(piece, 10,
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(centreScale, centreScale, centreScale), new Quaternionf())), 2L);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks || !hub.isValid()) {
                    collapse(all, 8);
                    cancel();
                    return;
                }
                morph(second, 2, handT(0.9f * (float) radius, 0.07f, t * 9.0));
                morph(minute, 2, handT(0.78f * (float) radius, 0.14f, t * 1.5));
                morph(hour, 2, handT(0.5f * (float) radius, 0.22f, -t * 0.6));
                double spin = -t * 5.0;
                double gr = radius * 0.5;
                for (int i = 0; i < gearCount; i++) {
                    double a = Math.toRadians(spin + i * 360.0 / gearCount);
                    gears[i].teleport(base.clone().add(Math.cos(a) * gr, 0.35 + 0.1 * Math.sin(t * 0.2 + i), Math.sin(a) * gr));
                    morph(gears[i], 2, boxT(0.3f, 0.3f, 0.3f, 0, 0, 0, new Quaternionf().rotateY((float) (t * 0.2 + i)).rotateX(0.6f)));
                }
                morph(piece, 2, new Transformation(new Vector3f(), rotY(t * 6.0), new Vector3f(centreScale, centreScale, centreScale), new Quaternionf()));
                t += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    // ==================================================================
    // shells, orbits, chains
    // ==================================================================

    /** A glass box around an entity (optionally with a darker concrete cube in the middle). Ends with the entity. */
    public void shell(Entity follow, Material glass, Material core, float sx, float sy, float sz, int ticks, boolean spin, float yOff) {
        final Location c = follow.getLocation().add(0, yOff, 0);
        final List<Display> parts = new ArrayList<>();
        final BlockDisplay outer = block(c, glass, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, new Quaternionf()));
        parts.add(outer);
        BlockDisplay inner = null;
        final float ci = Math.min(sx, Math.min(sy, sz)) * 0.45f;
        if (core != null) {
            inner = block(c, core, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, new Quaternionf()));
            parts.add(inner);
        }
        final BlockDisplay in = inner;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            morph(outer, 6, boxT(sx, sy, sz, 0, 0, 0, new Quaternionf()));
            if (in != null) morph(in, 6, boxT(ci, ci, ci, 0, 0, 0, new Quaternionf()));
        }, 1L);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!follow.isValid() || follow.isDead() || t >= ticks) {
                    collapse(parts, 5);
                    cancel();
                    return;
                }
                Location at = follow.getLocation().add(0, yOff, 0);
                for (Display d : parts) d.teleport(at);
                if (spin && t > 8) {
                    morph(outer, 2, boxT(sx, sy, sz, 0, 0, 0, rotY(t * 3.0)));
                    if (in != null) morph(in, 2, boxT(ci, ci, ci, 0, 0, 0, new Quaternionf().rotateY((float) Math.toRadians(-t * 7.0)).rotateX(0.7f)));
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Cubes circle an entity (bobbing, tumbling). Ends with the entity or after 'ticks'. */
    public void orbit(Entity follow, Material[] mats, int count, double radius, float size, int ticks, double degPerTick, double yOff) {
        final List<BlockDisplay> parts = new ArrayList<>();
        final Location c = follow.getLocation().add(0, yOff, 0);
        for (int i = 0; i < count; i++) {
            parts.add(block(c, mats[i % mats.length], boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, new Quaternionf())));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay d : parts) morph(d, 6, boxT(size, size, size, 0, 0, 0, new Quaternionf()));
        }, 1L);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!follow.isValid() || follow.isDead() || t >= ticks) {
                    collapse(parts, 5);
                    cancel();
                    return;
                }
                Location o = follow.getLocation().add(0, yOff, 0);
                for (int i = 0; i < parts.size(); i++) {
                    double a = Math.toRadians(t * degPerTick + i * 360.0 / parts.size());
                    BlockDisplay d = parts.get(i);
                    d.teleport(o.clone().add(Math.cos(a) * radius, Math.sin(t * 0.15 + i) * 0.25, Math.sin(a) * radius));
                    if (t > 4) morph(d, 2, boxT(size, size, size, 0, 0, 0, new Quaternionf().rotateY((float) (t * 0.25 + i)).rotateX((float) (t * 0.17))));
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Two rings of chain links turn in opposite directions around an entity. */
    public void chains(Entity follow, int ticks) {
        final int perRing = 9;
        final double[] heights = {0.45, 1.3};
        final double[] radii = {0.8, 0.72};
        final List<BlockDisplay> links = new ArrayList<>();
        final Location c = follow.getLocation();
        for (int r = 0; r < heights.length; r++) {
            for (int i = 0; i < perRing; i++) {
                links.add(block(c, CHAIN, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, new Quaternionf())));
            }
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!follow.isValid() || follow.isDead() || t >= ticks) {
                    collapse(links, 4);
                    cancel();
                    return;
                }
                Location o = follow.getLocation();
                for (int r = 0; r < heights.length; r++) {
                    double dir = r == 0 ? 1.0 : -1.0;
                    for (int i = 0; i < perRing; i++) {
                        double a = Math.toRadians(dir * t * 5.0 + i * 360.0 / perRing);
                        BlockDisplay d = links.get(r * perRing + i);
                        d.teleport(o.clone().add(Math.cos(a) * radii[r], heights[r], Math.sin(a) * radii[r]));
                        // the chain's long axis follows the direction of travel
                        Vector3f tangent = new Vector3f((float) (-Math.sin(a) * dir), 0f, (float) (Math.cos(a) * dir));
                        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), tangent);
                        morph(d, 2, boxT(0.85f, 0.85f, 0.85f, 0, 0, 0, q));
                    }
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    /** An item display that follows an entity (above its head, over its body...) and spins. */
    public ItemDisplay followItem(Entity e, ItemStack stack, float scale, double yOff, int ticks, float spinPerTick, BooleanSupplier keep) {
        final ItemDisplay d = item(e.getLocation().add(0, yOff, 0), stack, 0.01f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> morph(d, 6,
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf())), 1L);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!d.isValid() || !e.isValid() || e.isDead() || t >= ticks || !keep.getAsBoolean()) {
                    if (d.isValid()) collapse(List.of(d), 4);
                    cancel();
                    return;
                }
                d.teleport(e.getLocation().add(0, yOff, 0));
                if (spinPerTick != 0f) {
                    morph(d, 2, new Transformation(new Vector3f(), rotY(t * spinPerTick), new Vector3f(scale, scale, scale), new Quaternionf()));
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
        return d;
    }

    // ==================================================================
    // slash arc and dragon bite
    // ==================================================================

    /** Cubes appear one after another along an arc in front of the player and shrink away: a visible axe swing. */
    public void arc(Player p, Material glass, Material core, double radius, double spanDeg, int cubes) {
        final Location o = p.getLocation().add(0, 1.0, 0);
        final double yaw = Math.toRadians(p.getLocation().getYaw());
        for (int i = 0; i < cubes; i++) {
            final double off = Math.toRadians(-spanDeg / 2.0 + spanDeg * i / Math.max(1, cubes - 1));
            final double a = yaw + off;
            final Location at = o.clone().add(-Math.sin(a) * radius, 0.15 * Math.sin(i * 0.7), Math.cos(a) * radius);
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Quaternionf q = new Quaternionf().rotateY((float) -a).rotateX(0.5f);
                BlockDisplay g = block(at, glass, boxT(0.05f, 0.05f, 0.05f, 0, 0, 0, q));
                BlockDisplay k = block(at, core, boxT(0.05f, 0.05f, 0.05f, 0, 0, 0, q));
                morph(g, 2, boxT(0.7f, 0.7f, 0.7f, 0, 0, 0, q));
                morph(k, 2, boxT(0.34f, 0.34f, 0.34f, 0, 0, 0, q));
                List<Display> pair = List.of(g, k);
                Bukkit.getScheduler().runTaskLater(plugin, () -> collapse(pair, 5), 4L);
            }, (long) (idx * 0.6));
        }
    }

    /** Two dragon heads snap shut on a target. */
    public void bite(Location at) {
        final ItemStack head = new ItemStack(Material.DRAGON_HEAD);
        final ItemDisplay top = item(at.clone().add(0, 0.2, 0), head, 1.5f);
        final ItemDisplay bottom = item(at.clone().add(0, 0.2, 0), head, 1.5f);
        top.setTransformation(new Transformation(new Vector3f(0, 2.4f, 0), new Quaternionf().rotateX(0.5f), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
        bottom.setTransformation(new Transformation(new Vector3f(0, -0.4f, 0), new Quaternionf().rotateX((float) Math.PI - 0.5f), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
        morphLater(top, 1L, 4, new Transformation(new Vector3f(0, 1.15f, 0), new Quaternionf().rotateX(-0.1f), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
        morphLater(bottom, 1L, 4, new Transformation(new Vector3f(0, 0.65f, 0), new Quaternionf().rotateX((float) Math.PI + 0.1f), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf()));
        List<Display> pair = List.of(top, bottom);
        Bukkit.getScheduler().runTaskLater(plugin, () -> collapse(pair, 6), 14L);
    }

    // ==================================================================
    // Spider Blade / Widow's Axe: dark web on a real cobweb block
    // ==================================================================

    /**
     * A darkened web drawn inside the cobweb block: a dark glass cell, a thicker cobweb and the four space
     * diagonals as strands. It lives exactly as long as the real cobweb: it is removed when the block is broken,
     * replaced or after 'ticks'.
     */
    public void cobweb(Block b, int ticks) {
        if (webs.containsKey(b)) dissolve(b);
        final Location c = b.getLocation().add(0.5, 0.5, 0.5);
        final List<Display> parts = new ArrayList<>();
        final Quaternionf none = new Quaternionf();

        BlockDisplay dark = block(c, Material.BLACK_STAINED_GLASS, boxT(0.05f, 0.05f, 0.05f, 0, 0, 0, none));
        BlockDisplay web = block(c, Material.COBWEB, boxT(0.05f, 0.05f, 0.05f, 0, 0, 0, none));
        parts.add(dark);
        parts.add(web);
        final List<Transformation> full = new ArrayList<>();
        full.add(boxT(1.02f, 1.02f, 1.02f, 0, 0, 0, none));
        full.add(boxT(1.12f, 1.12f, 1.12f, 0, 0, 0, none));

        float[] sgn = {-1f, 1f};
        for (float sx : sgn) {
            for (float sz : sgn) {
                Vector dir = new Vector(sx, 1.0, sz).normalize();
                Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
                parts.add(block(c, Material.BLACK_CONCRETE, boxT(0.02f, 0.02f, 0.02f, 0, 0, 0, q)));
                full.add(boxT(0.035f, 0.035f, 1.7f, 0, 0, 0, q));
            }
        }
        webs.put(b, parts);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < parts.size(); i++) morph(parts.get(i), 6, full.get(i));
        }, 1L);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (webs.get(b) != parts) {   // already dissolved (broken) or replaced by a newer web
                    cancel();
                    return;
                }
                if (t >= ticks + 10 || b.getType() != Material.COBWEB) {
                    dissolve(b);
                    cancel();
                    return;
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void dissolve(Block b) {
        List<Display> parts = webs.remove(b);
        if (parts != null) collapse(parts, 6);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (webs.containsKey(e.getBlock())) dissolve(e.getBlock());
    }
}
