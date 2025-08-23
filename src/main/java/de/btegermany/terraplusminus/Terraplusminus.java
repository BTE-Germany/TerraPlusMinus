package de.btegermany.terraplusminus;


import com.mojang.brigadier.Command;
import de.btegermany.terraplusminus.commands.OffsetCommand;
import de.btegermany.terraplusminus.commands.TpllCommand;
import de.btegermany.terraplusminus.commands.WhereCommand;
import de.btegermany.terraplusminus.events.PlayerJoinEvent;
import de.btegermany.terraplusminus.events.PlayerMoveEvent;
import de.btegermany.terraplusminus.events.PluginMessageEvent;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.FileBuilder;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.buildtheearth.terraminusminus.TerraConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;
import java.util.logging.Level;

public final class Terraplusminus extends JavaPlugin implements Listener {
    public static FileConfiguration config;
    public static Terraplusminus instance;

    @Override
    public void onEnable() {
        instance = this;
        PluginDescriptionFile pdf = this.getDescription();
        String pluginVersion = pdf.getVersion();

        getLogger().log(Level.INFO, "\n╭━━━━╮\n" +
                "┃╭╮╭╮┃\n" +
                "╰╯┃┃┣┻━┳━┳━┳━━╮╭╮\n" +
                "╱╱┃┃┃┃━┫╭┫╭┫╭╮┣╯╰┳━━╮\n" +
                "╱╱┃┃┃┃━┫┃┃┃┃╭╮┣╮╭┻━━╯\n" +
                "╱╱╰╯╰━━┻╯╰╯╰╯╰╯╰╯\n" +
                "Version: " + pluginVersion);

        // Config ------------------]
        ConfigurationSerialization.registerClass(ConfigurationSerializable.class);
        this.saveDefaultConfig();
        config = getConfig();
        this.updateConfig();
        // --------------------------

        // Copies osm.json5 into terraplusplus/config/
        File[] terraPlusPlusDirectories = {new File("terraplusplus"), new File("terraplusplus/config/")};
        for (File file : terraPlusPlusDirectories) {
            if (!file.exists()) {
                file.mkdir();
            }
        }
        File osmJsonFile = new File("terraplusplus" + File.separator + "config" + File.separator + "osm.json5");
        if (!osmJsonFile.exists()) {
            this.copyFileFromResource("assets/terraplusminus/data/osm.json5", osmJsonFile);
        }
        // --------------------------

        // Register plugin messaging channel
        PlayerHashMapManagement playerHashMapManagement = new PlayerHashMapManagement();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:terraplusminus");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "bungeecord:terraplusminus", new PluginMessageEvent(playerHashMapManagement));
        // --------------------------

        // Registering events
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Terraplusminus.config.getBoolean("height_in_actionbar")) {
            Bukkit.getPluginManager().registerEvents(new PlayerMoveEvent(this), this);
        }
        if (Terraplusminus.config.getBoolean("linked_worlds.enabled")) {
            Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(playerHashMapManagement), this);
        }
        // --------------------------

        TerraConfig.reducedConsoleMessages = Terraplusminus.config.getBoolean("reduced_console_messages"); // Disables console log of fetching data

        registerCommands();

        Bukkit.getLogger().log(Level.INFO, "[T+-] Terraplusminus successfully enabled");
    }

    @Override
    public void onDisable() {
        // Unregister plugin messaging channel
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        // --------------------------

        Bukkit.getLogger().log(Level.INFO, "[T+-] Plugin deactivated");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        String datapackName = "world-height-datapack.zip";
        File datapackPath = new File(event.getWorld().getWorldFolder() + File.separator + "datapacks" + File.separator + datapackName);
        if (Terraplusminus.config.getBoolean("height_datapack")) {
            if (!event.getWorld().getName().contains("_nether") && !event.getWorld().getName().contains("_the_end")) { //event.getWorld().getGenerator() is null here
                if (!datapackPath.exists()) {
                    copyFileFromResource(datapackName, datapackPath);
                }
            }
        }
    }


    @Override
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, String id) {
        // Multiverse different y-offset support
        int yOffset = 0;
        if (Terraplusminus.config.getBoolean("linked_worlds.enabled") && Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE")) {
            for (LinkedWorld world : ConfigurationHelper.getWorlds()) {
                if (world.getWorldName().equalsIgnoreCase(worldName)) {
                    yOffset = world.getOffset();
                }
            }
        } else {
            yOffset = Terraplusminus.config.getInt("y_offset");
        }
        return new RealWorldGenerator(yOffset);
    }


    public void copyFileFromResource(String resourcePath, File destination) {
        InputStream in = getResource(resourcePath);
        OutputStream out;
        try {
            out = new FileOutputStream(destination);
        } catch (FileNotFoundException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[T+-] " + destination.getName() + " not found");
            throw new RuntimeException(e);
        }
        byte[] buf = new byte[1024];
        int len;
        try {
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException io) {
            Bukkit.getLogger().log(Level.SEVERE, "[T+-] Could not copy " + destination);
        } finally {
            try {
                out.close();
                if (resourcePath.equals("world-height-datapack.zip")) {
                    Bukkit.getLogger().log(Level.CONFIG, "[T+-] Copied datapack to world folder");
                    Bukkit.getLogger().log(Level.SEVERE, "[T+-] Stopping server to start again with datapack");
                    Bukkit.getServer().shutdown();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateConfig() {
        FileBuilder fileBuilder = new FileBuilder(this);

        Double configVersion = null;
        try {
            configVersion = this.config.getDouble("config_version");
        } catch (Exception e) {
            e.printStackTrace();
            Bukkit.getLogger().log(Level.SEVERE, "[T+-] Old config detected. Please delete and restart/reload.");
        }
        if (configVersion == 1.0) {
            String passthroughTpll = Terraplusminus.config.getString("passthrough_tpll");
            if (passthroughTpll == null) {
                passthroughTpll = "";
            }
            int y = (int) this.config.getDouble("terrain_offset");
            this.config.set("terrain_offset.x", 0);
            this.config.set("terrain_offset.y", y);
            this.config.set("terrain_offset.z", 0);
            this.config.set("config_version", 1.1);
            this.saveConfig();
            FileBuilder.addLineAbove("terrain_offset", "\n" +
                    "# Generation -------------------------------------------\n" +
                    "# Offset your section which fits into the world.");
            FileBuilder.deleteLine("# Passthrough tpll");
            FileBuilder.deleteLine("passthrough_tpll");
            FileBuilder.addLineAbove("# Generation", "# Passthrough tpll to other bukkit plugins. It will not passthrough when it's empty. Type in the name of your plugin. E.g. Your plugin name is vanillatpll you set passthrough_tpll: 'vanillatpll'\n" +
                    "passthrough_tpll: '" + passthroughTpll + "'\n\n\n"); //Fixes empty config entry from passthrough_tpll

        }
        if (configVersion == 1.1) {
            this.config.set("config_version", 1.2);
            this.saveConfig();
            FileBuilder.addLineAbove("# If disabled, tree generation is turned off.", "" +
                    "# Linked servers ---------------------------------------\n" +
                    "# If the height limit on this server is not enough, other servers can be linked to generate higher or lower sections.\n" +
                    "linked_servers:\n" +
                    "  enabled: false\n" +
                    "  servers:\n" +
                    "    - another_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.\n" +
                    "    - current_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.\n" +
                    "    - another_server                 # e.g. this server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032\n");
        }
        if (configVersion == 1.2) {
            this.config.set("config_version", 1.3);
            this.saveConfig();
            FileBuilder.deleteLine("# Linked servers -------------------------------------");
            FileBuilder.deleteLine("# If the height limit on this server is not enough, other servers can be linked to generate higher or lower sections");
            FileBuilder.deleteLine("linked_servers:");
            FileBuilder.deleteLine("  enabled: false");
            FileBuilder.deleteLine("  servers:");
            FileBuilder.deleteLine("- another_server");
            FileBuilder.deleteLine("- current_server");
            FileBuilder.addLineAbove("# If disabled, tree generation is turned off.", "" +
                    "# Linked worlds ---------------------------------------\n" +
                    "# If the height limit in this world/server is not enough, other worlds/servers can be linked to generate higher or lower sections\n" +
                    "linked_worlds:\n" +
                    "  enabled: false\n" +
                    "  method: 'SERVER'                         # 'SERVER' or 'MULTIVERSE'\n" +
                    "  # if method = MULTIVERSE -> world_name, y-offset\n" +
                    "  worlds:\n" +
                    "    - another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.\n" +
                    "    - current_world/server                 # do not change! e.g. this world/server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.\n" +
                    "    - another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032\n\n");
        }
        if (configVersion == 1.3) {
            this.config.set("config_version", 1.4);
            this.saveConfig();
            FileBuilder.addLineAfter("prefix:",
                    "\n# If disabled, the plugin will log every fetched data to the console\n" +
                            "reduced_console_messages: true");
            FileBuilder.deleteLine("- another_world/server");
            FileBuilder.deleteLine("- current_world/server");
            FileBuilder.addLineAbove("# If disabled, tree generation is turned off.",
                    "    - name: another_world/server          # e.g. this world/server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.\n" +
                            "      offset: 2032\n" +
                            "    - name: current_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.\n" +
                            "      offset: 0\n" +
                            "    - name: another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032\n" +
                            "      offset: -2032\n\n");
        }
        if (configVersion == 1.4) {
            this.config.set("config_version", 1.5);
            this.saveConfig();
            boolean differentBiomes = Terraplusminus.config.getBoolean("different_biomes");
            FileBuilder.deleteLine("# The biomes will be generated with https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification.");
            FileBuilder.deleteLine("# If turned off, everything will be plains biome.");
            FileBuilder.deleteLine("different_biomes:");
            FileBuilder.addLineAbove("# Customize the material, the blocks will be generated with.",
                    "biomes:\n" +
                    "  # If 'use_dataset' is enabled, biomes will be generated based on: https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification.\n" +
                    "  use_dataset: " + differentBiomes + "\n" +
                    "  # If 'use_dataset' is disabled, this biome will be used everywhere instead (if 'generate_trees' is also enabled -> oak and birch).\n" +
                    "  # Possible values found in \"Resource location\" on: https://minecraft.wiki/w/Biome#Biome_IDs (use with namespace e.g. minecraft:plains)\n" +
                    "  biome: minecraft:plains\n\n");
        }
    }

    private void registerCommands() {
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register("tpll", "Teleports you to longitude and latitude", List.of("tpc"), new TpllCommand());
            commands.register("where", "Gives you the longitude and latitude of your minecraft coordinates", new WhereCommand());
            commands.register("offset", "Displays the x,y and z offset of your world", new OffsetCommand());
        });
    }
}