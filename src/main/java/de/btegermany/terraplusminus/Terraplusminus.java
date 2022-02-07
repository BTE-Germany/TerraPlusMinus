package de.btegermany.terraplusminus;

import de.btegermany.terraplusminus.gen.*;
import de.btegermany.terraplusminus.utils.FileBuilder;
import net.buildtheearth.terraminusminus.TerraConstants;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
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
import com.fasterxml.jackson.core.JsonProcessingException;



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


        try {
            final String bteProjectionJson = "{\n"
                    + "        \"scale\": {\n"
                    + "            \"delegate\": {\n"
                    + "                \"flip_vertical\": {\n"
                    + "                    \"delegate\": {\n"
                    + "                        \"bte_conformal_dymaxion\": {}\n"
                    + "                    }\n"
                    + "                }\n"
                    + "            },\n"
                    + "            \"x\": 7318261.522857145,\n"
                    + "            \"y\": 7318261.522857145\n"
                    + "        }\n"
                    + "    }";
            GeographicProjection projection = TerraConstants.JSON_MAPPER.readValue(bteProjectionJson, GeographicProjection.class);

            double[] geos = {
                    2.350987d, 48.856667d,
                    -74.005974d, 40.714268d,
                    -0.166670d, 51.500000d,
                    116.397230d, 39.907500d,
                    -122.332070, 47.606210d,
                    151.208666d, -33.875113d,
                    2.295026d, 48.87378100000001d,
                    2.236214, 48.8926507,
                    2.349270d, 48.853474d,
                    2.348969d, 48.853065d
            };
            double[] mcs = {
                    2851660.278582057, -5049718.243628887,
                    -8526456.75523275, -6021812.714103152,
                    2774758.1546624764, -5411708.236500686,
                    11571988.173618957, -6472387.375809908,
                    -12410431.110669583, -6894851.702710003,
                    20001061.636216827, -2223355.8371363534,
                    2848192.3338641203, -5053053.018157968,
                    2844585.5271490104, -5056657.959395678,
                    2851410.680220599, -5049403.7778784195,
                    2851372.726732094, -5049365.549214174
            };

            for (int i = 0; i < geos.length / 2; i++) {
                double lon = geos[i * 2];
                double lat = geos[i*2 + 1];
                double x = mcs[i * 2];
                double z = mcs[i * 2 + 1];
                double[] lola = projection.toGeo(x, z);
                double[] xz = projection.fromGeo(lon, lat);
            }
        } catch (Exception e) {
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