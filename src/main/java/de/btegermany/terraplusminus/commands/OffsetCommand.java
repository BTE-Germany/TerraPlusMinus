package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.lang.Math.round;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.bukkit.ChatColor.*;

public class OffsetCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String... arguments) {

        String prefix = Terraplusminus.config.getString("prefix");

        if (!sender.hasPermission("t+-.offset")) {
            sender.sendMessage(Terraplusminus.config.getString("prefix") + GRAY + "No permission for /offset");
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

        sender.sendMessage(prefix + GRAY + "Offsets for world \"" + DARK_GRAY + world.getName() + GRAY + "\":");
        sender.sendMessage(prefix + GRAY + " | X: " + DARK_GRAY + offsetX);
        sender.sendMessage(prefix + GRAY + " | Y: " + DARK_GRAY + offsetY);
        sender.sendMessage(prefix + GRAY + " | Z: " + DARK_GRAY + offsetZ);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length > 1 || !sender.hasPermission("t+-.offset")) {
            return emptyList();
        }

        return Bukkit.getWorlds().stream()
                .filter(Terraplusminus::isTerraWorld)
                .map(World::getName)
                .collect(toList());
    }

}
