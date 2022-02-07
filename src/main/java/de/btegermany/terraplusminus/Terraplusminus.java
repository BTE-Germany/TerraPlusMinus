package de.btegermany.terraplusminus;

import de.btegermany.terraplusminus.gen.*;
import de.btegermany.terraplusminus.utils.FileBuilder;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.entity.Player;

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
        Bukkit.getLogger().log(Level.INFO, "Plugin loaded.");
        Bukkit.getPluginManager().registerEvents(this,this);

        config = new FileBuilder("plugins/TerraPlusMinus", "config.yml")
                .addDefault("min-height", 2032)
                .addDefault("max-height", 2032)
                .copyDefaults(true).save();

        try {
            injector = new NMSInjector();

        } catch (IllegalArgumentException | SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        if (event.getWorld().getGenerator() instanceof RealWorldGenerator) {
            injector.attemptInject(event.getWorld());
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id){
       //  ChunkGenerator res = new ChunkGenerator() {

       //  };
        return new RealWorldGenerator();
    }




}