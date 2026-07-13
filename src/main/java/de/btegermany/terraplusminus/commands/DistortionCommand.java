package de.btegermany.terraplusminus.commands;

import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.utils.Permission;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jspecify.annotations.NonNull;

public class DistortionCommand implements BasicCommand {

    @Override
    public boolean canUse(final @NonNull CommandSender sender) {
        return sender instanceof Player && Permission.DISTORTION_CMD.isGrantedTo(sender);
    }

    @Override
    public void execute(@NonNull CommandSourceStack stack, String @NonNull [] args) {
        if (!(stack.getSender() instanceof Player player)) return; // Will not happen because of Brigadier

        World world = player.getWorld();
        ChunkGenerator generator = world.getGenerator();

        if (!(generator instanceof RealWorldGenerator realGen)) {
            player.sendMessage(
                    Component.text("This world is not using Terra+- generator.", NamedTextColor.RED)
            );
            return;
        }

        GeographicProjection projection = realGen.getSettings().projection();

        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();

        try {
            double[] geo = projection.toGeo(x, z);
            double[] tissot = projection.tissot(geo[0], geo[1]);

            /*
             * tissot[] meaning:
             * [0] area distortion
             * [1] angular distortion (radians)
             * [2] max scale
             * [3] min scale
             */

            double areaDistortion = tissot[0];
            double angularDistortionDeg = Math.toDegrees(tissot[1]);
            double maxScale = tissot[2];
            double minScale = tissot[3];

            // More meaningful representation than sqrt(abs(area))
            double averageScale = (maxScale + minScale) / 2.0;

            player.sendMessage(
                    Component.text()
                            .append(Component.text("Distortion Analysis", NamedTextColor.GOLD))
                            .appendNewline()

                            .append(Component.text("Avg scale: ", NamedTextColor.GRAY))
                            .append(Component.text(
                                    String.format("%.6f m/m", averageScale),
                                    NamedTextColor.WHITE
                            ))
                            .appendNewline()

                            .append(Component.text("Max scale: ", NamedTextColor.GRAY))
                            .append(Component.text(
                                    String.format("%.6f", maxScale),
                                    NamedTextColor.WHITE
                            ))
                            .appendNewline()

                            .append(Component.text("Min scale: ", NamedTextColor.GRAY))
                            .append(Component.text(
                                    String.format("%.6f", minScale),
                                    NamedTextColor.WHITE
                            ))
                            .appendNewline()

                            .append(Component.text("Angular distortion: ", NamedTextColor.GRAY))
                            .append(Component.text(
                                    String.format("± %.4f°", angularDistortionDeg),
                                    NamedTextColor.WHITE
                            ))
                            .appendNewline()

                            .append(Component.text("Area factor: ", NamedTextColor.GRAY))
                            .append(Component.text(
                                    String.format("%.6f", areaDistortion),
                                    NamedTextColor.WHITE
                            ))
                            .build()
            );

        } catch (OutOfProjectionBoundsException e) {
            player.sendMessage(
                    Component.text(
                            "You are outside the projection bounds.",
                            NamedTextColor.RED
                    )
            );
        } catch (Exception e) {
            player.sendMessage(
                    Component.text(
                            "Error while computing distortion.",
                            NamedTextColor.DARK_RED
                    )
            );
        }
    }
}
