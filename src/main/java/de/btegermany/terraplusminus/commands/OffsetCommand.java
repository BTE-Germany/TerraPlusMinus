package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OffsetCommand implements BasicCommand {
    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (stack.getSender() instanceof Player player) {
            if (!player.hasPermission("t+-.offset")) {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /offset");
                return;
            }
            player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Offsets:");
            player.sendMessage(Terraplusminus.config.getString("prefix") + "§7 | X: §8" + Terraplusminus.config.getInt("terrain_offset.x"));

            if (!Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE") || !Terraplusminus.config.getBoolean("linked_worlds.enabled")) {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7 | Y: §8" + Terraplusminus.config.getInt("terrain_offset.y"));
            } else {
                if (Terraplusminus.config.getBoolean("linked_worlds.enabled") && Terraplusminus.config.getString("linked_worlds.method").equalsIgnoreCase("MULTIVERSE")) {
                    ConfigurationHelper.getWorlds().forEach(world -> player.sendMessage(Terraplusminus.config.getString("prefix") + "§9 " + world.getWorldName() + "§7 | Y: §8" + world.getOffset()));
                }
            }

            player.sendMessage(Terraplusminus.config.getString("prefix") + "§7 | Z: §8" + Terraplusminus.config.getInt("terrain_offset.z"));
        }
    }
}
