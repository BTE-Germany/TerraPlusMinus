package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import de.btegermany.terraplusminus.utils.LinkedWorldLayout;
import de.btegermany.terraplusminus.utils.Permission;
import de.btegermany.terraplusminus.utils.Properties;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class PlayerMoveEvent implements Listener {

    /**
     * A player who ends up this many blocks below their world's downward trigger was dragged past it
     * by a lag spike. The transition is then forced, ignoring the cooldown (but not a transition
     * that is already running).
     */
    private static final int FAILSAFE_MARGIN = 32;

    /**
     * 20 ticks. Long enough to keep a player from bouncing between two worlds.
     */
    private static final long TELEPORT_COOLDOWN_MS = 1000;

    /**
     * Cooldown after a transition that decided against moving the player. Short, because the next
     * step has to be judged again, but long enough that a plateau at the build limit is not
     * rescanned every tick.
     */
    private static final long RETRY_COOLDOWN_MS = 250;

    /**
     * Hard limit on how long a player counts as "already being moved". Only a safety net: a chunk
     * load or teleport that never completes must not lock a player out of transitions forever.
     */
    private static final long TRANSITION_TIMEOUT_MS = 15_000;

    /**
     * How far a player has to rise above their world's build limit before they are handed upwards
     * even though the world above has no terrain at that column.
     */
    private static final int FREE_ASCENT_MARGIN = 4;

    /**
     * Sentinel for {@link #highestSolidY(World, int, int)} when the whole column is empty.
     */
    private static final int NO_SOLID_BLOCK = Integer.MIN_VALUE;

    /**
     * Waiting a second before validating gives Multiverse time to load the linked worlds.
     */
    private static final long LAYOUT_VALIDATION_DELAY_TICKS = 20;

    final int yOffsetConfigEntry;

    private final boolean actionBarEnabled;
    private final boolean linkedWorldsActive;

    private final Plugin plugin;
    private final HashMap<String, Integer> worldHashMap;
    private final LinkedWorldLayout layout;

    private final Cache<UUID, Boolean> teleportCooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(TELEPORT_COOLDOWN_MS, TimeUnit.MILLISECONDS)
            .build();

    private final Cache<UUID, Boolean> retryCooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(RETRY_COOLDOWN_MS, TimeUnit.MILLISECONDS)
            .build();

    private final Cache<UUID, Boolean> transitionsInFlight = CacheBuilder.newBuilder()
            .expireAfterWrite(TRANSITION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();

    public PlayerMoveEvent(@org.jspecify.annotations.NonNull Plugin plugin) {
        this.plugin = plugin;
        yOffsetConfigEntry = plugin.getConfig().getInt(Properties.Y_OFFSET, 0);
        actionBarEnabled = plugin.getConfig().getBoolean(Properties.ACTIONBAR_HEIGHT);

        String linkedWorldsMethod = plugin.getConfig().getString(Properties.LINKED_WORLDS_METHOD);
        linkedWorldsActive = plugin.getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED)
                && linkedWorldsMethod != null
                && linkedWorldsMethod.equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV);

        worldHashMap = new HashMap<>();
        layout = LinkedWorldLayout.fromConfig();
        if (linkedWorldsActive) {
            List<LinkedWorld> worlds = ConfigurationHelper.getWorlds();
            for (LinkedWorld world : worlds) {
                this.worldHashMap.put(world.getWorldName(), world.getOffset());
            }
            plugin.getComponentLogger().info("Linked worlds enabled, using Multiverse method.");
            Bukkit.getScheduler().runTaskLater(plugin, () -> layout.validate(plugin), LAYOUT_VALIDATION_DELAY_TICKS);
        }
        if (actionBarEnabled) startKeepActionBarAlive();
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.@NonNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (actionBarEnabled) setHeightInActionBar(player);

        // A canceled move must not trigger a world transition.
        if (!this.linkedWorldsActive || event.isCancelled()) return;

        // Transitions only ever happen on a block boundary, so skip the vast majority of move events.
        // Horizontal steps count too: terrain clipped by a world's build limit makes the boundary a
        // walkable plateau, and crossing it never changes the player's height.
        if (!event.hasChangedBlock()) return;

        handleWorldTransition(player, event.getTo());
    }

    private void startKeepActionBarAlive() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                setHeightInActionBar(p);
            }
        }, 0, 20);
    }

    private void setHeightInActionBar(@NonNull Player p) {
        worldHashMap.putIfAbsent(p.getWorld().getName(), yOffsetConfigEntry);
        if (p.getInventory().getItemInMainHand().getType() != Material.DEBUG_STICK) {
            int height = p.getLocation().getBlockY() - worldHashMap.get(p.getWorld().getName());
            p.sendActionBar(Component.text(height + "m").decorate(TextDecoration.BOLD));
        }
    }

    /**
     * Moves the player into the world above or below once they leave the usable band of their
     * current world. Both boundaries come from {@link LinkedWorldLayout}. A world's band is not
     * assumed to start at {@code mcY 0}, so a world may cover its entire height range.
     */
    private void handleWorldTransition(@NonNull Player player, @NonNull Location to) {
        World current = player.getWorld();
        int index = layout.indexOf(current.getName());
        if (index == LinkedWorldLayout.NOT_LINKED) return;

        // A player is only ever handed over once at a time.
        if (isTransitionPending(player)) return;

        if (!Permission.AUTOTELEPORT.isGrantedTo(player)) return;

        int y = to.getBlockY(); // the player's feet

        if (layout.hasNext(index) && y >= LinkedWorldLayout.upwardThreshold(current)) {
            if (isOnTeleportCooldown(player)) return;
            transitionUp(player, to, index, current);
            return;
        }

        if (!layout.hasPrevious(index)) return;

        World below = resolve(player, layout.get(index - 1));
        if (below == null) return;

        int threshold = layout.downwardThreshold(index, below);
        if (y >= threshold) return;

        // Below the band there is nothing left to stand on, so a missed trigger has to be caught
        // even while the cooldown is still running.
        boolean failsafe = y <= threshold - FAILSAFE_MARGIN;
        if (!failsafe && isOnTeleportCooldown(player)) return;

        transitionDown(player, to, index, below, failsafe);
    }

    /**
     * Upwards the player usually enters the target world at the seam of a mountain that was clipped
     * off by the current world's build limit, so they are placed on top of the target column rather
     * than at the converted height.
     * <p>
     * The transition is called off when that column has no terrain at all: the player is then just
     * standing on top of their own world's terrain and would drop straight back through the
     * boundary. Only a player who is {@link #FREE_ASCENT_MARGIN} blocks clear of it is moved up
     * regardless, since they are flying rather than walking.
     */
    private void transitionUp(@NonNull Player player, @NonNull Location from, int index, @NonNull World current) {
        World target = resolve(player, layout.get(index + 1));
        if (target == null) return;

        int blockX = from.getBlockX();
        int blockZ = from.getBlockZ();
        // Where the boundary elevation lands in the target world - the bottom of its usable band.
        int bandBottom = layout.downwardThreshold(index + 1, current);
        int convertedY = layout.convertY(from.getBlockY(), index, index + 1);
        boolean clearOfBoundary = from.getBlockY() >= LinkedWorldLayout.upwardThreshold(current) + FREE_ASCENT_MARGIN;

        beginTransition(player);
        setTeleportCooldown(player);

        plugin.getComponentLogger().debug("Moving {} up from '{}' (y {}) into '{}'",
                player.getName(), current.getName(), from.getBlockY(), target.getName());

        // The column scan below needs the target chunk's blocks.
        target.getChunkAtAsyncUrgently(blockX >> 4, blockZ >> 4).whenComplete((chunk, error) -> onMainThread(() -> {
            if (!stillTransitioning(player, current, error)) return;

            int surfaceY = highestSolidY(target, blockX, blockZ);

            if (surfaceY < bandBottom && !clearOfBoundary) {
                plugin.getComponentLogger().debug("Keeping {} in '{}': '{}' has no terrain above y {} at {}/{}",
                        player.getName(), current.getName(), target.getName(), bandBottom, blockX, blockZ);
                // Nothing happened, so the next step has to be judged again.
                endTransition(player);
                setRetryCooldown(player);
                return;
            }

            // Never below the converted height: what the target world holds under its band duplicates
            // the terrain the player just left, and dropping them onto it would undo their climb.
            int y = Math.max(convertedY, surfaceY + 1);
            completeTransition(player, target, from, freeSpaceAt(target, blockX, y, blockZ));
        }));
    }

    /**
     * Downwards the target world's terrain covers the elevation the player is falling through, so
     * the height is converted directly and only corrected if it would place them inside a block.
     */
    private void transitionDown(@NonNull Player player, @NonNull Location from, int index, @NonNull World target,
                                boolean failsafe) {
        World current = player.getWorld();
        int blockX = from.getBlockX();
        int blockZ = from.getBlockZ();
        int convertedY = layout.convertY(from.getBlockY(), index, index - 1);

        beginTransition(player);
        setTeleportCooldown(player);

        plugin.getComponentLogger().debug("Moving {} down from '{}' (y {}) into '{}' at y {}{}",
                player.getName(), current.getName(), from.getBlockY(), target.getName(), convertedY,
                failsafe ? " (failsafe)" : "");

        target.getChunkAtAsyncUrgently(blockX >> 4, blockZ >> 4).whenComplete((chunk, error) -> onMainThread(() -> {
            if (!stillTransitioning(player, current, error)) return;
            completeTransition(player, target, from, freeSpaceAt(target, blockX, convertedY, blockZ));
        }));
    }

    /**
     * Whether a transition that was waiting on a chunk should still go ahead. The player may have
     * logged off or been sent somewhere else in the meantime, and teleporting them out of wherever
     * they ended up would be worse than doing nothing.
     */
    private boolean stillTransitioning(@NonNull Player player, @NonNull World source, @Nullable Throwable error) {
        if (error != null) {
            plugin.getComponentLogger().warn("Could not load the target chunk for {}: {}",
                    player.getName(), error.toString());
            endTransition(player);
            return false;
        }
        if (!player.isOnline() || !source.equals(player.getWorld())) {
            endTransition(player);
            return false;
        }
        return true;
    }

    /**
     * X and Z are carried over unchanged: {@code terrain_offset.x/z} applies to every linked world
     * alike, so adding it here would shift the player sideways on every transition.
     */
    private void completeTransition(@NonNull Player player, @NonNull World target, @NonNull Location from, int y) {
        Location destination = new Location(target, from.getX(), y, from.getZ(), from.getYaw(), from.getPitch());
        Motion motion = Motion.of(player);
        player.sendMessage(plugin.getConfig().getString(Properties.CHAT_PREFIX) + "§7Teleporting to linked world...");
        player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((success, error) -> onMainThread(() -> {
                    endTransition(player);
                    if (error != null || !Boolean.TRUE.equals(success) || !player.isOnline()) return;

                    setTeleportCooldown(player);
                    motion.restoreOn(player);
                }));
    }

    /**
     * How the player was moving when they were handed over. A teleport clears their motion and drops
     * them out of flight, so both are put back afterwards.
     */
    private record Motion(@NonNull Vector velocity, boolean flying) {

        // Vanilla velocity, in blocks per tick.
        private static final double MAX_SPEED = 3.92;

        static @NonNull Motion of(@NonNull Player player) {
            return new Motion(capped(player.getVelocity()), player.isFlying());
        }

        private static @NonNull Vector capped(@NonNull Vector velocity) {
            double speed = velocity.length();
            if (!Double.isFinite(speed)) return new Vector();
            return speed > MAX_SPEED ? velocity.multiply(MAX_SPEED / speed) : velocity;
        }

        void restoreOn(@NonNull Player player) {
            if (this.flying) {
                // A flying player is driven by their own client, so a velocity only fights it.
                if (player.getAllowFlight()) player.setFlying(true);
                return;
            }
            player.setVelocity(this.velocity);
        }
    }

    private @Nullable World resolve(@NonNull Player player, @NonNull LinkedWorld world) {
        World bukkitWorld = Bukkit.getWorld(world.getWorldName());
        if (bukkitWorld == null) {
            plugin.getComponentLogger().warn("Cannot move {} into linked world '{}': the world is not loaded.",
                    player.getName(), world.getWorldName());
        }
        return bukkitWorld;
    }

    /**
     * Paper completes both futures on the server thread, but block access and
     * {@link Player#setVelocity(Vector)} would blow up if that ever changed.
     */
    private void onMainThread(@NonNull Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    /**
     * @return the highest solid block in the column, or {@link #NO_SOLID_BLOCK} if there is none.
     * Unlike {@link World#getHighestBlockYAt(int, int)} this ignores non-solid blocks and reports
     * empty columns instead of returning the world's minimum height.
     */
    private static int highestSolidY(@NonNull World world, int x, int z) {
        int start = Math.clamp(world.getHighestBlockYAt(x, z), world.getMinHeight(), world.getMaxHeight() - 1);
        for (int y = start; y >= world.getMinHeight(); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) return y;
        }
        return NO_SOLID_BLOCK;
    }

    /**
     * Moves the given height up until the player fits, so a transition can never drop somebody into
     * terrain. Needed in both directions: the world below reaches up to its own build limit, so a
     * mountain can occupy exactly the converted height.
     */
    private static int freeSpaceAt(@NonNull World world, int x, int y, int z) {
        int min = world.getMinHeight() + 1;
        int max = world.getMaxHeight();
        int candidate = Math.clamp(y, min, max);
        while (candidate < max && !fitsAt(world, x, candidate, z)) {
            candidate++;
        }
        return candidate;
    }

    private static boolean fitsAt(@NonNull World world, int x, int y, int z) {
        return isPassableAt(world, x, y, z) && isPassableAt(world, x, y + 1, z);
    }

    /**
     * Treats everything outside the world's height range as free space: a player may stand above the
     * build limit even though no block can exist there.
     */
    private static boolean isPassableAt(@NonNull World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return true;
        return world.getBlockAt(x, y, z).isPassable();
    }

    private boolean isTransitionPending(@NonNull Player player) {
        return transitionsInFlight.getIfPresent(player.getUniqueId()) != null;
    }

    private void beginTransition(@NonNull Player player) {
        transitionsInFlight.put(player.getUniqueId(), Boolean.TRUE);
    }

    private void endTransition(@NonNull Player player) {
        transitionsInFlight.invalidate(player.getUniqueId());
    }

    private boolean isOnTeleportCooldown(@NonNull Player player) {
        return teleportCooldowns.getIfPresent(player.getUniqueId()) != null
                || retryCooldowns.getIfPresent(player.getUniqueId()) != null;
    }

    private void setTeleportCooldown(@NonNull Player player) {
        teleportCooldowns.put(player.getUniqueId(), Boolean.TRUE);
    }

    /**
     * Replaces the full cooldown after a transition that decided against moving the player.
     */
    private void setRetryCooldown(@NonNull Player player) {
        teleportCooldowns.invalidate(player.getUniqueId());
        retryCooldowns.put(player.getUniqueId(), Boolean.TRUE);
    }

}
