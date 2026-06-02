package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.tree.TreePopulator;
import de.btegermany.terraplusminus.utils.Properties;
import lombok.Getter;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.http.Http;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static java.util.Collections.singletonList;
import static net.buildtheearth.terraminusminus.substitutes.ChunkPos.blockToCube;
import static net.buildtheearth.terraminusminus.substitutes.ChunkPos.cubeToMinBlock;
import static org.bukkit.Material.*;
import static org.bukkit.block.Biome.*;

/**
 * A world generator using Terra-- as the generation engine.
 * It is opinionated and optimized for BTE creative building
 * (very bland terrain with no features at all).
 */
public class RealWorldGenerator extends ChunkGenerator {

    @Getter
    private final EarthGeneratorSettings settings;
    @Getter
    private final int yOffset;

    private static final int CHUNK_DATA_MAX_ATTEMPTS = 3;
    private static final long CHUNK_DATA_RETRY_DELAY_MILLIS = 500L;
    private static final int PRIME_CACHE_CONCURRENCY = 4;

    private final Terraplusminus plugin;
    private final LoadingCache<@NotNull ChunkPos, @NotNull CompletableFuture<CachedChunkData>> cache;
    private final CustomBiomeProvider customBiomeProvider;
    private final List<BlockPopulator> defaultPopulators;


    private final BlockData defaultSurfaceBlock;
    private final BlockData mountainSurfaceBlock = STONE.createBlockData();
    private final BlockData underwaterBlock = DIRT.createBlockData();
    private final Map<Biome, BlockData> defaultBiomeSurfaceBlocks = Map.of(
            DESERT, SAND.createBlockData(),
            SNOWY_SLOPES, SNOW_BLOCK.createBlockData(),
            SNOWY_PLAINS, SNOW_BLOCK.createBlockData(),
            FROZEN_PEAKS, SNOW_BLOCK.createBlockData()
    );
    private final BlockMapper blockMapper;

    private static final Set<Material> GRASS_LIKE_MATERIALS = Set.of(
            GRASS_BLOCK,
            DIRT_PATH,
            FARMLAND,
            MYCELIUM,
            SNOW
    );

    public RealWorldGenerator(int yOffset, Terraplusminus plugin) {
        this.plugin = plugin;

        Http.configChanged(); // This ensures the T-- default config is loaded regarding the number of concurrent http requests for specific urls.

        EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

        GeographicProjection projection = new OffsetProjectionTransform(
                settings.projection(),
                plugin.getConfig().getInt(Properties.X_OFFSET),
                plugin.getConfig().getInt(Properties.Z_OFFSET)
        );
        if (yOffset == 0) {
            this.yOffset = plugin.getConfig().getInt(Properties.Y_OFFSET);
        } else {
            this.yOffset = yOffset;
        }

        this.settings = settings.withProjection(projection);

        this.customBiomeProvider = new CustomBiomeProvider(projection);
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(this.settings));
        this.defaultPopulators = singletonList(new TreePopulator(this.customBiomeProvider, this.yOffset, this::getChunkDataAsync));

