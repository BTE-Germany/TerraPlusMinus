package de.btegermany.terraplusminus.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.TerraConnector;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import io.papermc.lib.PaperLib;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import static org.bukkit.ChatColor.RED;


public class TpllCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {

        Player player = (Player) commandSender;
        if (!command.getName().equalsIgnoreCase("tpll")) {
            player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Usage: /tpll <longitudes> <latitudes>");
            return true;
        }
        if (!player.hasPermission("t+-.tpll")) {
            player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /tpll");
            return true;
        }


        // Entity selector

        // detect if command starts with @ or with a player name

        if ((args[0].startsWith("@") || !isDouble(args[0].replace(",", "").replace("°", ""))) && player.hasPermission("t+-.forcetpll")) {
            if (args[0].equals("@a")) {
                StringBuilder playerList = new StringBuilder();
                Terraplusminus.instance.getServer().getOnlinePlayers().forEach(p -> {
                    p.chat("/tpll " + String.join(" ", args).substring(2));
                    if (Terraplusminus.instance.getServer().getOnlinePlayers().size() > 1) {
                        playerList.append(p.getName()).append(", ");
                    } else {
                        playerList.append(p.getName()).append(" ");
                    }
                });
                // delete last comma if no player follows
                if (playerList.length() > 0 && playerList.charAt(playerList.length() - 2) == ',') {
                    playerList.deleteCharAt(playerList.length() - 2);
                }
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported §9" + playerList + "§7to" + String.join(" ", args).substring(2));
                return true;
            } else if (args[0].equals("@p")) {
                // find nearest player but not the player itself
                Player nearestPlayer = null;
                double nearestDistance = Double.MAX_VALUE;
                for (Player p : Terraplusminus.instance.getServer().getOnlinePlayers()) {
                    if (p.getLocation().distanceSquared(player.getLocation()) < nearestDistance && (!p.equals(player) || Terraplusminus.instance.getServer().getOnlinePlayers().size() == 1)) {
                        nearestPlayer = p;
                        nearestDistance = p.getLocation().distanceSquared(player.getLocation());
                    }
                }
                if (nearestPlayer != null) {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported §9" + nearestPlayer.getName() + " §7to" + String.join(" ", args).substring(2));
                    nearestPlayer.chat("/tpll " + String.join(" ", args).substring(2));
                }
                return true;
            } else {
                Player target = null;
                //check if target player is online
                for (Player p : Terraplusminus.instance.getServer().getOnlinePlayers()) {
                    if (p.getName().equals(args[0])) {
                        target = p;
                    }
                }

                if (target == null) {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§cNo player found with name §9" + args[0]);
                    return true;
                }

                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported §9" + target.getName() + " §7to " + args[1] + " " + args[2]);
                target.chat("/tpll " + String.join(" ", args).replace(target.getName(), ""));
                return true;
            }
        }

        //Option to passthrough tpll to other bukkit plugins.
        String passthroughTpll = Terraplusminus.config.getString("passthrough_tpll");
        if (!passthroughTpll.isEmpty()) {
            //Check if any args are parsed.
            if (args.length == 0) {
                player.chat("/" + passthroughTpll + ":tpll");
            } else {
                player.chat("/" + passthroughTpll + ":tpll " + String.join(" ", args));
            }
            return true;
        }

