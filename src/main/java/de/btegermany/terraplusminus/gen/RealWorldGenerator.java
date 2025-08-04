package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.tree.TreePopulator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import lombok.Getter;
import lombok.SneakyThrows;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.BukkitBindings;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import org.apache.commons.lang3.tuple.ImmutablePair;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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


/**
 * Main {@link ChunkGenerator} for TerraPlusMinus.
 * This class is responsible for generating the world terrain based on real-world data.
 */
public class RealWorldGenerator extends ChunkGenerator {

    @Getter
    private final int yOffset;
    private Location spawnLocation = null;

    private static final Material surfaceMaterial = ConfigurationHelper.getMaterial(Terraplusminus.config,
            "surface_material", GRASS_BLOCK);
    private static final Map<String, Material> materialMapping = Map.of(
            "minecraft:bricks", ConfigurationHelper.getMaterial(Terraplusminus.config, "building_outlines_material", BRICKS),
            "minecraft:gray_concrete", ConfigurationHelper.getMaterial(Terraplusminus.config, "road_material", GRAY_CONCRETE_POWDER),
            "minecraft:dirt_path", ConfigurationHelper.getMaterial(Terraplusminus.config, "path_material", MOSS_BLOCK)
    );

    private static final Set<Material> GRASS_LIKE_MATERIALS = Set.of(
            GRASS_BLOCK,
            DIRT_PATH,
            FARMLAND,
            MYCELIUM,
            SNOW
    );

    /**
     * Constructor for the RealWorldGenerator.
     *
     * @param yOffset vertical offset to apply to the terrain.
     */
    public RealWorldGenerator(int yOffset) {
        if (yOffset == 0) {
            this.yOffset = Terraplusminus.config.getInt("terrain_offset.y");
        } else {
            this.yOffset = yOffset;
        }
    }


    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        var data = getTerraChunkDataAsync(new ChunkInfo(chunkX, chunkZ, yOffset, worldInfo.getName()));
        if (data == null || data.left == null) {
            // If we don't have the data yet, we can't generate the noise.
            chunkData.setRegion(0, worldInfo.getMinHeight(), 0, 16, worldInfo.getMinHeight() + 1, 16, STONE);
            return;
        }
        try {
            applyNoise(worldInfo, createBlockSetter(chunkData), data.left.get(), yOffset);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Terraplusminus.instance.getAsyncGenerator().supply(data.left, data.right);
            Terraplusminus.instance.getComponentLogger().error("Chunk generation interrupted for chunk {}.",
                    chunkData, e);
        } catch (ExecutionException e) {
            Terraplusminus.instance.getAsyncGenerator().supply(data.left, data.right);
            Terraplusminus.instance.getComponentLogger().error("Unrecoverable exception when generating noise for " +
                            "chunk {}.",
                    data.right, e);
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return TerraChunkGenerator.getInstance().getCustomBiomeProvider();
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        var terraData = getTerraChunkDataAsync(new ChunkInfo(chunkX, chunkZ, yOffset, worldInfo.getName()));
        if (terraData == null || terraData.left == null) {
            // If we don't have the data yet, we can't generate the surface.
            return;
        }

        try {
            applySurface(worldInfo,
                    createBlockSetter(chunkData), terraData.left.get(), yOffset);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Terraplusminus.instance.getAsyncGenerator().supply(terraData.left, terraData.right);
            Terraplusminus.instance.getComponentLogger().error("Chunk generation interrupted for chunk {}.",
                    chunkData, e);
        } catch (ExecutionException e) {
            Terraplusminus.instance.getAsyncGenerator().supply(terraData.left, terraData.right);
            Terraplusminus.instance.getComponentLogger().error("Unrecoverable exception when generating noise for " +
                            "chunk {}.",
                    terraData.right, e);
        }
    }

    /**
     * Fetches chunk data asynchronously. This is used always except there is no workaround.
     * If the data is not in the cache, it returns null immediately and schedules the terrain application upon future completion.
     *
     * @return {@link CachedChunkData} if already cached, otherwise null.
     */
    protected static @Nullable ImmutablePair<CompletableFuture<CachedChunkData>, ChunkInfo> getTerraChunkDataAsync(ChunkInfo chunk) {
        return getTerraChunkDataAsync(chunk, false);
    }

