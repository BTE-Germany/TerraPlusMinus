package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.geo.LatLng;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.Collection;

import static de.btegermany.terraplusminus.commands.CommandHelper.*;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNullElse;
import static org.bukkit.ChatColor.RED;

public class WhereCommand implements CommandExecutor {

    private static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("##.#####");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        String prefix = Terraplusminus.config.getString("prefix");

        if (!sender.hasPermission("t+-.where")) {
            sender.sendMessage(prefix + "§7No permission for /where");
            return true;
        }

        // Parse targets
        final Collection<Entity> targets;
        if (args.length == 0 && sender instanceof Player) {
            targets = singleton((Player) sender);
        } else if (args.length == 1) {
            try {
                targets = parseTargetSelector(sender, args[0]);
                if (!sender.hasPermission("t+-.admin") && !senderIsSoleTarget(sender, targets)) {
                    String message = requireNonNullElse(command.getPermissionMessage(), RED + "Missing permission");
                    sender.sendMessage(message);
                    return true;
                }
            } catch (CommandHelper.InvalidTargetSelectorException ignored) {
                return false;
            }
        } else {
            return false;
        }

        // Do the magic
        for (Entity target: targets) {
            final TextComponent message = new TextComponent(prefix);
            try {
                LatLng geolocation = this.getGeolocation(target);
                String googleMapsUrl = "https://www.google.com/maps/@" + geolocation.getLat() + "," + geolocation.getLng();
                String targetVerb = target == sender ? "Your": formatTargetName(target) + "'s";
                message.addExtra("§7" + targetVerb + " coordinates are:\n§8" + DECIMAL_FORMATTER.format(geolocation.getLat()) + ", " + DECIMAL_FORMATTER.format(geolocation.getLng()) + "§7.");
                message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, googleMapsUrl));
                message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7Click here to view in Google Maps.")));
            } catch (OutOfProjectionBoundsException e) {
                String targetVerb = target == sender? "You are": formatTargetName(target) + " is";
                message.addExtra(RED + targetVerb + " not in a Terra+- world or outside projection bounds.");
            }
            sender.spigot().sendMessage(message);
        }
        return true;
    }

    private LatLng getGeolocation(Entity target) throws OutOfProjectionBoundsException {
        World world = target.getWorld();
        ChunkGenerator chunkGenerator = world.getGenerator();
        if (!(chunkGenerator instanceof RealWorldGenerator)) {
            throw OutOfProjectionBoundsException.get();
        }
        RealWorldGenerator generator = (RealWorldGenerator) chunkGenerator;
        GeographicProjection projection = generator.getSettings().projection();
        Location location = target.getLocation();
        double[] coordinates = projection.toGeo(location.getX(), location.getZ());
        return new LatLng(coordinates[1], coordinates[0]);
    }

}
