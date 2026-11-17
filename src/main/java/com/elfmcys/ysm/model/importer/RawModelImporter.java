package com.elfmcys.ysm.model.importer;

import com.elfmcys.ysm.format.parser.ModelParser;
import com.elfmcys.ysm.format.parser.DefaultAnimationFilter;
import com.elfmcys.ysm.format.parser.RawCompileResult;
import com.elfmcys.ysm.format.vfs.Directory;
import com.elfmcys.ysm.model.catalog.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.storage.ModelFileHandle;
import com.elfmcys.ysm.natives.NativeArchive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class RawModelImporter {
    private final LegacyImporter v3Importer;
    private final DefaultAnimationFilter defaultAnimations;

    public RawModelImporter(LegacyImporter v3Importer,
                            DefaultAnimationFilter defaultAnimations) {
        this.v3Importer = Objects.requireNonNull(v3Importer, "v3Importer");
        this.defaultAnimations = Objects.requireNonNull(defaultAnimations, "defaultAnimations");
    }

    public RawCompileResult convert(Path source, Path outputDirectory) {
        // 旧版加密 .ysm（YSGP v3）先在纯 Java 侧解密并还原成文件夹，再走普通 VFS 流程
        if (Files.isDirectory(source)) {
            try (var vfs = new Directory(source)) {
                return ModelParser.compile(vfs, outputDirectory, defaultAnimations);
            }
        }
        var encryptedFolder = extractEncrypted(source, outputDirectory);
        if (encryptedFolder != null) {
            try (var vfs = new Directory(encryptedFolder)) {
                return ModelParser.compile(vfs, outputDirectory, defaultAnimations);
            }
        }
        try (var vfs = new NativeArchive(source.toString())) {
            return ModelParser.compile(vfs, outputDirectory, defaultAnimations);
        }
    }

    /** Extracts an encrypted V3 container into a folder, or returns null when not encrypted. */
    private Path extractEncrypted(Path source, Path outputDirectory) {
        if (!v3Importer.recognizes(source)) {
            return null;
        }
        try {
            return v3Importer.importModel(source, outputDirectory);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to extract encrypted model container", error);
        }
    }

    public boolean supportsHashProbe(Path source) {
        // 加密 .ysm 需要先解包才能探测，交给完整转换流程
        return !v3Importer.recognizes(source);
    }

    public Hash256 scanModelHash(Path source) {
        if (Files.isDirectory(source)) {
            try (var vfs = new Directory(source)) {
                return ModelParser.scanModelHash(vfs);
            }
        }
        try (var vfs = new NativeArchive(source.toString())) {
            return ModelParser.scanModelHash(vfs);
        }
    }
}
