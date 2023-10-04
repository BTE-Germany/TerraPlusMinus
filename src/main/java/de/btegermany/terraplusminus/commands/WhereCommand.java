package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

import static org.bukkit.ChatColor.RED;

public class WhereCommand implements CommandExecutor {

    private static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("##.#####");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        String prefix = Terraplusminus.config.getString("prefix");
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        if (!sender.hasPermission("t+-.where")) {
            sender.sendMessage(prefix + "§7No permission for /where");
            return true;
        }

        Player player = (Player) sender;
        World world = player.getWorld();
        ChunkGenerator chunkGenerator = world.getGenerator();

        if (!(chunkGenerator instanceof RealWorldGenerator)) {
            sender.sendMessage(prefix + RED + "Not a Terra+- world.");
            return true;
        }

        RealWorldGenerator generator = (RealWorldGenerator) chunkGenerator;
        GeographicProjection projection = generator.getSettings().projection();

        final TextComponent message = new TextComponent(prefix);
        try {
            Location location = player.getLocation();
            double[] coordinates = projection.toGeo(location.getX(), location.getZ());
            double longitude = coordinates[0];
            double latitude = coordinates[1];
            String googleMapsUrl = "https://www.google.com/maps/@" + latitude + "," + longitude;
            message.addExtra("§7Your coordinates are:\n§8" + DECIMAL_FORMATTER.format(latitude) + ", " + DECIMAL_FORMATTER.format(longitude) + "§7.");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, googleMapsUrl));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7Click here to view in Google Maps.")));
        } catch (OutOfProjectionBoundsException e) {
            message.addExtra(RED + "Outside projection bounds.");
        }
        player.spigot().sendMessage(message);
        return true;
    }
}
