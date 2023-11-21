package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import io.papermc.lib.PaperLib;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import static java.lang.String.valueOf;
import static org.bukkit.ChatColor.BOLD;


public class PlayerMoveEvent implements Listener {

    private BukkitRunnable runnable;
    private ArrayList<Integer> taskIDs = new ArrayList<>();
    private int yOffset;
    final int yOffsetConfigEntry;

    private final int xOffset;
    private final int zOffset;
    private final boolean linkedWorldsEnabled;

    private final String linkedWorldsMethod;
    private Plugin plugin;
    private List<LinkedWorld> worlds;
    private HashMap<String, Integer> worldHashMap;

    public PlayerMoveEvent(Plugin plugin) {
        this.plugin = plugin;
        this.xOffset = Terraplusminus.config.getInt("terrain_offset.x");
        this.yOffsetConfigEntry = Terraplusminus.config.getInt("terrain_offset.y");
        this.zOffset = Terraplusminus.config.getInt("terrain_offset.z");
        this.linkedWorldsEnabled = Terraplusminus.config.getBoolean("linked_worlds.enabled");
        this.linkedWorldsMethod = Terraplusminus.config.getString("linked_worlds.method");
        this.worldHashMap = new HashMap<>();
        if (this.linkedWorldsEnabled && this.linkedWorldsMethod.equalsIgnoreCase("MULTIVERSE")) {
            this.worlds = ConfigurationHelper.getWorlds();
            for (LinkedWorld world : worlds) {
                this.worldHashMap.put(world.getWorldName(), world.getOffset());
            }
            Bukkit.getLogger().log(Level.INFO, "[T+-] Linked worlds enabled, using Multiverse method.");
        } /*
        else {
            for (World world : Bukkit.getServer().getWorlds()) { // plugin loaded before worlds initialized, so that does not work
                this.worldHashMap.put(world.getName(), yOffsetConfigEntry);
            }
        }
        */
        this.startKeepActionBarAlive();
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        setHeightInActionBar(player);
    }

    private void startKeepActionBarAlive() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                setHeightInActionBar(p);
            }
        }, 0, 20);
    }

    private void setHeightInActionBar(Player p) {
        worldHashMap.putIfAbsent(p.getWorld().getName(), yOffsetConfigEntry);
        if (p.getInventory().getItemInMainHand().getType() != Material.DEBUG_STICK) {
            int height = p.getLocation().getBlockY() - worldHashMap.get(p.getWorld().getName());
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(BOLD + valueOf(height) + "m"));
        }
    }

    @EventHandler
    void onPlayerFall(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!this.linkedWorldsEnabled && !this.linkedWorldsMethod.equalsIgnoreCase("MULTIVERSE")) {
            return;
        }

        Player p = event.getPlayer();
        World world = p.getWorld();
        Location location = p.getLocation();

        // Verzögerte Teleportation
        new BukkitRunnable() {
            @Override
            public void run() {
                // Teleport player from world to world
                if (p.getLocation().getY() < 0) {
                    LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(world.getName());
                    if (previousServer != null) {
                        teleportPlayer(previousServer, location, p);
                    }
                } else if (p.getLocation().getY() > world.getMaxHeight()) {
                    LinkedWorld nextServer = ConfigurationHelper.getNextServerName(world.getName());
                    if (nextServer != null) {
                        teleportPlayer(nextServer, location, p);
                    }
                }
            }
        }.runTaskLater(plugin, 60L);
    }

    private void teleportPlayer(LinkedWorld linkedWorld, Location location, Player p) {
        World tpWorld = Bukkit.getWorld(linkedWorld.getWorldName());
        Location newLocation = new Location(tpWorld, location.getX() + xOffset, tpWorld.getMinHeight(), location.getZ() + zOffset, location.getYaw(), location.getPitch());
        PaperLib.teleportAsync(p, newLocation);
        p.setFlying(true);
        p.sendMessage(Terraplusminus.config.getString("prefix") + "§7You have been teleported to another world.");
    }
}
