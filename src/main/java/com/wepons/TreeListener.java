package com.wepons;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lumberjack Axe: one swing fells a whole NATURAL tree.
 * Logs a player placed are remembered (saved per chunk) and never chopped, and a log cluster only counts
 * as a tree when natural leaves (not player-placed) touch it, so houses and builds are left alone.
 */
public final class TreeListener implements Listener {

    private static final int MAX_LOGS = 400;
    private static final int RADIUS = 12;
    private static final int HEIGHT = 48;
    private static final int MIN_NATURAL_LEAVES = 3;

    private final Wepons plugin;
    private final NamespacedKey placedKey;
    private final Set<UUID> busy = new HashSet<>();

    public TreeListener(Wepons plugin) {
        this.plugin = plugin;
        this.placedKey = new NamespacedKey(plugin, "placed_logs");
    }

    // ------------------------------------------------------------------
    // Remember player-placed logs
    // ------------------------------------------------------------------

    private static boolean isTreeLog(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") && !n.startsWith("STRIPPED_");
    }

    private static long pack(Block b) {
        return ((long) (b.getX() & 15) << 40) | ((long) (b.getZ() & 15) << 32) | (long) (b.getY() + 2048);
    }

    private boolean isPlaced(Block b) {
        long[] arr = b.getChunk().getPersistentDataContainer().get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr == null) return false;
        long v = pack(b);
        for (long x : arr) if (x == v) return true;
        return false;
    }

    private void setPlaced(Block b, boolean placed) {
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        long[] arr = pdc.get(placedKey, PersistentDataType.LONG_ARRAY);
        long v = pack(b);
        Set<Long> set = new LinkedHashSet<>();
        if (arr != null) for (long x : arr) set.add(x);
        if (placed ? !set.add(v) : !set.remove(v)) return;
        if (set.isEmpty()) {
            pdc.remove(placedKey);
        } else {
            pdc.set(placedKey, PersistentDataType.LONG_ARRAY, set.stream().mapToLong(Long::longValue).toArray());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isTreeLog(e.getBlockPlaced().getType())) setPlaced(e.getBlockPlaced(), true);
    }

    /** Runs after the axe handler, so the clicked log is still known as placed while it is checked. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakAny(BlockBreakEvent e) {
        if (isTreeLog(e.getBlock().getType())) setPlaced(e.getBlock(), false);
    }

    // ------------------------------------------------------------------
    // The axe
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        Weapon w = plugin.registry().of(tool);
        if (w == null || !w.id.equals("lumberjack")) return;

        Block origin = e.getBlock();
        if (!isTreeLog(origin.getType()) || isPlaced(origin)) return;

        List<Block> tree = findTree(origin);
        if (tree == null) return; // not a natural tree: breaks like a normal block

        busy.add(p.getUniqueId());
        try {
            for (Block b : tree) {
                if (b.equals(origin) || b.getType() != origin.getType()) continue;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                plugin.getServer().getPluginManager().callEvent(ev); // protection plugins can veto each log
                if (ev.isCancelled()) continue;
                if (ev.isDropItems()) b.breakNaturally(tool);
                else b.setType(Material.AIR);
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
        origin.getWorld().playSound(origin.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.7f);
    }

    /** All connected logs of the same wood, or null if this is not a natural tree. */
    private List<Block> findTree(Block origin) {
        World world = origin.getWorld();
        Material type = origin.getType();
        List<Block> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> leaves = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(key(origin));

        while (!queue.isEmpty()) {
            Block b = queue.poll();
            found.add(b);
            if (found.size() > MAX_LOGS) return null; // too big: probably a structure

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int x = b.getX() + dx, y = b.getY() + dy, z = b.getZ() + dz;
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                        Block n = world.getBlockAt(x, y, z);

                        if (n.getBlockData() instanceof Leaves l) {
                            if (!l.isPersistent()) leaves.add(key(n)); // natural leaves only
                            continue;
                        }
                        if (n.getType() != type) continue;
                        if (Math.abs(x - origin.getX()) > RADIUS || Math.abs(z - origin.getZ()) > RADIUS
                                || Math.abs(y - origin.getY()) > HEIGHT) continue;
                        if (!seen.add(key(n))) continue;
                        if (isPlaced(n)) continue;
                        queue.add(n);
                    }
                }
            }
        }
        return leaves.size() >= MIN_NATURAL_LEAVES ? found : null;
    }

    private static long key(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | (long) ((b.getY() + 2048) & 0xFFF);
    }
}
