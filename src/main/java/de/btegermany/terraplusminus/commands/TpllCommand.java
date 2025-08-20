package de.btegermany.terraplusminus.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.TerraConnector;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import io.papermc.lib.PaperLib;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TpllCommand {
    public static final String LAT_LON_HEIGHT = "latLonHeight";
    static String prefix;

    private static void execute(CommandSender sender, @NotNull Player target, @NotNull String args) {
        World tpWorld = target.getWorld();

        FileConfiguration config = Terraplusminus.config;
        int xOffset = config.getInt("terrain_offset.x");
        int zOffset = config.getInt("terrain_offset.z");
        double minLat = config.getDouble("min_latitude");
        double maxLat = config.getDouble("max_latitude");
        double minLon = config.getDouble("min_longitude");
        double maxLon = config.getDouble("max_longitude");

        ChunkGenerator generator = tpWorld.getGenerator();
        if (!(generator instanceof RealWorldGenerator terraGenerator)) { // after server reload the generator isnt instanceof RealWorldGenerator anymore
            sender.sendMessage(prefix + "§cThe world generator must be set to Terraplusminus");
            return;
        }
        EarthGeneratorSettings generatorSettings = terraGenerator.getSettings();
        GeographicProjection projection = generatorSettings.projection();
        int yOffset = terraGenerator.getYOffset();

        double lat = 0;
        double lon = 0; // Will never be used because it exits before
        Double height = null;

        String[] split = args.split(" ");
        boolean parsingWorked = false;

        try {
            if (split.length == 2 || split.length == 3) {
                lat = Double.parseDouble(split[0].replace(",", "")
                        .replace("°", ""));
                lon = Double.parseDouble(split[1].replace("°", ""));
                if (split.length == 3) {
                    height = Double.parseDouble(split[2]) + yOffset;
                }
                parsingWorked = true;
            }
        } catch (NumberFormatException e) {/*ignored*/}

        if (!parsingWorked) {
            sendUsageMessage(sender);
            return;
        }

        double[] mcCoordinates;
        try {
            mcCoordinates = projection.fromGeo(lat, lon);
        } catch (OutOfProjectionBoundsException e) {
            sender.sendMessage(prefix + "§cLocation is not within projection bounds.");
            return;
        }

        boolean playerItselfIsTeleporting = sender == target;

        if (playerItselfIsTeleporting && minLat != 0 && maxLat != 0 && minLon != 0 && maxLon != 0 && !sender.hasPermission("t+-.admin") && (lat < minLat || lon < minLon || lat > maxLat || lon > maxLon)) {
                sender.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because this area is being worked on by another build team.");
                return;
            }


        if (!config.getBoolean("linked_worlds.enabled") && height == null) {
            height = tpWorld.getHighestBlockYAt((int) mcCoordinates[0], (int) mcCoordinates[1]) + 1d;
            checkAndTeleportIfCorrectWorld(target,
                    tpWorld,
                    mcCoordinates[0] + xOffset,
                    mcCoordinates[1] + zOffset,
                    height,
                    lat,
                    lon);
            return;
        }

        TerraConnector terraConnector = new TerraConnector();

        if (height == null) {
            height = terraConnector.getHeight((int) mcCoordinates[0],
                    (int) mcCoordinates[1]).join() + yOffset; // 57 + (-2032) = -1975
        }
        if (height > target.getWorld().getMaxHeight()) {
                if (config.getString("linked_worlds.method", "").equalsIgnoreCase("SERVER")) {
                    // send player uuid and coordinates to bungee
                    sendPluginMessageToBungeeBridge(true, target, lat, lon);
                } else if (config.getString("linked_worlds.method", "").equalsIgnoreCase("MULTIVERSE")) {
                    LinkedWorld nextServer = ConfigurationHelper.getNextServerName(target.getWorld().getName());
                    if (nextServer == null) {
                        target.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because the worlds are " +
                                "not high enough at the moment.");
                        return;
                    }
                    tpWorld = Bukkit.getWorld(nextServer.getWorldName());
                    height = height - yOffset + nextServer.getOffset();
                    target.sendMessage(prefix + "§7Teleporting to " + lat + ", " + lon + " in " +
                            "another world. This may take a bit...");
                    PaperLib.teleportAsync(target,
                            new Location(tpWorld,
                                    mcCoordinates[0] + xOffset,
                                    height,
                                    mcCoordinates[1] + zOffset,
                                    target.getLocation().getYaw(),
                                    target.getLocation().getPitch()));
                }
        } else if (height <= target.getWorld().getMinHeight()) {
                if (config.getString("linked_worlds.method", "").equalsIgnoreCase("SERVER")) {
                    // send player uuid and coordinates to bungee
                    sendPluginMessageToBungeeBridge(false, target, lat, lon);
                } else if (config.getString("linked_worlds.method", "").equalsIgnoreCase("MULTIVERSE")) {
                    LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(target.getWorld().getName());
                    if (previousServer == null) {
                        target.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because the world is not low enough at the moment.");
                        return;
                    }
                    tpWorld = Bukkit.getWorld(previousServer.getWorldName());
                    height = height - yOffset + previousServer.getOffset();
                    target.sendMessage(prefix + "§7Teleporting to " + lon + ", " + lat + " in another" +
                            " world. This may take a bit...");
                    PaperLib.teleportAsync(target,
                            new Location(tpWorld,
                                    mcCoordinates[0] + xOffset,
                                    height,
                                    mcCoordinates[1] + zOffset,
                                    target.getLocation().getYaw(),
                                    target.getLocation().getPitch()));
                }
        } else checkAndTeleportIfCorrectWorld(target, tpWorld, mcCoordinates[0] + xOffset, mcCoordinates[1] + zOffset, height, lat, lon);
    }

    private static void checkAndTeleportIfCorrectWorld(@NotNull Player target, World tpWorld, double x, double z, double height, double lat, double lon) {
        String msgPart1 = prefix + "§cYou cannot tpll to these coordinates, because the world is not ";
        String msgPart2 = "enough at the moment.";
        if (height > target.getWorld().getMaxHeight()) {
            target.sendMessage(msgPart1 + "high" + msgPart2);
            return;
        } else if (height <= target.getWorld().getMinHeight()) {
            target.sendMessage(msgPart1 + "low" + msgPart2);
            return;
        }

        Location location = new Location(tpWorld,
                x,
                z,
                height,
                target.getLocation().getYaw(),
                target.getLocation().getPitch());

        PaperLib.teleportAsync(target, location);

        target.sendMessage(prefix + "§7Teleported to " + lat + ", " + lon + ", " + height + ".");
    }

    private static int sendUsageMessage(@NotNull CommandContext<CommandSourceStack> ctx) {
        sendUsageMessage(ctx.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private static void sendUsageMessage(@NotNull CommandSender sender) {
        sender.sendMessage(prefix + "§7Invalid " + "coordinates or command usage!\n" +
                "Proper usage: /tpll <latitude> " + "<longitude> [height (optional)]");
    }

    private static void sendPluginMessageToBungeeBridge(boolean isNextServer, @NotNull Player player,
                                                        double lat, double lon) {
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
            player.sendMessage(prefix + "§cPlease contact server administrator. Your config is not set up correctly.");
            return;
        }
        out.writeUTF(lat + ", " + lon);
        player.sendPluginMessage(Terraplusminus.instance,
                "bungeecord:terraplusminus",
                out.toByteArray());

        player.sendMessage(prefix + "§cSending to another server...");
    }

    public static LiteralCommandNode<CommandSourceStack> create() {
        prefix = Terraplusminus.config.getString("prefix");

        return Commands.literal("tpll")
                .then(Commands.argument(LAT_LON_HEIGHT, StringArgumentType.greedyString())
                        .requires(TpllCommand::isPermittedDirect)
                        .executes(TpllCommand::executeDirect))
                .then(Commands.argument("player", ArgumentTypes.players())
                        .then(Commands.argument(LAT_LON_HEIGHT, StringArgumentType.greedyString())
                                .requires(TpllCommand::isPermittedTarget)
                                .executes(TpllCommand::executeTarget))
                        .requires(TpllCommand::isPermittedTarget)
                        .executes(TpllCommand::sendUsageMessage))
                .requires(TpllCommand::isPermitted)
                .executes(TpllCommand::sendUsageMessage)
                .build();
    }

    private static int executeTarget(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
        final List<Player> targets = targetResolver.resolve(ctx.getSource());
        final String latLonHeight = ctx.getArgument(LAT_LON_HEIGHT, String.class);
        for (final Player target : targets) {
            execute(ctx.getSource().getSender(), target, latLonHeight);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static boolean isPermittedTarget(@NotNull CommandSourceStack commandSourceStack) {
        return commandSourceStack.getSender().hasPermission("t+-.forcetpll");
    }

    private static int executeDirect(@NotNull CommandContext<CommandSourceStack> ctx) {
        final String latLonHeight = ctx.getArgument(LAT_LON_HEIGHT, String.class);
        if (ctx.getSource().getExecutor() instanceof Player player) {
            execute(ctx.getSource().getSender(), player, latLonHeight);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static boolean isPermittedDirect(@NotNull CommandSourceStack source) {
        return source.getExecutor() instanceof Player && isPermitted(source);
    }

    private static boolean isPermitted(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("t+-.forcetpll") || (source.getSender() == source.getExecutor() && source.getSender().hasPermission("t+.tpll"));
    }
}
