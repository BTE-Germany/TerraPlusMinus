package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.tree.TreePopulator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import lombok.Getter;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.BukkitBindings;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.min;
import static net.buildtheearth.terraminusminus.substitutes.ChunkPos.blockToCube;
import static net.buildtheearth.terraminusminus.substitutes.ChunkPos.cubeToMinBlock;
import static org.bukkit.Material.BRICKS;
import static org.bukkit.Material.DIRT;
import static org.bukkit.Material.DIRT_PATH;
import static org.bukkit.Material.FARMLAND;
import static org.bukkit.Material.GRASS_BLOCK;
import static org.bukkit.Material.GRAY_CONCRETE_POWDER;
import static org.bukkit.Material.MOSS_BLOCK;
import static org.bukkit.Material.MYCELIUM;
import static org.bukkit.Material.SNOW;
import static org.bukkit.Material.SNOW_BLOCK;
import static org.bukkit.Material.STONE;
import static org.bukkit.Material.WATER;
import static org.bukkit.block.Biome.*;

// TODO add json save of empty chunks so we can catchup when server crashed etc

/**
 * Main {@link ChunkGenerator} for TerraPlusMinus.
 * This class is responsible for generating the world terrain based on real-world data.
 */
public class RealWorldGenerator extends ChunkGenerator {

    /**
     * A functional interface to abstract block setting operations.
     * This allows the same terrain application logic to be used for both
     * initial chunk generation (writing to {@link ChunkData}) and asynchronous
     * generation (writing directly to the {@link World}).
     */
    @FunctionalInterface
    private interface BlockSetter {
        void setBlock(int x, int y, int z, Material material);

        default void setRegion(int x, int y, int z, int endX, int endY, int endZ, Material material) {
            for (int i = x; i < endX; i++) {
                for (int j = y; j < endY; j++) {
                    for (int k = z; k < endZ; k++) {
                        setBlock(i, j, k, material);
                    }
                }
            }
        }

        default Biome getBiome(int x, int y, int z) {
            return PLAINS; // Default biome
        }
    }

    @Getter
    private final EarthGeneratorSettings settings;
    @Getter
    private final int yOffset;
    private Location spawnLocation = null;

    private final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;
    private final CustomBiomeProvider customBiomeProvider;


    private final Material surfaceMaterial;
    private final Map<String, Material> materialMapping;

    private static final Set<Material> GRASS_LIKE_MATERIALS = Set.of(
            GRASS_BLOCK,
            DIRT_PATH,
            FARMLAND,
            MYCELIUM,
            SNOW
    );

    // Track how many times we’ve retried a given chunk
    private final Map<ChunkPos, AtomicInteger> retryCounts = new ConcurrentHashMap<>();

    // Define your back-off delays *in ticks* (20 ticks = 1 second)
    private static final long[] RETRY_DELAYS_TICKS = new long[]{
            20L,      // attempt #1 → after 1 second
            600L,     // attempt #2 → after 30 seconds
            1_200L,   // attempt #3 → after 60 seconds
            6_000L    // attempt #4 → after 5 minutes
    };

    /**
     * Constructor for the RealWorldGenerator.
     *
     * @param yOffset vertical offset to apply to the terrain.
     */
    public RealWorldGenerator(int yOffset) {
        EarthGeneratorSettings settingsWithoutProj = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

        GeographicProjection projection = new OffsetProjectionTransform(
                settingsWithoutProj.projection(),
                Terraplusminus.config.getInt("terrain_offset.x"),
                Terraplusminus.config.getInt("terrain_offset.z")
        );
        if (yOffset == 0) {
            this.yOffset = Terraplusminus.config.getInt("terrain_offset.y");
        } else {
            this.yOffset = yOffset;
        }

        this.settings = settingsWithoutProj.withProjection(projection);

        this.customBiomeProvider = new CustomBiomeProvider(projection);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(this.settings));

