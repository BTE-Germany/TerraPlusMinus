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
import de.btegermany.terraplusminus.utils.Properties;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.geo.CoordinateParseUtils;
import net.buildtheearth.terraminusminus.util.geo.LatLng;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

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
    public static final String TPLL_OTHERS_PERMISSION = "t+-.forcetpll";

    static String prefix;
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

        if (!config.getBoolean(Properties.LINKED_WORLDS_ENABLED) && height == null) {
            checkAndTeleportIfCorrectWorld(target,
                    tpWorld,
                    new Vector(x, tpWorld.getHighestBlockYAt((int) x, (int) z) + 1d, z),
                    yOffset,
                    latLngHeight.latLng(),
                    config);
            return;
        }

        if (height == null) {
            int roundedX = (int) Math.round(x);
            int roundedZ = (int) Math.round(z);
            terraGenerator.getBaseHeightAsync(roundedX, roundedZ).thenAcceptAsync(baseHeight -> checkAndTeleportIfCorrectWorld(target,
                    tpWorld,
                    new Vector(x, baseHeight.groundHeight(roundedX - ChunkPos.cubeToMinBlock(ChunkPos.blockToCube(roundedX)),
                            roundedX - ChunkPos.cubeToMinBlock(ChunkPos.blockToCube(roundedX))) + 1d, z),
                    yOffset,
                    latLngHeight.latLng(),
                    config));
        } else {
            checkAndTeleportIfCorrectWorld(target, tpWorld, new Vector(x, height, z), yOffset, latLngHeight.latLng(), config);
        }
    }

    /**
     * Teleports a player to a lower-elevation linked Multiverse world.
     * <p>
     * Used when the target height is below the current world's minimum height.
     *
     * @param target        The player to teleport
     * @param height        The calculated target height
     * @param yOffset       The Y-offset of the current world
     * @param latLng        The parsed latitude, longitude
     * @param x             The calculated Minecraft X coordinate
     * @param z             The calculated Minecraft Z coordinate
     */
    private static void teleportToPreviousMultiverseWorld(@NotNull Player target, Double height, double yOffset, LatLng latLng, double x, double z) {
        World tpWorld;
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

    /**
     * Teleports a player to a higher-elevation linked Multiverse world.
     * <p>
     * Used when the target height exceeds the current world's maximum height.
     *
     * @param target        The player to teleport
     * @param height        The calculated target height
     * @param yOffset       The Y-offset of the current world
     * @param latLng        The parsed latitude, longitude
     * @param x             The calculated Minecraft X coordinate
     * @param z             The calculated Minecraft Z coordinate
     */
    private static void teleportToNextMultiverseWorld(@NotNull Player target, double height, double yOffset, LatLng latLng, double x, double z) {
        World tpWorld;
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

    /**
     * Validates height bounds and teleports the player if within range.
     * <p>
     * If the height is outside the world's min/max height, an error message is sent.
     *
     * @param target  The player to teleport
     * @param tpWorld The target world
     * @param cord    The calculated Minecraft X/Y/Z coordinates
     * @param latLng  The geo coordinates (for message display)
     * @param config  The supplied config for linked worlds
     */
    private static void checkAndTeleportIfCorrectWorld(@NotNull Player target, World tpWorld, @NonNull Vector cord, double yOffset, LatLng latLng, FileConfiguration config) {
        String msgPart1 = prefix + "§cYou cannot tpll to these coordinates, because the world is not ";
        String msgPart2 = " enough at the moment.";

        if (cord.getBlockY() > target.getWorld().getMaxHeight()) {
            if (config.getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase("SERVER")) {
                // send player uuid and coordinates to bungee
                sendPluginMessageToBungeeBridge(true, target, latLng.getLat(), latLng.getLng());
                return;
            } else if (config.getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase("MULTIVERSE")) {
                teleportToNextMultiverseWorld(target, cord.getY(), yOffset, latLng, cord.getX(), cord.getZ());
                return;
            }
        } else if (cord.getBlockY() <= target.getWorld().getMinHeight()) {
            if (config.getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase("SERVER")) {
                // send player uuid and coordinates to bungee
                sendPluginMessageToBungeeBridge(false, target, latLng.getLat(), latLng.getLng());
                return;
            } else if (config.getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase("MULTIVERSE")) {
                teleportToPreviousMultiverseWorld(target, cord.getY(), yOffset, latLng, cord.getX(), cord.getZ());
                return;
            }
        }

        if (cord.getBlockY() > target.getWorld().getMaxHeight()) {
            target.sendMessage(msgPart1 + "high" + msgPart2);
            return;
        } else if (cord.getBlockY() <= target.getWorld().getMinHeight()) {
            target.sendMessage(msgPart1 + "low" + msgPart2);
            return;
        }

        Location location = new Location(tpWorld,
                cord.getX(),
                cord.getBlockY(),
                cord.getZ(),
                target.getLocation().getYaw(),
                target.getLocation().getPitch());

        target.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND);
        target.sendMessage(prefix + "§7Teleported to " + latLng.getLat() + ", " + latLng.getLng() + ", " + cord.getBlockY() + ".");
    }
    // </editor-fold>

    // <editor-fold desc="Messaging">
    private static int sendUsageMessage(@NotNull CommandContext<CommandSourceStack> ctx) {
        sendUsageMessage(ctx.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private static void sendUsageMessage(@NotNull CommandSender sender) {
        sender.sendMessage(prefix + "§7Invalid coordinates or command usage!\n" +
                "Usage: /tpll <latitude> <longitude> [height]\n" +
                "       /tpll -p <player/@selector> <latitude> <longitude> [height]");
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
        Terraplusminus plugin = (Terraplusminus) Terraplusminus.getProvidingPlugin(Terraplusminus.class);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(player.getUniqueId().toString());
        LinkedWorld server;
        if (isNextServer) {
            server = ConfigurationHelper.getNextServerName(plugin.getRegisteredServerName());
        } else {
            server = ConfigurationHelper.getPreviousServerName(plugin.getRegisteredServerName());
        }

        if (server != null) {
            out.writeUTF(server.getWorldName() + ", " + server.getOffset());
        } else {
            player.sendMessage(prefix + "§cPlease contact server administrator. Your config is not set up correctly.");
            return;
        }
        out.writeUTF(lat + ", " + lon);
        player.sendPluginMessage(plugin, "bungeecord:terraplusminus", out.toByteArray());

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
        prefix = Terraplusminus.instance.getConfig().getString("prefix");

        // Structure:
        // /tpll <coords>                       -> self teleport
        // /tpll -p <players> <coords>          -> force teleport (uses Brigadier player selector)
        //
        // Using a literal "-p" prefix avoids Brigadier trying to parse coordinates as player selectors.
        // This is the cleanest solution that works reliably with Brigadier.
        return Commands.literal("tpll")
                .then(Commands.literal("-p")
                        .requires(source -> source.getSender().hasPermission(TPLL_OTHERS_PERMISSION))
                        .then(Commands.argument("players", ArgumentTypes.players())
                                .then(Commands.argument(LAT_LON_HEIGHT, StringArgumentType.greedyString())
                                        .executes(TpllCommand::executeTarget)
                                        .requires(TpllCommand::isPermittedTarget))))
                .then(Commands.argument(LAT_LON_HEIGHT, StringArgumentType.greedyString())
                        .requires(TpllCommand::isPermitted)
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
        Terraplusminus.instance.getComponentLogger().debug("executeTarget called - force teleport branch");
        final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("players", PlayerSelectorArgumentResolver.class);
        final List<Player> targets = targetResolver.resolve(ctx.getSource());
        final String latLonHeight = ctx.getArgument(LAT_LON_HEIGHT, String.class);
        Terraplusminus.instance.getComponentLogger().debug("Targets: {}, coords: '{}'", targets.size(), latLonHeight);

        CommandSender sender = ctx.getSource().getSender();
        for (final Player target : targets) {
            execute(sender, target, latLonHeight);
        }
        sender.sendMessage(prefix + "§7Executed tpll for " + targets.size() + " player(s).");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Executes self-teleport using coordinates only.
     */
    private static int executeDirect(@NotNull CommandContext<CommandSourceStack> ctx) {
        Terraplusminus.instance.getComponentLogger().debug("executeDirect called - self teleport branch");
        final String latLonHeight = ctx.getArgument(LAT_LON_HEIGHT, String.class);
        Terraplusminus.instance.getComponentLogger().debug("coords: '{}'", latLonHeight);

        if (ctx.getSource().getExecutor() instanceof Player player) {
            execute(ctx.getSource().getSender(), player, latLonHeight);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Checks for {@code t+-.forcetpll} or {@code t+-.tpll} (if self-teleporting).
     */
    private static boolean isPermitted(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission(TPLL_OTHERS_PERMISSION) ||
               (source.getSender() == source.getExecutor() && source.getSender().hasPermission("t+-.tpll"));
    }

    /** Checks for {@code t+-.forcetpll} permission. */
    private static boolean isPermittedTarget(@NotNull CommandSourceStack commandSourceStack) {
        return commandSourceStack.getSender().hasPermission(TPLL_OTHERS_PERMISSION);
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
        Terraplusminus.instance.getComponentLogger().debug("parseArguments input: '{}', yOffset: {}", args, yOffset);

        String[] argsArray = args.split(" ");

        // Try parsing coordinates with height at the end (need at least 3 parts: lat, lon, height)
        if (argsArray.length >= 3) {
            String possibleHeight = argsArray[argsArray.length - 1];
            Terraplusminus.instance.getComponentLogger().debug("Possible height: '{}'", possibleHeight);
            Double parsedHeight = tryParseDouble(possibleHeight);
            Terraplusminus.instance.getComponentLogger().debug("Parsed height: {}", parsedHeight);
            if (parsedHeight != null) {
                LatLng latLng = CoordinateParseUtils.parseVerbatimCoordinates(String.join(" ", inverseSelectArray(argsArray, argsArray.length - 1)));
                if (latLng != null) {
                    return new LatLongHeight(latLng, parsedHeight + yOffset);
                }
            }
        }

        // Try parsing the full string as coordinates (no height specified)
        LatLng latLng = CoordinateParseUtils.parseVerbatimCoordinates(args);
        if (latLng != null) {
            return new LatLongHeight(latLng, null);
        }

        return new LatLongHeight(null, null);
    }

    /** Tries to parse a string as a double, returns null if parsing fails. */
    @Contract(pure = true)
    private static @Nullable Double tryParseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
    // </editor-fold>

    // <editor-fold desc="Inner Classes">
    /** Parsed coordinates with optional height. */
    private record LatLongHeight(LatLng latLng, Double height) { }
    // </editor-fold>
}
