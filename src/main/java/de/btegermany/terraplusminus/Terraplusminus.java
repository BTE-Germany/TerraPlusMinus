package de.btegermany.terraplusminus;

import de.btegermany.terraplusminus.commands.DistortionCommand;
import de.btegermany.terraplusminus.commands.OffsetCommand;
import de.btegermany.terraplusminus.commands.TpllCommand;
import de.btegermany.terraplusminus.commands.WhereCommand;
import de.btegermany.terraplusminus.events.PlayerCommandEvent;
import de.btegermany.terraplusminus.events.PlayerJoinEvent;
import de.btegermany.terraplusminus.events.PlayerMoveEvent;
import de.btegermany.terraplusminus.events.PluginMessageEvent;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.*;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import lombok.Setter;
import net.buildtheearth.terraminusminus.TerraConfig;
import net.buildtheearth.terraminusminus.TerraConstants;
import net.buildtheearth.terraminusminus.util.http.Disk;
import net.buildtheearth.terraminusminus.util.http.Http;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static java.lang.String.format;
import static net.daporkchop.lib.common.util.PValidation.checkState;

public final class Terraplusminus extends JavaPlugin implements Listener {

    /**
     * @deprecated Static Fields should not be used, because else config reloads won't work properly.
     * We'll rework the whole config system in a future update to make it more robust. Maybe switching to configurate.
     */
    @Deprecated(since = "1.6.0", forRemoval = true)
    public static FileConfiguration config;

    /**
     * @deprecated We'll switch to basic dependency injection from now on. Static Fields should not be used.
     */
    @Deprecated(since = "1.6.0", forRemoval = true)
    public static Terraplusminus instance;

    @Getter
    @Setter
    private String registeredServerName = null;

    /**
     * The config version ({@code config_version} in config.yml) this plugin release supports.
     * Configs from older versions are rejected on enable, support for (very) old config
     * versions was dropped - see https://github.com/BTE-Germany/TerraPlusMinus/issues/83
     */
    private static final double SUPPORTED_CONFIG_VERSION = 1.5;

    @Override
    public void onEnable() {
        new Metrics(this, 28392); // https://bstats.org/plugin/bukkit/Terraplusminus/28392
        instance = this;

        // Config ------------------]
        ConfigurationSerialization.registerClass(ConfigurationSerializable.class);
        this.saveDefaultConfig();
        config = getConfig();

        if (!this.hasSupportedConfigVersion()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        ConfigurationHelper.load();
        // --------------------------

        // Set-up Terra-- so it looks for its config files in our plugin dir, and then copy its default files there
        this.setupTerraMinusMinus();
        this.extractTerraConfigFileToPluginDir("/net/buildtheearth/terraminusminus/dataset/osm/osm.json5", "osm.json5");
        this.extractTerraConfigFileToPluginDir("config/readme-heights.md", "heights/README.md");
        this.extractTerraConfigFileToPluginDir("config/readme-tree_cover.md", "tree_cover/README.md");

        // Register plugin messaging channel
        PlayerHashMapManagement playerHashMapManagement = new PlayerHashMapManagement();
        PluginMessageEvent pluginMessageListener =
                new PluginMessageEvent(playerHashMapManagement, this);

        // Linked Server current server initialization
        if (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED)
                && getConfig().getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase(Properties.NonConfigurable.METHOD_SRV)) {

            // Talks to TerraPlusMinusProxyBridge on the proxy, which listens and answers on the same channel.
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, Properties.NonConfigurable.CROSS_TELEPORTATION_CHANNEL);
            this.getServer().getMessenger().registerIncomingPluginChannel(this, Properties.NonConfigurable.CROSS_TELEPORTATION_CHANNEL, pluginMessageListener);

            // Used once per session to ask the proxy for this server's name.
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", pluginMessageListener);
            getComponentLogger().debug("Linked server initialization successful");
        }
        // --------------------------

        // Registering events
        Bukkit.getPluginManager().registerEvents(this, this);

