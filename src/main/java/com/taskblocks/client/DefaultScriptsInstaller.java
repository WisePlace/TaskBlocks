package com.taskblocks.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.taskblocks.TaskBlocks;

import net.fabricmc.loader.api.FabricLoader;

// Copies the mod's bundled default scripts (source: src/main/resources/
// default_scripts/ at build time) into config/TaskBlocks/default/,
// checked individually on every launch. A file that doesn't exist yet
// gets installed. A file that already exists only gets overwritten if
// the bundled copy's version= is numerically higher than the existing
// file's version= — otherwise it's left alone, so user edits survive
// unless a mod update actually bumps that file's version.
public class DefaultScriptsInstaller {

    public static void installMissingFiles() {
        Path targetDir = FabricLoader.getInstance()
            .getConfigDir().resolve("TaskBlocks").resolve("default");

        FabricLoader.getInstance().getModContainer("taskblocks").ifPresent(container ->
            container.findPath("default_scripts").ifPresent(sourceDir -> {
                try {
                    Files.createDirectories(targetDir);
                    try (var stream = Files.walk(sourceDir)) {
                        stream.filter(Files::isRegularFile)
                            .forEach(source -> installFile(sourceDir, targetDir, source));
                    }
                } catch (IOException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Failed to install default scripts.", e);
                }
            })
        );
    }

    private static void installFile(Path sourceDir, Path targetDir, Path source) {
        try {
            Path relative = sourceDir.relativize(source);
            Path dest = targetDir.resolve(relative.toString());

            if (!Files.exists(dest)) {
                Files.createDirectories(dest.getParent());
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                TaskBlocks.LOGGER.info("[TaskBlocks] Installed new default file: " + relative);
                return;
            }

            String bundledVersion = readVersion(source);
            String existingVersion = readVersion(dest);

            if (VersionUtil.isNewer(bundledVersion, existingVersion)) {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                TaskBlocks.LOGGER.info("[TaskBlocks] Updated default file: " + relative
                    + " (" + existingVersion + " -> " + bundledVersion + ")");
            }
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to install default script: " + source, e);
        }
    }

    private static String readVersion(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("version=")) {
                    return line.substring(8).trim();
                }
                if (line.startsWith("[") || line.toLowerCase().startsWith("def ")) break;
            }
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to read version from: " + path, e);
        }
        return "1.0";
    }
}