package com.elfmcys.ysm.model.importer;

import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import rip.ysm.security.YsmCrypt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decrypts a YSM 2.x encrypted {@code .ysm} container (YSGP v3) and rewrites it as the
 * classic unpacked folder layout so the regular VFS importer can read it.
 *
 * <p>The decryption and binary deserialization code is taken from OpenYSM
 * (MIT, Copyright (c) 2026 OpenYSM); see NOTICE.md.</p>
 */
public final class V3ArchiveConverter {
    private static final String OUTPUT_DIRECTORY = "v3";

    private V3ArchiveConverter() {
    }

    public static Path convert(Path source, Path outputDirectory) throws IOException {
        final byte[] raw;
        try {
            raw = Files.readAllBytes(source);
        } catch (IOException error) {
            throw new IOException("Failed to read encrypted model container", error);
        }
        final byte[] payload;
        try {
            payload = YsmCrypt.decryptYsmFile(raw);
        } catch (Exception error) {
            throw new IOException("Failed to decrypt encrypted model container", error);
        }
        if (payload == null || payload.length == 0) {
            throw new IOException("Decrypted model payload is empty");
        }
        final RawYsmModel model;
        try {
            model = new YSMBinaryDeserializer(payload).deserializeKeepOpen();
        } catch (RuntimeException error) {
            throw new IOException("Failed to parse decrypted model payload", error);
        }
        var output = outputDirectory.resolve(OUTPUT_DIRECTORY);
        V3ModelExporter.export(model, output);
        return output;
    }
}
