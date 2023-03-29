package de.btegermany.terraplusminus.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.List;
import java.util.Set;

public class FileBuilder {
    private File file = null;
    private FileConfiguration cfg = null;
    private static Plugin plugin;

    public FileBuilder(String path, String file) {
        this.file = new File(path, file);
        this.cfg = YamlConfiguration.loadConfiguration(this.file);
    }

    public FileBuilder(Plugin plugin) {
        this.plugin = plugin;
    }

    public FileBuilder addDefault(String path, Object value) {
        this.cfg.addDefault(path, value);
        return this;
    }

    public FileBuilder copyDefaults(boolean copyDefaults) {
        this.cfg.options().copyDefaults(copyDefaults);
        return this;
    }

    public FileBuilder set(String path, Object value) {
        this.cfg.set(path, value);
        return this;
    }

    public FileBuilder save() {
        try {
            this.cfg.save(this.file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    public File getFile() {
        return this.file;
    }

    public void reload() {
        try {
            this.cfg.load(this.file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public boolean exists() {
        return this.file.exists();
    }

    public boolean contains(String value) {
        return this.cfg.contains(value);
    }

    public Object getObject(String path) {
        return this.cfg.get(path);
    }

    public String getString(String path) {
        return this.cfg.getString(path);
    }

    public int getInt(String path) {
        return this.cfg.getInt(path);
    }

    public double getDouble(String path) {
        return this.cfg.getDouble(path);
    }

    public long getLong(String path) {
        return this.cfg.getLong(path);
    }

    public boolean getBoolean(String path) {
        return this.cfg.getBoolean(path);
    }

    public List<String> getStringList(String path) {
        return this.cfg.getStringList(path);
    }

    public List<Boolean> getBooleanList(String path) {
        return this.cfg.getBooleanList(path);
    }

    public List<Double> getDoubleList(String path) {
        return this.cfg.getDoubleList(path);
    }

    public List<Integer> getIntegerList(String path) {
        return this.cfg.getIntegerList(path);
    }

    public Set<String> getKeys(boolean keys) {
        return this.cfg.getKeys(keys);
    }

    public ConfigurationSection getConfigurationSection(String section) {
        return this.cfg.getConfigurationSection(section);
    }

    public static void deleteLine(String path) {
        try {
            File inputFile = new File(plugin.getDataFolder() + File.separator + "config.yml");
            File tempFile = new File(plugin.getDataFolder() + File.separator + "temp.yml");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                if (currentLine.contains(path)) {
                    writer.write("");
                } else {
                    writer.write(currentLine + "\n");
                }
            }

            writer.close();
            reader.close();
            inputFile.delete();
            tempFile.renameTo(inputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addLineAbove(String path, String comment) {
        try {
            File inputFile = new File(plugin.getDataFolder() + File.separator + "config.yml");
            File tempFile = new File(plugin.getDataFolder() + File.separator + "temp.yml");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                if (currentLine.contains(path)) {
                    writer.write(comment + "\n");
                }
                writer.write(currentLine + "\n");
            }

            writer.close();
            reader.close();
            inputFile.delete();
            tempFile.renameTo(inputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void editPathValue(String path, String value) {
        try {
            File inputFile = new File(plugin.getDataFolder() + File.separator + "config.yml");
            File tempFile = new File(plugin.getDataFolder() + File.separator + "temp.yml");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                if (currentLine.contains(path)) {
                    writer.write(path + ": " + value + "\n");
                } else {
                    writer.write(currentLine + "\n");
                }
            }
            writer.close();
            reader.close();
            inputFile.delete();
            tempFile.renameTo(inputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void delete() {
        this.file.delete();
    }
}
