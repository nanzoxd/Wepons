package com.wepons;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One custom weapon: look, passive effects and abilities. */
public final class Weapon {

    /**
     * How an ability starts. ONE = F, TWO = F with an item in the offhand
     * (or Shift + F, see keybinds.ability-2 in config.yml).
     */
    public enum Trigger {
        PASSIVE("Passive", ""),
        HIT("On Hit", ""),
        BLOCK("On Block", ""),
        ONE("Ability 1", "F"),
        TWO("Ability 2", "F + Offhand");

        public final String label;
        public final String key;

        Trigger(String label, String key) {
            this.label = label;
            this.key = key;
        }

        public boolean isPower() {
            return this == ONE || this == TWO;
        }
    }

    public record Ability(String key, String name, Trigger trigger, int cooldown, String desc) {}

    public record Eff(PotionEffectType type, int amp) {}

    public final String id;
    public final String name;
    public final TextColor color;
    public final Material material;
    public final int cmd;

    public String kind;          // SWORD, AXE, CROWN ... shown in the lore header
    public String tagline = "";  // the white line under the header
    public Particle aura = Particle.CLOUD;
    public boolean worn;        // lives in the helmet slot
    public boolean swordStats;  // netherite-sword damage and attack speed
    public final List<Ability> abilities = new ArrayList<>();
    public final List<Eff> held = new ArrayList<>();   // effects while in main hand
    public final List<Eff> carry = new ArrayList<>();  // effects while anywhere in inventory
    public final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();

    public Weapon(String id, Material material, String name, String hex, int cmd) {
        this.id = id;
        this.material = material;
        this.name = name;
        this.color = TextColor.fromHexString(hex);
        this.cmd = cmd;
        String m = material.name();
        this.kind = m.substring(m.lastIndexOf('_') + 1);
    }

    public Weapon kind(String kind) {
        this.kind = kind;
        return this;
    }

    public Weapon tag(String tagline) {
        this.tagline = tagline;
        return this;
    }

    public Weapon aura(Particle particle) {
        this.aura = particle;
        return this;
    }

    public Weapon worn() {
        this.worn = true;
        return this;
    }

    public Weapon swordStats() {
        this.swordStats = true;
        return this;
    }

    public Weapon enchant(Enchantment e, int level) {
        enchants.put(e, level);
        return this;
    }

    /** Passive line in the lore. Effects are applied while the weapon is held. */
    public Weapon passive(String desc, Eff... effects) {
        abilities.add(new Ability("passive", "Passive", Trigger.PASSIVE, 0, desc));
        held.addAll(List.of(effects));
        return this;
    }

    /** Passive line in the lore. Effects are applied while the weapon is anywhere in the inventory. */
    public Weapon carry(String desc, Eff... effects) {
        abilities.add(new Ability("carry", "Carry", Trigger.PASSIVE, 0, desc));
        carry.addAll(List.of(effects));
        return this;
    }

    /** Passive that triggers on hit (or on block). Shown under Passives. */
    public Weapon proc(String key, String name, Trigger trigger, int cooldown, String desc) {
        abilities.add(new Ability(key, name, trigger, cooldown, desc));
        return this;
    }

    /** Ability 1 (F). */
    public Weapon one(String key, String name, int cooldown, String desc) {
        abilities.add(new Ability(key, name, Trigger.ONE, cooldown, desc));
        return this;
    }

    /** Ability 2 (F + offhand). */
    public Weapon two(String key, String name, int cooldown, String desc) {
        abilities.add(new Ability(key, name, Trigger.TWO, cooldown, desc));
        return this;
    }
}
