package com.wepons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Commands implements CommandExecutor {

    private final Wepons plugin;

    public Commands(Wepons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("holder")) {
            plugin.holders().scan(player);
            WeponsGui.open(plugin, player, WeponsGui.Type.HOLDERS);
        } else {
            WeponsGui.open(plugin, player, WeponsGui.Type.WEAPONS);
        }
        return true;
    }
}
