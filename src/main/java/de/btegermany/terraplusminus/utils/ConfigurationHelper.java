package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.Terraplusminus;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ConfigurationHelper {
    private static final List<LinkedWorld> worlds = convertList(Terraplusminus.config.getMapList("linked_worlds.worlds"));

    /**
     * Returns a material from the configuration,
     * or a default value if the configuration path is either missing or the value is not a valid material identifier.
     *
     * @param config        the configuration file to read from
     * @param path          the configuration path to retrieve
     * @param defaultValue  a default value to return if the value is missing from the config or invalid
     * @return a {@link Material} from the configuration, or {@code defaultValue} as a fallback
     */
    public static Material getMaterial(@NotNull FileConfiguration config, @NotNull String path, Material defaultValue) {
        String materialName = config.getString(path);
        if (materialName == null) {
            return defaultValue;
        }
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            return defaultValue;
        }
        return material;
    }

    private ConfigurationHelper() {
        throw new IllegalStateException();
    }

    public static List<LinkedWorld> convertList(List<Map<?, ?>> originalList) {
        return originalList.stream()
                .map(ConfigurationHelper::convertMapToLinkedWorld)
                .filter(world -> !world.getWorldName().equalsIgnoreCase("another_world/server") || !world.getWorldName().equalsIgnoreCase("current_world/server"))
                .collect(Collectors.toList());
    }

    private static LinkedWorld convertMapToLinkedWorld(Map<?, ?> originalMap) {
        String worldName = originalMap.get("name").toString();
        int offset = (Integer) originalMap.get("offset");
        return new LinkedWorld(worldName, offset);
    }

    public static LinkedWorld getNextServerName(String currentWorldName) {
        int currentIndex = -1;

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
            return null;
        }
    }

    public static LinkedWorld getPreviousServerName(String currentWorldName) {
        int currentIndex = -1;

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
            return null;
        }
    }

    public static List<LinkedWorld> getWorlds() {
        return worlds;
    }

}
