package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.KoppenClimateData;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.kyori.adventure.key.Key;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CustomBiomeProvider extends BiomeProvider {

    private final KoppenClimateData climateData = new KoppenClimateData();
    double biomeData;

    public static List<Biome> biomeList = new ArrayList<>(Arrays.asList(Biome.OCEAN, Biome.JUNGLE, Biome.BAMBOO_JUNGLE, Biome.SPARSE_JUNGLE, Biome.SAVANNA, Biome.DESERT, Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.BEACH, Biome.WINDSWEPT_GRAVELLY_HILLS,
            Biome.FLOWER_FOREST, Biome.STONY_PEAKS, Biome.SAVANNA_PLATEAU, Biome.WOODED_BADLANDS, Biome.SNOWY_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.SWAMP, Biome.OLD_GROWTH_PINE_TAIGA, Biome.FOREST, Biome.DARK_FOREST,
            Biome.TAIGA, Biome.FROZEN_PEAKS, Biome.SNOWY_PLAINS, Biome.ICE_SPIKES));

    private GeographicProjection projection;

    public CustomBiomeProvider(GeographicProjection projection) {
        this.projection = projection;
    }

    @NotNull
    @Override
    public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        if (Terraplusminus.config.getBoolean("biomes.use_dataset")) {
            double[] coords;
            try {
                coords = this.projection.toGeo(x, z);
            } catch (OutOfProjectionBoundsException ignored) {
                return Biome.PLAINS;
            }
            try {
                biomeData = this.climateData.getAsync(coords[0], coords[1]).get();
                return koppenDataToBukkitBiome(biomeData);
            } catch (InterruptedException | ExecutionException | OutOfProjectionBoundsException e) {
                e.printStackTrace();

            }
        } else biomeData = 8; // Default is plains for tree generation
        return parseDefaultBiome();
    }

    public double getBiome() {
        return biomeData;
    }

    @NotNull
    @Override
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return biomeList;
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

    public static Biome parseDefaultBiome() {
        final String FALLBACK_BIOME = "minecraft:plains";

        String biomeName = Terraplusminus.config.getString("biomes.biome");
        if (biomeName == null || biomeName.isBlank()) {
            biomeName = FALLBACK_BIOME;
        } else {
            biomeName = biomeName.toLowerCase();
            if (!biomeName.contains(":")) {
                biomeName = "minecraft:" + biomeName;
            }
        }

        var biomeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        return biomeRegistry.get(Key.key(biomeName));
    }
}
