package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.TerraConnector;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import static org.bukkit.Bukkit.createChunkData;

public class ReGenCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("regen")){
            Player player = (Player) commandSender;
            if (args.length == 0) {
                if (player.hasPermission("t+-.regen")) {


                    RealWorldGenerator real = new RealWorldGenerator();
                    real.regenerateSurface(player.getWorld(), player.getChunk().getX(), player.getChunk().getZ(), player);

                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Succesfully regened.");
                    return true;
                } else {
                    player.sendMessage(Terraplusminus.config.getString("prefix") + "§7No permission for /tpll");
                    return true;
                }
            }else {
                player.sendMessage(Terraplusminus.config.getString("prefix") + "§7Usage: /tpll <longitudes> <latitudes>");
                return true;
            }
        }
        return true;
    }
}
