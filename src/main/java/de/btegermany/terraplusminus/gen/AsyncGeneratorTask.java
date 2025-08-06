package de.btegermany.terraplusminus.gen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
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
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class AsyncGeneratorTask implements Runnable {
    // Track how many times we’ve retried a given chunk
    private final Map<RealWorldGenerator.ChunkInfo, AtomicInteger> retryCounts = new ConcurrentHashMap<>();

    private static final Duration[] RETRY_DELAYS_TICKS = new Duration[]{Duration.ofSeconds(1), Duration.ofSeconds(15), Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4),};

    @Getter
    private boolean enabled;
    private Gson gson = null;
    private File chunksToGenerateFile = null;
    private Queue<Map.Entry<RealWorldGenerator.ChunkInfo, CompletableFuture<CachedChunkData>>> chunksToProcess = null;
    private boolean haveLoadedEverything = false;
    private boolean isRunning = false;
    private int chunkBatchSize;
    private boolean changesToFile = false;

    public AsyncGeneratorTask(Terraplusminus i) {
        enabled = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit") && i.getTpmConfig().isAsyncGenerationEnabled();
        if (!enabled) {
            i.getComponentLogger().warn("FastAsyncWorldEdit is not enabled, the generator will " + "not run async.");
            return;
        }

        this.chunksToProcess = new ConcurrentLinkedQueue<>();
        chunkBatchSize = i.getTpmConfig().getChunkBatchSize();

        Bukkit.getScheduler().runTaskTimerAsynchronously(i,
                this,
                20,
                Tick.tick().fromDuration(Terraplusminus.instance.getTpmConfig().getGenerationTimerDuration()));

        if (i.getTpmConfig().getAsyncChunkGenFlag("load")) return;
        Bukkit.getScheduler().runTaskAsynchronously(i,
                () -> {
                    this.chunksToGenerateFile = new File(i.getDataFolder(),
                            "chunksToGenerate.json");
                    this.gson = new Gson();
                    var chunksToGenerate = loadChunksToGenerate();

                    if (chunksToGenerate != null) {
                        i.getComponentLogger().info("Queueing generation for {} previously unfinished " + "chunks...",
                                chunksToGenerate.size());
                        for (RealWorldGenerator.ChunkInfo chunk : chunksToGenerate) {
                            if (chunksToProcess.stream().anyMatch(z -> z.getKey().equals(chunk))) continue;
                            var data = RealWorldGenerator.getTerraChunkDataAsync(chunk);
                            if (data == null || data.left == null) {
                                // If we don't have the data yet, it will be generated asynchronously.
                                continue;
                            }
                            chunksToProcess.add(Map.entry(chunk,
                                    data.left));
                            changesToFile = true;
                        }
                    }
                });
    }

    public void shutdown() {
        enabled = false;
        // Stops all ongoing futures and clears the queue.
        if (chunksToProcess == null) return;
        chunksToProcess.forEach(entry -> entry.getValue().cancel(true));
        chunksToProcess.clear();
    }

    private @Nullable Set<RealWorldGenerator.ChunkInfo> loadChunksToGenerate() {
        if (chunksToGenerateFile.exists()) {
            try (Reader reader = new FileReader(chunksToGenerateFile)) {
                Type setType = new TypeToken<Set<RealWorldGenerator.ChunkInfo>>() {
                }.getType();
                Set<RealWorldGenerator.ChunkInfo> loaded = gson.fromJson(reader,
                        setType);
                if (!loaded.isEmpty()) {
                    Terraplusminus.instance.getComponentLogger().info("Found {} chunks to generate from previous runs.",
                            loaded.size());
                    haveLoadedEverything = true; // We have loaded all chunks from the file
                    return loaded;
                }
            } catch (IOException e) {
                Terraplusminus.instance.getLogger().log(Level.SEVERE,
                        "Could not load chunksToGenerate.json",
                        e);
            }
        }
        return null;
    }

    private void saveChunksToGenerate() {
        if (!enabled || chunksToProcess == null || !changesToFile) {
            return; // Disabled - we don't need to save anything
        }

        try (Writer writer = new FileWriter(chunksToGenerateFile)) {
            gson.toJson(chunksToProcess.stream().map(Map.Entry::getKey).toArray(),
                    writer);
            changesToFile = false; // Maybe bad because we try to save the file maybe in a endless loop when smt goes
            // wrong
        } catch (IOException e) {
            Terraplusminus.instance.getComponentLogger().error("Could not save chunksToGenerate.json.",
                    e);
        }
    }

    public void supply(CompletableFuture<CachedChunkData> future, RealWorldGenerator.ChunkInfo chunk) {
        if (!enabled) return;

        chunksToProcess.add(Map.entry(chunk,
                future));
        changesToFile = true; // We are adding a chunk, so we will save the file later
        if (haveLoadedEverything) saveChunksToGenerate();
    }

    public boolean isQueued(RealWorldGenerator.ChunkInfo chunk) {
        return enabled && chunksToProcess.stream().anyMatch(chunks -> chunks.getKey().equals(chunk));
    }

    /**
     * Callback executed when the asynchronous fetch of {@link CachedChunkData} completes.
     *
     * @param terraData The loaded chunk data, or null on failure.
     * @param chunk     The ChunkInfo object containing chunk metadata.
     */
    private void onTerraDataLoaded(CachedChunkData terraData, RealWorldGenerator.@NotNull ChunkInfo chunk, EditSession editSession) {
        // Success: apply terrain to the world directly.
        World world = Bukkit.getWorld(chunk.worldName());
        if (world == null) {
            Terraplusminus.instance.getComponentLogger().error("World {} not found for " + "applying async terrain for chunk {}, {}.",
                    chunk.worldName(),
                    chunk.x(),
                    chunk.z());
            return;
        }

        // We need a ChunkData-like interface to set blocks. Since we are outside the main generation pipeline,
        // we have to set blocks directly in the world. This is slower but necessary
        try {
            // but
            // sufficient
            // for this purpose.
            RealWorldGenerator.BlockSetter blockSetter = createBlockSetter(editSession,
                    chunk.x(),
                    chunk.z());
            RealWorldGenerator.applyNoise(world,
                    blockSetter,
                    terraData,
                    chunk.blockYOffset());
            RealWorldGenerator.applySurface(world,
                    blockSetter,
                    terraData,
                    chunk.blockYOffset());
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
     * @param ex    The exception that occurred.
     * @param chunk The ChunkInfo object containing chunk metadata.
     */
    private void handleTerraDataLoadFailure(@NotNull Throwable ex, RealWorldGenerator.ChunkInfo chunk) {
        if (ex.getCause() instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            Terraplusminus.instance.getComponentLogger().error("Chunk data load for {} was interrupted.",
                    chunk,
                    ex);
            return;
        }

        int attempts = retryCounts.computeIfAbsent(chunk,
                k -> new AtomicInteger(0)).incrementAndGet();

        if (attempts <= RETRY_DELAYS_TICKS.length) {
            long delay = Tick.tick().fromDuration(RETRY_DELAYS_TICKS[attempts - 1]);
            Terraplusminus.instance.getComponentLogger().warn("Failed to load chunk {} (attempt {} of {}), retrying in {}.",
                    chunk,
                    attempts,
                    RETRY_DELAYS_TICKS.length,
                    delay / 20.0,
                    ex);

            // Schedule a delayed retry.
            Bukkit.getScheduler().runTaskLaterAsynchronously(Terraplusminus.instance,
                    () -> {
                        if (isQueued(chunk)) {
                            // Retry loading the chunk data
                            var data = RealWorldGenerator.getTerraChunkDataAsync(chunk);
                            if (data == null || data.left == null) {
                                // If we don't have the data yet, it will be generated asynchronously.
                                return;
                            }
                            chunksToProcess.add(Map.entry(chunk,
                                    data.left));
                            changesToFile = true; // We are retrying a chunk, so we will save the file later
                        }
                    },
                    delay);
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
    private RealWorldGenerator.@NotNull BlockSetter createBlockSetter(EditSession session, int chunkX, int chunkZ) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        return new RealWorldGenerator.BlockSetter() {
            @Override
            public void setBlock(int x, int y, int z, @NotNull Material material) {
                session.setBlock(baseX + x,
                        y,
                        baseZ + z,
                        BukkitAdapter.adapt(material.createBlockData()));
            }

            @Override
            public void setRegion(int x, int y, int z, int endX, int endY, int endZ, Material material) {
                CuboidRegion region = new CuboidRegion(BlockVector3.at(x,
                        y,
                        z),
                        BlockVector3.at(endX,
                                endY,
                                endZ));
                session.setBlocks((Region) region,
                        BukkitAdapter.adapt(material.createBlockData()));
            }

            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return TerraChunkGenerator.getInstance().getCustomBiomeProvider().getBiome(info,
                        x,
                        y,
                        z);
            }
        };
    }

    @Override
    public void run() {
        if (!enabled) {
            Terraplusminus.instance.getComponentLogger().warn("Async generator task is disabled, skipping run.");
            return;
        }

        if (chunksToProcess.isEmpty()) {
            Terraplusminus.instance.getComponentLogger().warn("Async generator queue is empty, skipping run.");
            return;
        }

        if (isRunning) {
            if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
                Terraplusminus.instance.getComponentLogger().info("Async generator task is already running, skipping " + "this run.");
            }
            return;
        }
        isRunning = true;
        saveChunksToGenerate();
        String worldName = null;
        EditSession edit = null; // i think we need a EditSession saddly
        int i = 0;
        if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
            Terraplusminus.instance.getComponentLogger().info("Running async generator task with {} chunks to process.",
                    chunksToProcess.size());
        }
        Map.Entry<RealWorldGenerator.ChunkInfo, CompletableFuture<CachedChunkData>> chunk;
        do {
            chunk = chunksToProcess.poll();
            if (chunk == null) {
                continue;
            }
            changesToFile = true; // We are processing a chunk, so we will save the file later

            if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
                Terraplusminus.instance.getComponentLogger().info("Processing chunk {} - State: {}.",
                        chunk.getKey(),
                        chunk.getValue().state());
            }

            if (chunk.getValue().isCompletedExceptionally()) {
                try {
                    chunk.getValue().get(); // To trigger the exception
                } catch (Exception e) {
                    handleTerraDataLoadFailure(e,
                            chunk.getKey());
                }
                continue;
            }

            if (chunk.getValue() == null || !chunk.getValue().isDone()) {
                continue; // Skip non dune futures
            }

            try {
                if (!Objects.equals(chunk.getKey().worldName(),
                        worldName)) {
                    var weWorld = BukkitAdapter.adapt(Bukkit.getWorld(chunk.getKey().worldName()));
                    if (edit != null) {
                        edit.commit();
                    }
                    edit = WorldEdit.getInstance().newEditSession(weWorld);
                    edit.setTrackingHistory(false);
                    worldName = chunk.getKey().worldName();
                } else if (!Terraplusminus.instance.getTpmConfig().getAsyncChunkGenFlag("cec") && edit != null) {
                    edit.commit(); // Commit the previous edit session if we are in the same world
                }
                i++;
                onTerraDataLoaded(chunk.getValue().get(),
                        chunk.getKey(),
                        edit);
            } catch (Exception e) {
                Terraplusminus.instance.getComponentLogger().error("Failed to apply async-loaded chunk data for chunk" + " {}.",
                        chunk.getKey(),
                        e);
                handleTerraDataLoadFailure(e,
                        chunk.getKey());
            }
        } while (!chunksToProcess.isEmpty() && i % chunkBatchSize != 0 && chunk != null && enabled);

        if (Terraplusminus.instance.getTpmConfig().isDevModeEnabled()) {
            Terraplusminus.instance.getComponentLogger().info("Processed {} of {} chunks in this batch.",
                    i,
                    chunksToProcess.size());

        }

        // Flush the edit session every chunkBatchSize chunks to avoid memory issues
        if (edit != null) {
            edit.close();
        }
        saveChunksToGenerate();
        isRunning = false;
    }
}
