package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.TerraConnector;
import io.papermc.lib.PaperLib;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TpllCommand implements CommandExecutor {

    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("tpll")){
            Player player = (Player) commandSender;
            if (args.length == 2) {
                if (player.hasPermission("t+-.tpll")) {

                    int move = Terraplusminus.config.getInt("moveTerrain");

                    double[] coordinates = new double[2];
                    coordinates[1] = Double.parseDouble(args[0].replace(",", ""));
                    coordinates[0] = Double.parseDouble(args[1]);

                    double[] mcCoordinates = new double[0];
                    try {
                        mcCoordinates = bteGeneratorSettings.projection().fromGeo(coordinates[0], coordinates[1]);
                    } catch (OutOfProjectionBoundsException e) {
                        e.printStackTrace();
                    }
                    TerraConnector terraConnector = new TerraConnector();

                    double height = terraConnector.getHeight((int) mcCoordinates[0], (int) mcCoordinates[1]).join()+move;

                    if(height > player.getWorld().getMaxHeight()){
                        player.sendMessage(Terraplusminus.config.getString("prefix") + "§cYou cannot tpll to these coordinates, because the world is not high enough at the moment.");
                        return true;
                    }

                    Location location = new Location(player.getWorld(), mcCoordinates[0], height, mcCoordinates[1]);

                    if(PaperLib.isChunkGenerated(location)){
                        location = new Location(player.getWorld(), mcCoordinates[0], player.getWorld().getHighestBlockYAt((int) mcCoordinates[0], (int) mcCoordinates[1])+1, mcCoordinates[1]);
                    }
                    PaperLib.teleportAsync(player,location);

                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported to " + coordinates[1] + ", " + coordinates[0] + ".");
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
