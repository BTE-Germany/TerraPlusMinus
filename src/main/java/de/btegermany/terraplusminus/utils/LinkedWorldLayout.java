package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An ordered, immutable snapshot of {@code linked_worlds.worlds} together with the height math
 * that connects the worlds to each other.
 * <p>
 * The list is expected to be ordered from the lowest to the highest world, which is the same
 * order {@link ConfigurationHelper#getNextServerName(String)} and
 * {@link ConfigurationHelper#getPreviousServerName(String)} assume.
 * <p>
 * Every world stores a y-offset that maps Minecraft heights onto real elevations as
 * {@code real = mcY - offset}. The offset alone decides where a world's terrain sits; where two
 * worlds hand players over is a separate question, and it is answered by the lower world's build
 * limit:
 * <ul>
 *     <li>terrain can never exist above a world's build limit, so that is exactly the elevation at
 *     which the world above has to take over - the
 *     {@linkplain #upwardThreshold(World) upward trigger};</li>
 *     <li>converted into the upper world's coordinates the same elevation becomes the bottom of its
 *     usable band, and therefore the
 *     {@linkplain #downwardThreshold(int, World) downward trigger}.</li>
 * </ul>
 */
public final class LinkedWorldLayout {

    /**
     * Returned by {@link #indexOf(String)} for worlds that are not part of the linked setup.
     */
    public static final int NOT_LINKED = -1;

    private final List<LinkedWorld> worlds;

    /**
     * Name to position, so {@link #indexOf(String)} stays a single lookup.
     */
    private final Map<String, Integer> indexByName;

    private LinkedWorldLayout(List<LinkedWorld> worlds) {
        this.worlds = worlds;
        this.indexByName = new HashMap<>(worlds.size());
        for (int i = 0; i < worlds.size(); i++) {
            this.indexByName.putIfAbsent(worlds.get(i).getWorldName().toLowerCase(Locale.ROOT), i);
        }
    }

    /**
     * Reads the current configuration once and freezes it into a layout.
     */
    public static @NonNull LinkedWorldLayout fromConfig() {
        return new LinkedWorldLayout(List.copyOf(ConfigurationHelper.getWorlds()));
    }

    public int size() {
        return this.worlds.size();
    }

    public @NonNull LinkedWorld get(int index) {
        return this.worlds.get(index);
    }

    /**
     * @return the index of the given world in the linked setup, or {@link #NOT_LINKED}
     */
    public int indexOf(String worldName) {
        return this.indexByName.getOrDefault(worldName.toLowerCase(Locale.ROOT), NOT_LINKED);
    }

    public boolean hasNext(int index) {
        return index >= 0 && index < this.worlds.size() - 1;
    }

    public boolean hasPrevious(int index) {
        return index > 0 && index < this.worlds.size();
    }

    /**
     * The height at which a player leaves a world upwards: its build limit. Nothing can be built or
     * generated above it, so it is the highest elevation the world can still represent.
     */
    public static int upwardThreshold(@NonNull World world) {
        return world.getMaxHeight();
    }

    /**
     * The height at which a player leaves world {@code index} downwards, in that world's own
     * coordinates.
     * <p>
     * Derived from {@link #upwardThreshold(World)} of the world below so the two directions can
     * never disagree about where the boundary sits.
     *
     * @param worldBelow the Bukkit world of {@code index - 1}, check {@link #hasPrevious(int)} first
     */
    public int downwardThreshold(int index, @NonNull World worldBelow) {
        return this.convertY(upwardThreshold(worldBelow), index - 1, index);
    }

    /**
     * Converts a Minecraft height from one linked world into the equivalent height in another,
     * keeping the real elevation intact.
     */
    public int convertY(int mcY, int fromIndex, int toIndex) {
        return mcY - this.worlds.get(fromIndex).getOffset() + this.worlds.get(toIndex).getOffset();
    }

    /**
     * Checks that the configured worlds actually form a usable, gap-free stack and reports every
     * problem it finds. Nothing is thrown. A broken layout degrades the transitions but must not
     * prevent the server from starting.
     */
    public void validate(@NonNull Plugin plugin) {
        var logger = plugin.getComponentLogger();

        if (this.worlds.size() < 2) {
            logger.warn("Linked worlds are enabled but only {} world(s) are configured. "
                    + "At least two worlds are required for transitions to happen.", this.worlds.size());
            return;
        }

        Set<String> seen = new HashSet<>();
        for (LinkedWorld world : this.worlds) {
            if (!seen.add(world.getWorldName().toLowerCase(Locale.ROOT))) {
                logger.error("Linked world '{}' is configured more than once. World lookups match by name, "
                        + "so only the first entry will ever be found and transitions will break.", world.getWorldName());
            }
        }

        for (int i = 0; i < this.worlds.size(); i++) {
            LinkedWorld world = this.worlds.get(i);
            World bukkitWorld = Bukkit.getWorld(world.getWorldName());

            if (bukkitWorld == null) {
                logger.warn("Linked world '{}' is not loaded. Players will not be able to transition into it.",
                        world.getWorldName());
            } else {
                // The height range is what a missing or rejected height datapack shows up as, and it
                // decides every threshold below.
                logger.info("Linked world '{}': y {}..{} (offset {}) covers real {}..{} m.",
                        world.getWorldName(), bukkitWorld.getMinHeight(), bukkitWorld.getMaxHeight() - 1,
                        world.getOffset(), bukkitWorld.getMinHeight() - world.getOffset(),
                        bukkitWorld.getMaxHeight() - 1 - world.getOffset());

                ChunkGenerator generator = bukkitWorld.getGenerator();
                if (!(generator instanceof RealWorldGenerator realWorldGenerator)) {
                    logger.warn("Linked world '{}' is not generated by Terra+-. Its terrain will not line up "
                            + "with the configured offset {}.", world.getWorldName(), world.getOffset());
                } else if (realWorldGenerator.getYOffset() != world.getOffset()) {
                    logger.warn("Linked world '{}' was generated with y-offset {} but is configured with {}. "
                                    + "Players will be teleported to the wrong height.",
                            world.getWorldName(), realWorldGenerator.getYOffset(), world.getOffset());
                }
            }

            if (!this.hasNext(i)) {
                continue;
            }

            LinkedWorld above = this.worlds.get(i + 1);

            if (world.getOffset() - above.getOffset() <= 0) {
                logger.error("Linked world '{}' (offset {}) is listed below '{}' (offset {}), but its offset is not "
                                + "larger. The list must be ordered from the lowest to the highest world with strictly "
                                + "decreasing offsets.",
                        world.getWorldName(), world.getOffset(), above.getWorldName(), above.getOffset());
                continue;
            }

            World bukkitAbove = Bukkit.getWorld(above.getWorldName());
            if (bukkitWorld == null || bukkitAbove == null) {
                continue;
            }

            // The two worlds meet at the lower one's build limit. That elevation has to land inside
            // the upper world's height range, otherwise players can never reach the boundary.
            int bandBottom = this.downwardThreshold(i + 1, bukkitWorld);

            if (bandBottom < bukkitAbove.getMinHeight()) {
                logger.error("'{}' takes over from '{}' at y {}, which is below its floor of y {}. Players dropping "
                                + "out of '{}' would fall into the void instead of returning to '{}'. The offset of "
                                + "'{}' must not be lower than {}.",
                        above.getWorldName(), world.getWorldName(), bandBottom, bukkitAbove.getMinHeight(),
                        above.getWorldName(), world.getWorldName(), above.getWorldName(),
                        bukkitAbove.getMinHeight() - upwardThreshold(bukkitWorld) + world.getOffset());
            } else if (bandBottom >= bukkitAbove.getMaxHeight()) {
                logger.error("'{}' takes over from '{}' at y {}, which is at or above its own build limit of y {}. "
                                + "'{}' has no usable height range and will never be reached.",
                        above.getWorldName(), world.getWorldName(), bandBottom, bukkitAbove.getMaxHeight(),
                        above.getWorldName());
            }
        }
    }
}
