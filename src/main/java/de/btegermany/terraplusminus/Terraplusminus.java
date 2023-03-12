package de.btegermany.terraplusminus;


import de.btegermany.terraplusminus.commands.TpllCommand;
import de.btegermany.terraplusminus.commands.WhereCommand;
import de.btegermany.terraplusminus.events.PlayerMoveEvent;
import de.btegermany.terraplusminus.gen.*;
import de.btegermany.terraplusminus.utils.FileBuilder;

import org.bukkit.Bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.logging.Level;



public final class Terraplusminus extends JavaPlugin implements Listener {
    public static FileBuilder config;

    @Override
    public void onEnable() {

        Bukkit.getLogger().log(Level.INFO,"\n╭━━━━╮\n" +
                "┃╭╮╭╮┃\n" +
                "╰╯┃┃┣┻━┳━┳━┳━━╮╭╮\n" +
                "╱╱┃┃┃┃━┫╭┫╭┫╭╮┣╯╰┳━━╮\n" +
                "╱╱┃┃┃┃━┫┃┃┃┃╭╮┣╮╭┻━━╯\n" +
                "╱╱╰╯╰━━┻╯╰╯╰╯╰╯╰╯");

        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("tpll").setExecutor(new TpllCommand());
        getCommand("where").setExecutor(new WhereCommand());

        config = new FileBuilder("plugins/TerraPlusMinus", "config.yml")
                .addDefault("prefix", "§2§lT+- §8» ")
                .addDefault("height-datapack", false)
                .addDefault("useBiomes", true)
                .addDefault("generateTrees", true)
                .addDefault("height-in-actionbar", false)
                .addDefault("moveTerrain", 0)
                .addDefault("minLat", 0) // 46.94694079137405
                .addDefault("maxLat", 0) // 55.337721930180116
                .addDefault("minLon", 0) // 1.9049932813372725
                .addDefault("maxLon", 0) // 15.665992332846406
                .addDefault("surface", "GRASS_BLOCK")
                .addDefault("houseOutlines", "BRICKS")
                .addDefault("streets", "GRAY_CONCRETE_POWDER")
                .addDefault("paths", "MOSS_BLOCK")
                .copyDefaults(true).save();

        if (Terraplusminus.config.getBoolean("height-in-actionbar")){
            Bukkit.getPluginManager().registerEvents(new PlayerMoveEvent(this), this);
         }

        Bukkit.getLogger().log(Level.INFO, "[T+-] Plugin loaded");

    }

    @Override
    public void onDisable() {
        Bukkit.getLogger().log(Level.INFO, "[T+-] Plugin deactivated");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event)  {
        String datapackName = "world-height-datapack.zip";
        File datapackPath = new File(event.getWorld().getWorldFolder() + File.separator + "datapacks" + File.separator + datapackName);
        if(Terraplusminus.config.getBoolean("height-datapack")) {
            if (!event.getWorld().getName().contains("_nether") && !event.getWorld().getName().contains("_the_end") ) { //event.getWorld().getGenerator() is null here
                if(!datapackPath.exists()) {
                    InputStream in = getResource(datapackName);
                    OutputStream out;
                    try {
                        out = new FileOutputStream(datapackPath);
                    } catch (FileNotFoundException e) {
                        Bukkit.getLogger().log(Level.WARNING, "Datapack filepath not found");
                        throw new RuntimeException(e);
                    }
                    byte[] buf = new byte[1024];
                    int len;
                    try {
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    } catch (IOException io) {
                        Bukkit.getLogger().log(Level.WARNING, "Could not save " + datapackName);
                    } finally {
                        try {
                            out.close();
                            Bukkit.getLogger().log(Level.CONFIG, "Copied datapack to world folder");
                            Bukkit.getLogger().log(Level.INFO, "Stopping server to start again with datapack");
                            Bukkit.getServer().shutdown();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id){
        return new RealWorldGenerator(this);
    }

}