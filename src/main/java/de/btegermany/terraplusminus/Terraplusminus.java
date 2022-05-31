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
        Bukkit.getLogger().log(Level.INFO, "\n╭━━━━╮\n" +
                "┃╭╮╭╮┃\n" +
                "╰╯┃┃┣┻━┳━┳━┳━━╮╭╮\n" +
                "╱╱┃┃┃┃━┫╭┫╭┫╭╮┣╯╰┳━━╮\n" +
                "╱╱┃┃┃┃━┫┃┃┃┃╭╮┣╮╭┻━━╯\n" +
                "╱╱╰╯╰━━┻╯╰╯╰╯╰╯╰╯");


        Bukkit.getPluginManager().registerEvents(this,this);
       // Bukkit.getPluginManager().registerEvents(new PlayerBlockPlacingEvent(), this);

        Objects.requireNonNull(getCommand("tpll")).setExecutor(new TpllCommand());

        config = new FileBuilder("plugins/TerraPlusMinus", "config.yml")
                .addDefault("prefix","§2§lT+- §8» ")
                .addDefault("nms","false")
                .addDefault("min-height", -64)
                .addDefault("max-height", 2032)
                .addDefault("useBiomes", true)
                .addDefault("generateTrees",true)
                .addDefault("moveTerrain",0)
                .copyDefaults(true).save();

     /*   ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(
                new PacketAdapter(this, ListenerPriority.NORMAL,
                        PacketType.Play.Server.MAP_CHUNK) {


                    @Override
                    public void onPacketSending(PacketEvent event) {

                        if (event.getPacketID() == 0x22) { //event.getPacketID() == 0x22
                            PacketContainer packet = event.getPacket();

                            ClientboundLevelChunkPacketData oldChunkData = (ClientboundLevelChunkPacketData) packet.getModifier().read(2);
                            byte[] chunkSections = oldChunkData.a().b();

                            System.out.println("ByteArray: "+ Arrays.toString(chunkSections));

                            byte[] newChunkSections = new byte[3];

                            newChunkSections[0] = chunkSections[chunkSections.length-3];
                            newChunkSections[1] = chunkSections[chunkSections.length-2];
                            newChunkSections[2] = chunkSections[chunkSections.length-1];

                            PacketDataSerializer updatedData = new PacketDataSerializer(null);


                            updatedData.writeNbt(oldChunkData.getHeightmaps());

                            updatedData.writeInt(newChunkSections.length);
                            updatedData.writeBytes(newChunkSections);

                            ClientboundLevelChunkPacketData newChunkData = new ClientboundLevelChunkPacketData(updatedData, i , j); //I have no clue about this integers, I'll investigate a bit but not now
                            event.getPacket().getModifier().write(2,newChunkData);

                            try {
                                event.getPacket().getModifier().write(2, newPacketDataSerializer);
                                event.getPlayer().sendMessage("Changed chunk!");
                            }catch(Exception e){
                                e.printStackTrace();
                            }


                     }
                    }
                });
        protocolManager.addPacketListener(
                new PacketAdapter(this, ListenerPriority.NORMAL,
                        PacketType.Play.Client.POSITION) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {

                        if(event.getPacketType() == PacketType.Play.Client.POSITION){
                            PacketContainer packet = event.getPacket();
                            double height = packet.getDoubles().read(1);
                            //System.out.println("Höhe: "+height);


                        }
                    }
                });
*/
        if(Terraplusminus.config.getBoolean("nms")) {
            try {
                injector = new NMSInjector();

            } catch (IllegalArgumentException | SecurityException e) {
                e.printStackTrace();
            }
            Bukkit.getLogger().log(Level.INFO,"[T+-] §4Activated height expansion");
        }else{
            Bukkit.getLogger().log(Level.INFO,"[T+-] §4Deactivated height expansion");
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
                injector.attemptInject(event.getWorld());
            }
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id){
       return new RealWorldGenerator(this);
    }

}