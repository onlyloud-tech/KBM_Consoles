// Copyright (c) 2026 Grymlock.
// SPDX-License-Identifier: GPL-3.0-only
package com.kbmbasic;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

/** Resolve WAD ownership from the loaded bridge jar, never the mod folder name. */
final class PackagedWadPaths {
    private PackagedWadPaths() {}

    static boolean contains(File wad, Class<?> bridge) {
        if (wad == null || bridge == null) return false;
        try {
            CodeSource source = bridge.getProtectionDomain().getCodeSource();
            URL location = source == null ? null : source.getLocation();
            if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) return false;
            Path jar = Paths.get(location.toURI()).toRealPath();
            if (!Files.isRegularFile(jar)) return false;
            Path javaDir = jar.getParent();
            Path mediaDir = javaDir == null ? null : javaDir.getParent();
            if (mediaDir == null || !"java".equalsIgnoreCase(javaDir.getFileName().toString())
                    || !"media".equalsIgnoreCase(mediaDir.getFileName().toString())) return false;

            Path candidateParent = wad.getCanonicalFile().toPath().getParent();
            if (candidateParent == null) return false;
            if (isDataDirectory(candidateParent, mediaDir.resolve("data"))) return true;

            // A version-specific jar can share the mod's common data folder.
            Path versionDir = mediaDir.getParent();
            if (versionDir != null && versionDir.getFileName().toString().matches("[0-9]+(?:\\.[0-9]+)*")) {
                Path modDir = versionDir.getParent();
                return modDir != null && isDataDirectory(candidateParent, modDir.resolve("common/media/data"));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isDataDirectory(Path candidateParent, Path dataDir) {
        try {
            return Files.isDirectory(dataDir) && candidateParent.equals(dataDir.toRealPath());
        } catch (Exception e) {
            return false;
        }
    }
}
