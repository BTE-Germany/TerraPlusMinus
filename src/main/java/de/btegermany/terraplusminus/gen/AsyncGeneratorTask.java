package de.btegermany.terraplusminus.gen;

import com.fastasyncworldedit.core.FaweAPI;
import com.fastasyncworldedit.core.queue.IQueueChunk;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import de.btegermany.terraplusminus.Terraplusminus;
import io.papermc.paper.util.Tick;
import lombok.Getter;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class AsyncGeneratorTask implements Runnable {
    // Track how many times we’ve retried a given chunk
    private final Map<RealWorldGenerator.ChunkInfo, AtomicInteger> retryCounts = new ConcurrentHashMap<>();

    private static final Duration[] RETRY_DELAYS_TICKS = new Duration[]{
            Duration.ofSeconds(1),
            Duration.ofSeconds(15),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(4),
    };

    @Getter
    private boolean enabled;
    private Gson gson = null;
    private File chunksToGenerateFile = null;
    private Map<RealWorldGenerator.ChunkInfo, CompletableFuture<CachedChunkData>> chunksToProcess = null;
    private boolean haveLoadedEverything = false;
    private boolean isRunning = false;
    private int chunkBatchSize;

    public AsyncGeneratorTask(Terraplusminus i) {
        enabled = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit") && i.getTpmConfig().isAsyncGenerationEnabled();
        if (!enabled) {
            i.getComponentLogger().warn("FastAsyncWorldEdit is not enabled, the generator will " +
                    "not run async.");
            return;
        }

        this.chunksToProcess = new ConcurrentHashMap<>();
        chunkBatchSize = i.getTpmConfig().getChunkBatchSize();

        Bukkit.getScheduler().runTaskAsynchronously(i, () -> {
            this.chunksToGenerateFile = new File(i.getDataFolder(), "chunksToGenerate.json");
            this.gson = new Gson();
            var chunksToGenerate = loadChunksToGenerate();

            if (chunksToGenerate != null) {
                i.getComponentLogger().info("Queueing generation for {} previously unfinished " +
                        "chunks...", chunksToGenerate.size());
                for (RealWorldGenerator.ChunkInfo chunk : chunksToGenerate) {
                    if (chunksToProcess.containsKey(chunk)) continue;
                    var data = RealWorldGenerator.getTerraChunkDataAsync(chunk);
                    if (data == null || data.left == null) {
                        // If we don't have the data yet, it will be generated asynchronously.
                        continue;
                    }
                    chunksToProcess.put(chunk, data.left);
                }
            }
        });

        Bukkit.getScheduler().runTaskTimerAsynchronously(i, this, 20L, 20L * i.getTpmConfig().getGenerationTimerSeconds());
    }

    public void shutdown() {
        enabled = false;
        // Stops all ongoing futures and clears the queue.
        if (chunksToProcess == null) return;
        chunksToProcess.values().forEach(future -> future.cancel(true));
        chunksToProcess.clear();
    }

    private @Nullable Set<RealWorldGenerator.ChunkInfo> loadChunksToGenerate() {
        if (chunksToGenerateFile.exists()) {
            try (Reader reader = new FileReader(chunksToGenerateFile)) {
                Type setType = new TypeToken<Set<RealWorldGenerator.ChunkInfo>>() {}.getType();
                Set<RealWorldGenerator.ChunkInfo> loaded = gson.fromJson(reader, setType);
                if (!loaded.isEmpty()) {
                    Terraplusminus.instance.getComponentLogger().info("Found {} chunks to generate from previous runs.", loaded.size());
                    haveLoadedEverything = true; // We have loaded all chunks from the file
                    return loaded;
                }
            } catch (IOException e) {
                Terraplusminus.instance.getLogger().log(Level.SEVERE, "Could not load chunksToGenerate.json", e);
            }
        }
        return null;
    }

    private void saveChunksToGenerate() {
        if (chunksToProcess == null || chunksToProcess.isEmpty()) {
            return;
        }

        try (Writer writer = new FileWriter(chunksToGenerateFile)) {
            gson.toJson(chunksToProcess.keySet(), writer);
        } catch (IOException e) {
            Terraplusminus.instance.getComponentLogger().error("Could not save chunksToGenerate.json.", e);
        }
    }

    public void supply(CompletableFuture<CachedChunkData> future, RealWorldGenerator.ChunkInfo chunk) {
        if (!enabled) return;

        if (chunksToProcess.containsKey(chunk)) {
            Terraplusminus.instance.getComponentLogger().debug("Chunk {} is already in the queue, skipping.",
                    chunk);
            return;
        }

        chunksToProcess.put(chunk, future);
        if (haveLoadedEverything) saveChunksToGenerate();
    }

    public boolean isQueued(RealWorldGenerator.ChunkInfo chunk) {
        return chunksToProcess.containsKey(chunk);
    }


    /**
     * Callback executed when the asynchronous fetch of {@link CachedChunkData} completes.
     *
     * @param terraData The loaded chunk data, or null on failure.
     * @param chunk     The ChunkInfo object containing chunk metadata.
     */
    private void onTerraDataLoaded(CachedChunkData terraData, RealWorldGenerator.@NotNull ChunkInfo chunk, IQueueExtent<IQueueChunk> editSession) {
            // Success: apply terrain to the world directly.
            World world = Bukkit.getWorld(chunk.worldName());
            if (world == null) {
                Terraplusminus.instance.getComponentLogger().error("World {} not found for " +
                        "applying async terrain for chunk {}, {}.", chunk.worldName(), chunk.x(), chunk.z());
                return;
            }

            // We need a ChunkData-like interface to set blocks. Since we are outside the main generation pipeline,
            // we have to set blocks directly in the world. This is slower but necessary
            try {
                Random random = new Random(world.getSeed() + chunk.x() + chunk.z()); // Not a perfect seed,
                // but
                // sufficient
                // for this purpose.
                RealWorldGenerator.BlockSetter blockSetter = createBlockSetter(editSession, chunk.x(), chunk.z());
                RealWorldGenerator.applyNoise(world, blockSetter, terraData, chunk.blockYOffset());
                RealWorldGenerator.applySurface(world, random, blockSetter, terraData, chunk.blockYOffset());
            } catch (Exception e) {
                Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk {}",
                        chunk,
                        e);
            }
            retryCounts.remove(chunk);

    }

    /**
     * Handles failures in asynchronously loading chunk data. Implements a retry mechanism with back-off.
     *
     * @param ex        The exception that occurred.
     * @param chunk     The ChunkInfo object containing chunk metadata.
     */
    private void handleTerraDataLoadFailure(@NotNull Throwable ex, RealWorldGenerator.ChunkInfo chunk) {
        if (ex.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            Terraplusminus.instance.getComponentLogger().error("Chunk data load for {} was interrupted.",
                    chunk, ex);
            return;
        }

        int attempts = retryCounts.computeIfAbsent(chunk, k -> new AtomicInteger(0)).incrementAndGet();

        if (attempts <= RETRY_DELAYS_TICKS.length) {
            long delay = Tick.tick().fromDuration(RETRY_DELAYS_TICKS[attempts - 1]);
            Terraplusminus.instance.getComponentLogger().warn(
                    "Failed to load chunk {} (attempt {} of {}), retrying in {}.",
                    chunk, attempts, RETRY_DELAYS_TICKS.length, delay / 20.0, ex
            );

            // Schedule a delayed retry.
            Bukkit.getScheduler().runTaskLaterAsynchronously(Terraplusminus.instance, () -> {
                // Retry loading the chunk data
                var data = RealWorldGenerator.getTerraChunkDataAsync(chunk);
                if (data == null || data.left == null) {
                    // If we don't have the data yet, it will be generated asynchronously.
                    return;
                }
                chunksToProcess.put(chunk, data.left);
            }, delay);
        } else {
            Terraplusminus.instance.getComponentLogger().error("Failed to load chunk {} after {} attempts. Giving up.",
                    chunk,
                    attempts,
                    ex);
        }
    }

    /**
     * Creates a {@link RealWorldGenerator.BlockSetter} for writing directly to a {@link World}.
     *
     * @param chunkX The chunk's X coordinate.
     * @param chunkZ The chunk's Z coordinate.
     * @return A new BlockSetter instance.
     */
    private RealWorldGenerator.@NotNull BlockSetter createBlockSetter(IQueueExtent<IQueueChunk> session, int chunkX, int chunkZ) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        return new RealWorldGenerator.BlockSetter() {
            @Override
            public void setBlock(int x, int y, int z, @NotNull Material material) {
                session.setBlock(baseX + x, y, baseZ + z, BukkitAdapter.adapt(material.createBlockData()));
            }

            @Override
            public void setRegion(int x, int y, int z, int endX, int endY, int endZ, Material material) {
                CuboidRegion region = new CuboidRegion(BlockVector3.at(x,y,z), BlockVector3.at(endX,endY,endZ));
                session.setBlocks((Region) region, BukkitAdapter.adapt(material.createBlockData()));
            }

            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return TerraChunkGenerator.getInstance().getCustomBiomeProvider().getBiome(info, x, y, z);
            }
        };
    }

    @Override
    public void run() {
        if (isRunning) {
            if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
                Terraplusminus.instance.getComponentLogger().debug("Async generator task is already running, skipping this run.");
            }
            return;
        }
        isRunning = true;
        saveChunksToGenerate();
        String worldName = null;
        IQueueExtent<IQueueChunk> edit = null;
        int i = 0;
        if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
            Terraplusminus.instance.getComponentLogger().info("Running async generator task with {} chunks to process.",
                    chunksToProcess.size());
        }

        var currentChunksToProcess = new HashSet<>(chunksToProcess.entrySet());

        for (var entry : currentChunksToProcess) {
            if (!enabled) return;

            if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
                Terraplusminus.instance.getComponentLogger().info("Processing chunk {} - State: {}.",
                        entry.getKey(), entry.getValue().state());
            }

            if (entry.getValue().isCancelled()) {
                chunksToProcess.remove(entry.getKey());
                continue;
            }
            if (entry.getValue().isCompletedExceptionally()) {
                try {
                    entry.getValue().get(); // To trigger the exception
                } catch (Exception e) {
                    handleTerraDataLoadFailure(e, entry.getKey());
                }
                chunksToProcess.remove(entry.getKey());
                continue;
            }
            if (!entry.getValue().isDone()) {
                continue; // Skip futures that are not yet completed
            }

            try {
                if (!Objects.equals(entry.getKey().worldName(), worldName)) {
                    if (edit != null) {
                        edit.flush();
                    }
                    worldName = entry.getKey().worldName();
                    edit = FaweAPI.createQueue(BukkitAdapter.adapt(Bukkit.getWorld(entry.getKey().worldName())), true);

                }

                i++;
                onTerraDataLoaded(entry.getValue().get(), entry.getKey(), edit);
                chunksToProcess.remove(entry.getKey());

                if (i % chunkBatchSize == 0) {
                    if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
                        Terraplusminus.instance.getComponentLogger().info("Processed {} of {} chunks in this batch.",
                                i, chunksToProcess.size());

                    }
                    // Flush the edit session every chunkBatchSize chunks to avoid memory issues
                    if (edit != null) {
                        edit.flush();
                    }
                    saveChunksToGenerate();
                    isRunning = false;
                    return;
                }
            } catch (Exception e) {
                Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk" +
                                " {}.",
                        entry.getKey(),
                        e);
                handleTerraDataLoadFailure(e, entry.getKey());
                chunksToProcess.remove(entry.getKey());
            }
        }

        if (edit != null) {
            edit.flush();
        }

        saveChunksToGenerate();
        isRunning = false;
    }
}
