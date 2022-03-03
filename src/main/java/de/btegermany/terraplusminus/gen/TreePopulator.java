package de.btegermany.terraplusminus.gen;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import de.btegermany.terraplusminus.data.TerraConnector;
import io.papermc.lib.PaperLib;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.data.TreeCoverBaker;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.BlockPos;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;


import java.sql.Ref;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TreePopulator extends BlockPopulator {

    public final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;
    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    ChunkDataLoader loader = new ChunkDataLoader(bteGeneratorSettings);;



    public TreePopulator() {
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(this.bteGeneratorSettings));
    }

    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull LimitedRegion limitedRegion) {

        World world = Bukkit.getWorld(worldInfo.getName());
        TerraConnector terraConnector = new TerraConnector();
/*
        CachedChunkData terraData = null;
        try {
            terraData = this.loader.load(new ChunkPos(x,z)).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }*/
        Location location = null;

       // if(location.getChunk().isLoaded()){
           // location = new Location(world, x*16, world.getHighestBlockYAt(x*16, z*16), z*16);
       // }

/*
        try {
            //limitedRegion.generateTree(location, random, TreeType.TREE);
        }catch (Exception e){
            e.printStackTrace();
        }

        double[] treecover = null;
        try {
            treecover = terraConnector.getTreeCover(x*16,z*16).join();
            System.out.println("TreeCover: "+(int)treecover[0]+(int)treecover[1]);
            CachedChunkData data = this.cache.getUnchecked(new ChunkPos(x, z)).join();
            int groundY = data.groundHeight((int)treecover[0], (int) treecover[1]);
            System.out.println(x*16+", "+groundY+", "+z*16);
            location = new Location(world, x*16+(int)treecover[0], groundY, z*16+(int)treecover[1]);
            limitedRegion.generateTree(location, random, TreeType.TREE);
        } catch (OutOfProjectionBoundsException e) {
            e.printStackTrace();
        }

*/


        /*
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                CachedChunkData data = this.cache.getUnchecked(new ChunkPos(x + dx, z + dz)).join();
                byte[] treeCover = data.getCustom(EarthGeneratorPipelines.KEY_DATA_TREE_COVER, TreeCoverBaker.FALLBACK_TREE_DENSITY);

                if ((1 & 0xFF) < (treeCover[(((x + dx) & 0xF) << 4) | ((z + dz) & 0xF)] & 0xFF)) {
                    int groundY = terraData.groundHeight(dx, dz);
                    location = new Location(world, x*16, groundY, z*16);
                    System.out.println(x*16+", "+ location.getBlockY()+z*16);
                    limitedRegion.generateTree(new Location(world, x*16 + dx, groundY, z*16 + dz), random, TreeType.TREE);
                }


            }
        }*/
    }

}
