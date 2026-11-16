package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.natives.render.ProjectionMatrixCarrier;
import com.elfmcys.ysm.natives.render.ProjectionMatrixHolder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.11 里 26.1.2 的 {@code ProjectionMatrixBuffer} 已拆成
 * {@code CachedPerspectiveProjectionMatrixBuffer}（按屏幕尺寸缓存）与
 * {@code PerspectiveProjectionMatrixBuffer}（吃 Matrix4f，世界投影走这条）。
 * 后者与 26.x 的入口签名一致（{@code getBuffer(Matrix4f)}），正好拿来抓 CPU 侧投影矩阵：
 * 写回 slice 上的 CPU 副本（GpuBufferSliceMixin 提供的 carrier），同时更新全局兜底 holder。
 */
@Mixin(PerspectiveProjectionMatrixBuffer.class)
public class ProjectionMatrixBufferMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void ysm$captureProjection(Matrix4f matrix, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (matrix == null) {
            return;
        }
        ProjectionMatrixHolder.set(matrix);
        var slice = cir.getReturnValue();
        if (((Object) slice) instanceof ProjectionMatrixCarrier carrier) {
            carrier.ysm$setProjectionMatrix(matrix);
        }
    }
}
