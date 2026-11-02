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

    public enum Trigger {
        PASSIVE("Passive"),
        RIGHT_CLICK("Right-Click"),
        HIT("On Hit"),
        BLOCK("On Block"),
        SHIFT_RIGHT("Shift + Right-Click"),
        SHIFT_SWAP("Shift + F");

        public final String label;

        Trigger(String label) {
            this.label = label;
        }
    }

    public record Ability(String key, String name, Trigger trigger, int cooldown, String desc) {}

    public record Eff(PotionEffectType type, int amp) {}

    public final String id;
    public final String name;
    public final TextColor color;
    public final Material material;
    public final int cmd;

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

    /** Passive shown in the lore. Effects are applied while the weapon is held. */
    public Weapon passive(String title, String desc, Eff... effects) {
        abilities.add(new Ability("passive", title, Trigger.PASSIVE, 0, desc));
        held.addAll(List.of(effects));
        return this;
    }

    /** Passive shown in the lore. Effects are applied while the weapon is anywhere in the inventory. */
    public Weapon carry(String title, String desc, Eff... effects) {
        abilities.add(new Ability("carry", title, Trigger.PASSIVE, 0, desc));
        carry.addAll(List.of(effects));
        return this;
    }

    public Weapon ability(String key, String title, Trigger trigger, int cooldown, String desc) {
        abilities.add(new Ability(key, title, trigger, cooldown, desc));
        return this;
    }
}
