package de.btegermany.terraplusminus;


import de.btegermany.terraplusminus.commands.OffsetCommand;
import de.btegermany.terraplusminus.commands.TpllCommand;
import de.btegermany.terraplusminus.commands.WhereCommand;
import de.btegermany.terraplusminus.events.PlayerCommandPreprocessEvent;
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
import org.jetbrains.annotations.NotNull;

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

    @Override
    public void onEnable() {
        new Metrics(this, 28392); // https://bstats.org/plugin/bukkit/Terraplusminus/28392
        instance = this;

        // Config ------------------]
        ConfigurationSerialization.registerClass(ConfigurationSerializable.class);
        this.saveDefaultConfig();
        config = getConfig();
        this.updateConfig();
        // --------------------------

        // Set-up Terra-- so it looks for its config files in our plugin dir, and then copy its default files there
        this.setupTerraMinusMinus();
        this.extractTerraConfigFileToPluginDir("/net/buildtheearth/terraminusminus/dataset/osm/osm.json5", "osm.json5");
        this.extractTerraConfigFileToPluginDir("config/readme-heights.md", "heights/README.md");
        this.extractTerraConfigFileToPluginDir("config/readme-tree_cover.md", "tree_cover/README.md");

        // Register plugin messaging channel
        PlayerHashMapManagement playerHashMapManagement = new PlayerHashMapManagement();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:terraplusminus");
        PluginMessageEvent pluginMessageListener = new PluginMessageEvent(playerHashMapManagement, this);
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "bungeecord:terraplusminus", pluginMessageListener);

        // Linked Server current server initialization
        if (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED) && getConfig().getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase(Properties.NonConfigurable.METHOD_SRV)) {
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", pluginMessageListener);
            getComponentLogger().debug("Linked server initialization successful");
        }
        // --------------------------

        // Registering events
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getConfig().getBoolean("height_in_actionbar") ||
                (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED) && getConfig().getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV))) {
            Bukkit.getPluginManager().registerEvents(new PlayerMoveEvent(this), this);
        }
        if (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED) && !getConfig().getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase(Properties.NonConfigurable.METHOD_CUSTOM)) {
            Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(playerHashMapManagement, this), this);
        }

        String passthroughTpll = getConfig().getString(Properties.PASSTHROUGH_TPLL);
        if (passthroughTpll != null && !passthroughTpll.isEmpty()) {
            Bukkit.getPluginManager().registerEvents(new PlayerCommandPreprocessEvent(passthroughTpll), this);
        }
        // --------------------------

        TerraConfig.reducedConsoleMessages = getConfig().getBoolean("reduced_console_messages"); // Disables console log of fetching data

        registerCommands();

        this.getComponentLogger().info(
                "Terraplusminus successfully enabled ({} v{}, {} v{})",
                this.getName(), this.getVersion(), TerraConstants.LIB_NAME, TerraConstants.LIB_VERSION
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
    public void onWorldInit(@NotNull WorldInitEvent event) {
        World world = event.getWorld();
        boolean shouldInstallHeightDatapack = getConfig().getBoolean(Properties.HEIGHT_DATAPACK);
        boolean isDefaultWorld = Bukkit.getWorlds().getFirst().getUID().equals(world.getUID());
        if (shouldInstallHeightDatapack && isDefaultWorld) {
            // Datapacks should be installed in the default world and will apply to all of them.
            // Getting the default world this way is reliable according to https://www.spigotmc.org/threads/ask-getting-the-servers-main-world.349626/#post-3238378
            this.enforceDatapackInstallation("world-height-datapack.zip", world);
        }
    }


    @Contract("_, _ -> new")
    @Override
    public @NotNull ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        // Multiverse different y-offset support
        getLogger().info("Generating world: " + worldName + " with generator id: " + id);
        int yOffset = 0;
        if (getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED)
                && getConfig().getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV)) {
            for (LinkedWorld world : ConfigurationHelper.getWorlds()) {
                if (world.getWorldName().equalsIgnoreCase(worldName)) {
                    yOffset = world.getOffset();
                }
            }
        } else if (id != null) {
            yOffset = getOffsetFromGeneratorId(id, getConfig().getInt(Properties.Y_OFFSET));
        } else {
            yOffset = getConfig().getInt(Properties.Y_OFFSET);
        }
        return new RealWorldGenerator(yOffset, this);
    }

    public void enforceDatapackInstallation(String datapackResourcePath, @NotNull World world) {
        String datapackName = Path.of(datapackResourcePath).getFileName().toString();
        File droppedFile = world
                .getWorldFolder().toPath()
                .resolve("datapacks")
                .resolve(datapackName)
                .toFile();
        if (droppedFile.exists()) {
            this.getComponentLogger().debug("Datapack {} was already installed in world {}", datapackName, world.getName());
            return;
        }
        try(InputStream in = this.getResource(datapackResourcePath); OutputStream out = new FileOutputStream(droppedFile)) {
            checkState(in != null, "Missing internal resource: %s", datapackResourcePath);
            in.transferTo(out);
        } catch (IOException io) {
            this.getComponentLogger().error(
                    "Failed to extract datapack from resource '{}' to '{}'",
                    datapackResourcePath, droppedFile.getAbsolutePath()
            );
            throw new RuntimeException(io);
        }
        this.getComponentLogger().error(
                "Datapack {} was missing from world {} and has been automatically installed by Terraplusminus. The server needs to be manually restarted for the change to take effect. Terraplusminus will now shutdown the server so it can be restarted.",
                datapackName, world.getName()
        );
        Bukkit.getServer().shutdown();
    }

    /**
     * @deprecated We'll rework the whole config system in a future update to make it more robust. Maybe switching to configurate.
     * The current system has some flaws like only can update one config version at a time and is generally hard to maintain.
     */
    @Deprecated(since = "1.6.0", forRemoval = true)
    private void updateConfig() {
        PluginConfigManipulator manipulator = new PluginConfigManipulator(this);

        double configVersion = getConfig().getDouble("config_version");

        if (configVersion == 0.0) {  // That's the default value if the field was not set at all in the YAML
            this.getComponentLogger().error("Old config detected. Please delete and restart/reload.");
        }
        if (configVersion == 1.0) {
            String passthroughTpll = getConfig().getString(Properties.PASSTHROUGH_TPLL, "");
            int y = (int) getConfig().getDouble("terrain_offset");
            getConfig().set("terrain_offset.x", 0);
            getConfig().set("terrain_offset.y", y);
            getConfig().set("terrain_offset.z", 0);
            getConfig().set("config_version", 1.1);
            this.saveConfig();
            manipulator.addLineAbove(
                    "terrain_offset",
                    """
                    
                    # Generation -------------------------------------------
                    # Offset your section which fits into the world."""
            );
            manipulator.deleteLine("# Passthrough tpll");
            manipulator.deleteLine("passthrough_tpll");
            manipulator.addLineAbove(
                    "# Generation",
                    """
                    # Passthrough tpll to other bukkit plugins. It will not passthrough when it's empty. Type in the name of your plugin. E.g. Your plugin name is vanillatpll you set passthrough_tpll: 'vanillatpll'
                    passthrough_tpll: 'PASSTHROUGH_TPLL'
                    
                    
                    """.replace("PASSTHROUGH_TPLL", passthroughTpll)); //Fixes empty config entry from passthrough_tpll

        }
        if (configVersion == 1.1) {
            getConfig().set("config_version", 1.2);
            this.saveConfig();
            manipulator.addLineAbove(
                    "# If disabled, tree generation is turned off.",
                    """
                    # Linked servers ---------------------------------------
                    # If the height limit on this server is not enough, other servers can be linked to generate higher or lower sections.
                    linked_servers:
                      enabled: false
                      servers:
                        - another_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.
                        - current_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.
                        - another_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032
                    """
            );
        }
        if (configVersion == 1.2) {
            getConfig().set("config_version", 1.3);
            this.saveConfig();
            manipulator.deleteLine("# Linked servers -------------------------------------");
            manipulator.deleteLine("# If the height limit on this server is not enough, other servers can be linked to generate higher or lower sections");
            manipulator.deleteLine("linked_servers:");
            manipulator.deleteLine("  enabled: false");
            manipulator.deleteLine("  servers:");
            manipulator.deleteLine("- another_server");
            manipulator.deleteLine("- current_server");
            manipulator.addLineAbove(
                    "# If disabled, tree generation is turned off.",
                    """
                    # Linked worlds ---------------------------------------
                    # If the height limit in this world/server is not enough, other worlds/servers can be linked to generate higher or lower sections
                    linked_worlds:
                      enabled: false
                      method: 'SERVER'                         # 'SERVER' or 'MULTIVERSE'
                      # if method = MULTIVERSE -> world_name, y-offset
                      worlds:
                        - another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.
                        - current_world/server                 # do not change! e.g. this world/server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.
                        - another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032
                    """
            );
        }
        if (configVersion == 1.3) {
            getConfig().set("config_version", 1.4);
            this.saveConfig();
            manipulator.addLineBelow(
                    "prefix:",
                    """
                    
                    # If disabled, the plugin will log every fetched data to the console
                    reduced_console_messages: true"""
            );
            manipulator.deleteLine("- another_world/server");
            manipulator.deleteLine("- current_world/server");
            manipulator.addLineAbove(
                    "# If disabled, tree generation is turned off.",
                        """
                        - name: another_world/server          # e.g. this world/server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.
                          offset: 2032
                        - name: current_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.
                          offset: 0
                        - name: another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032
                          offset: -2032
                    
                    """);
        }
        if (configVersion == 1.4) {
            getConfig().set("config_version", 1.5);
            this.saveConfig();
            boolean differentBiomes = getConfig().getBoolean("different_biomes");
            manipulator.deleteLine("# The biomes will be generated with https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification.");
            manipulator.deleteLine("# If turned off, everything will be plains biome.");
            manipulator.deleteLine("different_biomes:");
            manipulator.addLineAbove(
                    "# Customize the material, the blocks will be generated with.",
                    """
                    biomes:
                      # If 'use_dataset' is enabled, biomes will be generated based on: https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification.
                      use_dataset: USE_DATASET
                      # If 'use_dataset' is disabled, this biome will be used everywhere instead (if 'generate_trees' is also enabled -> oak and birch).
                      # Possible values found in "Resource location" on: https://minecraft.wiki/w/Biome#Biome_IDs (use with namespace e.g. minecraft:plains)
                      biome: minecraft:plains
                    
                    """.replace("USE_DATASET", "" + differentBiomes)
            );
        }
    }

    private void registerCommands() {
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(TpllCommand.create(), "tpll", List.of("tpc"));
            commands.register("where", "Gives you the longitude and latitude of your minecraft coordinates", new WhereCommand());
            commands.register("offset", "Displays the x,y and z offset of your world", new OffsetCommand());
        });
    }

    private void setupTerraMinusMinus() {
        FolderMigrator.migrateTerraPlusPlusFolder();
        Disk.setConfigRoot(this.getDataFolder());
        Disk.setCacheRoot(this.getDataPath().resolve("cache").toFile());

        String userAgent = this.createHttpUserAgent();
        this.getComponentLogger().debug("Terraplusminus HTTP user agent: {}", userAgent);
        Http.userAgent(userAgent);
    }

    private @NotNull String getVersion() {
        PluginMeta meta = this.getPluginMeta();
        return meta.getVersion();
    }

    private @NotNull String createHttpUserAgent() {
        PluginMeta metadata = this.getPluginMeta();
        return format(Locale.ENGLISH, "%s/%s (%s/%s; +%s)",
                metadata.getName(),
                metadata.getVersion(),
                TerraConstants.LIB_NAME,
                TerraConstants.LIB_VERSION,
                metadata.getWebsite()
        );
    }

    private void extractTerraConfigFileToPluginDir(@NotNull String resourcePath, @NotNull String dropPath) {
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

    private int getOffsetFromGeneratorId(String generatorId, int defaultOffset) {
        try {
            return Integer.parseInt(generatorId);
        } catch (NumberFormatException e) {
            return defaultOffset;
        }
    }
}
