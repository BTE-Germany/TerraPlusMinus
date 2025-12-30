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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.bukkit.ChatColor.RED;

/**
 * Command handler for the /tpll command.
 * <p>
 * This command allows players to teleport to real-world geographic coordinates (latitude/longitude)
 * within a Terraplusminus world. It supports:
 * <ul>
 *     <li>Direct teleportation using latitude and longitude</li>
 *     <li>Optional height specification</li>
 *     <li>Teleporting other players (with appropriate permissions)</li>
 *     <li>Cross-world teleportation via Multiverse or Proxy</li>
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
        double minLat = config.getDouble(Properties.MIN_LAT);
        double maxLat = config.getDouble(Properties.MAX_LAT);
        double minLon = config.getDouble(Properties.MIN_LON);
        double maxLon = config.getDouble(Properties.MAX_LON);

        ChunkGenerator generator = tpWorld.getGenerator();
        RealWorldGenerator terraGenerator = null;
        if (!(generator instanceof RealWorldGenerator tg)) {
            var worlds = Bukkit.getWorlds();
            if (target.hasPermission("t+-.tpll.otherWorld")) {
                for (var world : worlds) {
                    if (world.getGenerator() instanceof RealWorldGenerator gen) {
                        terraGenerator = gen;
                        tpWorld = world;
                        break;
                    }
                }
            }

            if (terraGenerator == null) {
                sender.sendMessage(prefix + "§cThis is not a Terraplusmins world.");
                Terraplusminus.instance.getComponentLogger().warn("This is not a Terraplusminus world: {}." +
                        "The world generator must be set to Terraplusminus for T+- to work." +
                        "Remove the permission t+-.tpll for this world if you don't want to see this warning.", tpWorld.getName());
                return;
            }
        } else {
            terraGenerator = tg;
        }
        EarthGeneratorSettings generatorSettings = terraGenerator.getSettings();
        GeographicProjection projection = generatorSettings.projection();
        LatLongHeight latLngHeight = parseArguments(args);

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

        int yOffset = terraGenerator.getYOffset();

        if (!config.getBoolean(Properties.LINKED_WORLDS_ENABLED) && latLngHeight.height() == null) {
            finalizeTeleport(target,
                    tpWorld,
                    new Vector(x, tpWorld.getHighestBlockYAt((int) x, (int) z), z),
                    latLngHeight.latLng(),
                    config, yOffset);
            return;
        }

        if (latLngHeight.height() == null) {
            int roundedX = (int) Math.round(x);
            int roundedZ = (int) Math.round(z);
            World finalTpWorld = tpWorld;
            terraGenerator.getBaseHeightAsync(roundedX, roundedZ)
                    .thenAcceptAsync(baseHeight ->
                            finalizeTeleport(target,
                                    finalTpWorld,
                                    new Vector(x, baseHeight.groundHeight(roundedX - ChunkPos.cubeToMinBlock(ChunkPos.blockToCube(roundedX)),
                                            roundedX - ChunkPos.cubeToMinBlock(ChunkPos.blockToCube(roundedX))), z),
                                    latLngHeight.latLng(),
                                    config,
                                    yOffset
                            )).exceptionally(ex -> {
                        target.sendMessage(RED + "Error while fetching elevation from API!");
                        Terraplusminus.instance.getComponentLogger().error("Error while fetching elevation from API for tpll!", ex);
                        return null;
                    });
        } else {
            finalizeTeleport(target, tpWorld, new Vector(x, latLngHeight.height(), z), latLngHeight.latLng(), config, yOffset);
        }
    }

    /**
     * Teleports a player to a higher-elevation linked Multiverse world or another server.
     * <p>
     * Used when the target height exceeds the current world's maximum height.
     *
     * @param target    The player to teleport
     * @param isNext    Teleport to next or previous world?
     * @param geoCoords The parsed latitude, longitude
     * @param mcCoords  The calculated Minecraft X/Y/Z coordinates
     * @param xOff      The configured X-offset
     * @param zOff      The configured Z-offset
     */
    private static void handleLinkedWorlds(Player target, boolean isNext, LatLng geoCoords, @NonNull Vector mcCoords, int xOff, int zOff) {
        String method = Terraplusminus.config.getString(Properties.LINKED_WORLDS_METHOD, "");
        if (!Terraplusminus.config.getBoolean(Properties.LINKED_WORLDS_ENABLED) ||
                !(method.equalsIgnoreCase(Properties.NonConfigurable.METHOD_SRV) || method.equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV))) {
            target.sendMessage(prefix + RED + "World height limit reached!");
            return;
        }

        if (method.equalsIgnoreCase(Properties.NonConfigurable.METHOD_SRV)) {
            sendPluginMessageToBungeeBridge(isNext, target, geoCoords);
        } else if (method.equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV)) {
            LinkedWorld linked = isNext ? ConfigurationHelper.getNextServerName(target.getWorld().getName()) : ConfigurationHelper.getPreviousServerName(target.getWorld().getName());
            if (linked == null) {
                target.sendMessage(prefix + RED + "No linked world found!");
                return;
            }
            World linkedWorld = Bukkit.getWorld(linked.getWorldName());
            double newHeight = mcCoords.getY() + linked.getOffset() + 1;
            target.sendMessage(prefix + "§7Teleporting to linked world...");
            target.teleportAsync(new Location(linkedWorld, mcCoords.getX() + xOff, newHeight, mcCoords.getZ() + zOff, target.getLocation().getYaw(), target.getLocation().getPitch()))
                    .thenAcceptAsync(success -> {
                        if (Boolean.TRUE.equals(success))
                            target.sendMessage(prefix + "§7Teleported to " + geoCoords.getLat() + ", " + geoCoords.getLng() + ", " + mcCoords.getBlockY() + ".");
                    });
        }
    }

    /**
     * Validates height bounds and teleports the player if within range.
     * <p>
     * Depending on the configuration it uses multiverse worlds or the configured server if the height limit is exceeded.
     *
     * @param target    The player to teleport
     * @param tpWorld   The target world
     * @param mcCoords  The calculated Minecraft X/Y/Z coordinates
     * @param geoCoords The geo coordinates (for message display)
     * @param config    The supplied config for linked worlds
     * @param yOffset   The configured terrain offset
     */
    private static void finalizeTeleport(@NotNull Player target, @NonNull World tpWorld, @NonNull Vector mcCoords, LatLng geoCoords, @NonNull FileConfiguration config, int yOffset) {
        int xOffset = config.getInt(Properties.X_OFFSET);
        int zOffset = config.getInt(Properties.Z_OFFSET);
        int destinationY = mcCoords.getBlockY() + yOffset + 1; // You want to stand above the block you are teleporting to

        Terraplusminus.instance.getComponentLogger().debug("Current world max height: {}, min height: {}, requested height: {}", tpWorld.getMaxHeight(), tpWorld.getMinHeight(), destinationY);

        if (destinationY > tpWorld.getMaxHeight()) {
            handleLinkedWorlds(target, true, geoCoords, mcCoords, xOffset, zOffset);
            return;
        } else if (destinationY <= tpWorld.getMinHeight()) {
            handleLinkedWorlds(target, false, geoCoords, mcCoords, xOffset, zOffset);
            return;
        }

        Location location = new Location(tpWorld,
                mcCoords.getX() + xOffset,
                destinationY,
                mcCoords.getZ() + zOffset,
                target.getLocation().getYaw(),
                target.getLocation().getPitch());

        target.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND);
        target.sendMessage(prefix + "§7Teleported to " + geoCoords.getLat() + ", " + geoCoords.getLng() + ", " + mcCoords.getBlockY() + ".");
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
     * Sends a plugin message to the Proxy bridge for cross-server teleportation.
     * <p>
     * Used when the target height is outside the current world's bounds and the server
     * is configured to use Proxy for linked worlds.
     *
     * @param isNextServer {@code true} to teleport to a higher world, {@code false} for lower
     * @param player       The player to teleport
     * @param geoCoords    The geo coordinates
     */
    private static void sendPluginMessageToBungeeBridge(boolean isNextServer, @NotNull Player player,
                                                        LatLng geoCoords) {
        Terraplusminus plugin = (Terraplusminus) JavaPlugin.getProvidingPlugin(Terraplusminus.class);
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
        out.writeUTF(geoCoords.getLat() + ", " + geoCoords.getLng());
        player.sendPluginMessage(plugin, Properties.NonConfigurable.CROSS_TELEPORTATION_CHANNEL, out.toByteArray());

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
        prefix = Terraplusminus.instance.getConfig().getString(Properties.CHAT_PREFIX);

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

    /**
     * Checks for {@code t+-.forcetpll} permission.
     */
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
     * @param args The raw argument string to parse
     * @return A {@link LatLongHeight} record containing parsed coordinates and height
     */
    @Contract("_ -> new")
    private static @NotNull LatLongHeight parseArguments(String args) {
        Terraplusminus.instance.getComponentLogger().debug("parseArguments input: '{}'", args);

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
                    return new LatLongHeight(latLng, parsedHeight);
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

    /**
     * Tries to parse a string as a double, returns null if parsing fails.
     */
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
     * @param args    Initial array
     * @param toIndex Starting index
     * @return Selected array
     */
    private static String @NotNull [] inverseSelectArray(String[] args, int toIndex) {
        List<String> array = new ArrayList<>(Arrays.asList(args).subList(0, toIndex));
        return array.toArray(String[]::new);
    }
    // </editor-fold>

    // <editor-fold desc="Inner Classes">

    /**
     * Parsed coordinates with optional height.
     */
    private record LatLongHeight(LatLng latLng, Double height) {
    }
    // </editor-fold>
}
