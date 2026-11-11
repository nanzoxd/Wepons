package com.wepons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Commands implements CommandExecutor, TabCompleter {

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
            return true;
        }

        // /wepons enchants [name]: dialog with every enchantment and its limit
        if (args.length >= 1 && (args[0].equalsIgnoreCase("enchants") || args[0].equalsIgnoreCase("enchantments"))) {
            if (!player.hasPermission("wepons.enchants")) {
                player.sendMessage("You don't have permission.");
                return true;
            }
            if (args.length == 1) plugin.enchantDialogs().list(player);
            else plugin.enchantDialogs().detail(player, args[1]);
            return true;
        }
        // /wepons setenchant <name> <level>: the Apply button of the enchantment dialog
        if (args.length >= 3 && args[0].equalsIgnoreCase("setenchant")) {
            if (!player.hasPermission("wepons.enchants")) {
                player.sendMessage("You don't have permission.");
                return true;
            }
            plugin.enchantDialogs().apply(player, args[1], args[2]);
            return true;
        }

        if (!player.hasPermission("wepons.use")) {
            player.sendMessage("You don't have permission.");
            return true;
        }
        WeponsGui.open(plugin, player, WeponsGui.Type.WEAPONS);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("wepons")) return out;
        if (args.length == 1) {
            if ("enchants".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("enchants");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("enchants") && sender instanceof Player p) {
            for (Enchantment e : plugin.enchantDialogs().available(p.getInventory().getItemInMainHand())) {
                String k = e.getKey().getKey();
                if (k.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k);
            }
        }
        return out;
    }
}
