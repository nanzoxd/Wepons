package com.wepons;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * The block palette and signature model effects of every weapon (used by DisplayFX through VisualFX).
 * glass = the big tumbling cubes, chunks = scattered debris and spikes, tile = the flat ring on the ground.
 */
public final class ModelKits {

    public enum Sig {
        CUBES, RING, PILLARS, TENDRILS, TORNADO, ORBIT, WAVE, WEB, PLATES, HELIX, EMBERS, ITEM_RAIN, FISSURE,
        RIFT, BOLTS, WINGS, METEOR, DROP, HALO, SONAR, SERPENT, BOOMERANG, WEAPON_RAIN, BLOOM, SINGULARITY
    }

    public record Kit(Material glass, Material[] chunks, Material tile, Material[] items, Sig[] sigs) {}

    private static final Map<String, Kit> KITS = new HashMap<>();
    private static final Kit DEFAULT = kit(Material.WHITE_STAINED_GLASS,
            new Material[]{Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE}, Material.WHITE_CONCRETE, Sig.CUBES);

    private static Kit kit(Material glass, Material[] chunks, Material tile, Sig... sigs) {
        return new Kit(glass, chunks, tile, new Material[]{Material.STICK}, sigs);
    }

    private static void items(String id, Material... its) {
        Kit k = KITS.get(id);
        KITS.put(id, new Kit(k.glass(), k.chunks(), k.tile(), its, k.sigs()));
    }

    private static void sigs(String id, Sig... sg) {
        Kit k = KITS.get(id);
        KITS.put(id, new Kit(k.glass(), k.chunks(), k.tile(), k.items(), sg));
    }

    private static void tile(String id, Material t) {
        Kit k = KITS.get(id);
        KITS.put(id, new Kit(k.glass(), k.chunks(), t, k.items(), k.sigs()));
    }

    private static Material[] m(Material... ms) {
        return ms;
    }

    private static void put(String id, Material glass, Material[] chunks, Material tile, Sig... sigs) {
        KITS.put(id, kit(glass, chunks, tile, sigs));
    }

