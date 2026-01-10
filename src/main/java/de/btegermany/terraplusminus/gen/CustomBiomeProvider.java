package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.KoppenClimateData;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.substitutes.TerraBukkit;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;


public class CustomBiomeProvider extends BiomeProvider {
    private final List<Biome> biomeList = new ArrayList<>();

    private final RealWorldGenerator generator;

    public CustomBiomeProvider(RealWorldGenerator generator) {
        //Populate the biomeList from the Paper Biome Registry,
        //as well as pre-cache the T-- Biome cache via TerraBukkit
        final Registry<Biome> biomeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);

        for(Biome biome : biomeRegistry) {
            net.buildtheearth.terraminusminus.substitutes.Biome _biome = TerraBukkit.fromBukkitBiome(biome);
            biomeList.add(TerraBukkit.toBukkitBiome(_biome));
        }

        this.generator = generator;
    }

    /**
     * Get the Biome for the location from the Terra pipeline, ex the Koppen biome filter, legacy terra filter, etc..
     * @param worldInfo The info of the world
     * @param x The X position of the block
     * @param y The Y position of the block
     * @param z The Z position of the block
     * @return The Biome at the position or the default biome
     */
    @NotNull
    @Override
    public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        if (generator.getPlugin().getConfig().getBoolean("biomes.use_dataset")) {
            //Calculate the chunk position from the block position
            final ChunkPos dataChunkPos = generator.getDataChunkPosFromBlock(x, z);
            try {
                final CachedChunkData chunkData = generator.getTerraChunkData(dataChunkPos.x(), dataChunkPos.z());
                return TerraBukkit.toBukkitBiome(chunkData.biome((x - generator.getXOffset()) & 15, (z - generator.getZOffset()) & 15));
            }catch (Exception ex) {
                generator.getPlugin().getComponentLogger().warn(
                        "Exception when generating biome at position {}/{}/{} in world {}",
                        x, y, z,
                        worldInfo.getName(),
                        ex
                );
            }
        }
        return parseDefaultBiome();
    }

    @NotNull
    @Override
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return biomeList;
    }

    public Biome parseDefaultBiome() {
        final String FALLBACK_BIOME = "minecraft:plains";

        String biomeName = generator.getPlugin().getConfig().getString("biomes.biome");
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
