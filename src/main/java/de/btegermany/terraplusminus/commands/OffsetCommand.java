package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import static java.lang.Math.round;
import static org.bukkit.ChatColor.GRAY;
import static org.bukkit.ChatColor.RED;

public class OffsetCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String... arguments) {

        String prefix = Terraplusminus.config.getString("prefix");

        if (!sender.hasPermission("t+-.offset")) {
            sender.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /offset");
            return true;
        }

        World world;
        if (arguments.length == 1) {
            String worldName = arguments[0];
            world = Bukkit.getWorld(worldName);
        } else if (arguments.length == 0 && sender instanceof Player) {
            Player player = (Player) sender;
            world = player.getWorld();
        } else {
            return false;
        }

        if (world == null) {
            sender.sendMessage(prefix + RED + "No such world");
            return true;
        }

        ChunkGenerator generator = world.getGenerator();
        if (!(generator instanceof RealWorldGenerator)) {
            sender.sendMessage(prefix + GRAY + world.getName() + RED + " is not a Terra+- world");
            return true;
        }
        RealWorldGenerator realWorldGenerator = (RealWorldGenerator) generator;
        GeographicProjection projection = realWorldGenerator.getSettings().projection();

        int offsetY = realWorldGenerator.getYOffset();
        int offsetX, offsetZ;
        if (projection instanceof OffsetProjectionTransform) {
            OffsetProjectionTransform transform = (OffsetProjectionTransform) projection;
            // We assume there is ony one offset transform, or that it is the only one the user cares about
            // We can safely round to int as we are only dealing with blocks at that scale
            offsetX = (int) round(transform.dx());
            offsetZ = (int) round(transform.dy());
        } else {
            offsetX = offsetZ = 0;
        }

        sender.sendMessage(prefix + "§7Offsets for world" + world.getName() + ":");
        sender.sendMessage(prefix + "§7 | X: §8" + offsetX);
        sender.sendMessage(prefix + "§7 | Y: §8" + offsetY);
        sender.sendMessage(prefix + "§7 | Z: §8" + offsetZ);
        return true;
    }
}
