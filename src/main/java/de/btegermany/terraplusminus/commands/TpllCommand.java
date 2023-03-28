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
        if (command.getName().equalsIgnoreCase("tpll")) {
            //If sender is not a player cancel the command.
            if (!(commandSender instanceof Player)) {
                commandSender.sendMessage("This command can only be used by players!");
                return true;
            }
            Player player = (Player) commandSender;

            //Option to passthrough tpll to other bukkit plugins.
            String passthroughTpll = Terraplusminus.config.getString("passthrough_tpll");
            if (passthroughTpll != null) {
                //Check if any args are parsed.
                if (args.length == 0) {
                    player.chat("/" + passthroughTpll + ":tpll");
                } else {
                    player.chat("/" + passthroughTpll + ":tpll " + String.join(" ", args));
                }
                return true;
            }

            // -
            if (args.length >= 2) {
                if (player.hasPermission("t+-.tpll")) {

                    int move = Terraplusminus.config.getInt("terrain_offset");
                    Double minLat = Terraplusminus.config.getDouble("min_latitude");
                    Double maxLat = Terraplusminus.config.getDouble("max_latitude");
                    Double minLon = Terraplusminus.config.getDouble("min_longitude");
                    Double maxLon = Terraplusminus.config.getDouble("max_longitude");

                    double[] coordinates = new double[2];
                    coordinates[1] = Double.parseDouble(args[0].replace(",", ""));
                    coordinates[0] = Double.parseDouble(args[1]);

                    double[] mcCoordinates = new double[0];
                    try {
                        mcCoordinates = bteGeneratorSettings.projection().fromGeo(coordinates[0], coordinates[1]);
                    } catch (OutOfProjectionBoundsException e) {
                        e.printStackTrace();
                    }

                    if (minLat != 0 && maxLat != 0 && minLon != 0 && maxLon != 0) {
                        if (coordinates[1] < minLat || coordinates[0] < minLon || coordinates[1] > maxLat || coordinates[0] > maxLon) {
                            player.sendMessage(Terraplusminus.config.getString("prefix") + "§cYou cannot tpll to these coordinates, because this area is being worked on by another build team.");
                            return true;
                        }
                    }

                    TerraConnector terraConnector = new TerraConnector();

                    double height;
                    if (args.length >= 3) {
                        height = Double.parseDouble(args[2]) + move;
                    } else {
                        height = terraConnector.getHeight((int) mcCoordinates[0], (int) mcCoordinates[1]).join() + move;
                    }

                    if (height > player.getWorld().getMaxHeight()) {
                        player.sendMessage(Terraplusminus.config.getString("prefix") + "§cYou cannot tpll to these coordinates, because the world is not high enough at the moment.");
                        return true;
                    }

                    Location location = new Location(player.getWorld(), mcCoordinates[0], height, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch());
                    player.sendMessage(String.valueOf(location.getY()));

                    if (PaperLib.isChunkGenerated(location)) {
                        if (args.length >= 3) {
                            location = new Location(player.getWorld(), mcCoordinates[0], height, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch());
                        } else {
                            location = new Location(player.getWorld(), mcCoordinates[0], player.getWorld().getHighestBlockYAt((int) mcCoordinates[0], (int) mcCoordinates[1]) + 1, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch());
                        }
                    } else {
                        player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Location is generating. Please wait a moment...");
                    }
                    PaperLib.teleportAsync(player, location);
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported to " + coordinates[1] + ", " + coordinates[0] + ".");

                    if (args.length >= 3) {
                        player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Using custom height " + height);
                    }

                    return true;
                } else {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /tpll");
                    return true;
                }
            } else {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Usage: /tpll <longitudes> <latitudes>");
                return true;
            }
        }
        return true;
    }
}
