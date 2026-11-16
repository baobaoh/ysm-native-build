package com.elfmcys.ysm.model.storage;

import java.nio.file.Path;

public final class SharedCachePaths {
    private final Path root;

    public SharedCachePaths(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static SharedCachePaths userDefault() {
        var home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            throw new IllegalStateException("The user.home system property is unavailable");
        }
        return new SharedCachePaths(Path.of(home).resolve(".ysm").resolve("unstable"));
    }

    /**
     * 1.21.11 移植版默认缓存根：游戏目录下的 ysm/cache。
     * 原版放在 ~/.ysm，home 不可写（受限沙箱、只读用户目录、部分启动器）时
     * 客户端初始化会直接崩在 createDirectories；放游戏目录既避开这个问题，
     * 也让「模型和缓存都在一个文件夹」对玩家更好解释。
     */
    public static SharedCachePaths gameDirectory() {
        var gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
        return new SharedCachePaths(gameDir.resolve("ysm").resolve("cache"));
    }

    public Path root() {
        return root;
    }

    public Path convertedObjects() {
        return root.resolve("converted/objects");
    }

    public Path convertedRoot() {
        return root.resolve("converted");
    }

    public Path convertedObjects(ConversionProfileId profile) {
        return convertedObjects().resolve(profile.pathComponent());
    }

    public Path convertedIndex() {
        return root.resolve("converted/index");
    }

    public Path convertedIndex(ConversionProfileId profile) {
        return convertedIndex().resolve(profile.pathComponent());
    }

    public Path convertedTemporary() {
        return root.resolve("converted/tmp");
    }

    public Path convertedQuarantine() {
        return root.resolve("converted/quarantine");
    }

    public Path convertedLifecycleLock() {
        return locks().resolve("converted-lifecycle.lck");
    }

    public Path remoteModels() {
        return root.resolve("remote/v0/models");
    }

    public Path remoteChunks() {
        return root.resolve("remote/v0/chunks");
    }

    public Path baked() {
        return root.resolve("baked");
    }

    public Path locks() {
        return root.resolve("locks");
    }

    public Path temporary() {
        return root.resolve("tmp");
    }
}
