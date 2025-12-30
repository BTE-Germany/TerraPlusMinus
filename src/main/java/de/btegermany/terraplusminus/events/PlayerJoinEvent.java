package de.btegermany.terraplusminus.events;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
import de.btegermany.terraplusminus.utils.Properties;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jspecify.annotations.NonNull;

public class PlayerJoinEvent implements Listener {
    final PlayerHashMapManagement playerHashMapManagement;
    final Terraplusminus plugin;

    public PlayerJoinEvent(PlayerHashMapManagement playerHashMapManagement, Terraplusminus plugin) {
        this.playerHashMapManagement = playerHashMapManagement;
        this.plugin = plugin;
    }

    @EventHandler
    private void onPlayerJoin(org.bukkit.event.player.@NonNull PlayerJoinEvent event) {
        if (plugin.getRegisteredServerName() == null && plugin.getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED)
                && plugin.getConfig().getString(Properties.LINKED_WORLDS_METHOD, "").equalsIgnoreCase(Properties.NonConfigurable.METHOD_SRV)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getComponentLogger().info("Sending plugin message to BungeeCord to get server name.");
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("GetServer");
                event.getPlayer().sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            }, 20);
        }
        if (playerHashMapManagement.containsPlayer(event.getPlayer())) {
            event.getPlayer().performCommand("tpll " + playerHashMapManagement.getCoordinates(event.getPlayer()));
            playerHashMapManagement.removePlayer(event.getPlayer());
        }
    }
}
