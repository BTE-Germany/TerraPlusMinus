package de.btegermany.terraplusminus.events;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.PlayerHashMapManagement;
import de.btegermany.terraplusminus.utils.Properties;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;


public class PluginMessageEvent implements PluginMessageListener {

    PlayerHashMapManagement playerHashMapManagement;
    Terraplusminus tpm;

    public PluginMessageEvent(PlayerHashMapManagement playerHashMapManagement, Terraplusminus tpm) {
        this.playerHashMapManagement = playerHashMapManagement;
        this.tpm = tpm;
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte @NonNull [] message) {
        tpm.getComponentLogger().debug("Received plugin message on channel: {}", channel);
        if (channel.equals(Properties.NonConfigurable.CROSS_TELEPORTATION_CHANNEL)) {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            try {
                UUID playerUUID = UUID.fromString(in.readUTF());
                Player targetPlayer = Bukkit.getPlayer(playerUUID);
                String coordinates = in.readUTF();
                if (targetPlayer == null) {
                    // The proxy sends this the moment it hands the player over, so it regularly
                    // arrives before they finish connecting. Park the target until they join.
                    playerHashMapManagement.addPlayer(playerUUID, coordinates);
                } else {
                    // online
                    targetPlayer.performCommand("tpll " + coordinates);
                }
            } catch (IOException e) {
                tpm.getComponentLogger().warn("Failed to read plugin message", e);
            }
        } else if (("BungeeCord".equals(channel) || "bungeecord:main".equals(channel))
                && tpm.getRegisteredServerName() == null) {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            if (subchannel.equals("GetServer")) {
                String serverName = in.readUTF();
                tpm.setRegisteredServerName(serverName);
                tpm.getComponentLogger().info("Registered server name: {}", serverName);
            }
        }
    }
}
