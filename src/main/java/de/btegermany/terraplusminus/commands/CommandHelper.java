package de.btegermany.terraplusminus.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNullElseGet;

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

    /**
     * Checks if a collection of entities is comprised solely of a single command sender (which may appear multiple times).
     * This is useful for permission checks where one might allow a sender to execute a command on themselves,
     * but not on other entities.
     *
     * @param sender    the {@link CommandSender sender} executing the command
     * @param targets   the {@link Collection collection} of targets
     *
     * @return whether a {@link CommandSender sender} is the only entity in a {@link Collection collection} of targets
     */
    public static boolean senderIsSoleTarget(CommandSender sender, @NotNull Collection<Entity> targets) {
        return !targets.stream().allMatch(target -> target == sender);
    }

    public static String formatTargetName(Entity target) {
        return requireNonNullElseGet(target.getCustomName(), target::getName);
    }

    public static class InvalidTargetSelectorException extends Exception {

    }

    private CommandHelper() {
        throw new IllegalStateException();
    }

}
