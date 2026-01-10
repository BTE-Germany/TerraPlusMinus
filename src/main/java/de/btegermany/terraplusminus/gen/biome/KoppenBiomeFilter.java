package de.btegermany.terraplusminus.gen.biome;

import de.btegermany.terraplusminus.gen.RealWorldGeneratorPipelines;
import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.generator.ChunkBiomesBuilder;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import net.buildtheearth.terraminusminus.generator.biome.IEarthBiomeFilter;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.substitutes.TerraBukkit;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;
import org.bukkit.block.Biome;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class KoppenBiomeFilter implements IEarthBiomeFilter<double[]> {

    @Override
    public CompletableFuture<double[]> requestData(ChunkPos chunkPos, GeneratorDatasets generatorDatasets, Bounds2d bounds2d, CornerBoundingBox2d boundsGeo) throws OutOfProjectionBoundsException {
        return generatorDatasets.<IScalarDataset>getCustom(RealWorldGeneratorPipelines.KEY_DATASET_KOPPEN_CLIMATE).getAsync(boundsGeo, 16, 16);
    }

    @Override
    public void bake(ChunkPos chunkPos, ChunkBiomesBuilder chunkBiomesBuilder, double[] data) {
        net.buildtheearth.terraminusminus.substitutes.Biome[] biomes = chunkBiomesBuilder.state();

        if(data == null){
            Arrays.fill(biomes, TerraBukkit.fromBukkitBiome(Biome.OCEAN));
            return;
        }

        for(int b = 0; b < 256; b++)
            biomes[b] = TerraBukkit.fromBukkitBiome(koppenDataToBukkitBiome(data[b]));
    }

    public static Biome koppenDataToBukkitBiome(double koppenData) {
        switch ((int) koppenData) {
            case 0 -> {
                return Biome.OCEAN;
            }
            case 1, 12 -> {
                return Biome.JUNGLE;
            }
            case 2 -> {
                return Biome.BAMBOO_JUNGLE;
            }
            case 3, 11 -> {
                return Biome.SPARSE_JUNGLE;
            }
            case 4, 7, 5 -> {
                return Biome.DESERT;
            }
            case 6 -> {
                return Biome.SAVANNA;
            }
            case 8 -> {
                return Biome.PLAINS;
            }
            case 9 -> {
                return Biome.SUNFLOWER_PLAINS;
            }
            case 10 -> {
                return Biome.BEACH;
            }
            case 13 -> {
                return Biome.WINDSWEPT_GRAVELLY_HILLS;
            }
            case 14, 15 -> {
                return Biome.FLOWER_FOREST;
            }
            case 16 -> {
                return Biome.WINDSWEPT_HILLS;
            }
            case 17 -> {
                return Biome.SAVANNA_PLATEAU;
            }
            case 18 -> {
                return Biome.WOODED_BADLANDS;
            }
            case 19 -> {
                return Biome.SNOWY_TAIGA;
            }
            case 20 -> {
                return Biome.OLD_GROWTH_PINE_TAIGA;
            }
            case 21, 22 -> {
                return Biome.SWAMP;
            }
            case 23, 24 -> {
                return Biome.OLD_GROWTH_SPRUCE_TAIGA;
            }
            case 25 -> {
                return Biome.FOREST;
            }
            case 26 -> {
                return Biome.DARK_FOREST;
            }
            case 27 -> {
                return Biome.TAIGA;
            }
            case 28 -> {
                return Biome.SNOWY_SLOPES;
            }
            case 29 -> {
                return Biome.SNOWY_PLAINS;
            }
            case 30 -> {
                return Biome.ICE_SPIKES;
            }
            default -> {
                return Biome.PLAINS;
            }
        }
    }


}
