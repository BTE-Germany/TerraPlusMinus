package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.Terraplusminus;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.data.TreeCoverBaker;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
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
        int move = Terraplusminus.config.getInt("moveTerrain");
        if(Terraplusminus.config.getBoolean("generateTrees")) {
            try {

                CachedChunkData data = this.loader.load(new ChunkPos(x, z)).get();

                byte[] treeCover = data.getCustom(EarthGeneratorPipelines.KEY_DATA_TREE_COVER, TreeCoverBaker.FALLBACK_TREE_DENSITY);
                byte[] rng = RNG_CACHE.get();


                        for (int i = 0, dx = 0; dx < 16 >> 1; dx++) {
                            for (int dz = 0; dz < 16 >> 1; dz++, i++) {
                                if ((rng[i] & 0xFF) < (treeCover[(((x * 16 + dx) & 0xF) << 4) | ((z * 16 + dz) & 0xF)] & 0xFF)) {
                                    random.nextBytes(rng);

                                    int valueX = random.nextInt(8) + 1;
                                    int valueZ = random.nextInt(8) + 1;
                                    int groundY = 0;
                                    int waterY = 0;
                                    BlockState state = data.surfaceBlock(0,0);

                                    try{
                                        groundY = data.groundHeight(valueX + dx, valueZ + dz);
                                        waterY = data.waterHeight(valueX + dx, valueZ + dz);
                                        state = data.surfaceBlock(valueX+dx, valueZ+dz);
                                    }catch (IndexOutOfBoundsException e){
                                        e.printStackTrace();
                                    }

                                    if (groundY < waterY) { return; }

                                    Location loc = new Location(world, valueX+dx + x * 16 , groundY+1+move , valueZ+dz + z * 16);

                                    if (!(groundY < waterY) && groundY < world.getMaxHeight()-12 && state == null) {

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
