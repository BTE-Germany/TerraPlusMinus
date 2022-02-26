package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.data.KoppenClimateData;
import de.btegermany.terraplusminus.data.TerraConnector;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.minecraft.world.level.biome.Biomes;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CustomBiomeProvider extends BiomeProvider {

    @NotNull
    @Override
    public Biome getBiome(@NotNull WorldInfo worldInfo, int i, int i1, int i2) {
        return Biome.FOREST;
    }

    @NotNull
    @Override
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        List<Biome> l = new ArrayList<Biome>();
        l.add(Biome.FOREST);
        return l;

    }
}
