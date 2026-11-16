package com.elfmcys.ysm.mixin.client;

/**
 * 26.1.2 通过 {@code ProjectionMatrixBuffer.getBuffer(Matrix4f)} 上传透视投影，本 mixin 包一层拿 CPU 副本。
 * 1.21.11 已无该类（改由 {@code RenderSystem.getProjectionMatrixBuffer()} 与
 * {@code CachedPerspectiveProjectionMatrixBuffer} 管理），无法在不确认新 API 的情况下安全移植，
 * 故降级为不注入任何内容的空壳类。
 * <p>
 * 该 mixin 未注册在 {@code ysm.mixins.json} 中，本类保留仅为兼容旧引用；不再被任何代码使用。
 */
@Deprecated
public final class GameRendererProjectionMixin {
    private GameRendererProjectionMixin() {
    }
}
