package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.TerraConnector;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TpllCommand implements CommandExecutor {


    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("tpll")){
            Player player = (Player) commandSender;
            if (args.length == 2) {
                if (player.hasPermission("t+-.tpll")) {

                    double[] coordinates = new double[2];
                    coordinates[0] = Double.parseDouble(args[0].replace(",", ""));
                    coordinates[1] = Double.parseDouble(args[1]);

                    double[] mccoordinates = TerraConnector.fromGeo(coordinates[0], coordinates[1]);

                    Location location = new Location(player.getWorld(), mccoordinates[0], player.getWorld().getHighestBlockYAt((int) mccoordinates[0], (int) mccoordinates[1]) + 1, mccoordinates[1]);

                    player.teleport(location);

                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported to " + coordinates[0] + ", " + coordinates[1] + ".");
                    return true;
                } else {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /tpll");
                    return true;
                }
            }else {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Usage: /tpll <longitudes> <latitudes>");
                return true;
            }
        }
        return true;
    }
}
