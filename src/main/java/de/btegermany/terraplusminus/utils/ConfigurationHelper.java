package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.Terraplusminus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigurationHelper {

    private static final Set<String> PLACEHOLDER_NAMES =
            Set.of("world/server", "another_world/server", "current_world/server");

    private static volatile List<LinkedWorld> worlds = List.of();

    private ConfigurationHelper() {
        throw new IllegalStateException();
    }

    private static List<LinkedWorld> convertList(@NonNull List<Map<?, ?>> originalList) {
        return originalList.stream()
                .map(ConfigurationHelper::convertMapToLinkedWorld)
                .filter(Objects::nonNull)
                .filter(world -> !PLACEHOLDER_NAMES.contains(world.getWorldName().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private static @Nullable LinkedWorld convertMapToLinkedWorld(@NonNull Map<?, ?> originalMap) {
        Object name = originalMap.get("name");
        Object offset = originalMap.get("offset");
        if (name == null || !(offset instanceof Number number)) {
            Terraplusminus.instance.getComponentLogger().warn("Skipping invalid linked world entry {} (requires 'name' and numeric 'offset')", originalMap);
            return null;
        }
        return new LinkedWorld(name.toString(), number.intValue());
    }

    /**
     * Parses the linked worlds list from the config.
     */
    public static void load() {
        worlds = List.copyOf(convertList(Terraplusminus.instance.getConfig().getMapList("linked_worlds.worlds")));
    }

    private static List<LinkedWorld> getWorldsList() {
        return worlds;
    }

    public static @Nullable LinkedWorld getNextServerName(String currentWorldName) {
        List<LinkedWorld> worlds = getWorldsList();
        int currentIndex = -1;

        Terraplusminus.instance.getComponentLogger().debug("Searching for world: '{}' in linked worlds list", currentWorldName);
        for (LinkedWorld w : worlds) {
            Terraplusminus.instance.getComponentLogger().debug("Available world in config: '{}'", w.getWorldName());
        }

        for (int i = 0; i < worlds.size(); i++) {
            LinkedWorld world = worlds.get(i);
            if (world.getWorldName().equalsIgnoreCase(currentWorldName)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex >= 0 && currentIndex < worlds.size() - 1) {
            return worlds.get(currentIndex + 1);
        } else {
            // Entweder wurde die Welt nicht gefunden oder sie ist die letzte Welt in der Liste
            Terraplusminus.instance.getComponentLogger().warn("World after '{}' not found in linked worlds configuration", currentWorldName);
            return null;
        }
    }

    public static @Nullable LinkedWorld getPreviousServerName(String currentWorldName) {
        List<LinkedWorld> worlds = getWorldsList();
        int currentIndex = -1;

        Terraplusminus.instance.getComponentLogger().debug("Searching for world: '{}' in linked worlds list", currentWorldName);
        for (LinkedWorld w : worlds) {
            Terraplusminus.instance.getComponentLogger().debug("Available world in config: '{}'", w.getWorldName());
        }

        for (int i = 0; i < worlds.size(); i++) {
            LinkedWorld world = worlds.get(i);
            if (world.getWorldName().equalsIgnoreCase(currentWorldName)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex > 0) {
            return worlds.get(currentIndex - 1);
        } else {
            // Entweder wurde die Welt nicht gefunden oder sie ist die erste Welt in der Liste
            Terraplusminus.instance.getComponentLogger().warn("World after '{}' not found in linked worlds configuration", currentWorldName);
            return null;
        }
    }

    public static List<LinkedWorld> getWorlds() {
        return getWorldsList();
    }

}