        // The listener carries both the action bar and the Multiverse transitions.
        if (getConfig().getBoolean(Properties.ACTIONBAR_HEIGHT)
                || (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED)
                && getConfig().getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV))) {
            Bukkit.getPluginManager().registerEvents(new PlayerMoveEvent(this), this);
        }

        if (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED)) {
            Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(playerHashMapManagement, this), this);
        }

        String passthroughTpll = getConfig().getString(Properties.PASSTHROUGH_TPLL);
        if (passthroughTpll != null && !passthroughTpll.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(
                    new PlayerCommandEvent(passthroughTpll),
                    this
            );
        }
        // --------------------------

        TerraConfig.reducedConsoleMessages =
                getConfig().getBoolean("reduced_console_messages"); // Disables console log of fetching data

        registerCommands();

        new Updater(this);

        this.getComponentLogger().info(
                "Terraplusminus successfully enabled ({} v{}, {} v{})",
                this.getName(),
                this.getVersion(),
                TerraConstants.LIB_NAME,
                TerraConstants.LIB_VERSION
        );
    }

    @Override
    public void onDisable() {
        // Unregister plugin messaging channel
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        // --------------------------

        this.getComponentLogger().info("Plugin deactivated");
    }

    @EventHandler
    public void onWorldInit(@NonNull WorldInitEvent event) {
        World world = event.getWorld();

        boolean shouldInstallHeightDatapack =
                getConfig().getBoolean(Properties.HEIGHT_DATAPACK);

        boolean isDefaultWorld =
                Bukkit.getWorlds().getFirst().getUID().equals(world.getUID());

        if (shouldInstallHeightDatapack && isDefaultWorld) {
            // Datapacks should be installed in the default world and will apply to all of them.
            // Getting the default world this way is reliable according to https://www.spigotmc.org/threads/ask-getting-the-servers-main-world.349626/#post-3238378
            this.enforceDatapackInstallation("world-height-datapack.zip", world);
        }
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull ChunkGenerator getDefaultWorldGenerator(
            @NonNull String worldName,
            String id
    ) {
        // Multiverse different y-offset support
        int yOffset = 0;

        if (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED)
                && getConfig().getString(Properties.LINKED_WORLDS_METHOD, "")
                .equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV)) {

            for (LinkedWorld world : ConfigurationHelper.getWorlds()) {
                if (world.getWorldName().equalsIgnoreCase(worldName)) {
                    yOffset = world.getOffset();
                }
            }
        } else {
            yOffset = getConfig().getInt(Properties.Y_OFFSET);
        }

        return new RealWorldGenerator(yOffset, this);
    }

    public void enforceDatapackInstallation(
            String datapackResourcePath,
            @NonNull World world
    ) {
        String datapackName = Path.of(datapackResourcePath)
                .getFileName()
                .toString();

        File droppedFile = world.getWorldFolder()
                .toPath()
                .resolve("datapacks")
                .resolve(datapackName)
                .toFile();

        if (droppedFile.exists()) {
            this.getComponentLogger().debug(
                    "Datapack {} was already installed in world {}",
                    datapackName,
                    world.getName()
            );
            return;
        }

        try (InputStream in = this.getResource(datapackResourcePath);
             OutputStream out = new FileOutputStream(droppedFile)) {

            checkState(in != null,
                    "Missing internal resource: %s",
                    datapackResourcePath);

            in.transferTo(out);

        } catch (IOException io) {
            this.getComponentLogger().error(
                    "Failed to extract datapack from resource '{}' to '{}'",
                    datapackResourcePath,
                    droppedFile.getAbsolutePath()
            );
            throw new RuntimeException(io);
        }

        this.getComponentLogger().error(
                "Datapack {} was missing from world {} and has been automatically installed by Terraplusminus. The server needs to be manually restarted for the change to take effect. Terraplusminus will now shutdown the server so it can be restarted.",
                datapackName, world.getName()
        );
        Bukkit.getServer().shutdown();
    }

    private boolean hasSupportedConfigVersion() {
        double configVersion = getConfig().getDouble(Properties.CONFIG_VERSION);

        if (configVersion < SUPPORTED_CONFIG_VERSION) {
            this.getComponentLogger().error(
                    "Unsupported config version {} in {}. Support for (very) old config versions was dropped. Please delete the config and restart/reload the server, so a fresh one can be generated.",
                    configVersion,
                    this.getDataPath().resolve("config.yml")
            );
            return false;
        }

        if (configVersion > SUPPORTED_CONFIG_VERSION) {
            this.getComponentLogger().warn(
                    "Config version {} is newer than the supported version {}. Was the plugin downgraded?",
                    configVersion,
                    SUPPORTED_CONFIG_VERSION
            );
        }

        return true;
    }

    private void registerCommands() {
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();

        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(
                    TpllCommand.create(),
                    "tpll",
                    List.of("tpc")
            );

            commands.register(
                    "where",
                    "Gives you the longitude and latitude of your minecraft coordinates",
                    new WhereCommand()
            );

            commands.register(
                    "offset",
                    "Displays the x,y and z offset of your world",
                    new OffsetCommand()
            );

            commands.register(
                    "distortion",
                    "Displays projection distortion at your current location",
                    new DistortionCommand()
            );
        });
    }

    private void setupTerraMinusMinus() {
        FolderMigrator.migrateTerraPlusPlusFolder(getComponentLogger(), getDataFolder());
        Disk.setConfigRoot(this.getDataFolder());
        Disk.setCacheRoot(this.getDataPath().resolve("cache").toFile());

        String userAgent = this.createHttpUserAgent();
        this.getComponentLogger().debug("Terraplusminus HTTP user agent: {}", userAgent);
        Http.userAgent(userAgent);
    }

    private @NonNull String getVersion() {
        PluginMeta meta = this.getPluginMeta();
        return meta.getVersion();
    }

    private @NonNull String createHttpUserAgent() {
        PluginMeta metadata = this.getPluginMeta();
        return format(Locale.ENGLISH, "%s/%s (%s/%s; +%s)",
                metadata.getName(),
                metadata.getVersion(),
                TerraConstants.LIB_NAME,
                TerraConstants.LIB_VERSION,
                metadata.getWebsite()
        );
    }

    private void extractTerraConfigFileToPluginDir(@NonNull String resourcePath, @NonNull String dropPath) {
        File droppedFile = this.getDataPath().resolve(dropPath).toFile();
        if (droppedFile.exists()) {
            this.getComponentLogger().debug("Terra-- config file {} is already present in plugin directory", droppedFile.getAbsolutePath());
            return;
        }
        if(droppedFile.getParentFile().mkdirs()) {
            this.getComponentLogger().trace("Created parent directory before extracting Terra-- configuration to: {}", droppedFile.getAbsolutePath());
        }
        try (InputStream resourceStream = this.getClass().getResourceAsStream(resourcePath); OutputStream fileStream = new FileOutputStream(droppedFile)) {
            checkState(resourceStream != null, "Missing internal resource: %s", resourcePath);
            resourceStream.transferTo(fileStream);
        } catch (IOException e) {
            this.getComponentLogger().warn("Failed to drop a Terra configuration file in plugin directory", e);
        }
        this.getComponentLogger().info("Created default Terra-- configuration at {}", droppedFile.getAbsolutePath());
    }
}
