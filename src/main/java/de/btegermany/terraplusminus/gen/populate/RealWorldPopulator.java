package de.btegermany.terraplusminus.gen.populate;

import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import lombok.RequiredArgsConstructor;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.logging.Level;

@RequiredArgsConstructor
public abstract class RealWorldPopulator extends BlockPopulator {
    private final RealWorldGenerator worldGenerator;

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
        final ChunkPos dataChunkPos = worldGenerator.getDataChunkPosFromChunk(chunkX, chunkZ);

        try {
            final CachedChunkData chunkData = worldGenerator.getTerraChunkData(dataChunkPos.x(), dataChunkPos.z());
            if(chunkData != null)
                populate(worldInfo, random,  chunkX, chunkZ, worldGenerator.getXOffset(), worldGenerator.getZOffset(), worldGenerator.getYOffset(), limitedRegion, chunkData, worldGenerator);
        }catch (Exception ex){
            worldGenerator.getPlugin().getComponentLogger().warn(
                    "Unrecoverable error while running RealWorldPopulator#populate for chunk x:{}, z: {} in world {}",
                    chunkX, chunkZ,
                    worldInfo.getName(),
                    ex
            );
        }
    }

    public abstract void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, @NotNull int chunkX, @NotNull int chunkZ, @NotNull int xOffset, @NotNull int zOffset, @NotNull int yOffset, @NotNull LimitedRegion limitedRegion, @NotNull CachedChunkData chunkData, @NotNull RealWorldGenerator worldGenerator);
}
