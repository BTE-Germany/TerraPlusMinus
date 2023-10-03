package de.btegermany.terraplusminus.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.PluginMessageUtil;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.geo.LatLng;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.btegermany.terraplusminus.commands.CommandHelper.InvalidTargetSelectorException;
import static de.btegermany.terraplusminus.commands.CommandHelper.parseTargetSelector;
import static io.papermc.lib.PaperLib.isChunkGenerated;
import static io.papermc.lib.PaperLib.teleportAsync;
import static java.lang.Double.isNaN;
import static java.lang.Double.parseDouble;
import static java.lang.String.join;
import static java.util.Arrays.copyOfRange;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.toList;
import static net.buildtheearth.terraminusminus.util.geo.CoordinateParseUtils.parseVerbatimCoordinates;
import static org.bukkit.ChatColor.*;


public class TpllCommand implements CommandExecutor {

    private static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("##.#####");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] arguments) {

        if (!sender.hasPermission("t+-.tpll")) {
            sender.sendMessage(RED + "You do not have permission to use that command");
            return true;
        }

        final String prefix = Terraplusminus.config.getString("prefix"); // Used in feedback messages

        // Option to pass through tpll to other bukkit plugins.
        String passthroughTpll = Terraplusminus.config.getString("passthrough_tpll");
        if (!isNullOrEmpty(passthroughTpll)) {
            Terraplusminus.instance.getServer().dispatchCommand(
                    sender,
                    passthroughTpll + ":tpll " + join(" ", arguments)
            );
            return true;
        }

        int consumedArguments = 0; // Number of consumed arguments, starting left

        // Parse targets first.
        // They were either explicitly provided in the first argument, or it's just the sender
        Collection<Entity> targets = null;
        if (arguments.length > 1) {
            try {
                targets = parseTargetSelector(sender, arguments[consumedArguments]);
                consumedArguments++;
            } catch (InvalidTargetSelectorException ignored) {
                // Not a valid selector, probably coordinates
            }
        }
        if (targets == null) {
            if (sender instanceof Player) {
                targets = Collections.singleton((Player) sender);
            } else {
                return false; // Invalid command, non player senders must specify a valid target
            }
        } else if ((targets.size() > 1 || !targets.contains(sender)) && !sender.hasPermission("t+-.forcetpll")){
            // Sender specified a target which is not themselves
            sender.sendMessage(
                    prefix
                    + RED + "You do not have permission to use TPLL on others"
            );
            return true;
        }

        // Now, parse destination latitude and longitude.
        // This is trickier, as they may span multiple arguments as we delegate parsing to Terra--.
        // We start left and arguments, stopping as soon as we found the first valid set of coordinates.
        LatLng searchingLocation = null;
        final LatLng geoLocation;  // We are passing it to a lambda later, so it needs to be final
        int i = consumedArguments;
        for (; i < arguments.length && searchingLocation == null; i++) {
            String rawArgumentString = join(" ", copyOfRange(arguments, consumedArguments, i + 1));
            searchingLocation = parseVerbatimCoordinates(rawArgumentString);
        }
        if (searchingLocation == null) {
            // No valid position, command is invalid
            return false;
        } else {
            geoLocation = searchingLocation;
        }
        consumedArguments = i;

        // Parse optional destination altitude
        // If arguments remain after that, the command is invalid
        double altitude;
        if (consumedArguments == arguments.length - 1) {
            // An altitude was passed in
            try {
                altitude = parseDouble(arguments[arguments.length - 1]);
            } catch (NumberFormatException e) {
                // Altitude is not valid
                return false;
            }
        } else if (consumedArguments == arguments.length){
            // We will calculate the world height for each target latter (they may be in different worlds)
            altitude = Double.NaN;
        } else {
            // Arguments would remain, syntax is wrong
            return false;
        }

        Map<UUID, World> worldMap = new HashMap<>();
        Map<UUID, List<Entity>> targetMap = new HashMap<>();

        // Group targets by world
        for (Entity target: targets) {
            World world = target.getWorld();
            UUID worldId = world.getUID();
            worldMap.put(worldId, world);
            targetMap.computeIfAbsent(worldId, u -> new ArrayList<>()).add(target);
        }

        // Dispatch targets
        for (Map.Entry<UUID, List<Entity>> entry: targetMap.entrySet()) {
            UUID worldId = entry.getKey();
            World world = worldMap.get(worldId);
            Collection<Entity> worldTargets = entry.getValue();
            try {
                this.dispatchTargetsInWorld(sender, worldTargets, world, geoLocation, altitude);
            } catch (CommandException e) {
                sender.sendMessage(prefix + RED + "Could not teleport " + this.formatTargetList(targets) + ", " + e.getMessage());
            }
        }

        return true;
    }

    private void dispatchTargetsInWorld(CommandSender sender, Collection<Entity> targets, World world, final LatLng geolocation, double altitude) throws CommandException {

        // Get X and Z
        Location destination = this.projectGeolocation(world, geolocation, altitude);

        double y = destination.getY();
        boolean tooLow = y < world.getMinHeight();
        boolean tooHigh = y >= world.getMaxHeight();

        String prefix = Terraplusminus.config.getString("prefix");

        if (tooLow || tooHigh) {
            // Not within world bounds, we either send to a different server or fail
            if (!Terraplusminus.config.getBoolean("linked_servers.enabled")) {
                throw new CommandException("the world is not " + (tooLow ? "low" : "high") + " enough at the moment");
            }
            final String server;
            if (tooLow) {
                server = PluginMessageUtil.getLastServerName();
            } else {
                server = PluginMessageUtil.getNextServerName();
            }
            if (server == null) {
                throw new CommandException("the server configuration is not set up properly, please contact the server administrator");
            }
            targets.forEach(t -> this.dispatchToOtherServer(sender, t, server, geolocation));
        } else if (!sender.hasPermission("t+-.admin") && !this.isWithinTeleportationBounds(world, geolocation)) {
            throw new CommandException("you cannot tpll to these coordinates, because this area is being worked on by another build team");
        } else {
            if (!isChunkGenerated(destination)) {
                sender.sendMessage(prefix + "Generating destination in world " + world.getName() + "...");
            }
            final Map<Entity, CompletableFuture<Boolean>> futures = new HashMap<>();
            targets.forEach(
                    target -> futures.put(target, teleportAsync(target, destination))
            );

            // Wait for all teleportations to complete
            CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).thenAccept(unused -> {
                List<Entity> success = futures.entrySet().stream()
                        .filter(e -> {
                            try {
                                return e.getValue().get();
                            } catch (InterruptedException | ExecutionException ex) {
                                throw new IllegalStateException();
                            }
                        })
                        .map(Map.Entry::getKey)
                        .collect(toList());
                List<Entity> failures = futures.entrySet().stream()
                        .filter(e -> {
                            try {
                                return !e.getValue().get();
                            } catch (InterruptedException | ExecutionException ex) {
                                throw new IllegalStateException();
                            }
                        })
                        .map(Map.Entry::getKey)
                        .collect(toList());
                if (!failures.isEmpty()) {
                    sender.sendMessage(prefix + RED + "Failed to teleport §9" + this.formatTargetList(success) + RED + " to " + this.formatDestination(geolocation));
                }
                if (!success.isEmpty()) {
                    sender.sendMessage(prefix + "§7Teleported §9" + this.formatTargetList(success) + " §7to " + this.formatDestination(geolocation));
                }
            });
        }
    }

    private Location projectGeolocation(World world, LatLng geolocation, double altitude) throws CommandException {

        ChunkGenerator chunkGenerator = world.getGenerator();
        if (!(chunkGenerator instanceof RealWorldGenerator)) {
            throw new CommandException("world " + world.getName() + " is not a Terra+- world");
        }

        RealWorldGenerator generator = (RealWorldGenerator) chunkGenerator;

        // We update this later
        final Location destination = new Location(world, 0, 0, 0);

        // Set destination X and Z
        GeographicProjection projection = generator.getSettings().projection();
        try {
            double[] xz = projection.fromGeo(geolocation.getLng(), geolocation.getLat());
            destination.setX(xz[0]);
            destination.setZ(xz[1]);
        } catch (OutOfProjectionBoundsException e) {
            throw new CommandException("destination is out of projection bounds");
        }

        // Set destination Y
        if (isNaN(altitude)) { // Is set to NaN when not explicitly provided by the command sender
            destination.setY(world.getHighestBlockYAt(destination));
        } else {
            destination.setY(altitude + generator.getYOffset());
        }

        return destination;
    }

    @SuppressWarnings("unused") // Passing world as an argument because ideally we could set boundaries per-world
    private boolean isWithinTeleportationBounds(World world, LatLng geolocation) {

        // Read the configuration
        double minLat = Terraplusminus.config.getDouble("min_latitude");
        double maxLat = Terraplusminus.config.getDouble("max_latitude");
        double minLon = Terraplusminus.config.getDouble("min_longitude");
        double maxLon = Terraplusminus.config.getDouble("max_longitude");

        // Keeping this for backward compatibility
        // This is a bit abusive, there are legitimate use cases where the bounds could be 0.
        // The best approach would probably be to simply make it optional in the config
        if (minLat == 0d || maxLat == 0d || minLon == 0d || maxLon == 0d) {
            return true; // No bounds configured
        }

        // Actual boundary check (bounds inclusive)
        double latitude = geolocation.getLat();
        double longitude = geolocation.getLng();
        return latitude >= minLat && longitude >= minLon & latitude >= maxLat && longitude >= maxLon;
    }

    private void dispatchToOtherServer(CommandSender sender, Entity target, String server, LatLng geolocation) {

        // Make sure target is a player
        String prefix = Terraplusminus.config.getString("prefix");
        if (!(target instanceof Player)) {
            sender.sendMessage(prefix +
                    RED + "Cannot teleport " + GRAY + this.formatTargetName(target) +
                    RED + ": destination is outside of range and only players may be sent to linked servers."
            );
            return;
        }
        Player player = (Player) target;


        // Send the message to the proxy using the player's connexion
        byte[] message = this.encodeTerraLinkDispatchMessage(player, server, geolocation);
        player.sendPluginMessage(Terraplusminus.instance, "bungeecord:terraplusminus", message);

        // Send feedback to command sender and target
        sender.sendMessage(prefix +
                GRAY + "Sending " + DARK_GRAY + this.formatTargetName(target) +
                GRAY + " to server " + DARK_GRAY + server + GRAY + "."
        );
        player.sendMessage(prefix +
                "§cSending to another server..."
        );

    }

    private String formatTargetName(Entity target) {
        return requireNonNullElseGet(target.getCustomName(), target::getName);
    }

    private String formatTargetList(Collection<Entity> targets) {
        if (targets.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        targets.stream()
                .map(this::formatTargetName)
                .sorted()
                .forEach(names::add);
        if (names.size() == 1) {
            return names.get(0);
        }
        String last = names.remove(names.size() - 1);
        return join(", ", names) + " and " + last;
    }

    private String formatDestination(LatLng location) {
        return
                BLUE + DECIMAL_FORMATTER.format(location.getLat())
                + GRAY + ", "
                + BLUE + DECIMAL_FORMATTER.format(location.getLng());
    }

    private byte[] encodeTerraLinkDispatchMessage(Player player, String serverName, LatLng destination) {
        // Keeping it as is for backward compatibility,
        // but we could cut down message length by more than half by sending doubles and longs directly
        // That would also eliminate the cost of encoding numbers to UTF-8 and having to send string length information
        // Also, relying on a beta functionality is not ideal when there are viable alternatives in the JDK
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(player.getUniqueId().toString());
        out.writeUTF(serverName);
        String coordinateString = destination.getLat() + ", " + destination.getLng();
        out.writeUTF(coordinateString);
        return out.toByteArray();
    }

}
