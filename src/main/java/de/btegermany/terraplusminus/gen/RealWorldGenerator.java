package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.tree.TreePopulator;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
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
import java.util.concurrent.TimeoutException;


public class RealWorldGenerator extends ChunkGenerator {
    private Location spawnLocation = null;

    EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    public LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;
    private final CustomBiomeProvider customBiomeProvider;

    int xOffset, yOffset, zOffset;

    private final Material houses, streets, paths, surface;

    public RealWorldGenerator() {
        this.customBiomeProvider = new CustomBiomeProvider();
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(settings));

        houses = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("building_outlines_material"), "BRICKS"));
        streets = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("road_material"), "GRAY_CONCRETE_POWDER"));
        paths = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("path_material"), "MOSS_BLOCK"));
        surface = Material.getMaterial(Objects.requireNonNullElse(Terraplusminus.config.getString("surface_material"), "GRASS_BLOCK"));

        xOffset = Terraplusminus.config.getInt("terrain_offset.x");
        yOffset = Terraplusminus.config.getInt("terrain_offset.y");
        zOffset = Terraplusminus.config.getInt("terrain_offset.z");
    }


    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkData chunkData) {

    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return this.customBiomeProvider;
    }

    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        CompletableFuture<CachedChunkData> future = this.cache.getUnchecked(new ChunkPos(chunkX - (xOffset / 16), chunkZ - (zOffset / 16)));
        generateSurface(worldInfo, future, chunkData, yOffset);
    }

    private void generateSurface(@NotNull WorldInfo worldInfo, CompletableFuture<CachedChunkData> future, @NotNull ChunkData chunkData, int yOffset) {
        final int minY = worldInfo.getMinHeight();
        final int maxY = worldInfo.getMaxHeight();
        Material material = surface;

        try {

            CachedChunkData terraData = future.get(5L, TimeUnit.MINUTES);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {

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

                    //Generates stone under all surfaces
                    //Get the surface height.
                    int sY = Math.min(maxY, groundY + yOffset);
                    //If the surface height is at or below the minimum height, then there is no reason to run this.
                    if (minY < sY) {
                        //Set the column to stone.
                        chunkData.setRegion(x, minY, z, x + 1, sY, z + 1, Material.STONE);
                    }

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
                    for (int y = groundY + yOffset + 1; y <= Math.min(maxY, waterY + yOffset); y++) {
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                }
            }

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
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
