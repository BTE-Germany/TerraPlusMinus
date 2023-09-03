package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerJoinEvent implements Listener {
    PlayerHashMapManagement playerHashMapManagement;

    public PlayerJoinEvent(PlayerHashMapManagement playerHashMapManagement) {
        this.playerHashMapManagement = playerHashMapManagement;
    }

    @EventHandler
    private void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (playerHashMapManagement.containsPlayer(event.getPlayer())) {
            event.getPlayer().chat("/tpll " + playerHashMapManagement.getCoordinates(event.getPlayer()));
            playerHashMapManagement.removePlayer(event.getPlayer());
        }
    }
}
