package de.btegermany.terraplusminus.events;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
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
        if (plugin.getRegisteredServerName() == null) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GetServer");
            event.getPlayer().sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
        if (playerHashMapManagement.containsPlayer(event.getPlayer())) {
            event.getPlayer().chat("/tpll " + playerHashMapManagement.getCoordinates(event.getPlayer()));
            playerHashMapManagement.removePlayer(event.getPlayer());
        }
    }
}
