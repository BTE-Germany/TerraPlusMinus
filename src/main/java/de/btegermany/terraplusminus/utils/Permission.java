package de.btegermany.terraplusminus.utils;

import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public enum Permission {
    DISTORTION_CMD(Properties.NonConfigurable.PERMISSION_PREFIX + "distortion"),
    OFFSET_CMD(Properties.NonConfigurable.PERMISSION_PREFIX + "offset"),
    WHERE_CMD(Properties.NonConfigurable.PERMISSION_PREFIX + "where"),
    TPLL_CMD(Properties.NonConfigurable.PERMISSION_PREFIX + "tpll"),
    TPLL_OTHER_WORLD_CMD(TPLL_CMD.node + ".otherWorld"),
    TPLL_UNGENERATED_CHUNKS(TPLL_CMD.node + ".ungenerated-chunks"),
    FORCETPLL_CMD(Properties.NonConfigurable.PERMISSION_PREFIX + "forcetpll"),
    ADMIN(Properties.NonConfigurable.PERMISSION_PREFIX + "admin"),
    AUTOTELEPORT(Properties.NonConfigurable.PERMISSION_PREFIX + "autoteleport"),
    UPDATENOTIFY(Properties.NonConfigurable.PERMISSION_PREFIX + "notify.update");

    @Getter
    private final String node;

    Permission(String node) {
        this.node = node;
    }

    public boolean isGrantedTo(@NonNull CommandSender sender) {
        return sender.hasPermission(node);
    }

    public boolean isGrantedTo(@NonNull Player player) {
        return player.hasPermission(node);
    }
}
