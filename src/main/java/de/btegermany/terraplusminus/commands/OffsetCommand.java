package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OffsetCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (command.getName().equalsIgnoreCase("offset")) {
            Player player = (Player) commandSender;
            String worldName = player.getWorld().getName();
            if (player.hasPermission("t+-.offset")) {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Offsets:");
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7 | X: §8" + Terraplusminus.config.getInt("terrain_offset.x"));

                if (!Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE") || !Terraplusminus.config.getBoolean("linked_worlds.enabled")) {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7 | Y: §8" + Terraplusminus.config.getInt("terrain_offset.y"));
                } else {
                    if (Terraplusminus.config.getBoolean("linked_worlds.enabled") && Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE")) {
                        player.sendMessage(Terraplusminus.config.getString("prefix") + "§9 World§7 | Y: §8" + Terraplusminus.config.getInt("terrain_offset.y"));
                        String lastServerName = ConfigurationHelper.getLastServerName("world");
                        String nextServerName = ConfigurationHelper.getNextServerName("world");
                        if (lastServerName != null) {
                            player.sendMessage(Terraplusminus.config.getString("prefix") + "§9 " + lastServerName.split(",")[0] + "§7 | Y: §8" + Integer.parseInt(lastServerName.split(",")[1].replace(" ", "")));
                        } else if (nextServerName != null) {
                            player.sendMessage(Terraplusminus.config.getString("prefix") + "§9 " + nextServerName.split(",")[0] + "§7 | Y: §8" + Integer.parseInt(nextServerName.split(",")[1].replace(" ", "")));
                        }
                    }
                }

                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7 | Z: §8" + Terraplusminus.config.getInt("terrain_offset.z"));
            } else {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /offset");
                return true;
            }
        }
        return true;
    }
}
