package de.btegermany.terraplusminus.gen;

import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
;
import org.bukkit.*;
import org.bukkit.block.Block;

import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class RealWorldGenerator extends ChunkGenerator {
    private Location spawnLocation = null;


    EarthGeneratorSettings settings = EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    ChunkDataLoader loader;

    public RealWorldGenerator(){
        System.out.println(settings);
        this.loader = new ChunkDataLoader(settings);
        System.out.println("ChunkDataLoader erstellt");
    }


    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkData chunkData) {

    }


    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {


              //  loader.load(new ChunkPos(chunkX, chunkZ)).thenAccept((cachedChunkData) -> {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            chunkData.setBlock(x, 1800, z, Material.MOSS_BLOCK); //cachedChunkData.surfaceHeight(x, z)
                        }
                    }
             //   });


                // for (int y = -16; y < 32; y++) { }

    }

    public void regenerateSurface(WorldInfo worldInfo, int chunkX, int chunkZ, Player player){
        ChunkData chunk = createVanillaChunkData(player.getWorld(), chunkX, chunkZ);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlock(x, 1800, z, Material.COPPER_BLOCK);
            }
        }
    }

    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {
        // no bedrock, because bedrock bad
    }

    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {
        // no caves, because caves scary
    }

    @Nullable
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return null;
    }


    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        throw new UnsupportedOperationException("Not implemented");
    }


    @NotNull
    @Deprecated
    public ChunkGenerator.ChunkData generateChunkData(@NotNull World world, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.BiomeGrid biome) {

        throw new UnsupportedOperationException("Custom generator " + getClass().getName() + " is missing required method generateChunkData");
    }


    public boolean canSpawn(@NotNull World world, int x, int z) {
        Block highest = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);

        switch (world.getEnvironment()) {
            case NETHER:
                return true;
            case THE_END:
                return highest.getType() != Material.AIR && highest.getType() != Material.WATER && highest.getType() != Material.LAVA;
            case NORMAL:
            default:
                return highest.getType() == Material.SAND || highest.getType() == Material.GRAVEL;
        }
    }

    @NotNull
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return new ArrayList<BlockPopulator>();
    }

    @Nullable
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        if (spawnLocation == null)
            spawnLocation = new Location(world, 0, 0, 0);
        return spawnLocation;
    }

    @Deprecated
    public boolean isParallelCapable() {
        return false;
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


    @NotNull
    public ChunkGenerator.ChunkData createVanillaChunkData(@NotNull World world, int x, int z) {
        var chunk = Bukkit.getServer().createVanillaChunkData(world, x, z);
        Field maxHeightField = null;
        try {
            maxHeightField = chunk.getClass().getDeclaredField("maxHeight");
            maxHeightField.setAccessible(true);
            maxHeightField.set(chunk, 2032);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return chunk;

    }
    // Paper
}
