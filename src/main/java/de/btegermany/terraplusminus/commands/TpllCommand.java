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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Command handler for the /tpll command.
 * <p>
 * This command allows players to teleport to real-world geographic coordinates (latitude/longitude)
 * within a Terraplusminus world. It supports:
 * <ul>
 *     <li>Direct teleportation using latitude and longitude</li>
 *     <li>Optional height specification</li>
 *     <li>Teleporting other players (with appropriate permissions)</li>
 *     <li>Cross-world teleportation via Multiverse or BungeeCord</li>
 * </ul>
 *
 * @see RealWorldGenerator
 * @see ConfigurationHelper
 */
public class TpllCommand {

    // <editor-fold desc="Constants and Fields">
    public static final String LAT_LON_HEIGHT = "latLonHeight";

    /** Degree symbol (°) - replaced with space when parsing coordinates. */
    private static final char DEGREE_SYMBOL = (char) 176;
    private static final char SPACE = ' ';

    static String prefix;
    private static final Random dummyRandom = new Random();
    // </editor-fold>

    // <editor-fold desc="Core Teleportation Logic">
    /**
     * Executes the teleportation logic for a player to geographic coordinates.
     * <p>
     * This method:
     * <ol>
     *     <li>Validates the world is a Terraplusminus world</li>
     *     <li>Parses the coordinates from the arguments</li>
     *     <li>Checks boundary restrictions</li>
     *     <li>Handles cross-world teleportation if needed</li>
     *     <li>Performs the actual teleport</li>
     * </ol>
     *
     * @param sender The command sender (may differ from target for force-teleports)
     * @param target The player to teleport
     * @param args   The coordinate arguments string (latitude, longitude, optional height)
     */
    private static void execute(CommandSender sender, @NotNull Player target, @NotNull String args) {
        World tpWorld = target.getWorld();
        FileConfiguration config = Terraplusminus.instance.getConfig();
        double minLat = config.getDouble("min_latitude");
        double maxLat = config.getDouble("max_latitude");
        double minLon = config.getDouble("min_longitude");
        double maxLon = config.getDouble("max_longitude");

        ChunkGenerator generator = tpWorld.getGenerator();
        if (!(generator instanceof RealWorldGenerator terraGenerator)) { // after server reloads the generator isn't instanceof RealWorldGenerator anymore
            sender.sendMessage(prefix + "§cThis is not a Terraplusmins world.");
            Terraplusminus.instance.getComponentLogger().warn("This is not a Terraplusminus world: {}." +
                    "The world generator must be set to Terraplusminus for T+- to work." +
                    "Remove the permission t+-.tpll for this world if you don't want to see this warning.", tpWorld.getName());
            return;
        }
        EarthGeneratorSettings generatorSettings = terraGenerator.getSettings();
        GeographicProjection projection = generatorSettings.projection();
        int yOffset = terraGenerator.getYOffset();

        LatLongHeight latLngHeight = parseArguments(args, yOffset);
        Double height = latLngHeight.height();

        if (latLngHeight.latLng() == null) {
            sendUsageMessage(sender);
            return;
        }

        double x;
        double z;
        try {
            double[] mcCoordinates = projection.fromGeo(latLngHeight.latLng().getLng(), latLngHeight.latLng().getLat()); // projection.fromGeo is eccentric and expects lon, lat
            x = mcCoordinates[0];
            z = mcCoordinates[1];
        } catch (OutOfProjectionBoundsException e) {
            sender.sendMessage(prefix + "§cLocation is not within projection bounds.");
            return;
        }

        boolean playerItselfIsTeleporting = sender == target;

        if (playerItselfIsTeleporting && minLat != 0 && maxLat != 0 && minLon != 0 && maxLon != 0 &&
                !sender.hasPermission("t+-.admin") &&
                (latLngHeight.latLng().getLat() < minLat || latLngHeight.latLng().getLng() < minLon || latLngHeight.latLng().getLat() > maxLat || latLngHeight.latLng().getLng() > maxLon)) {
            sender.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because this area is being worked on by another build team.");
            return;
        }

        if (!config.getBoolean("linked_worlds.enabled") && height == null) {
            checkAndTeleportIfCorrectWorld(target,
                    tpWorld,
                    x,
                    z,
                    tpWorld.getHighestBlockYAt((int) x, (int) z) + 1d,
                    latLngHeight.latLng().getLat(),
                    latLngHeight.latLng().getLng());
            return;
        }

        if (height == null) {
            height = (double) terraGenerator.getBaseHeight(tpWorld, dummyRandom, (int) Math.round(x), (int) Math.round(z), HeightMap.WORLD_SURFACE) + 1;
        }

        if (height > target.getWorld().getMaxHeight()) {
            if (config.getString("linked_worlds.method", "").equalsIgnoreCase("SERVER")) {
                // send player uuid and coordinates to bungee
                sendPluginMessageToBungeeBridge(true, target, latLngHeight.latLng().getLat(), latLngHeight.latLng().getLng());
            } else if (config.getString("linked_worlds.method", "").equalsIgnoreCase("MULTIVERSE")) {
                teleportToNextMultiverseWorld(target, height, yOffset, latLngHeight, x, z);
            }
        } else if (height <= target.getWorld().getMinHeight()) {
            if (config.getString("linked_worlds.method", "").equalsIgnoreCase("SERVER")) {
                // send player uuid and coordinates to bungee
                sendPluginMessageToBungeeBridge(false, target, latLngHeight.latLng().getLat(), latLngHeight.latLng().getLng());
            } else if (config.getString("linked_worlds.method", "").equalsIgnoreCase("MULTIVERSE")) {
                teleportToPreviousMultiverseWorld(target, height, yOffset, latLngHeight, x, z);
            }
        } else
            checkAndTeleportIfCorrectWorld(target, tpWorld, x, z, height, latLngHeight.latLng().getLat(), latLngHeight.latLng().getLng());
    }

