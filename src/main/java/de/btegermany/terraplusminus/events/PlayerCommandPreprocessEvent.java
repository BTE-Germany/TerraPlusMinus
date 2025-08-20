package de.btegermany.terraplusminus.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerCommandPreprocessEvent implements Listener {
    private final String pluginName;

    public PlayerCommandPreprocessEvent(String pluginName) {
        this.pluginName = pluginName;
    }

    @EventHandler
    public void onCommand(@NotNull org.bukkit.event.player.PlayerCommandPreprocessEvent command) {
        if (command.getMessage().startsWith("/tpll")) {
            command.setMessage(pluginName + ": " + command.getMessage().substring(1));
        }
    }
}
