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

import java.util.ArrayList;


public class PlayerMoveEvent implements Listener {

    BukkitRunnable runnable;
    ArrayList<Integer> taskIDs = new ArrayList<>();
    int yOffset;
    Plugin plugin;

    public PlayerMoveEvent(Plugin plugin) {
        this.plugin = plugin;
        this.yOffset = Terraplusminus.config.getInt("terrain_offset.y");
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!p.getInventory().getItemInMainHand().getType().toString().equalsIgnoreCase("DEBUG_STICK")) {
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
}
