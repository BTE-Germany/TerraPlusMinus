package de.btegermany.terraplusminus.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.geo.CoordinateParseUtils;
import net.buildtheearth.terraminusminus.util.geo.LatLng;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public class TpllCommand {
    public static final String LAT_LON_HEIGHT = "latLonHeight";
    static String prefix;
    private static final Random dummyRandom = new Random();  // To be used in places that require a random, but we know it doesn't matter

    private static void execute(CommandSender sender, @NotNull Player target, @NotNull String args) {
        World tpWorld = target.getWorld();
        FileConfiguration config = Terraplusminus.config;
        double minLat = config.getDouble("min_latitude");
        double maxLat = config.getDouble("max_latitude");
        double minLon = config.getDouble("min_longitude");
        double maxLon = config.getDouble("max_longitude");

        ChunkGenerator generator = tpWorld.getGenerator();
        if (!(generator instanceof RealWorldGenerator terraGenerator)) { // after server reloads the generator isn't instanceof RealWorldGenerator anymore
            sender.sendMessage(prefix + "§cThe world generator must be set to Terraplusminus");
            return;
        }
        EarthGeneratorSettings generatorSettings = terraGenerator.getSettings();
        GeographicProjection projection = generatorSettings.projection();
        int yOffset = terraGenerator.getYOffset();

        LatLng latLng = null;
        Double height = null;

        boolean parsingWorked = false;

        try {
            latLng = CoordinateParseUtils.parseVerbatimCoordinates(args);
            int indexFirstSpace = args.indexOf(' ');
            int indexHeight = indexFirstSpace == -1 ? -1 : args.indexOf(' ', indexFirstSpace + 1);
            int indexThirdSpace = args.indexOf(' ', indexHeight + 1);
            if (indexHeight != -1)
                height = Double.parseDouble(args.substring(indexHeight + 1, indexThirdSpace == -1 ? args.length() : indexThirdSpace)) + yOffset;
            if (latLng != null) parsingWorked = true;
        } catch (NumberFormatException e) {/*ignored*/}

        if (!parsingWorked) {
            sendUsageMessage(sender);
            return;
        }

        double x;
        double z;
        try {
            double[] mcCoordinates = projection.fromGeo(latLng.getLng(), latLng.getLat()); // projection.fromGeo is eccentric and expects lon, lat
            x = mcCoordinates[0];
            z = mcCoordinates[1];
        } catch (OutOfProjectionBoundsException e) {
            sender.sendMessage(prefix + "§cLocation is not within projection bounds.");
            return;
        }

        boolean playerItselfIsTeleporting = sender == target;

        if (playerItselfIsTeleporting && minLat != 0 && maxLat != 0 && minLon != 0 && maxLon != 0 &&
                !sender.hasPermission("t+-.admin") &&
                (latLng.getLat() < minLat || latLng.getLng() < minLon || latLng.getLat() > maxLat || latLng.getLng() > maxLon)) {
            sender.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because this area is being worked on by another build team.");
            return;
        }

        if (!config.getBoolean("linked_worlds.enabled") && height == null) {
            height = tpWorld.getHighestBlockYAt((int) x >> 4, (int) z >> 4) + 1d;  // >> 4 converts block cords to chunk cords
            checkAndTeleportIfCorrectWorld(target,
                    tpWorld,
                    x,
                    z,
                    height,
                    latLng.getLat(),
                    latLng.getLng());
            return;
        }

        if (height == null) {
            height = (double) terraGenerator.getBaseHeight(tpWorld, dummyRandom, (int) Math.round(x), (int) Math.round(z), HeightMap.WORLD_SURFACE);
        }

        if (height > target.getWorld().getMaxHeight()) {
            if (config.getString("linked_worlds.method", "").equalsIgnoreCase("SERVER")) {
                // send player uuid and coordinates to bungee
                sendPluginMessageToBungeeBridge(true, target, latLng.getLat(), latLng.getLng());
            } else if (config.getString("linked_worlds.method", "").equalsIgnoreCase("MULTIVERSE")) {
                LinkedWorld nextServer = ConfigurationHelper.getNextServerName(target.getWorld().getName());
                if (nextServer == null) {
                    target.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because the worlds are " +
                            "not high enough at the moment.");
                    return;
                }
                tpWorld = Bukkit.getWorld(nextServer.getWorldName());
                height = height - yOffset + nextServer.getOffset();
                target.sendMessage(prefix + "§7Teleporting to " + latLng.getLat() + ", " + latLng.getLng() + " in " +
                        "another world. This may take a bit...");
                target.teleportAsync(
                        new Location(tpWorld,
                                x,
                                height,
                                z,
                                target.getLocation().getYaw(),
                                target.getLocation().getPitch()),
                        PlayerTeleportEvent.TeleportCause.COMMAND);
            }
        } else if (height <= target.getWorld().getMinHeight()) {
            if (config.getString("linked_worlds.method", "").equalsIgnoreCase("SERVER")) {
                // send player uuid and coordinates to bungee
                sendPluginMessageToBungeeBridge(false, target, latLng.getLat(), latLng.getLng());
            } else if (config.getString("linked_worlds.method", "").equalsIgnoreCase("MULTIVERSE")) {
                LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(target.getWorld().getName());
                if (previousServer == null) {
                    target.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because the world is not low enough at the moment.");
                    return;
                }
                tpWorld = Bukkit.getWorld(previousServer.getWorldName());
                height = height - yOffset + previousServer.getOffset();
                target.sendMessage(prefix + "§7Teleporting to " + latLng.getLat() + ", " + latLng.getLng() + " in another" +
                        " world. This may take a bit...");
                target.teleportAsync(
                        new Location(tpWorld,
                                x,
                                height,
                                z,
                                target.getLocation().getYaw(),
                                target.getLocation().getPitch()),
                        PlayerTeleportEvent.TeleportCause.COMMAND);
            }
        } else
            checkAndTeleportIfCorrectWorld(target, tpWorld, x, z, height, latLng.getLat(), latLng.getLng());
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
                height,
                z,
                target.getLocation().getYaw(),
                target.getLocation().getPitch());

        target.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND);
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
