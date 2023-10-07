package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
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


public class PlayerMoveEvent implements Listener {

    BukkitRunnable runnable;
    ArrayList<Integer> taskIDs = new ArrayList<>();
    int yOffset;
    final int yOffsetConfigEntry;

    final int xOffset;
    final int zOffset;
    final boolean linkedWorldsEnabled;

    final String linkedWorldsMethod;
    Plugin plugin;
    String lastServerName;
    String nextServerName;

    public PlayerMoveEvent(Plugin plugin) {
        this.plugin = plugin;
        this.xOffset = Terraplusminus.config.getInt("terrain_offset.x");
        this.yOffsetConfigEntry = Terraplusminus.config.getInt("terrain_offset.y");
        this.zOffset = Terraplusminus.config.getInt("terrain_offset.z");
        this.linkedWorldsEnabled = Terraplusminus.config.getBoolean("linked_worlds.enabled");
        this.linkedWorldsMethod = Terraplusminus.config.getString("linked_worlds.method");
        lastServerName = ConfigurationHelper.getLastServerName("world");
        nextServerName = ConfigurationHelper.getNextServerName("world");
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player p = event.getPlayer();
        // Multiverse support
        if (this.linkedWorldsEnabled && this.linkedWorldsMethod.equalsIgnoreCase("MULTIVERSE")) {
            if (lastServerName != null) {
                if (p.getWorld().getName().equalsIgnoreCase(lastServerName.split(",")[0])) {
                    yOffset = Integer.parseInt(lastServerName.split(",")[1].replace(" ", ""));
                }
            }
            if (nextServerName != null) {
                if (p.getWorld().getName().equalsIgnoreCase(nextServerName.split(",")[0])) {
                    yOffset = Integer.parseInt(nextServerName.split(",")[1].replace(" ", ""));
                }
            }
            if (p.getWorld().getName().equalsIgnoreCase("world")) {
                yOffset = yOffsetConfigEntry;
            }
        } else {
            yOffset = yOffsetConfigEntry;
        }
        if (p.getInventory().getItemInMainHand().getType() != Material.DEBUG_STICK) {
            runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    int height = p.getLocation().getBlockY() - yOffset;
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§l" + height + "m"));
                }
            };
            runnable.runTaskTimer(plugin, 0, 20);
            if (!taskIDs.contains(runnable.getTaskId())) {
                taskIDs.add(runnable.getTaskId());
            }
        } else {
            for (int id : taskIDs) {
                Bukkit.getScheduler().cancelTask(id);
            }
            taskIDs.clear();
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
                    String[] lastServerName = ConfigurationHelper.getLastServerName(world.getName()).split(",");
                    if (lastServerName != null) {
                        World tpWorld = Bukkit.getWorld(lastServerName[0]);
                        Location newLocation = new Location(tpWorld, location.getX() + xOffset, tpWorld.getMaxHeight(), location.getZ() + zOffset, location.getYaw(), location.getPitch());
                        PaperLib.teleportAsync(p, newLocation);
                        p.sendMessage(Terraplusminus.config.getString("prefix") + "§7You have been teleported to another world.");
                    }
                } else if (p.getLocation().getY() > world.getMaxHeight()) {
                    String[] nextServerName = ConfigurationHelper.getNextServerName(world.getName()).split(",");
                    if (nextServerName != null) {
                        World tpWorld = Bukkit.getWorld(nextServerName[0]);
                        Location newLocation = new Location(tpWorld, location.getX() + xOffset, tpWorld.getMinHeight(), location.getZ() + zOffset, location.getYaw(), location.getPitch());
                        PaperLib.teleportAsync(p, newLocation);
                        p.sendMessage(Terraplusminus.config.getString("prefix") + "§7You have been teleported to another world.");
                    }
                }
            }
        }.runTaskLater(plugin, 60L);
    }
}
