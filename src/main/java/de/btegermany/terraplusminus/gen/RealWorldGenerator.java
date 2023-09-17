package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.tree.TreePopulator;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.BukkitBindings;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;
import static net.buildtheearth.terraminusminus.substitutes.ChunkPos.*;


public class RealWorldGenerator extends ChunkGenerator {
    private Location spawnLocation = null;

    public LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;
    private final CustomBiomeProvider customBiomeProvider;

    private final int yOffset;

    private final Material houses, streets, paths, surface;

    public RealWorldGenerator() {

        EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

        GeographicProjection projection = new OffsetProjectionTransform(
                settings.projection(),
                Terraplusminus.config.getInt("terrain_offset.x"),
                Terraplusminus.config.getInt("terrain_offset.z")
        );
        this.yOffset = Terraplusminus.config.getInt("terrain_offset.y");

        settings = settings.withProjection(projection);

        this.customBiomeProvider = new CustomBiomeProvider();
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(settings));

        houses = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("building_outlines_material"), "BRICKS"));
        streets = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("road_material"), "GRAY_CONCRETE_POWDER"));
        paths = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("path_material"), "MOSS_BLOCK"));
        surface = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("surface_material"), "GRASS_BLOCK"));

    }


    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {

        CachedChunkData terraData = this.getTerraChunkData(chunkX, chunkZ);

        int minWorldY = worldInfo.getMinHeight();
        int maxWorldY = worldInfo.getMaxHeight();

        // We start by finding the lowest 16x16x16 cube that's not underground
        //TODO expose the minimum surface Y in Terra-- so we don't have to scan this way
        int minSurfaceCubeY = blockToCube(minWorldY - this.yOffset);
        int maxWorldCubeY = blockToCube(maxWorldY);
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
                    Material.STONE
            );
            return; // All done, everything is underground
        } else {
            chunkData.setRegion(
                    0, minWorldY, 0,
                    0, cubeToMinBlock(minSurfaceCubeY), 0,
                    Material.STONE
            );
        }

        // And now, we build the actual terrain shape on top of everything
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int groundHeight = min(terraData.groundHeight(x, z) + this.yOffset, maxWorldY - 1);
                int waterHeight = min(terraData.waterHeight(x, z) + this.yOffset, maxWorldY - 1);
                chunkData.setRegion(
                        x, minWorldY, z,
                        x + 1, groundHeight + 1, z +1,
                        Material.STONE
                );
                chunkData.setRegion(
                        x, groundHeight + 1, z,
                        x + 1, waterHeight + 1, z +1,
                        Material.WATER
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
        final int minY = worldInfo.getMinHeight();
        final int maxY = worldInfo.getMaxHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Material material = surface;

                int groundY = terraData.groundHeight(x, z);
                int waterY = terraData.waterHeight(x, z);
                BlockState state = terraData.surfaceBlock(x, z);

                // Sets block on mountains over 1700m to stone
                int randomizer = (int) Math.floor(Math.random() * (1700 - 1695 + 1) + 1695);
                if (groundY >= randomizer) {
                    material = Material.STONE;
                }
                //--------------------------------------------------------

                //Generates sand in deserts and snow on mountains
                material = switch ((int) customBiomeProvider.getBiome()) {
                    case 4 -> Material.SAND;
                    case 28, 29, 30 -> Material.SNOW_BLOCK;
                    default -> material;
                };

                //Genrates terrain with block states
                if (groundY + yOffset < maxY) {
                    if (state != null) {
                        BlockData blockData = BukkitBindings.getAsBlockData(state);
                        if (blockData != null) {
                            //System.out.println(state.getBlock().toString());
                            switch (state.getBlock().toString()) {
                                case "minecraft:gray_concrete" ->
                                        chunkData.setBlock(x, groundY + yOffset, z, streets);
                                case "minecraft:dirt_path" -> chunkData.setBlock(x, groundY + yOffset, z, paths);
                                case "minecraft:bricks" -> chunkData.setBlock(x, groundY + yOffset, z, houses);
                                default ->
                                        chunkData.setBlock(x, groundY + yOffset, z, BukkitBindings.getAsBlockData(state));
                            }
                        } else {
                            chunkData.setBlock(x, groundY + yOffset, z, material);
                        }
                    } else {
                        chunkData.setBlock(x, groundY + yOffset, z, material);
                    }

                }
                for (int y = groundY + yOffset + 1; y <= min(maxY, waterY + yOffset); y++) {
                    chunkData.setBlock(x, y, z, Material.WATER);
                }
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

    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {
        // no bedrock, because bedrock bad
    }

    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {
        // no caves, because caves scary
    }


    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public boolean canSpawn(@NotNull World world, int x, int z) {
        Block highest = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);

        return switch (world.getEnvironment()) {
            case NETHER -> true;
            case THE_END ->
                    highest.getType() != Material.AIR && highest.getType() != Material.WATER && highest.getType() != Material.LAVA;
            default -> highest.getType() == Material.SAND || highest.getType() == Material.GRAVEL;
        };
    }

    @NotNull
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return Collections.singletonList(new TreePopulator(customBiomeProvider));
    }

    @Nullable
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        if (spawnLocation == null)
            spawnLocation = new Location(world, 3517417, 58, -5288234);
        return spawnLocation;
    }

    public boolean shouldGenerateNoise() {
        return false;
    }


    public boolean shouldGenerateSurface() {
        return false;
    }


    public boolean shouldGenerateBedrock() {
        return false;
    }


    public boolean shouldGenerateCaves() {
        return false;
    }


    public boolean shouldGenerateDecorations() {
        return false;
    }


    public boolean shouldGenerateMobs() {
        return false;
    }

    public boolean shouldGenerateStructures() {
        return false;
    }
}
