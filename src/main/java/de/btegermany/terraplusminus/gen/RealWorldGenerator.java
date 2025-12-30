package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.tree.TreePopulator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.Properties;
import lombok.Getter;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.http.Http;
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
import static net.buildtheearth.terraminusminus.substitutes.TerraBukkit.toBukkitBlockData;
import static org.bukkit.Material.*;
import static org.bukkit.block.Biome.*;

public class RealWorldGenerator extends ChunkGenerator {

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

    public RealWorldGenerator(int yOffset, Terraplusminus plugin) {

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

        this.surfaceMaterial = ConfigurationHelper.getMaterial(plugin.getConfig(), Properties.SURFACE_MATERIAL, GRASS_BLOCK);
        this.materialMapping = Map.of(
                "minecraft:bricks", ConfigurationHelper.getMaterial(plugin.getConfig(), Properties.BUILDING_OUTLINES_MATERIAL, BRICKS),
                "minecraft:gray_concrete", ConfigurationHelper.getMaterial(plugin.getConfig(), Properties.ROAD_MATERIAL, GRAY_CONCRETE_POWDER),
                "minecraft:dirt_path", ConfigurationHelper.getMaterial(plugin.getConfig(), Properties.PATH_MATERIAL, MOSS_BLOCK)
        );

    }


    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {

        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);

        int minWorldY = worldInfo.getMinHeight();
        int maxWorldY = worldInfo.getMaxHeight();

        // We start by finding the lowest 16x16x16 cube that's not underground
        //TODO expose the minimum surface Y in Terra-- so we don't have to scan this way
        int minSurfaceCubeY = blockToCube(minWorldY - this.yOffset);
        int maxWorldCubeY = blockToCube(maxWorldY - this.yOffset);
        if (terraData.aboveSurface(minSurfaceCubeY)) {
            return; // All done, it's all air
        }
        while (minSurfaceCubeY < maxWorldCubeY && terraData.belowSurface(minSurfaceCubeY)) {
            minSurfaceCubeY++;
        }

        // We can now fill most of the underground in a single call.
        // Hopefully the underlying implementation can take advantage of that...
        if (minSurfaceCubeY >= maxWorldCubeY) {
            chunkData.setRegion(
                    0, minWorldY, 0,
                    16, maxWorldY, 16,
                    STONE
            );
            return; // All done, everything is underground
        } else {
            chunkData.setRegion(
                    0, minWorldY, 0,
                    0, cubeToMinBlock(minSurfaceCubeY), 0,
                    STONE
            );
        }

        // And now, we build the actual terrain shape on top of everything
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

                // We do that for each column, so it does not depend on the configuration but only on the seed
                int startMountainHeight = random.nextInt(7500, 7520);

                if (groundY < minWorldY || groundY >= maxWorldY) {
                    continue; // We are not within vertical bounds, continue
                }

                Material material;

                BlockState state = terraData.surfaceBlock(x, z);
                if (state != null) {
                    // Terra--'s OSM config says a feature should be drawn there, let's transform it to respect our config
                    material = this.materialMapping.get(state.getBlock().toString());
                    if (material == null) {
                        // We don't know what material this is, let's respect what the Terra-- configuration says
                        material = toBukkitBlockData(state).getMaterial();
                    }
                } else if (groundY >= startMountainHeight) {
                    material = STONE; // Mountains stare bare
                } else {
                    // Fallback to a generic block that matches the biome
                    Biome biome = chunkData.getBiome(x, groundY, z);
                    if (biome == DESERT) {
                        material = Material.SAND;

                    } else if (biome == SNOWY_SLOPES || biome == SNOWY_PLAINS || biome == FROZEN_PEAKS){
                        material = SNOW_BLOCK;

                    } else {
                        material = this.surfaceMaterial;
                    }
                }

                // We don't want grass, snow, and all underwater
                boolean isUnderWater = groundY + 1 >= maxWorldY || chunkData.getBlockData(x, groundY + 1, z).getMaterial().equals(WATER);
                if (isUnderWater && GRASS_LIKE_MATERIALS.contains(material)) {
                    material = DIRT;
                }

                chunkData.setBlock(x, groundY, z, material);

            }
        }
    }

    private CachedChunkData getTerraChunkData(int chunkX, int chunkZ) {
        try {
            return this.cache.getUnchecked(new ChunkPos(chunkX, chunkZ)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Unrecoverable exception when generating chunk data asynchronously in Terra--", e);
        }
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

    public CompletableFuture<CachedChunkData> getBaseHeightAsync(int x, int z) {
        int chunkX = blockToCube(x);
        int chunkZ = blockToCube(z);
        return this.cache.getUnchecked(new ChunkPos(chunkX, chunkZ));
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
        return Collections.singletonList(new TreePopulator(customBiomeProvider, yOffset));
    }

    @Override
    @Nullable
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        if (spawnLocation == null)
            spawnLocation = new Location(world, 3517417, 58, -5288234);
        return spawnLocation;
    }
}
