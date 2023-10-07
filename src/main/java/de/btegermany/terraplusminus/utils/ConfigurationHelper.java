package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.Terraplusminus;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ConfigurationHelper {

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

    private static List<String> getList() {
        return (List<String>) Terraplusminus.config.getList("linked_worlds.worlds");
    }

    public static String getNextServerName(String currentWorldName) {
        List<String> servers = getList();
        int index = servers.indexOf("current_world/server");
        String servername;
        if (currentWorldName.equalsIgnoreCase("world")) {
            servername = servers.get(index + 1);
        } else {
            return "world, 0";
        }
        if (servername.equals("another_world/server")) {
            return null;
        } else {
            return servername;
        }
    }

    public static String getLastServerName(String currentWorldName) {
        List<String> servers = getList();
        int index = servers.indexOf("current_world/server");
        String servername;
        if (currentWorldName.equalsIgnoreCase("world")) {
            servername = servers.get(index - 1);
        } else {
            return "world, 0";
        }
        if (servername.equals("another_world/server")) {
            return null;
        } else {
            return servername;
        }
    }

}