    static {
        put("weaving", Material.PURPLE_STAINED_GLASS, m(Material.BLACK_CONCRETE, Material.PURPLE_CONCRETE, Material.COBWEB), Material.PURPLE_CONCRETE, Sig.RING, Sig.CUBES);
        put("resistance", Material.LIGHT_BLUE_STAINED_GLASS, m(Material.IRON_BLOCK, Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE), Material.CYAN_CONCRETE, Sig.ORBIT);
        put("speed", Material.WHITE_STAINED_GLASS, m(Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE), Material.LIGHT_BLUE_CONCRETE, Sig.TORNADO);
        put("invisibility", Material.GRAY_STAINED_GLASS, m(Material.BLACK_CONCRETE, Material.GRAY_CONCRETE), Material.BLACK_CONCRETE, Sig.CUBES);
        put("regeneration", Material.LIME_STAINED_GLASS, m(Material.GREEN_CONCRETE, Material.LIME_CONCRETE, Material.MOSS_BLOCK), Material.LIME_CONCRETE, Sig.PILLARS, Sig.RING);
        put("saturation", Material.ORANGE_STAINED_GLASS, m(Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.HAY_BLOCK), Material.ORANGE_CONCRETE, Sig.CUBES);
        put("haste", Material.YELLOW_STAINED_GLASS, m(Material.COBBLED_DEEPSLATE, Material.GOLD_BLOCK, Material.RAW_GOLD_BLOCK), Material.YELLOW_CONCRETE, Sig.RING);
        put("strength", Material.RED_STAINED_GLASS, m(Material.RED_CONCRETE, Material.NETHER_WART_BLOCK, Material.REDSTONE_BLOCK), Material.RED_CONCRETE, Sig.RING, Sig.CUBES);
        put("dash", Material.LIGHT_BLUE_STAINED_GLASS, m(Material.BLUE_ICE, Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE), Material.LIGHT_BLUE_CONCRETE, Sig.WAVE);
        put("voidfang", Material.PURPLE_STAINED_GLASS, m(Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.PURPLE_CONCRETE), Material.PURPLE_CONCRETE, Sig.CUBES);
        put("stormbreaker", Material.YELLOW_STAINED_GLASS, m(Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE), Material.YELLOW_CONCRETE, Sig.PILLARS, Sig.RING);
        put("freeze", Material.LIGHT_BLUE_STAINED_GLASS, m(Material.PACKED_ICE, Material.BLUE_ICE, Material.ICE), Material.LIGHT_BLUE_CONCRETE, Sig.CUBES);
        put("scythe", Material.WHITE_STAINED_GLASS, m(Material.BONE_BLOCK, Material.SOUL_SAND, Material.BLACK_CONCRETE), Material.BLACK_CONCRETE, Sig.CUBES);
        put("fireshield", Material.ORANGE_STAINED_GLASS, m(Material.MAGMA_BLOCK, Material.ORANGE_CONCRETE, Material.RED_CONCRETE), Material.ORANGE_CONCRETE, Sig.ORBIT);
        put("flightsword", Material.WHITE_STAINED_GLASS, m(Material.WHITE_WOOL, Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE), Material.WHITE_CONCRETE, Sig.TORNADO);
        put("lightningsword", Material.YELLOW_STAINED_GLASS, m(Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE, Material.GOLD_BLOCK), Material.YELLOW_CONCRETE, Sig.PILLARS);
        put("cobwebaxe", Material.MAGENTA_STAINED_GLASS, m(Material.PURPLE_CONCRETE, Material.BLACK_CONCRETE, Material.MAGENTA_CONCRETE), Material.MAGENTA_CONCRETE, Sig.RING);
        put("gravityaxe", Material.CYAN_STAINED_GLASS, m(Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.CYAN_CONCRETE), Material.CYAN_CONCRETE, Sig.ORBIT, Sig.CUBES);
        put("grapplebow", Material.MAGENTA_STAINED_GLASS, m(Material.MAGENTA_CONCRETE, Material.WHITE_CONCRETE), Material.MAGENTA_CONCRETE, Sig.WAVE);
        put("dashmace", Material.ORANGE_STAINED_GLASS, m(Material.MAGMA_BLOCK, Material.NETHERRACK, Material.ORANGE_CONCRETE), Material.ORANGE_CONCRETE, Sig.RING, Sig.CUBES);
        put("banhammer", Material.GRAY_STAINED_GLASS, m(Material.IRON_BLOCK, Material.BLACK_CONCRETE, Material.RED_CONCRETE), Material.RED_CONCRETE, Sig.RING);
        put("smelter", Material.ORANGE_STAINED_GLASS, m(Material.MAGMA_BLOCK, Material.ORANGE_CONCRETE, Material.COBBLED_DEEPSLATE), Material.ORANGE_CONCRETE, Sig.RING);
        put("lumberjack", Material.LIME_STAINED_GLASS, m(Material.OAK_LOG, Material.DARK_OAK_LOG, Material.GREEN_CONCRETE), Material.GREEN_CONCRETE, Sig.RING);
        put("impact", Material.RED_STAINED_GLASS, m(Material.RED_CONCRETE, Material.NETHER_WART_BLOCK, Material.REDSTONE_BLOCK), Material.RED_CONCRETE, Sig.RING);
        put("monarchcrown", Material.YELLOW_STAINED_GLASS, m(Material.GOLD_BLOCK, Material.YELLOW_CONCRETE, Material.RAW_GOLD_BLOCK), Material.YELLOW_CONCRETE, Sig.CUBES, Sig.RING);
        put("fortune_blade", Material.YELLOW_STAINED_GLASS, m(Material.GOLD_BLOCK, Material.RAW_GOLD_BLOCK, Material.YELLOW_CONCRETE), Material.YELLOW_CONCRETE, Sig.PILLARS);
        put("flame_blade", Material.ORANGE_STAINED_GLASS, m(Material.MAGMA_BLOCK, Material.ORANGE_CONCRETE, Material.RED_CONCRETE), Material.ORANGE_CONCRETE, Sig.PILLARS, Sig.RING);
        put("bloom_blade", Material.PINK_STAINED_GLASS, m(Material.PINK_CONCRETE, Material.MAGENTA_CONCRETE, Material.CHERRY_LEAVES), Material.PINK_CONCRETE, Sig.CUBES);
        put("ancient_blade", Material.CYAN_STAINED_GLASS, m(Material.SCULK, Material.DEEPSLATE, Material.CYAN_CONCRETE), Material.CYAN_CONCRETE, Sig.PILLARS, Sig.RING);
        put("dragonrend", Material.MAGENTA_STAINED_GLASS, m(Material.PURPLE_CONCRETE, Material.OBSIDIAN, Material.MAGENTA_CONCRETE), Material.PURPLE_CONCRETE, Sig.CUBES, Sig.ORBIT);
        put("tidebreaker", Material.BLUE_STAINED_GLASS, m(Material.PRISMARINE, Material.BLUE_CONCRETE, Material.DARK_PRISMARINE), Material.BLUE_CONCRETE, Sig.TORNADO, Sig.RING);
        put("wither_symbiote", Material.BLACK_STAINED_GLASS, m(Material.BLACK_CONCRETE, Material.SOUL_SAND, Material.PURPLE_CONCRETE), Material.BLACK_CONCRETE, Sig.CUBES);
        put("omen", Material.CYAN_STAINED_GLASS, m(Material.DARK_PRISMARINE, Material.CYAN_CONCRETE, Material.PRISMARINE), Material.CYAN_CONCRETE, Sig.ORBIT);

        // ---- every weapon gets its own set piece (these replace the shared defaults above) ----
        sigs("weaving", Sig.WEB);                         tile("weaving", Material.WHITE_CONCRETE);
        sigs("resistance", Sig.PLATES);
        sigs("speed", Sig.TORNADO);
        sigs("invisibility", Sig.RIFT);
        sigs("regeneration", Sig.HELIX, Sig.EMBERS);
        sigs("saturation", Sig.ITEM_RAIN);
        items("saturation", Material.GOLDEN_APPLE, Material.COOKED_BEEF, Material.BREAD, Material.APPLE);
        sigs("haste", Sig.FISSURE);
        sigs("dash", Sig.RIFT, Sig.WAVE);
        sigs("voidfang", Sig.SINGULARITY);
        sigs("stormbreaker", Sig.BOLTS, Sig.RING);
        sigs("scythe", Sig.BOOMERANG);
        sigs("flightsword", Sig.WINGS);
        sigs("lightningsword", Sig.BOLTS);
        sigs("cobwebaxe", Sig.WEB);                       tile("cobwebaxe", Material.LIGHT_GRAY_CONCRETE);
        sigs("dashmace", Sig.METEOR);
        sigs("banhammer", Sig.DROP);
        sigs("smelter", Sig.FISSURE);
        sigs("lumberjack", Sig.ITEM_RAIN);
        items("lumberjack", Material.OAK_LEAVES, Material.OAK_SAPLING, Material.STICK, Material.APPLE);
        sigs("monarchcrown", Sig.HALO, Sig.CUBES);
        sigs("fortune_blade", Sig.ITEM_RAIN);
        items("fortune_blade", Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.RAW_GOLD, Material.GOLD_NUGGET);
        sigs("flame_blade", Sig.PILLARS, Sig.EMBERS);
        sigs("bloom_blade", Sig.BLOOM, Sig.ITEM_RAIN);
        items("bloom_blade", Material.PINK_PETALS, Material.CHERRY_SAPLING);
        sigs("ancient_blade", Sig.SONAR, Sig.PILLARS);
        sigs("dragonrend", Sig.SERPENT);
        sigs("tidebreaker", Sig.SERPENT, Sig.RING);
        sigs("wither_symbiote", Sig.TENDRILS);
        sigs("omen", Sig.WEAPON_RAIN);
    }

    public static Kit of(String weaponId) {
        return KITS.getOrDefault(weaponId, DEFAULT);
    }

    private ModelKits() {}
}
