package com.wepons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Smelter Pickaxe: ores drop smelted with Fortune III, every other block drops
 * like Silk Touch, and each swing breaks 2 blocks.
 */
public final class MinerListener implements Listener {

    private static final Map<Material, Material> SMELT = Map.of(
            Material.IRON_ORE, Material.IRON_INGOT,
            Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT,
            Material.GOLD_ORE, Material.GOLD_INGOT,
            Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT,
            Material.NETHER_GOLD_ORE, Material.GOLD_INGOT,
            Material.COPPER_ORE, Material.COPPER_INGOT,
            Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT,
            Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP
    );

    private final Wepons plugin;
    private final Set<UUID> busy = new HashSet<>();
    private final ItemStack silkTool = new ItemStack(Material.NETHERITE_PICKAXE);
    private final ItemStack fortuneTool = new ItemStack(Material.NETHERITE_PICKAXE);

    public MinerListener(Wepons plugin) {
        this.plugin = plugin;
        silkTool.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        fortuneTool.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE) return;

        Weapon w = plugin.registry().of(p.getInventory().getItemInMainHand());
        if (w == null || !w.id.equals("smelter")) return;

        Block main = e.getBlock();
        Block extra = extraBlock(p, main);

        // the block you mined
        List<ItemStack> mainDrops = collect(p, main);
        e.setDropItems(false);
        drop(main, mainDrops);

        // the second block (goes through the normal break event so protection plugins can stop it)
        if (extra == null || !canBreak(extra)) return;
        busy.add(p.getUniqueId());
        try {
            BlockBreakEvent second = new BlockBreakEvent(extra, p);
            Bukkit.getPluginManager().callEvent(second);
            if (second.isCancelled()) return;

            List<ItemStack> extraDrops = collect(p, extra);
            int exp = second.getExpToDrop();
            boolean dropItems = second.isDropItems();
            Material type = extra.getType();

            extra.setType(Material.AIR);
            extra.getWorld().playSound(extra.getLocation(), Sound.BLOCK_STONE_BREAK, 0.6f, 1f);
            if (dropItems) drop(extra, extraDrops);
            if (exp > 0) {
                extra.getWorld().spawn(extra.getLocation().add(0.5, 0.5, 0.5), ExperienceOrb.class, o -> o.setExperience(exp));
            }
            extra.getWorld().spawnParticle(Particle.BLOCK, extra.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, type.createBlockData());
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    /** Block above, or the block behind when mining straight up or down. */
    private Block extraBlock(Player p, Block main) {
        BlockFace face = p.getTargetBlockFace(6);
        if (face == BlockFace.UP || face == BlockFace.DOWN) return main.getRelative(face.getOppositeFace());
        return main.getRelative(BlockFace.UP);
    }

    private boolean canBreak(Block b) {
        Material t = b.getType();
        if (t.isAir() || b.isLiquid() || t.getHardness() < 0 || t == Material.SPAWNER) return false;
        return !(b.getState() instanceof Container);
    }

    private boolean isOre(Material t) {
        return t == Material.ANCIENT_DEBRIS || t.name().endsWith("_ORE");
    }

    private List<ItemStack> collect(Player p, Block b) {
        Material type = b.getType();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Material smelted = SMELT.get(type);
        if (smelted != null) {
            int base = (smelted == Material.COPPER_INGOT) ? 2 + rnd.nextInt(4) : 1;
            int bonus = Math.max(0, rnd.nextInt(5) - 1);            // Fortune III
            int amount = (type == Material.ANCIENT_DEBRIS) ? 1 : base * (1 + bonus);
            b.getWorld().spawnParticle(Particle.FLAME, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.02);
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.3f, 1.6f);
            return new ArrayList<>(List.of(new ItemStack(smelted, amount)));
        }
        if (isOre(type)) return new ArrayList<>(b.getDrops(fortuneTool, p));
        return new ArrayList<>(b.getDrops(silkTool, p));
    }

    private void drop(Block b, List<ItemStack> drops) {
        for (ItemStack item : drops) {
            if (item == null || item.getType().isAir()) continue;
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
        }
    }
}