        // This code is explicitly there for backward compatibility and is legitimate in using the deprecated config keys
        this.blockMapper = BlockMapper.fromPlugin(plugin)
                .withStaticGenericSurface(GRASS_BLOCK)
                .withConfiguredGenericSurface(Properties.SURFACE_MATERIAL)  // Overrides the static definition if present
                .withConfiguredMapping("minecraft:bricks", Properties.BUILDING_OUTLINES_MATERIAL)
                .withConfiguredMapping("minecraft:gray_concrete", Properties.ROAD_MATERIAL)
                .withConfiguredMapping("minecraft:dirt_path", Properties.PATH_MATERIAL)
                .build();
        this.defaultSurfaceBlock = this.blockMapper.genericSurfaceBlock();
    }


    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);

        int minWorldY = worldInfo.getMinHeight();
        int maxWorldY = worldInfo.getMaxHeight();

        // Optimization: if the entire chunk is above the surface, there is nothing to do
        int minSurfaceCubeY = blockToCube(minWorldY - this.yOffset);
        if (terraData.aboveSurface(minSurfaceCubeY)) {
            return;
        }

        // And now, we build the actual terrain shape
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int groundHeight = min(terraData.groundHeight(x, z) + this.yOffset, maxWorldY - 1);
                int waterHeight = min(terraData.waterHeight(x, z) + this.yOffset, maxWorldY - 1);
                chunkData.setRegion(
                        x, minWorldY, z,
                        x + 1, groundHeight + 1, z + 1,
                        STONE
                );
                chunkData.setRegion(
                        x, groundHeight + 1, z,
                        x + 1, waterHeight + 1, z + 1,
                        WATER
                );
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return this.customBiomeProvider;
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);
        final int minWorldY = worldInfo.getMinHeight();
        final int maxWorldY = worldInfo.getMaxHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                int groundY = terraData.groundHeight(x, z) + this.yOffset;

                if (groundY < minWorldY || groundY >= maxWorldY) {
                    continue; // We are not within vertical bounds, continue
                }

                BlockData surfaceBlock = this.blockMapper.map(terraData.surfaceBlock(x, z));
                if (surfaceBlock == null) {
                    // We do that for each column, so it does not depend on the configuration but only on the seed
                    int startMountainHeight = random.nextInt(7500, 7520);
                    if (groundY >= startMountainHeight) {
                        surfaceBlock = this.mountainSurfaceBlock; // Mountains stay bare
                    } else {
                        // Fallback to a generic block that matches the biome, or to the default block
                        Biome biome = chunkData.getBiome(x, groundY, z);
                        surfaceBlock = this.defaultBiomeSurfaceBlocks.getOrDefault(biome, this.defaultSurfaceBlock);
                    }
                }

                // We don't want grass, snow, and all that underwater
                boolean isUnderWater = groundY + 1 >= maxWorldY || chunkData.getBlockData(x, groundY + 1, z).getMaterial().equals(WATER);
                if (isUnderWater && GRASS_LIKE_MATERIALS.contains(surfaceBlock.getMaterial())) {
                    surfaceBlock = this.underwaterBlock;
                }

                chunkData.setBlock(x, groundY, z, surfaceBlock);

            }
        }
    }

    private CachedChunkData getTerraChunkData(int chunkX, int chunkZ) {
        try {
            return this.getChunkDataAsync(new ChunkPos(chunkX, chunkZ)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted when generating chunk data asynchronously in Terra--", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Unrecoverable exception when generating chunk data asynchronously in Terra--", e);
        }
    }

    /**
     * Gets Chunk Data async with the supplied chunk coordinates.
     *
     * @param chunkX The Chunk X coordinate
     * @param chunkZ The Chunk Z coordinate
     * @return A CompletableFuture containing the CachedChunkData
     */
    public CompletableFuture<CachedChunkData> getBaseHeightAsync(int chunkX, int chunkZ) {
        return this.getChunkDataAsync(new ChunkPos(chunkX, chunkZ));
    }

    /**
     * Starts loading Terra-- data for a square chunk radius around a target chunk.
     * The returned future completes when all requested chunk data is present in the cache.
     */
    public CompletableFuture<Void> primeCache(int centerChunkX, int centerChunkZ, int radiusChunks) {
        int radius = Math.max(0, radiusChunks);
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        List<ChunkPos> batch = new ArrayList<>(PRIME_CACHE_CONCURRENCY);
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                batch.add(new ChunkPos(chunkX, chunkZ));
                if (batch.size() >= PRIME_CACHE_CONCURRENCY) {
                    result = this.appendPrimeBatch(result, batch);
                    batch = new ArrayList<>(PRIME_CACHE_CONCURRENCY);
                }
            }
        }
        if (!batch.isEmpty()) {
            result = this.appendPrimeBatch(result, batch);
        }
        return result;
    }

    private CompletableFuture<Void> appendPrimeBatch(CompletableFuture<Void> previous, List<ChunkPos> batch) {
        List<ChunkPos> chunkBatch = List.copyOf(batch);
        return previous.thenCompose(unused -> CompletableFuture.allOf(
                chunkBatch.stream()
                        .map(chunkPos -> this.getChunkDataAsync(chunkPos).thenApply(data -> null))
                        .toArray(CompletableFuture[]::new)
        ));
    }

    private CompletableFuture<CachedChunkData> getChunkDataAsync(ChunkPos chunkPos) {
        return this.getChunkDataAsync(chunkPos, 1);
    }

    private CompletableFuture<CachedChunkData> getChunkDataAsync(ChunkPos chunkPos, int attempt) {
        CompletableFuture<CachedChunkData> future;
        try {
            future = this.cache.getUnchecked(chunkPos);
        } catch (RuntimeException e) {
            return this.retryChunkData(chunkPos, attempt, e);
        }

        return future.handle((data, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.completedFuture(data);
            }
            return this.retryChunkData(chunkPos, attempt, throwable);
        }).thenCompose(result -> result);
    }

    private CompletableFuture<CachedChunkData> retryChunkData(ChunkPos chunkPos, int attempt, Throwable throwable) {
        this.cache.invalidate(chunkPos);
        if (attempt >= CHUNK_DATA_MAX_ATTEMPTS) {
            return CompletableFuture.failedFuture(throwable);
        }

        Throwable rootCause = unwrap(throwable);
        this.plugin.getComponentLogger().warn(
                "Terra-- chunk data load failed for chunk {}/{} on attempt {}/{}. Retrying in {} ms.",
                chunkPos.x(),
                chunkPos.z(),
                attempt,
                CHUNK_DATA_MAX_ATTEMPTS,
                CHUNK_DATA_RETRY_DELAY_MILLIS,
                rootCause
        );

        return CompletableFuture
                .supplyAsync(
                        () -> null,
                        CompletableFuture.delayedExecutor(CHUNK_DATA_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                )
                .thenCompose(unused -> this.getChunkDataAsync(chunkPos, attempt + 1));
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        int chunkX = blockToCube(x);
        int chunkZ = blockToCube(z);
        x -= cubeToMinBlock(chunkX);
        z -= cubeToMinBlock(chunkZ);
        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);
        switch (heightMap) {
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> {
                return terraData.groundHeight(x, z) + this.yOffset;
            }
            default -> {
                return terraData.surfaceHeight(x, z) + this.yOffset;
            }
        }
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        Block highest = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);

        return switch (world.getEnvironment()) {
            case NETHER -> true;
            case THE_END ->
                    highest.getType() != Material.AIR && highest.getType() != WATER && highest.getType() != Material.LAVA;
            default -> highest.getType() == Material.SAND || highest.getType() == Material.GRAVEL;
        };
    }

    @Override
    @NotNull
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return this.defaultPopulators;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Nullable
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 3517417, 58, -5288234);
    }

}
