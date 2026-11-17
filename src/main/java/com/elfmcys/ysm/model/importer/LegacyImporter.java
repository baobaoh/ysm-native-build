package com.elfmcys.ysm.model.importer;

import com.elfmcys.ysm.format.legacy.LegacyYsmHeader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Importer for the encrypted V3 {@code .ysm} containers produced by YSM 2.x.
 *
 * <p>Decryption and binary deserialization run in pure Java (ported from OpenYSM, MIT);
 * the result is rewritten into the classic unpacked folder layout before compilation.</p>
 */
public final class LegacyImporter {
    public static final String PROFILE_VERSION = "0.1.0-unstable:v3-java-importer";

    public boolean recognizes(Path source) {
        if (!source.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".ysm")) {
            return false;
        }
        try {
            return LegacyYsmHeader.probe(source) == LegacyYsmHeader.Version.V3_ENCRYPTED;
        } catch (IOException error) {
            throw new UncheckedIOException("Invalid .ysm source header", error);
        }
    }

    public Path importModel(Path source, Path outputDirectory) throws IOException {
        return V3ArchiveConverter.convert(source, outputDirectory);
    }
}
