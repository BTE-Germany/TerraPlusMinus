package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.Terraplusminus;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;


public class PlayerMoveEvent implements Listener {

    BukkitRunnable runnable;
    int move;
    Plugin plugin;

    public PlayerMoveEvent(Plugin pPlugin){
        plugin = pPlugin;
        move = Terraplusminus.config.getInt("moveTerrain");
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event){


            runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    Player p = event.getPlayer();
                    int height = p.getLocation().getBlockY()-move;
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§l"+height+"m"));
                }
            };

            runnable.runTaskTimer(plugin, 40,20);


    }
}
