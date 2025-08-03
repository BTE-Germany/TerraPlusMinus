package de.btegermany.terraplusminus.gen;

import com.fastasyncworldedit.core.FaweAPI;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import de.btegermany.terraplusminus.Terraplusminus;
import io.papermc.paper.util.Tick;
import lombok.Getter;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;

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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncGenerator {
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
    private final boolean enabled;
    private Set<RealWorldGenerator.ChunkInfo> chunksToGenerate = null;
    private Gson gson = null;
    private File chunksToGenerateFile = null;
    private Set<CompletableFuture<CachedChunkData>> futures = null;
    private boolean haveLoadedEverything = false;
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);

    public AsyncGenerator(Terraplusminus i) {
        enabled = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        if (!enabled) {
            i.getComponentLogger().warn("FastAsyncWorldEdit is not enabled, the generator will " +
                    "not run async.");
            return;
        }

        chunksToGenerate = ConcurrentHashMap.newKeySet();
        this.futures = ConcurrentHashMap.newKeySet();

        Bukkit.getScheduler().runTaskAsynchronously(i, () -> {
            this.chunksToGenerateFile = new File(i.getDataFolder(), "chunksToGenerate.json");
            this.gson = new Gson();
            loadChunksToGenerate();

            if (!this.chunksToGenerate.isEmpty()) {
                i.getComponentLogger().info("Queueing generation for {} previously unfinished " +
                        "chunks...", this.chunksToGenerate.size());
                for (RealWorldGenerator.ChunkInfo chunk : new HashSet<>(this.chunksToGenerate)) {
                    var data = RealWorldGenerator.getTerraChunkDataAsync(chunk);
                    if (data == null || data.left == null) {
                        // If we don't have the data yet, it will be generated asynchronously.
                        continue;
                    }
                    onTerraDataLoaded(data.left, null, data.right);
                }
            }
        });
    }

    public void shutdown() {
        // Stops all ongoing futures and clears the queue.
        if (futures == null) return;
        futures.forEach(future -> future.cancel(true));
        executor.shutdownNow();
    }

    private void loadChunksToGenerate() {
        if (chunksToGenerateFile.exists()) {
            try (Reader reader = new FileReader(chunksToGenerateFile)) {
                Type setType = new TypeToken<Set<RealWorldGenerator.ChunkInfo>>() {}.getType();
                Set<RealWorldGenerator.ChunkInfo> loaded = gson.fromJson(reader, setType);
                if (!loaded.isEmpty()) {
                    Terraplusminus.instance.getComponentLogger().info("Found {} chunks to generate from previous runs.", loaded.size());
                    chunksToGenerate.addAll(loaded);
                    haveLoadedEverything = true; // We have loaded all chunks from the file
                }
            } catch (IOException e) {
                Terraplusminus.instance.getLogger().log(java.util.logging.Level.SEVERE, "Could not load chunksToGenerate.json", e);
            }
        }
    }

    private void saveChunksToGenerate() {
        if (chunksToGenerate == null || chunksToGenerate.isEmpty()) {
            return;
        }
        try (Writer writer = new FileWriter(chunksToGenerateFile)) {
            gson.toJson(chunksToGenerate, writer);
        } catch (IOException e) {
            Terraplusminus.instance.getComponentLogger().error("Could not save chunksToGenerate.json.", e);
        }
    }

    public void supply(CompletableFuture<CachedChunkData> future, RealWorldGenerator.ChunkInfo chunk) {
        if (!enabled) return;

        executor.submit(() -> {
            if (chunksToGenerate.contains(chunk)) {
                return;
            }
            chunksToGenerate.add(chunk);
            if (haveLoadedEverything) saveChunksToGenerate();
            futures.add(future);

            // Data is not ready yet. Return null to generate an empty chunk for now.
            // The terrain will be applied once the future completes.
            future.whenComplete((data, ex) -> {
                try {
                    onTerraDataLoaded(data, ex, chunk);
                    futures.remove(future);
                } catch (Exception e) {
                    Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk" +
                                    " {}.",
                            chunk,
                            e);
                }
            });
        });
    }


    /**
     * Callback executed when the asynchronous fetch of {@link CachedChunkData} completes.
     *
     * @param terraData The loaded chunk data, or null on failure.
     * @param ex        The exception, if the load failed.
     * @param chunk     The ChunkInfo object containing chunk metadata.
     */
    private void onTerraDataLoaded(CachedChunkData terraData, Throwable ex, RealWorldGenerator.ChunkInfo chunk) {
        if (ex != null) {
            handleTerraDataLoadFailure(ex, chunk);
        } else {
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
                RealWorldGenerator.BlockSetter blockSetter = createBlockSetter(world, chunk.x(), chunk.z());
                RealWorldGenerator.applyNoise(world, blockSetter, terraData, chunk.blockYOffset());
                RealWorldGenerator.applySurface(world, random, blockSetter, terraData, chunk.blockYOffset());
                blockSetter.flush(); // Flush the changes to the world
                chunksToGenerate.remove(chunk);
                saveChunksToGenerate();
            } catch (Exception e) {
                Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk {}",
                        chunk,
                        e);
            }
            retryCounts.remove(chunk);
        }
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
                onTerraDataLoaded(data.left, null, chunk);
            }, delay * 50); // Convert ticks to ms
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
     * @param world  The world to write to.
     * @param chunkX The chunk's X coordinate.
     * @param chunkZ The chunk's Z coordinate.
     * @return A new BlockSetter instance.
     */
    private RealWorldGenerator.@NotNull BlockSetter createBlockSetter(World world, int chunkX, int chunkZ) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        var queue = FaweAPI.createQueue(BukkitAdapter.adapt(world), true);
        return new RealWorldGenerator.BlockSetter() {
            @Override
            public void setBlock(int x, int y, int z, Material material) {
                queue.setBlock(baseX + x, y, baseZ + z, BukkitAdapter.adapt(material.createBlockData()));
            }

            @Override
            public void setRegion(int x, int y, int z, int endX, int endY, int endZ, Material material) {
                for (int i = x; i < endX; i++) {
                    for (int j = y; j < endY; j++) {
                        for (int k = z; k < endZ; k++) {
                            setBlock(i, j, k, material);
                        }
                    }
                }
            }

            @Override
            public Biome getBiome(int x, int y, int z) {
                return world.getBiome(baseX + x, y, baseZ + z);
            }

            @Override
            public void flush() {
                queue.flush();
            }
        };
    }
}
