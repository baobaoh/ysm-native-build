package com.elfmcys.ysm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骨架阶段的编译/运行探针：不依赖任何业务类，即使主源码尚未编译通过也能独立验证
 * 测试工具链（JUnit 5 + toolchain 21 + processResources）是否就绪。
 */
class SkeletonSmokeTest {

    @Test
    void modIdIsStable() {
        assertEquals("ysm", "ysm");
    }

    @Test
    void modMetadataFilesExist() {
        Path resources = Path.of("src/main/resources");
        assertTrue(Files.isRegularFile(resources.resolve("fabric.mod.json")), "fabric.mod.json missing");
        assertTrue(Files.isRegularFile(resources.resolve("ysm.mixins.json")), "ysm.mixins.json missing");
        assertTrue(Files.isRegularFile(resources.resolve("META-INF/native/ysm.dll")),
                "native library must live under META-INF/native/ (NativeLibUtil.LIB_PATH)");
    }

    @Test
    void mixinConfigUsesJava21() throws Exception {
        Path config = Path.of("src/main/resources/ysm.mixins.json");
        String json = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(json.contains("JAVA_21"), "compatibilityLevel must be JAVA_21 on the 1.21.x line");
    }
}
