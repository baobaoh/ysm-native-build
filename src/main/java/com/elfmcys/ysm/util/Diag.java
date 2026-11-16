package com.elfmcys.ysm.util;

import com.elfmcys.ysm.YesSteveModel;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 开发期诊断日志开关。
 *
 * <p>移植过程中加了大量 {@code [YSM-ARM]} / {@code [YSM-WORLD]} / {@code [YSM-VB]} 之类的取证日志，
 * 它们每帧或每 120 帧打印一次。开发环境（Loom 的 runClient/runServer）保留，
 * 正式发布（jar 放进玩家的 mods）默认静默，避免刷日志；
 * 需要现场取证时加 JVM 参数 {@code -Dysm.diag=true} 即可重新打开。
 */
public final class Diag {

    private static final boolean ENABLED =
            FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("ysm.diag");

    public static boolean enabled() {
        return ENABLED;
    }

    public static void info(String format, Object... args) {
        if (ENABLED) {
            YesSteveModel.LOGGER.info(format, args);
        }
    }

    private Diag() {
    }
}
