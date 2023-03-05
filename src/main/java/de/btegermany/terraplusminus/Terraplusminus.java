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

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.logging.Level;



public final class Terraplusminus extends JavaPlugin implements Listener {

    public static final PrivateFieldHandler privateFieldHandler;
    public static FileBuilder config;

    static {
        PrivateFieldHandler handler;
        try {
            Field.class.getDeclaredField("modifiers");
            handler = new Pre14PrivateFieldHandler();
        } catch (NoSuchFieldException | SecurityException ex) {
            handler = new Post14PrivateFieldHandler();
        }
        privateFieldHandler = handler;
    }

    @Override
    public void onEnable() {

        Bukkit.getLogger().log(Level.INFO, "\n╭━━━━╮\n" +
                "┃╭╮╭╮┃\n" +
                "╰╯┃┃┣┻━┳━┳━┳━━╮╭╮\n" +
                "╱╱┃┃┃┃━┫╭┫╭┫╭╮┣╯╰┳━━╮\n" +
                "╱╱┃┃┃┃━┫┃┃┃┃╭╮┣╮╭┻━━╯\n" +
                "╱╱╰╯╰━━┻╯╰╯╰╯╰╯╰╯");


        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("where").setExecutor(new WhereCommand());
        getCommand("tpll").setExecutor(new TpllCommand());
        //Objects.requireNonNull(getCommand("tree")).setExecutor(new SchematicCommand());

        config = new FileBuilder("plugins/TerraPlusMinus", "config.yml")
                .addDefault("prefix", "§2§lT+- §8» ")
                .addDefault("height-datapack", "false")
                .addDefault("nms", "false")
                .addDefault("min-height", -64)
                .addDefault("max-height", 2032)
                .addDefault("useBiomes", true)
                .addDefault("generateTrees", true)
                .addDefault("moveTerrain", 0)
                .addDefault("surface", "GRASS_BLOCK")
                .addDefault("houseOutlines", "BRICKS")
                .addDefault("streets", "GRAY_CONCRETE_POWDER")
                .addDefault("paths", "MOSS_BLOCK")
                .addDefault("height-in-actionbar", false)
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
    public void onWorldInit(WorldInitEvent event) {
        if(Terraplusminus.config.getBoolean("nms")){
            if (event.getWorld().getGenerator() instanceof RealWorldGenerator) {
                NMSInjector injector = null;
                try {
                    injector = new NMSInjector();
                } catch (IllegalArgumentException | SecurityException e) {
                    e.printStackTrace();
                }
                injector.attemptInject(event.getWorld());
                Bukkit.getLogger().log(Level.INFO,"[T+-] §4Activated height expansion");
            }
        }
        if(Terraplusminus.config.getBoolean("height-datapack")) {
/*
            if (event.getWorld().getGenerator().toString().equalsIgnoreCase("TerraPlusMinus")) {

                String datapackPath = event.getWorld().getWorldFolder() + File.separator + "datapacks" + File.separator;
                System.out.println(datapackPath);

                InputStream inputStream = getResource("world-height-datapack.zip");
                OutputStream out = new FileOutputStream(file);
                byte[] buf = new byte[1024];
                int len;
                try {
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                } catch (IOException io) {
                    System.err.println("[TARDIS] Checker: Could not save the file (" + file + ").");
                } finally {
                    try {
                        out.close();
                    } catch (IOException e) {
                        System.err.println("[TARDIS] Checker: Could not close the output stream.");
                    }
                }
            } catch(FileNotFoundException e){
                System.err.println("[TARDIS] Checker: File not found: " + filename);
            } */
        }



    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id){
        if(Bukkit.getBukkitVersion().equalsIgnoreCase("1.17.1-R0.1-SNAPSHOT")){
            Bukkit.getLogger().log(Level.WARNING,"[T+-] §cWorld generation does not work in 1.17");
            return new RealWorldGenerator(this);
        }else {
            return new RealWorldGenerator(this);
        }

    }

}