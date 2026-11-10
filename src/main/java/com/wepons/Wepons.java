package com.wepons;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Wepons extends JavaPlugin {

    private WeaponRegistry registry;
    private HolderManager holders;
    private Abilities abilities;
    private Relics relics;
    private VisualFX fx;
    private Upgrades upgrades;
    private EnchantDialogs enchantDialogs;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        registry = new WeaponRegistry(this);
        holders = new HolderManager(this);
        abilities = new Abilities(this);
        relics = new Relics(this);
        fx = new VisualFX(this);
        upgrades = new Upgrades(this);
        enchantDialogs = new EnchantDialogs(this);

        getServer().getPluginManager().registerEvents(abilities, this);
        getServer().getPluginManager().registerEvents(relics, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new MinerListener(this), this);
        getServer().getPluginManager().registerEvents(new TreeListener(this), this);
        getServer().getPluginManager().registerEvents(fx, this);
        getServer().getPluginManager().registerEvents(upgrades, this);
        abilities.start();

        Commands commands = new Commands(this);
        for (String name : new String[]{"wepons", "holder"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        getLogger().info("Loaded " + registry.all().size() + " weapons.");
    }

    @Override
    public void onDisable() {
        if (abilities != null) abilities.shutdown();
        if (holders != null) holders.save();
    }

    public WeaponRegistry registry() {
        return registry;
    }

    public Relics relics() {
        return relics;
    }

    public VisualFX fx() {
        return fx;
    }

    public EnchantDialogs enchantDialogs() {
        return enchantDialogs;
    }

    public Upgrades upgrades() {
        return upgrades;
    }

    public HolderManager holders() {
        return holders;
    }
}
