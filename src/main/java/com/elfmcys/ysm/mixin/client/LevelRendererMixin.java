package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.animation.AnimationParallelTicker;
import com.elfmcys.ysm.natives.render.ProjectionMatrixHolder;
import com.elfmcys.ysm.util.RenderUtil;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 的 renderLevel 仍是老签名：
 * (GraphicsResourceAllocator, DeltaTracker, boolean, Camera, pose, projection, frustum, GpuBufferSlice, Vector4f, boolean)。
 * 26.1.2 把它换成了 (CameraRenderState, Matrix4fc, GpuBufferSlice, Vector4f, boolean, ChunkSectionsToRender)，这里按 1.21.11 写回。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    private void beforeRenderLevel(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                   boolean renderBlockOutline, Camera camera,
                                   Matrix4f pose, Matrix4f projection, Matrix4f frustum,
                                   GpuBufferSlice shaderFog, Vector4f fogColor,
                                   boolean renderSky, CallbackInfo ci) {
        // 世界投影矩阵每帧落地，作为原生渲染器的兜底（PiP/GUI 路径走 slice 上的 carrier）
        ProjectionMatrixHolder.set(projection);
        if (YesSteveModel.isAvailable()) {
            RenderUtil.setRenderingLevel(true);
            AnimationParallelTicker.scheduleAll(deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    private void afterRenderEntities(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                     boolean renderBlockOutline, Camera camera,
                                     Matrix4f pose, Matrix4f projection, Matrix4f frustum,
                                     GpuBufferSlice shaderFog, Vector4f fogColor,
                                     boolean renderSky, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            AnimationParallelTicker.waitAll();
            RenderUtil.setRenderingLevel(false);
        }
    }
}