    /**
     * Fetches chunk data asynchronously. This is used always except there is no workaround.
     * If the data is not in the cache, it returns null immediately and schedules the terrain application upon future completion.
     *
     * @return {@link CachedChunkData} if already cached, otherwise null.
     */
    protected static @Nullable ImmutablePair<CompletableFuture<CachedChunkData>, ChunkInfo> getTerraChunkDataAsync(ChunkInfo chunk,
                                                                                                boolean force) {
        AsyncGeneratorTask gen = Terraplusminus.instance.getAsyncGenerator();
        if (gen.isQueued(chunk)) {
            return null;
        }

        try {var cache = TerraChunkGenerator.getInstance().getCache();
            CompletableFuture<CachedChunkData> future = cache.getUnchecked(new ChunkPos(chunk.x, chunk.z));

            if (!force && Terraplusminus.instance.getAsyncGenerator().isEnabled() && future.get(Terraplusminus.instance.getTpmConfig().getDirectlyTimeoutMillis(),
                    TimeUnit.MILLISECONDS) == null) {
                gen.supply(future, chunk);
                return null;
            } else {
                return new ImmutablePair<>(future, chunk);
            }
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) Thread.currentThread().interrupt();
            Terraplusminus.instance.getComponentLogger().error("Unrecoverable exception when getting chunk data future for chunk {}",
                    chunk,
                    e);
            return null;
        }
    }

    /**
     * Applies the base terrain (noise) to a chunk using a {@link BlockSetter}.
     * This is the core implementation for noise generation.
     *
     * @param worldInfo The world information.
     * @param blockSetter The block setter to use for setting blocks.
     * @param terraData The real-world terrain data.
     */
    protected static void applyNoise(@NotNull WorldInfo worldInfo, BlockSetter blockSetter,
                               @NotNull CachedChunkData terraData, int yOffset) {
        int minWorldY = worldInfo.getMinHeight();
        int maxWorldY = worldInfo.getMaxHeight();

        // We start by finding the lowest 16x16x16 cube that's not underground
        int minSurfaceCubeY = blockToCube(minWorldY - yOffset);
        int maxWorldCubeY = blockToCube(maxWorldY - yOffset);
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
                int groundHeight = min(terraData.groundHeight(x, z) + yOffset, maxWorldY - 1);
                blockSetter.setRegion(x, cubeToMinBlock(minSurfaceCubeY), z, x + 1, groundHeight + 1, z + 1, STONE);

                int waterHeight = min(terraData.waterHeight(x, z) + yOffset, maxWorldY - 1);
                blockSetter.setRegion(x, groundHeight + 1, z, x + 1, waterHeight + 1, z + 1, WATER);
            }
        }
    }

    /**
     * Applies the surface decoration to a chunk using a {@link BlockSetter}.
     * This is the core implementation for surface generation.
     *
     * @param worldInfo The world information.
     * @param blockSetter The block setter to use for setting blocks.
     * @param terraData The real-world terrain data.
     */
    protected static void applySurface(@NotNull WorldInfo worldInfo, BlockSetter blockSetter,
                                       CachedChunkData terraData, int yOffset) {
        final int minWorldY = worldInfo.getMinHeight();
        final int maxWorldY = worldInfo.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int groundY = terraData.groundHeight(x, z) + yOffset;
                if (groundY < minWorldY || groundY >= maxWorldY) {
                    continue;
                }

                Material material = determineSurfaceMaterial(terraData, x, z, groundY,
                        blockSetter.getBiome(worldInfo, x, groundY, z));

                boolean isUnderWater =
                        groundY + 1 >= maxWorldY || blockSetter.getBiome(worldInfo, x, groundY + 1, z).toString().contains(
                                "OCEAN"); // A bit of a hack
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
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return chunkData.getBiome(x, y, z);
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
     * @param biome     The biome at this column.
     * @return The {@link Material} for the surface block.
     */
    private static Material determineSurfaceMaterial(@NotNull CachedChunkData terraData, int x, int z, int groundY, Biome biome) {
        BlockState state = terraData.surfaceBlock(x, z);
        if (state != null) {
            // Terra--'s OSM config says a feature should be drawn there.
            Material material = materialMapping.get(state.getBlock().toString());
            if (material != null) {
                return material;
            }
            // If we don't have a mapping, respect what the Terra-- config says.
            return BukkitBindings.getAsBlockData(state).getMaterial();
        }

        // From this point on, we are dealing with natural terrain.
        if (groundY >= 7500) { // Above 7500 blocks, we assume it's a mountain.
            return STONE; // Mountains are bare stone.
        }

        // Fallback to a generic block that matches the biome.
        if (biome == DESERT) {
            return Material.SAND;
        } else if (biome == SNOWY_SLOPES || biome == SNOWY_PLAINS || biome == FROZEN_PEAKS) {
            return SNOW_BLOCK;
        } else {
            return surfaceMaterial;
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

    @SneakyThrows
    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        int chunkX = blockToCube(x);
        int chunkZ = blockToCube(z);
        x -= cubeToMinBlock(chunkX);
        z -= cubeToMinBlock(chunkZ);
        // Must be synchronous for this method.
        CachedChunkData terraData = Objects.requireNonNull(getTerraChunkDataAsync(new ChunkInfo(chunkX,
                        chunkZ,
                        yOffset, worldInfo.getName()),
                true)).left.get();
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
        return Collections.singletonList(new TreePopulator(TerraChunkGenerator.getInstance().getCustomBiomeProvider(), yOffset));
        // TODO make this also run async / handle api outage
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

    /**
     * A functional interface to abstract block setting operations.
     * This allows the same terrain application logic to be used for both
     * initial chunk generation (writing to {@link ChunkData}) and asynchronous
     * generation (writing directly to the {@link World}).
     */
    protected interface BlockSetter {
        void setBlock(int x, int y, int z, Material material);

        void setRegion(int x, int y, int z, int endX, int endY, int endZ, Material material);

        Biome getBiome(WorldInfo info, int x, int y, int z);
    }

    public record ChunkInfo(int x, int z, int blockYOffset, String worldName) {}
}
