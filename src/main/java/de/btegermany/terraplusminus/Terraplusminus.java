package de.btegermany.terraplusminus;


import de.btegermany.terraplusminus.commands.TpllCommand;
import de.btegermany.terraplusminus.commands.WhereCommand;
import de.btegermany.terraplusminus.events.PlayerMoveEvent;
import de.btegermany.terraplusminus.gen.*;


import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.logging.Level;

public final class Terraplusminus extends JavaPlugin implements Listener {
    public static FileConfiguration config;

    @Override
    public void onEnable() {
        PluginDescriptionFile pdf = this.getDescription();
        String pluginVersion = pdf.getVersion();

        Bukkit.getLogger().log(Level.INFO,"\n╭━━━━╮\n" +
                "┃╭╮╭╮┃\n" +
                "╰╯┃┃┣┻━┳━┳━┳━━╮╭╮\n" +
                "╱╱┃┃┃┃━┫╭┫╭┫╭╮┣╯╰┳━━╮\n" +
                "╱╱┃┃┃┃━┫┃┃┃┃╭╮┣╮╭┻━━╯\n" +
                "╱╱╰╯╰━━┻╯╰╯╰╯╰╯╰╯\n" +
                "Version: " + pluginVersion);

        // Config ------------------
        this.saveDefaultConfig();
        this.config = getConfig();

        Double configVersion = null;
        try{
            configVersion = this.config.getDouble("config_version");
        }catch (Exception e){
            e.printStackTrace();
            Bukkit.getLogger().log(Level.SEVERE, "[T+-] Old config detected. Please delete and restart/reload.");
        }
        // --------------------------

        // Copies osm.json5 into terraplusplus/config/
        File[] terraPlusPlusDirectories = {new File("terraplusplus"), new File("terraplusplus/config/")};
        for(File file : terraPlusPlusDirectories){
            if(!file.exists()){
                file.mkdir();
            }
        }
        File osmJsonFile = new File("terraplusplus" + File.separator + "config" + File.separator + "osm.json5");
        if(!osmJsonFile.exists()) {
            this.copyFileFromResource("assets/terraplusminus/data/osm.json5", osmJsonFile);
        }
        // --------------------------

        // Registering events
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Terraplusminus.config.getBoolean("height_in_actionbar")){
            Bukkit.getPluginManager().registerEvents(new PlayerMoveEvent(this), this);
        }
        // --------------------------

        // Registering commands
        getCommand("tpll").setExecutor(new TpllCommand());
        getCommand("where").setExecutor(new WhereCommand());
        // --------------------------

        Bukkit.getLogger().log(Level.INFO, "[T+-] Terraplusminus successfully enabled");
    }

    @Override
    public void onDisable() {
        Bukkit.getLogger().log(Level.INFO, "[T+-] Plugin deactivated");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event)  {
        String datapackName = "world-height-datapack.zip";
        File datapackPath = new File(event.getWorld().getWorldFolder() + File.separator + "datapacks" + File.separator + datapackName);
        if(Terraplusminus.config.getBoolean("height_datapack")) {
            if (!event.getWorld().getName().contains("_nether") && !event.getWorld().getName().contains("_the_end") ) { //event.getWorld().getGenerator() is null here
                if(!datapackPath.exists()) {
                    copyFileFromResource(datapackName, datapackPath);
                }
            }
        }
    }




    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id){
        return new RealWorldGenerator();
    }


    private void copyFileFromResource(String resourcePath, File destination){
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
                if(resourcePath.equals("world-height-datapack.zip")) {
                    Bukkit.getLogger().log(Level.CONFIG, "[T+-] Copied datapack to world folder");
                    Bukkit.getLogger().log(Level.INFO, "[T+-] Stopping server to start again with datapack");
                    Bukkit.getServer().shutdown();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}