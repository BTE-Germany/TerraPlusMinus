package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.Terraplusminus;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FolderMigrator {
    public static void migrateTerraPlusPlusFolder() {
        var logger = Terraplusminus.instance.getComponentLogger();
        // Migrate Terra-- v1 / TerraPlusPlus files if needed
        File terraDir = new File("terraplusplus");
        if (!terraDir.exists()) return;
        Path config = terraDir.toPath().resolve("config");
        File[] entries = config.toFile().listFiles();

        boolean migrationSuccessful = true;

        if (entries != null) {
            logger.info("Migrating Terra-- v1 config from terraplusplus/config");
            for (File entry : entries) {
                Path dest = new File(Terraplusminus.instance.getDataFolder(), entry.getName()).toPath();
                if (!migrateDirectory(entry.toPath(), dest)) {
                    migrationSuccessful = false;
                }
            }
            if (!config.toFile().delete()) {
                logger.warn("Failed to delete terraplusplus/config directory");
            }
        }

        var cacheDir = terraDir.toPath().resolve("cache").toFile();
        if (cacheDir.exists()) {
            try {
                FileUtils.deleteDirectory(cacheDir);
            } catch (IOException e) {
                logger.warn("Failed to delete terraplusplus/cache directory", e);
            }
        }

        if (migrationSuccessful) {
            if (terraDir.delete()) {
                logger.info("Deleted old terraplusplus directory");
            } else {
                logger.warn("Failed to delete terraplusplus directory");
            }
        }
    }

    private static boolean migrateDirectory(Path source, Path dest) {
        var logger = Terraplusminus.instance.getComponentLogger();
        try {
            if (Files.exists(dest))
                if (!Files.exists(dest)) {
                    try {
                        Files.move(source, dest);
                        logger.info("Moved {} -> {}", source, dest);
                        return true;
                    } catch (IOException moveEx) {
                        logger.warn("Move failed, trying copy for {}", source, moveEx);
                        try {
                            if (Files.isDirectory(source)) {
                                FileUtils.copyDirectory(source.toFile(), dest.toFile());
                                FileUtils.deleteDirectory(source.toFile());
                            } else {
                                Files.copy(source, dest);
                                Files.deleteIfExists(source);
                            }
                            logger.info("Copied {} -> {} (fallback)", source, dest);
                            return true;
                        } catch (IOException copyEx) {
                            logger.warn("Failed to migrate {}! Migrate manually!", source, copyEx);
                            return false;
                        }
                    }
                }

            // Destination exists: must be a directory to merge
            if (!Files.isDirectory(dest)) {
                logger.warn("Destination {} exists and is not a directory; skipping {}", dest, source);
                return false;
            }

            // Merge children
            File[] children = source.toFile().listFiles();
            boolean wasSuccessful = true;

            if (children == null) return true;
            for (File child : children) {
                Path childSource = child.toPath();
                Path childDest = dest.resolve(child.getName());

                if (Files.exists(childDest)) {
                    logger.warn("Skipping {} because {} already exists", childSource, childDest);
                    continue;
                }

                if (!migrateDirectory(childSource, childDest) && wasSuccessful) {
                    wasSuccessful = false;
                }
            }

            // delete empty directory
            children = source.toFile().listFiles();
            if (children == null || children.length == 0) {
                Files.deleteIfExists(source);
            }

            return wasSuccessful;
        } catch (IOException ex) {
            logger.warn("Migration error for {}", source, ex);
            return false;
        }
    }
}
