package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

public class PluginMessageEvent implements PluginMessageListener {

    PlayerHashMapManagement playerHashMapManagement;

    public PluginMessageEvent(PlayerHashMapManagement playerHashMapManagement) {
        this.playerHashMapManagement = playerHashMapManagement;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (channel.equals("bungeecord:terraplusminus")) {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            try {
                UUID playerUUID = UUID.fromString(in.readUTF());
                Player targetPlayer = Bukkit.getPlayer(playerUUID);
                String coordinates = in.readUTF();
                if (targetPlayer == null) {
                    // not online
                    playerHashMapManagement.addPlayer(player, coordinates);
                } else {
                    // online
                    targetPlayer.chat("/tpll " + coordinates);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
