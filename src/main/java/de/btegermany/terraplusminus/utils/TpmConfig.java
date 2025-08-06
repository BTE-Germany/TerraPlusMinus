package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.Terraplusminus;
import org.bukkit.configuration.Configuration;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * TpmConfig is a utility class that provides access to the configuration settings
 * for the Terraplusminus plugin. It allows retrieval of various configuration options
 * such as asynchronous generation, chunk batch size, generation timer, and development mode.
 * This class is designed to encapsulate configuration access and provide a clean API for
 * interacting with the plugin's settings.
 * Long Term we want to switch to Configurate, but for now we use the Bukkit Configuration API.
 */
public class TpmConfig {
    Configuration config;

    private static final String ASYNC_GEN = "enable-async-generation";
    private static final String CHUNK_BATCH_SIZE = "async-generation-chunk-batch-size";
    private static final String GENERATION_TIMER_MILLIS = "async-generation-timer-millis";
    private static final String DEV_MODE = "dev-mode";
    private static final String DIRECTLY_TIMOUT_MILLIS = "async-generation-directly-timout-millis";


    public TpmConfig(@NotNull Terraplusminus plugin) {
        config = plugin.getConfig();
    }

    public boolean isAsyncGenerationEnabled() {
        return config.getBoolean(ASYNC_GEN, true);
    }

    public int getChunkBatchSize() {
        return config.getInt(CHUNK_BATCH_SIZE, 400);
    }

    public boolean isDevModeEnabled() {
        return config.getBoolean(DEV_MODE, false);
    }

    public Duration getGenerationTimerDuration() {
        return Duration.ofMillis(config.getInt(GENERATION_TIMER_MILLIS, 1000));
    }

    public int getDirectlyTimeoutMillis() {
        return config.getInt(DIRECTLY_TIMOUT_MILLIS, 400);
    }

    public boolean getAsyncChunkGenFlag(String flag) {
        return config.getBoolean("ag-" + flag, false);
    }
}