        //If sender is not a player cancel the command.
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("This command can only be used by players!");
            return true;
        }

        // -
        if (args.length >= 2) {

            World tpWorld = player.getWorld();

            double minLat = Terraplusminus.config.getDouble("min_latitude");
            double maxLat = Terraplusminus.config.getDouble("max_latitude");
            double minLon = Terraplusminus.config.getDouble("min_longitude");
            double maxLon = Terraplusminus.config.getDouble("max_longitude");

            double[] coordinates = new double[2];
            coordinates[1] = Double.parseDouble(args[0].replace(",", "").replace("°", ""));
            coordinates[0] = Double.parseDouble(args[1].replace("°", ""));

            ChunkGenerator generator = tpWorld.getGenerator();
            if (!(generator instanceof RealWorldGenerator)) { // after server reload the generator isnt instanceof RealWorldGenerator anymore
                commandSender.sendMessage(Terraplusminus.config.getString("prefix") + RED + "The world generator must be set to Terraplusminus");
                return true;
            }
            RealWorldGenerator terraGenerator = (RealWorldGenerator) generator;
            EarthGeneratorSettings generatorSettings = terraGenerator.getSettings();
            GeographicProjection projection = generatorSettings.projection();
            int yOffset = terraGenerator.getYOffset();

            double[] mcCoordinates;
            try {
                mcCoordinates = projection.fromGeo(coordinates[0], coordinates[1]);
            } catch (OutOfProjectionBoundsException e) {
                commandSender.sendMessage(RED + "Location is not within projection bounds");
                return true;
            }

            if (minLat != 0 && maxLat != 0 && minLon != 0 && maxLon != 0 && !player.hasPermission("t+-.admin")) {
                if (coordinates[1] < minLat || coordinates[0] < minLon || coordinates[1] > maxLat || coordinates[0] > maxLon) {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + RED + "You cannot tpll to these coordinates, because this area is being worked on by another build team.");
                    return true;
                }
            }

            TerraConnector terraConnector = new TerraConnector();

            double height;
            if (args.length >= 3) {
                height = Double.parseDouble(args[2]) + yOffset;
            } else {
                height = terraConnector.getHeight((int) mcCoordinates[0], (int) mcCoordinates[1]).join() + yOffset; // 57 + (-2032) = -1975
            }
            if (height > player.getWorld().getMaxHeight()) {
                if (Terraplusminus.config.getBoolean("linked_worlds.enabled")) {
                    if (Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("SERVER")) {
                        //send player uuid and coordinates to bungee
                        return sendPluginMessageToBungeeBridge(true, player, coordinates);
                    } else if (Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE")) {
                        LinkedWorld nextServer = ConfigurationHelper.getNextServerName(player.getWorld().getName());
                        if (nextServer == null) {
                            player.sendMessage(Terraplusminus.config.getString("prefix") + "§cYou cannot tpll to these coordinates, because the world is not high enough at the moment.");
                            return true;
                        }
                        tpWorld = Bukkit.getWorld(nextServer.getWorldName());
                        height = height - yOffset + nextServer.getOffset();
                        player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleporting to " + coordinates[1] + ", " + coordinates[0] + " in another world. This may take a bit...");
                        //player.teleport(new Location(tpWorld, mcCoordinates[0], height, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch()));
                        PaperLib.teleportAsync(player, new Location(tpWorld, mcCoordinates[0], height, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch()));
                        return true;
                    }
                } else {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§cYou cannot tpll to these coordinates, because the world is not high enough at the moment.");
                    return true;
                }
            } else if (height <= player.getWorld().getMinHeight()) {
                if (Terraplusminus.config.getBoolean("linked_worlds.enabled")) {
                    if (Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("SERVER")) {
                        //send player uuid and coordinates to bungee
                        return sendPluginMessageToBungeeBridge(false, player, coordinates);
                    } else if (Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE")) {
                        LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(player.getWorld().getName());
                        if (previousServer == null) {
                            player.sendMessage(Terraplusminus.config.getString("prefix") + "§cYou cannot tpll to these coordinates, because the world is not low enough at the moment.");
                            return true;
                        }
                        tpWorld = Bukkit.getWorld(previousServer.getWorldName());
                        height = height - yOffset + previousServer.getOffset();
                        player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleporting to " + coordinates[1] + ", " + coordinates[0] + " in another world. This may take a bit...");
                        PaperLib.teleportAsync(player, new Location(tpWorld, mcCoordinates[0], height, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch()));
                        return true;
                    }
                } else {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§cYou cannot tpll to these coordinates, because the world is not low enough at the moment.");
                    return true;
                }
            }
            Location location = new Location(tpWorld, mcCoordinates[0], height, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch());

            if (PaperLib.isChunkGenerated(location)) {
                if (args.length >= 3) {
                    location = new Location(tpWorld, mcCoordinates[0], height, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch());
                } else {
                    location = new Location(tpWorld, mcCoordinates[0], tpWorld.getHighestBlockYAt((int) mcCoordinates[0], (int) mcCoordinates[1]) + 1, mcCoordinates[1], player.getLocation().getYaw(), player.getLocation().getPitch());
                }
            } else {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Location is generating. Please wait a moment...");
            }
            PaperLib.teleportAsync(player, location);


            if (args.length >= 3) {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported to " + coordinates[1] + ", " + coordinates[0] + ", " + height + ".");
            } else {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Teleported to " + coordinates[1] + ", " + coordinates[0] + ".");
            }

            return true;

        }
        return true;
    }

    private static boolean sendPluginMessageToBungeeBridge(boolean isNextServer, Player player, double[] coordinates) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(player.getUniqueId().toString());
        LinkedWorld server;
        if (isNextServer) {
            server = ConfigurationHelper.getNextServerName(Bukkit.getServer().getName()); //TODO: Bukkit.getServer().getName() does not return the real name
        } else {
            server = ConfigurationHelper.getPreviousServerName(Bukkit.getServer().getName()); //TODO: Bukkit.getServer().getName() does not return the real name
        }

        if (server != null) {
            out.writeUTF(server.getWorldName() + ", " + server.getOffset());
        } else {
            player.sendMessage(Terraplusminus.config.getString("prefix") + "§cPlease contact server administrator. Your config is not set up correctly.");
            return true;
        }
        out.writeUTF(coordinates[1] + ", " + coordinates[0]);
        player.sendPluginMessage(Terraplusminus.instance, "bungeecord:terraplusminus", out.toByteArray());

        player.sendMessage(Terraplusminus.config.getString("prefix") + "§cSending to another server...");
        return true;
    }

    public boolean isDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
