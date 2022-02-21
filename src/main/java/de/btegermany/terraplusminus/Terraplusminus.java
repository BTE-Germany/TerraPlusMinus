package de.btegermany.terraplusminus;

import de.btegermany.terraplusminus.commands.TpllCommand;
import de.btegermany.terraplusminus.gen.*;
import de.btegermany.terraplusminus.utils.FileBuilder;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.logging.Level;



public final class Terraplusminus extends JavaPlugin implements Listener {

    public static final PrivateFieldHandler privateFieldHandler;
    public static NMSInjector injector;
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
        System.out.println("\n" +
                "╭━━━━╮\n" +
                "┃╭╮╭╮┃\n" +
                "╰╯┃┃┣┻━┳━┳━┳━━╮╭╮\n" +
                "╱╱┃┃┃┃━┫╭┫╭┫╭╮┣╯╰┳━━╮\n" +
                "╱╱┃┃┃┃━┫┃┃┃┃╭╮┣╮╭┻━━╯\n" +
                "╱╱╰╯╰━━┻╯╰╯╰╯╰╯╰╯");


        Bukkit.getPluginManager().registerEvents(this,this);

        Objects.requireNonNull(getCommand("tpll")).setExecutor(new TpllCommand());

        config = new FileBuilder("plugins/TerraPlusMinus", "config.yml")
                .addDefault("prefix","§2§lT+- §8» ")
                .addDefault("nms","false")
                .addDefault("min-height", -64)
                .addDefault("max-height", 2032)
                .copyDefaults(true).save();

        if(Terraplusminus.config.getBoolean("nms")) {
            System.out.println("[T+-] §4Activated height expansion");
        }else{
            System.out.println("[T+-] §4Deactivated height expansion");
        }

        try {
            injector = new NMSInjector();

        } catch (IllegalArgumentException | SecurityException e) {
            e.printStackTrace();
        }

        Bukkit.getLogger().log(Level.INFO, "[T+-] Plugin loaded.");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        if(Terraplusminus.config.getBoolean("nms")){
            if (event.getWorld().getGenerator() instanceof RealWorldGenerator) {
                injector.attemptInject(event.getWorld());
            }
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id){
       return new RealWorldGenerator(this);
    }




}