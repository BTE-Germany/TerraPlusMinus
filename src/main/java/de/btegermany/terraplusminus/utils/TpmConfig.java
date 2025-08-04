package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.Terraplusminus;
import org.bukkit.configuration.Configuration;
import org.jetbrains.annotations.NotNull;

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
    private static final String GENERATION_TIMER_SECONDS = "async-generation-timer-seconds";
    private static final String DEV_MODE = "dev-mode";


    public TpmConfig(@NotNull Terraplusminus plugin) {
        config = plugin.getConfig();
    }

    public boolean isAsyncGenerationEnabled() {
        return config.getBoolean(ASYNC_GEN, true);
    }

    public int getChunkBatchSize() {
        return config.getInt(CHUNK_BATCH_SIZE, 10);
    }

    public boolean isDevModeEnabled() {
        return config.getBoolean(DEV_MODE, false);
    }



    public int getGenerationTimerSeconds() {
        return config.getInt(GENERATION_TIMER_SECONDS, 5);
    }
}
