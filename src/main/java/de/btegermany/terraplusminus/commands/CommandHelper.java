package de.btegermany.terraplusminus.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

/**
 * Utility class to help with commands.
 *
 * @author Smyler
 */
public final class CommandHelper {

    /**
     * Parses a <a href="https://minecraft.fandom.com/wiki/Target_selectors">command target selector</a> like '@a'.
     * Currently supported selectors:
     * <ul>
     *     <li><code>@a</code></li>
     *     <li><code>@p</code> (doesn't select the sender)</li>
     * </ul>
     * Target selector arguments are not supported.
     * It's a shame that Bukkit does not expose the vanilla command system...
     *
     * @param sender    the {@link CommandSender command sender}
     * @param selector  the selector string
     * @return the collection of {@link Entity entities} that match the selector
     *
     * @throws InvalidTargetSelectorException if the selector is invalid, either syntactically or in the specific context
     */
    public static Collection<Entity> parseTargetSelector(@NotNull CommandSender sender, String selector) throws InvalidTargetSelectorException {
        if (selector.startsWith("@") && selector.length() >= 2) {
            char selectorChar = selector.charAt(1);
            if (selectorChar == 'a') {
                return Bukkit.getOnlinePlayers().stream()
                        .map(p -> (Entity)p)
                        .collect(Collectors.toList());
            } else if (selectorChar == 'p' && sender instanceof Entity) {
                Entity entitySender = (Entity) sender;
                Location senderLocation = entitySender.getLocation();
                return Collections.singleton(
                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> p != sender)
                                .min(comparing(p -> p.getLocation().distanceSquared(senderLocation)))
                                .orElseThrow(InvalidTargetSelectorException::new)
                );
            }
        } else {
            Player player = Bukkit.getPlayerExact(selector);
            if (player == null || !player.isOnline()) {
                throw new InvalidTargetSelectorException();
            } else {
                return List.of(player);
            }
        }

        throw new InvalidTargetSelectorException();

    }

    public static class InvalidTargetSelectorException extends Exception {

    }

    private CommandHelper() {
        throw new IllegalStateException();
    }

}
