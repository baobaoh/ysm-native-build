package com.elfmcys.ysm.client.renderer.pip;

import com.elfmcys.ysm.util.RenderUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;

/**
 * YSM GUI 预览的 PiP 渲染器，负责把 {@link YsmPreviewRenderState}
 * 渲染到离屏纹理。渲染顺序为：
 * 床（方块实体）→ 原版坐骑实体 → YSM 自定义模型。
 * <p>
 * 1.21.11 没有 {@code BlockModelResolver}/{@code BlockModelRenderState}/{@code BlockDisplayContext}，
 * 地面方块的提交（{@code state.blocks()}）在此版本被跳过，只保留方块实体与实体两段。
 */
public class YsmPreviewRenderer extends PictureInPictureRenderer<YsmPreviewRenderState> {

    public YsmPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    protected void renderToTexture(YsmPreviewRenderState state, PoseStack poseStack) {
        if (Boolean.getBoolean("ysm.noPreview")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // 预览只服务于 GUI。没有打开任何界面时若仍然渲染，几何会被刷到世界帧上
        // （表现为世界里多出一个悬浮的重复模型），所以这里直接跳过。
        if (mc.screen == null) {
            return;
        }
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        FeatureRenderDispatcher featureDispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeCollector collector = featureDispatcher.getSubmitNodeStorage();

        RenderUtil.setRenderingInInventory(!state.paperDoll());
        RenderUtil.setRenderingInPaperDoll(state.paperDoll());
        try {
            poseStack.pushPose();
            try {
                poseStack.last().mulPose(state.poseMatrix());

                CameraRenderState cameraState = new CameraRenderState();
                for (YsmPreviewRenderState.BlockEntityEntry blockEntity : state.blockEntities()) {
                    poseStack.pushPose();
                    poseStack.translate(blockEntity.offset().x, blockEntity.offset().y, blockEntity.offset().z);
                    poseStack.translate(-blockEntity.state().blockPos.getX(),
                            -blockEntity.state().blockPos.getY(),
                            -blockEntity.state().blockPos.getZ());
                    mc.getBlockEntityRenderDispatcher().submit(blockEntity.state(), poseStack, collector, cameraState);
                    poseStack.popPose();
                }

                for (YsmPreviewRenderState.VanillaEntityEntry entity : state.vanillaEntities()) {
                    EntityRenderState entityState = entity.state();
                    mc.getEntityRenderDispatcher().submit(entityState, cameraState,
                            entityState.x, entityState.y, entityState.z, poseStack, collector);
                }

                if (state.ysmEntity() != null) {
                    state.ysmEntity().execute(poseStack, collector, cameraState);
                }
            } finally {
                poseStack.popPose();
            }
            // 实体的原生渲染发生在 renderAllFeatures 里，上下文标记必须覆盖到这里
            featureDispatcher.renderAllFeatures();
            this.bufferSource.endBatch();
        } finally {
            RenderUtil.setRenderingInInventory(false);
            RenderUtil.setRenderingInPaperDoll(false);
        }
    }

    @Override
    public Class<YsmPreviewRenderState> getRenderStateClass() {
        return YsmPreviewRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "ysm_preview";
    }
}
