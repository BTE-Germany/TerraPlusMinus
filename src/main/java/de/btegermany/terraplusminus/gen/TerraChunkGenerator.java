package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import lombok.Getter;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TerraChunkGenerator {
    @Getter
    private final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;
    @Getter
    private final EarthGeneratorSettings settings;
    @Getter
    private final CustomBiomeProvider customBiomeProvider;
    @Getter
    private static final TerraChunkGenerator instance = new TerraChunkGenerator();

    private TerraChunkGenerator() {
        EarthGeneratorSettings settingsWithoutProj = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

        GeographicProjection projection = new OffsetProjectionTransform(
                settingsWithoutProj.projection(),
                Terraplusminus.config.getInt("terrain_offset.x"),
                Terraplusminus.config.getInt("terrain_offset.z")
        );

        this.settings = settingsWithoutProj.withProjection(projection);

        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(15L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(this.settings));

        this.customBiomeProvider = new CustomBiomeProvider(projection);
    }
}
