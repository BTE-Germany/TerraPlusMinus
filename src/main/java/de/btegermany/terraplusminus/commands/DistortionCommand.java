package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DistortionCommand implements BasicCommand {
    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (!(stack.getSender() instanceof Player)) {
            stack.getSender().sendMessage("This command can only be used by players!");
            return;
        }
        Player player = (Player) stack.getSender();
        if (!player.hasPermission("t+-.distortion")) {
            player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /distortion");
            return;
        }

        int xOffset = Terraplusminus.config.getInt("terrain_offset.x");
        int zOffset = Terraplusminus.config.getInt("terrain_offset.z");

        double playerX = player.getLocation().getX() - xOffset;
        double playerZ = player.getLocation().getZ() - zOffset;

        double[] c = new double[0];
        TextComponent message = new TextComponent(Terraplusminus.config.getString("prefix"));

        try {
            c = bteGeneratorSettings.projection().toGeo(playerX, playerZ);
            c = bteGeneratorSettings.projection().tissot(c[0], c[1], 0.00001);
            message.addExtra("§7Your distortion is:");
            message.addExtra("\n§8" + Math.sqrt(Math.abs(c[0])) + ", " + c[1] * 180.0 / Math.PI + "§7.");
        } catch (OutOfProjectionBoundsException e) {
            message.addExtra(ChatColor.RED + "You are currently outside of the world's projection and your location in the Minecraft world has no equivalent on Earth.");
        }

        player.spigot().sendMessage(message);
    }
}