    /**
     * Teleports a player to a lower-elevation linked Multiverse world.
     * <p>
     * Used when the target height is below the current world's minimum height.
     *
     * @param target        The player to teleport
     * @param height        The calculated target height
     * @param yOffset       The Y-offset of the current world
     * @param latLngHeight  The parsed latitude, longitude, and height
     * @param x             The calculated Minecraft X coordinate
     * @param z             The calculated Minecraft Z coordinate
     */
    private static void teleportToPreviousMultiverseWorld(@NotNull Player target, Double height, int yOffset, LatLongHeight latLngHeight, double x, double z) {
        World tpWorld;
        LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(target.getWorld().getName());
        if (previousServer == null) {
            target.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because the world is not low enough at the moment.");
            return;
        }
        tpWorld = Bukkit.getWorld(previousServer.getWorldName());
        height = height - yOffset + previousServer.getOffset();
        target.sendMessage(prefix + "§7Teleporting to " + latLngHeight.latLng().getLat() + ", " + latLngHeight.latLng().getLng() + " in another" +
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

    /**
     * Teleports a player to a higher-elevation linked Multiverse world.
     * <p>
     * Used when the target height exceeds the current world's maximum height.
     *
     * @param target        The player to teleport
     * @param height        The calculated target height
     * @param yOffset       The Y-offset of the current world
     * @param latLngHeight  The parsed latitude, longitude, and height
     * @param x             The calculated Minecraft X coordinate
     * @param z             The calculated Minecraft Z coordinate
     */
    private static void teleportToNextMultiverseWorld(@NotNull Player target, Double height, int yOffset, LatLongHeight latLngHeight, double x, double z) {
        World tpWorld;
        LinkedWorld nextServer = ConfigurationHelper.getNextServerName(target.getWorld().getName());
        if (nextServer == null) {
            target.sendMessage(prefix + "§cYou cannot tpll to these coordinates, because the worlds are " +
                    "not high enough at the moment.");
            return;
        }
        tpWorld = Bukkit.getWorld(nextServer.getWorldName());
        height = height - yOffset + nextServer.getOffset();
        target.sendMessage(prefix + "§7Teleporting to " + latLngHeight.latLng().getLat() + ", " + latLngHeight.latLng().getLng() + " in " +
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

    /**
     * Validates height bounds and teleports the player if within range.
     * <p>
     * If the height is outside the world's min/max height, an error message is sent.
     *
     * @param target The player to teleport
     * @param tpWorld The target world
     * @param x The Minecraft X coordinate
     * @param z The Minecraft Z coordinate
     * @param height The target height
     * @param lat The latitude coordinate (for message display)
     * @param lon The longitude coordinate (for message display)
     */
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
    // </editor-fold>

    // <editor-fold desc="Messaging">
    private static int sendUsageMessage(@NotNull CommandContext<CommandSourceStack> ctx) {
        sendUsageMessage(ctx.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private static void sendUsageMessage(@NotNull CommandSender sender) {
        sender.sendMessage(prefix + "§7Invalid " + "coordinates or command usage!\n" +
                "Proper usage: /tpll [player or @p (optional)] <latitude> " + "<longitude> [height (optional)]");
    }

    /**
     * Sends a plugin message to the BungeeCord bridge for cross-server teleportation.
     * <p>
     * Used when the target height is outside the current world's bounds and the server
     * is configured to use BungeeCord for linked worlds.
     *
     * @param isNextServer {@code true} to teleport to a higher world, {@code false} for lower
     * @param player       The player to teleport
     * @param lat          The target latitude
     * @param lon          The target longitude
     */
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
    // </editor-fold>

    // <editor-fold desc="Command Registration">
    /**
     * Creates and returns the Brigadier command node for the /tpll command.
     * <p>
     * This method sets up the command structure with:
     * <ul>
     *     <li>Player selector argument for force-teleporting others</li>
     *     <li>Latitude/longitude/height arguments</li>
     *     <li>Permission checks for each command branch</li>
     * </ul>
     *
     * @return The configured {@link LiteralCommandNode} for registration
     */
    public static LiteralCommandNode<CommandSourceStack> create() {
        prefix = Terraplusminus.config.getString("prefix");

        return Commands.literal("tpll")
                .then(Commands.argument("players", ArgumentTypes.players())
                        .then(Commands.argument(LAT_LON_HEIGHT, StringArgumentType.greedyString())
                                .requires(TpllCommand::isPermittedTarget)
                                .executes(TpllCommand::executeTarget))
                        .requires(TpllCommand::isPermittedTarget)
                        .executes(TpllCommand::sendUsageMessage))
                .then(Commands.argument(LAT_LON_HEIGHT, StringArgumentType.greedyString())
                        .requires(TpllCommand::isPermittedDirect)
                        .executes(TpllCommand::executeDirect))
                .requires(TpllCommand::isPermitted)
                .executes(TpllCommand::sendUsageMessage)
                .build();
    }

    /**
     * Executes the /tpll command for targeted players.
     * <p>
     * This method is used when an admin force-teleports other players.
     *
     * @param ctx The Brigadier command context
     * @return {@link Command#SINGLE_SUCCESS}
     * @throws CommandSyntaxException If player selector resolution fails
     */
    private static int executeTarget(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("players", PlayerSelectorArgumentResolver.class);
        final List<Player> targets = targetResolver.resolve(ctx.getSource());
        final String latLonHeight = ctx.getArgument(LAT_LON_HEIGHT, String.class);
        for (final Player target : targets) {
            execute(ctx.getSource().getSender(), target, latLonHeight);
        }
        ctx.getSource().getSender().sendMessage("Executed tpll for " + targets.size() + " players.");
        return Command.SINGLE_SUCCESS;
    }

    /** Checks for {@code t+-.forcetpll} permission. */
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

    /**
     * Checks for {@code t+-.forcetpll} or {@code t+.tpll} (if self-teleporting).
     */
    private static boolean isPermitted(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("t+-.forcetpll") || (source.getSender() == source.getExecutor() && source.getSender().hasPermission("t+.tpll"));
    }
    // </editor-fold>

    // <editor-fold desc="Argument Parsing">
    /**
     * Parses the raw command arguments into latitude, longitude, and optional height.
     * <p>
     * This method handles multiple input formats:
     * <ul>
     *     <li>{@code <lat> <lon>} - Basic coordinates</li>
     *     <li>{@code <lat> <lon> <height>} - Coordinates with explicit height</li>
     *     <li>{@code <player> <lat> <lon> [height]} - With player prefix (handled elsewhere)</li>
     * </ul>
     *
     * @param args    The raw argument string to parse
     * @param yOffset The Y-offset to apply to the height
     * @return A {@link LatLongHeight} record containing parsed coordinates and height
     */
    @Contract("_, _ -> new")
    private static @NotNull LatLongHeight parseArguments(String args, int yOffset) {
        Double height = null;
        args = args.trim();  // I think Brigadier takes care of that, but unsure
        LatLng latLng = CoordinateParseUtils.parseVerbatimCoordinates(args);
        String[] argsArray = args.split(" ");

        if (latLng == null) {
            LatLng possiblePlayerCoords = CoordinateParseUtils.parseVerbatimCoordinates(getRawArguments(selectArray(argsArray, 1)));
            if (possiblePlayerCoords != null) {
                latLng = possiblePlayerCoords;
            }
        }

        LatLng possibleHeightCoords = CoordinateParseUtils.parseVerbatimCoordinates(getRawArguments(inverseSelectArray(argsArray, argsArray.length - 1)));
        if (possibleHeightCoords != null) {
            latLng = possibleHeightCoords;
            try {
                height = Double.parseDouble(argsArray[argsArray.length - 1]);
            } catch (Exception e) { /* Ignored */}
        }

        LatLng possibleHeightNameCoords = CoordinateParseUtils.parseVerbatimCoordinates(getRawArguments(inverseSelectArray(selectArray(argsArray, 1), selectArray(argsArray, 1).length - 1)));
        if (possibleHeightNameCoords != null) {
            latLng = possibleHeightNameCoords;
            try {
                height = Double.parseDouble(selectArray(argsArray, 1)[selectArray(argsArray, 1).length - 1]);
            } catch (Exception e) {/* Ignored */}
        }

        if (height != null) height += yOffset;
        return new LatLongHeight(latLng, height);
    }

    /**
     * Gets all objects in a string array above a given index
     * Example: {@code selectArray(["a", "b", "c"], 1)} → {@code ["b", "c"]}
     *
     * @param args  Initial array
     * @param fromIndex Starting index
     * @return Selected array
     */
    private static String @NotNull [] selectArray(String @NotNull [] args, @SuppressWarnings("SameParameterValue") int fromIndex) {
        List<String> array = new ArrayList<>(Arrays.asList(args).subList(fromIndex, args.length));
        return array.toArray(String[]::new);
    }

    /**
     * Gets all objects in a string array under a given index
     * Example: {@code inverseSelectArray(["a", "b", "c"], 2)} → {@code ["a", "b"]}
     *
     * @param args  Initial array
     * @param toIndex Starting index
     * @return Selected array
     */
    private static String @NotNull [] inverseSelectArray(String[] args, int toIndex) {
        List<String> array = new ArrayList<>(Arrays.asList(args).subList(0, toIndex));
        return array.toArray(String[]::new);
    }

    /** Joins array elements with spaces, replacing degree symbols (°) with spaces. */
    private static String getRawArguments(String @NotNull [] args) {
        if (args.length == 0) {
            return "";
        }
        if (args.length == 1) {
            return args[0];
        }

        StringBuilder arguments = new StringBuilder(args[0].replace(DEGREE_SYMBOL, SPACE).trim());

        for (int x = 1; x < args.length; x++) {
            arguments.append(" ").append(args[x].replace(DEGREE_SYMBOL, SPACE).trim());
        }

        return arguments.toString();
    }
    // </editor-fold>

    // <editor-fold desc="Inner Classes">
    /** Parsed coordinates with optional height. */
    private record LatLongHeight(LatLng latLng, Double height) { }
    // </editor-fold>
}
