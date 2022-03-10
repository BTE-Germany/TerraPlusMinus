package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.data.TreeCoverBaker;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.daporkchop.lib.common.reference.ReferenceStrength;
import net.daporkchop.lib.common.reference.cache.Cached;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TreePopulator extends BlockPopulator {

    public final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;
    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    ChunkDataLoader loader = new ChunkDataLoader(bteGeneratorSettings);
    public static final Cached<byte[]> RNG_CACHE = Cached.threadLocal(() -> new byte[16 * 16], ReferenceStrength.SOFT);

    public TreePopulator() {
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(this.bteGeneratorSettings));
    }

    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull LimitedRegion limitedRegion) {
        World world = Bukkit.getWorld(worldInfo.getName());
        if(Terraplusminus.config.getBoolean("generateTrees")) {
            try {

                CachedChunkData data = this.loader.load(new ChunkPos(x, z)).get();

                byte[] treeCover = data.getCustom(EarthGeneratorPipelines.KEY_DATA_TREE_COVER, TreeCoverBaker.FALLBACK_TREE_DENSITY);
                byte[] rng = RNG_CACHE.get();

                random.nextBytes(rng);
                for (int i = 0, dx = 0; dx < 16 >> 1; dx++) {
                    for (int dz = 0; dz < 16 >> 1; dz++, i++) {
                        if ((rng[i] & 0xFF) < (treeCover[(((x * 16 + dx) & 0xF) << 4) | ((z * 16 + dz) & 0xF)] & 0xFF)) {

                            int value = random.nextInt(17) + 1;
                            int groundY = data.groundHeight(8+dx, 8+dz);
                            int waterY = data.waterHeight(8+dx, 8+dz);

                            Location loc = new Location(world, x * 16 + value, groundY + 1, z * 16 + value);
                            //System.out.println(data.waterHeight(dx, dz));

                            if (!(groundY < waterY) && groundY < 1955) {
                                limitedRegion.generateTree(loc, random, TreeType.TREE);
                            }

                        }
                    }
                }


            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