        this.surfaceMaterial = ConfigurationHelper.getMaterial(Terraplusminus.config, "surface_material", GRASS_BLOCK);
        this.materialMapping = Map.of(
                "minecraft:bricks", ConfigurationHelper.getMaterial(Terraplusminus.config, "building_outlines_material", BRICKS),
                "minecraft:gray_concrete", ConfigurationHelper.getMaterial(Terraplusminus.config, "road_material", GRAY_CONCRETE_POWDER),
                "minecraft:dirt_path", ConfigurationHelper.getMaterial(Terraplusminus.config, "path_material", MOSS_BLOCK)
        );

    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        CachedChunkData terraData = this.getTerraChunkDataAsync(chunkX, chunkZ, worldInfo.getName());
        if (terraData == null) {
            // If we don't have the data yet, we can't generate the noise.
            return;
        }
        applyNoise(worldInfo, chunkData, terraData);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return this.customBiomeProvider;
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        CachedChunkData terraData = this.getTerraChunkDataAsync(chunkX, chunkZ, worldInfo.getName());
        if (terraData == null) {
            // If we don't have the data yet, we can't generate the surface.
            return;
        }
        applySurface(worldInfo, random, chunkData, terraData);
    }

    /**
     * Fetches chunk data asynchronously. This is used always except there is no workaround.
     * If the data is not in the cache, it returns null immediately and schedules the terrain application upon future completion.
     *
     * @param chunkX The chunk's X coordinate.
     * @param chunkZ The chunk's Z coordinate.
     * @param worldName The name of the world.
     * @return {@link CachedChunkData} if already cached, otherwise null.
     */
    private @Nullable CachedChunkData getTerraChunkDataAsync(int chunkX, int chunkZ, String worldName) {
        try {
            CompletableFuture<CachedChunkData> future = this.cache.getUnchecked(new ChunkPos(chunkX, chunkZ));
            // TODO kill this task when server shutdown
            if (!future.isDone()) {
                // Data is not ready yet. Return null to generate an empty chunk for now.
                // The terrain will be applied once the future completes.
                future.whenComplete((data, ex) -> {
                    try {
                        onTerraDataLoaded(new ChunkPos(chunkX, chunkZ), data, ex, worldName, chunkX, chunkZ);
                    } catch (Exception e) {
                        Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk " + chunkX + ", " + chunkZ, e);
                    }
                });
                return null;
            } else {
                // Data was ready immediately.
                return future.get();
            }
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) Thread.currentThread().interrupt();
            Terraplusminus.instance.getComponentLogger().error("Unrecoverable exception when getting chunk data future for chunk " + chunkX + ", " + chunkZ, e);
            return null;
        }
    }

    /**
     * Callback executed when the asynchronous fetch of {@link CachedChunkData} completes.
     *
     * @param pos       The position of the chunk.
     * @param terraData The loaded chunk data, or null on failure.
     * @param ex        The exception, if the load failed.
     * @param worldName The name of the world.
     * @param chunkX    The chunk's X coordinate.
     * @param chunkZ    The chunk's Z coordinate.
     */
    private void onTerraDataLoaded(ChunkPos pos, CachedChunkData terraData, Throwable ex, String worldName, int chunkX, int chunkZ) {
        if (ex != null) {
            handleTerraDataLoadFailure(pos, ex, worldName, chunkX, chunkZ);
        } else {
            // Success: clear retry counter and apply terrain to the world directly.
            retryCounts.remove(pos);
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                Terraplusminus.instance.getComponentLogger().error("World " + worldName + " not found for applying async terrain for chunk " + chunkX + ", " + chunkZ);
                return;
            }

            // We need a ChunkData-like interface to set blocks. Since we are outside the main generation pipeline,
            // we have to set blocks directly in the world. This is slower but necessary - need to runs on main
            // thread or we would have to use fawe maybe we should add that as a third option?
            Bukkit.getScheduler().runTask(Terraplusminus.instance, () -> {
                try {
                    Random random = new Random(world.getSeed() + chunkX + chunkZ); // Not a perfect seed, but sufficient for this purpose.
                    BlockSetter blockSetter = createBlockSetter(world, chunkX, chunkZ);
                    applyNoise(world, blockSetter, terraData);
                    applySurface(world, random, blockSetter, terraData);
                    // Apply trees and other features
                    //new TreePopulator(customBiomeProvider, yOffset).populate(world, new Random(), chunkX, chunkZ,
                    //        terraData);
                    // TODO populator make that actually work or nbypass it

                } catch (Exception e) {
                    Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk " + chunkX + ", " + chunkZ, e);
                }
            });
        }
    }

    /**
     * Handles failures in asynchronously loading chunk data. Implements a retry mechanism with back-off.
     *
     * @param pos       The position of the chunk that failed to load.
     * @param ex        The exception that occurred.
     * @param worldName The name of the world.
     * @param chunkX    The chunk's X coordinate.
     * @param chunkZ    The chunk's Z coordinate.
     */
    private void handleTerraDataLoadFailure(ChunkPos pos, Throwable ex, String worldName, int chunkX, int chunkZ) {
        if (ex.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            Terraplusminus.instance.getComponentLogger().error("Chunk data load for " + pos + " was interrupted.", ex);
            return;
        }

        int attempts = retryCounts.computeIfAbsent(pos, k -> new AtomicInteger(0)).incrementAndGet();

        if (attempts <= RETRY_DELAYS_TICKS.length) {
            long delay = RETRY_DELAYS_TICKS[attempts - 1];
            Terraplusminus.instance.getLogger().warning(String.format(
                    "Failed to load chunk %s (attempt %d of %d), retrying in %.1fs: %s",
                    pos, attempts, RETRY_DELAYS_TICKS.length, delay / 20.0, ex.getMessage()
            ));

            // Schedule a delayed retry. // TODO save and should down this task
            Bukkit.getScheduler().runTaskLaterAsynchronously(Terraplusminus.instance, () -> {
                // Retry loading the chunk data
                CompletableFuture<CachedChunkData> future = this.cache.getUnchecked(pos);
                future.whenComplete((data, error) -> {
                    try {
                        onTerraDataLoaded(pos, data, error, worldName, chunkX, chunkZ);
                    } catch (Exception e) {
                        Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk {}, {}",
                                chunkX,
                                chunkZ,
                                e);
                    }
                });
            }, delay);
        } else {
            Terraplusminus.instance.getComponentLogger().error("Failed to load chunk " + pos + " after " + attempts + " " +
                    "attempts. Giving up" +
                    ".", ex);
        }
    }

    /**
     * Fetches chunk data synchronously, blocking until the data is available.
     *
     * @param chunkX The chunk's X coordinate.
     * @param chunkZ The chunk's Z coordinate.
     * @return The {@link CachedChunkData}.
     * @throws RuntimeException if loading fails.
     */
    private @NotNull CachedChunkData getTerraChunkDataSync(int chunkX, int chunkZ) {
        try {
            return this.cache.getUnchecked(new ChunkPos(chunkX, chunkZ)).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Unrecoverable exception when generating chunk data synchronously in Terra-- for chunk " + chunkX + ", " + chunkZ, e);
        }
    }

    /**
     * Applies the base terrain (noise) to a chunk using {@link ChunkData}.
     * This is used during the main chunk generation phase.
     *
     * @param worldInfo The world information.
     * @param chunkData The chunk data to modify.
     * @param terraData The real-world terrain data.
     */
    private void applyNoise(WorldInfo worldInfo, @NotNull ChunkData chunkData, CachedChunkData terraData) {
        applyNoise(worldInfo, createBlockSetter(chunkData), terraData);
    }

    /**
     * Applies the base terrain (noise) to a chunk using a {@link BlockSetter}.
     * This is the core implementation for noise generation.
     *
     * @param worldInfo The world information.
     * @param blockSetter The block setter to use for setting blocks.
     * @param terraData The real-world terrain data.
     */
    private void applyNoise(WorldInfo worldInfo, BlockSetter blockSetter, CachedChunkData terraData) {
        int minWorldY = worldInfo.getMinHeight();
        int maxWorldY = worldInfo.getMaxHeight();

        // We start by finding the lowest 16x16x16 cube that's not underground
        int minSurfaceCubeY = blockToCube(minWorldY - this.yOffset);
        int maxWorldCubeY = blockToCube(maxWorldY - this.yOffset);
        if (terraData.aboveSurface(minSurfaceCubeY)) {
            return; // All done, it's all air
        }
        while (minSurfaceCubeY < maxWorldCubeY && terraData.belowSurface(minSurfaceCubeY)) {
            minSurfaceCubeY++;
        }

        // We can now fill most of the underground in a single call.
        if (minSurfaceCubeY >= maxWorldCubeY) {
            blockSetter.setRegion(0, minWorldY, 0, 16, maxWorldY, 16, STONE);
            return; // All done, everything is underground
        } else {
            blockSetter.setRegion(0, minWorldY, 0, 16, cubeToMinBlock(minSurfaceCubeY), 16, STONE);
        }

        // And now, we build the actual terrain shape on top of everything
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int groundHeight = min(terraData.groundHeight(x, z) + this.yOffset, maxWorldY - 1);
                blockSetter.setRegion(x, minWorldY, z, x + 1, groundHeight + 1, z + 1, STONE);

                int waterHeight = min(terraData.waterHeight(x, z) + this.yOffset, maxWorldY - 1);
                blockSetter.setRegion(x, groundHeight + 1, z, x + 1, waterHeight + 1, z + 1, WATER);
            }
        }
    }

    /**
     * Applies the surface decoration to a chunk using {@link ChunkData}.
     * This is used during the main chunk generation phase.
     *
     * @param worldInfo The world information.
     * @param random    A random number generator.
     * @param chunkData The chunk data to modify.
     * @param terraData The real-world terrain data.
     */
    private void applySurface(WorldInfo worldInfo, @NotNull Random random, @NotNull ChunkData chunkData, CachedChunkData terraData) {
        applySurface(worldInfo, random, createBlockSetter(chunkData), terraData);
    }

    /**
     * Applies the surface decoration to a chunk using a {@link BlockSetter}.
     * This is the core implementation for surface generation.
     *
     * @param worldInfo The world information.
     * @param random    A random number generator.
     * @param blockSetter The block setter to use for setting blocks.
     * @param terraData The real-world terrain data.
     */
    private void applySurface(WorldInfo worldInfo, @NotNull Random random, BlockSetter blockSetter, CachedChunkData terraData) {
        final int minWorldY = worldInfo.getMinHeight();
        final int maxWorldY = worldInfo.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int groundY = terraData.groundHeight(x, z) + this.yOffset;
                if (groundY < minWorldY || groundY >= maxWorldY) {
                    continue;
                }

                Material material = determineSurfaceMaterial(terraData, x, z, groundY, random, blockSetter.getBiome(x, groundY, z));

                boolean isUnderWater = groundY + 1 >= maxWorldY || blockSetter.getBiome(x, groundY + 1, z).toString().contains("OCEAN"); // A bit of a hack
                if (isUnderWater && GRASS_LIKE_MATERIALS.contains(material)) {
                    material = DIRT;
                }
                blockSetter.setBlock(x, groundY, z, material);
            }
        }
    }

    /**
     * Creates a {@link BlockSetter} for writing to {@link ChunkData}.
     *
     * @param chunkData The chunk data to wrap.
     * @return A new BlockSetter instance.
     */
    private BlockSetter createBlockSetter(ChunkData chunkData) {
        return new BlockSetter() {
            @Override
            public void setBlock(int x, int y, int z, Material material) {
                chunkData.setBlock(x, y, z, material);
            }

            @Override
            public void setRegion(int x, int y, int z, int endX, int endY, int endZ, Material material) {
                chunkData.setRegion(x, y, z, endX, endY, endZ, material);
            }

            @Override
            public Biome getBiome(int x, int y, int z) {
                return chunkData.getBiome(x, y, z);
            }
        };
    }

    /**
     * Creates a {@link BlockSetter} for writing directly to a {@link World}.
     *
     * @param world  The world to write to.
     * @param chunkX The chunk's X coordinate.
     * @param chunkZ The chunk's Z coordinate.
     * @return A new BlockSetter instance.
     */
    private BlockSetter createBlockSetter(World world, int chunkX, int chunkZ) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        return new BlockSetter() {
            @Override
            public void setBlock(int x, int y, int z, Material material) {
                world.getBlockAt(baseX + x, y, baseZ + z).setType(material, false);
            }

            @Override
            public Biome getBiome(int x, int y, int z) {
                return world.getBiome(baseX + x, y, baseZ + z);
            }
        };
    }

    /**
     * Determines the appropriate surface material for a given block column.
     *
     * @param terraData The real-world terrain data.
     * @param x         The block's X coordinate within the chunk (0-15).
     * @param z         The block's Z coordinate within the chunk (0-15).
     * @param groundY   The ground height at this column.
     * @param random    A random number generator.
     * @param biome     The biome at this column.
     * @return The {@link Material} for the surface block.
     */
    private Material determineSurfaceMaterial(@NotNull CachedChunkData terraData, int x, int z, int groundY, Random random, Biome biome) {
        BlockState state = terraData.surfaceBlock(x, z);
        if (state != null) {
            // Terra--'s OSM config says a feature should be drawn there.
            Material material = this.materialMapping.get(state.getBlock().toString());
            if (material != null) {
                return material;
            }
            // If we don't have a mapping, respect what the Terra-- config says.
            return BukkitBindings.getAsBlockData(state).getMaterial();
        }

        // From this point on, we are dealing with natural terrain.
        int startMountainHeight = random.nextInt(7500, 7520);
        if (groundY >= startMountainHeight) {
            return STONE; // Mountains are bare stone.
        }

        // Fallback to a generic block that matches the biome.
        if (biome == DESERT) {
            return Material.SAND;
        } else if (biome == SNOWY_SLOPES || biome == SNOWY_PLAINS || biome == FROZEN_PEAKS) {
            return SNOW_BLOCK;
        } else {
            return this.surfaceMaterial;
        }
    }


    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {
        // We don't want bedrock.
    }

    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {
        // We don't want vanilla caves.
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        int chunkX = blockToCube(x);
        int chunkZ = blockToCube(z);
        x -= cubeToMinBlock(chunkX);
        z -= cubeToMinBlock(chunkZ);
        // Must be synchronous for this method.
        CachedChunkData terraData = this.getTerraChunkDataSync(chunkX, chunkZ);
        return switch (heightMap) {
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> terraData.groundHeight(x, z) + this.yOffset;
            default -> terraData.surfaceHeight(x, z) + this.yOffset;
        };
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        // Allow spawning on sand and gravel for beaches.
        Block highest = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);
        return switch (world.getEnvironment()) {
            case NETHER -> true;
            case THE_END -> highest.getType() != Material.AIR && highest.getType() != WATER && highest.getType() != Material.LAVA;
            default -> highest.getType() == Material.SAND || highest.getType() == Material.GRAVEL;
        };
    }

    @Override
    @NotNull
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return Collections.singletonList(new TreePopulator(customBiomeProvider, yOffset));
    }

    @Override
    @Nullable
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        if (spawnLocation == null)
            spawnLocation = new Location(world, 3517417, 58, -5288234); // Default spawn location
        return spawnLocation;
    }

    // The following methods disable vanilla generation steps that we replace or don't want.
    // Our logic is injected via generateNoise and generateSurface.

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }


    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }


    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }


    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }


    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }


    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
