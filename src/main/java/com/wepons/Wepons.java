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
    private DisplayFX displays;
    private PackSync packSync;
    private KingsBridge kings;
    private CustomFX customFx;
    private Crazy crazy;
    private CooldownBar cooldownBar;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        registry = new WeaponRegistry(this);
        kings = new KingsBridge(this);
        holders = new HolderManager(this);
        abilities = new Abilities(this);
        relics = new Relics(this);
        displays = new DisplayFX(this);
        customFx = new CustomFX(this);
        crazy = new Crazy(this);
        fx = new VisualFX(this);
        upgrades = new Upgrades(this);
        enchantDialogs = new EnchantDialogs(this);

        getServer().getPluginManager().registerEvents(abilities, this);
        getServer().getPluginManager().registerEvents(crazy, this);
        getServer().getPluginManager().registerEvents(customFx, this);
        cooldownBar = new CooldownBar(this);
        getServer().getPluginManager().registerEvents(cooldownBar, this);
        cooldownBar.start();
        crazy.start();
        getServer().getPluginManager().registerEvents(relics, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new MinerListener(this), this);
        getServer().getPluginManager().registerEvents(new TreeListener(this), this);
        getServer().getPluginManager().registerEvents(fx, this);
        getServer().getPluginManager().registerEvents(upgrades, this);
        packSync = new PackSync(this);
        getServer().getPluginManager().registerEvents(packSync, this);
        packSync.start();
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
        if (displays != null) displays.cleanup();
        if (crazy != null) crazy.shutdown();
        if (customFx != null) customFx.cleanup();
        if (holders != null) holders.save();
    }

    public WeaponRegistry registry() {
        return registry;
    }

    public Abilities abilities() {
        return abilities;
    }

    public Crazy crazy() {
        return crazy;
    }

    public CustomFX customFx() {
        return customFx;
    }

    public Relics relics() {
        return relics;
    }

    public VisualFX fx() {
        return fx;
    }

    public DisplayFX displays() {
        return displays;
    }

    public EnchantDialogs enchantDialogs() {
        return enchantDialogs;
    }

    public Upgrades upgrades() {
        return upgrades;
    }

    public PackSync packSync() {
        return packSync;
    }

    public KingsBridge kings() {
        return kings;
    }

    public HolderManager holders() {
        return holders;
    }
}
