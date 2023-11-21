package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WhereCommand implements CommandExecutor {

    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("where")) {
            if (!(commandSender instanceof Player)) {
                commandSender.sendMessage("This command can only be used by players!");
                return true;
            }
            Player player = (Player) commandSender;
            if (!player.hasPermission("t+-.where")) {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /where");
                return true;
            }
            int xOffset = Terraplusminus.config.getInt("terrain_offset.x");
            int zOffset = Terraplusminus.config.getInt("terrain_offset.z");

            double[] mcCoordinates = new double[2];
            mcCoordinates[0] = player.getLocation().getX() - xOffset;
            mcCoordinates[1] = player.getLocation().getZ() - zOffset;
            System.out.println(mcCoordinates[0] + ", " + mcCoordinates[1]);
            double[] coordinates = new double[0];
            try {
                coordinates = bteGeneratorSettings.projection().toGeo(mcCoordinates[0], mcCoordinates[1]);
            } catch (OutOfProjectionBoundsException e) {
                e.printStackTrace();
            }
            TextComponent message = new TextComponent(Terraplusminus.config.getString("prefix") + "§7Your coordinates are:");
            message.addExtra("\n§8" + coordinates[1] + ", " + coordinates[0] + "§7.");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://maps.google.com/maps?t=k&q=loc:" + coordinates[1] + "+" + coordinates[0]));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7Click here to view in Google Maps.").create()));
            player.spigot().sendMessage(message);
        }
        return true;
    }
}
