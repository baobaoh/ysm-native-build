package com.elfmcys.ysm.geckolib3.geo;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.api.rendering.v0.TargetKind;
import com.elfmcys.ysm.api.rendering.v0.event.RenderLayerEvent;
import com.elfmcys.ysm.api.rendering.v0.event.RenderModelEvent;
import com.elfmcys.ysm.capability.VehicleAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.fabric.EntityCapabilityHolder;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.geckolib3.core.util.Color;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderer;

/**
 * 26.1.2 移植：原版实体渲染改为 extract/submit 两段式，YSM 渲染器不再作为
 * 原版渲染器注册使用，而是由 EntityRenderDispatcherMixin 在 submit 阶段拦截调用。
 * 因此这里只继承 EntityRenderer 获取 font / dispatcher / 名牌等基础能力，
 * 渲染本身通过 {@link net.minecraft.client.renderer.SubmitNodeCollector#submitCustomGeometry} 提交给原生渲染器。
 */
public abstract class GeoReplacedEntityRenderer<TEntity extends net.minecraft.world.entity.LivingEntity, T extends CustomHumanoidEntity<TEntity>> extends net.minecraft.client.renderer.entity.EntityRenderer<TEntity, net.minecraft.client.renderer.entity.state.EntityRenderState> implements IGeoRenderer<T> {
    protected final List<GeoLayerRenderer<T>> layerRenderers = new ObjectArrayList<>();
    private static final java.util.concurrent.atomic.AtomicInteger WORLD_DIAG = new java.util.concurrent.atomic.AtomicInteger();

    public GeoReplacedEntityRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public net.minecraft.client.renderer.entity.state.EntityRenderState createRenderState() {
        return new net.minecraft.client.renderer.entity.state.EntityRenderState();
    }

    public static int getPackedOverlay(net.minecraft.world.entity.LivingEntity entity, float u) {
        return net.minecraft.client.renderer.texture.OverlayTexture.pack(net.minecraft.client.renderer.texture.OverlayTexture.u(u), net.minecraft.client.renderer.texture.OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
    }

    public void renderAnimatableEntity(T animatableEntity, float entityYaw, float partialTick,
                                       com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, int packedLight,
                                       net.minecraft.client.renderer.state.CameraRenderState cameraState) {
        renderAnimatableEntity(animatableEntity, null, entityYaw, partialTick, poseStack, collector, packedLight, cameraState);
    }

    public void renderAnimatableEntity(T animatableEntity, @Nullable net.minecraft.resources.Identifier textureOverride, float entityYaw, float partialTick,
                                       com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, int packedLight,
                                       net.minecraft.client.renderer.state.CameraRenderState cameraState) {
        final TEntity entity = animatableEntity.getEntity();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (entity instanceof net.minecraft.world.entity.player.Player && com.elfmcys.ysm.util.Diag.enabled()
                && com.elfmcys.ysm.util.RenderUtil.isRenderingLevel() && WORLD_DIAG.getAndIncrement() < 8) {
            var m = poseStack.last().pose();
            com.elfmcys.ysm.util.Diag.info("[YSM-WORLD] sub={} poseT=({}, {}, {}) level={} self={}",
                    entity.getClass().getSimpleName(),
                    String.format("%.2f", m.m30()), String.format("%.2f", m.m31()), String.format("%.2f", m.m32()),
                    com.elfmcys.ysm.util.RenderUtil.isRenderingLevel(), entity == mc.player);
        }
        var data = animatableEntity.update(partialTick);
        if (data != null && mc.player != null) {
            poseStack.pushPose();
            try {
                if (entity.getPose() == net.minecraft.world.entity.Pose.SLEEPING) {
                    net.minecraft.core.Direction direction = entity.getBedOrientation();
                    if (direction != null) {
                        float eyeOffset = entity.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) - 0.1f;
                        poseStack.translate(-direction.getStepX() * eyeOffset, 0, -direction.getStepZ() * eyeOffset);
                    }
                }

                setupRotations(entity, poseStack, data.animationData.lerpedAge, data.animationData.lerpBodyRot, partialTick);

                if (animatableEntity.getEntity().getVehicle() != null) {
                    EntityCapabilityHolder.get(animatableEntity.getEntity().getVehicle(), VehicleAnimatableCapabilityProvider.CAP).ifPresent(cap -> {
                        var rot = cap.getRotation();
                        if (rot != null) {
                            poseStack.mulPose(new Quaternionf().rotateZYX(rot.z, 0, rot.x).invert());
                        }
                    });
                }

                poseStack.translate(0, 0.01f, 0);

                var texture = textureOverride == null ? data.texture : textureOverride;
                var bodyVisible = this.isBodyVisible(entity) && !entity.isInvisibleTo(mc.player);
                var glowing = mc.shouldEntityAppearGlowing(entity);
                var renderType = getRenderType(texture,
                        bodyVisible, glowing,
                        data.modelState.hasTranslucentVertices());

                var renderLayersFirst = data.renderLayersFirst;
                var packedOverlay = getPackedOverlay(entity, getOverlayProgress(entity, partialTick));

                if (renderType != null) {
                    preRender(data, animatableEntity, poseStack, collector,
                            packedLight, packedOverlay, Color.WHITE);
                    if (renderLayersFirst && !entity.isSpectator()) {
                        renderLayer(poseStack, collector, animatableEntity, data, packedLight, packedOverlay);
                    }

                    if (data.modelState.isValid()) {
                        var event = new RenderModelEvent(animatableEntity.getEntity(),
                                TargetKind.PLAYER,
                                data,
                                collector,
                                renderType,
                                poseStack,
                                packedLight,
                                packedOverlay,
                                Color.WHITE.getColor());
                        if (!YesSteveModel.postEvent(event)) {
                            render(data, animatableEntity, renderType, poseStack, collector,
                                    packedLight, packedOverlay, Color.WHITE);
                        }
                    }

                    if (!renderLayersFirst && !entity.isSpectator()) {
                        renderLayer(poseStack, collector, animatableEntity, data, packedLight, packedOverlay);
                    }
                    postRender(data, animatableEntity, poseStack, collector,
                            packedLight, packedOverlay, Color.WHITE);
                }
            } finally {
                poseStack.popPose();
            }
        }

        renderNameTag(entity, entity.getDisplayName(), poseStack, collector, cameraState, packedLight, partialTick);
    }

    protected void renderLayer(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, T animatable, GeoRenderData renderData, int packedLight, int overlay) {
        var event = new RenderLayerEvent(animatable.getEntity(),
                TargetKind.PLAYER,
                renderData,
                poseStack,
                collector,
                packedLight,
                overlay);
        if (!YesSteveModel.postEvent(event)) {
            for (GeoLayerRenderer<T> layerRenderer : this.layerRenderers) {
                layerRenderer.render(poseStack, collector, animatable, renderData, packedLight, overlay);
            }
        }
    }

    protected void renderNameTag(TEntity entity, net.minecraft.network.chat.Component displayName, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                 net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.CameraRenderState cameraState, int packedLight, float partialTick) {
        if (displayName == null) {
            return;
        }
        double distance = this.entityRenderDispatcher.distanceToSqr(entity);
        if (!this.shouldShowName(entity, distance)) {
            return;
        }
        net.minecraft.world.phys.Vec3 offset = new net.minecraft.world.phys.Vec3(0, entity.getBbHeight() + 0.5F, 0);
        collector.submitNameTag(poseStack, offset, 0, displayName, !entity.isDiscrete(), packedLight, distance, cameraState);
    }

    protected float getOverlayProgress(TEntity entity, float partialTicks) {
        return 0.0F;
    }

    /**
     * 内联原版 LivingEntityRenderer.setupRotations 的实体参数版本；
     * 与 1.20.1 时代相同，YSM 跳过死亡倒地与鞘翅旋转（由动画系统接管）。
     */
    protected void setupRotations(TEntity entity, com.mojang.blaze3d.vertex.PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        float yaw = rotationYaw;
        if (entity.isFullyFrozen()) {
            yaw += (float) (Math.cos(entity.tickCount * 3.25) * Math.PI * 0.4F);
        }

        // 爬梯时，禁止旋转
        if (entity.onClimbable()) {
            Optional<net.minecraft.core.BlockPos> climbablePos = entity.getLastClimbablePos();
            if (climbablePos.isPresent()) {
                net.minecraft.world.level.block.state.BlockState blockState = entity.level().getBlockState(climbablePos.get());
                Optional<net.minecraft.core.Direction> optionalValue = blockState.getOptionalValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
                if (optionalValue.isPresent()) {
                    yaw = optionalValue.get().getOpposite().get2DDataValue() * 90;
                }
            }
        }

        net.minecraft.world.entity.Pose pose = entity.getPose();
        if (pose != net.minecraft.world.entity.Pose.SLEEPING) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yaw));
        }

        if (pose == net.minecraft.world.entity.Pose.SLEEPING) {
            net.minecraft.core.Direction direction = entity.getBedOrientation();
            float bedYaw = direction != null ? getFacingAngle(direction) : yaw;
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bedYaw));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0F));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(270.0F));
        } else if (entity.hasCustomName() || entity instanceof net.minecraft.world.entity.player.Player) {
            if (isUpsideDown(entity)) {
                poseStack.translate(0.0F, entity.getBbHeight() + 0.1F, 0.0F);
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
            }
        }
    }

    private static float getFacingAngle(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case SOUTH -> 90.0F;
            case WEST -> 0.0F;
            case NORTH -> 270.0F;
            case EAST -> 180.0F;
            default -> 0.0F;
        };
    }

    private static boolean isUpsideDown(net.minecraft.world.entity.LivingEntity entity) {
        String name = entity instanceof net.minecraft.world.entity.player.Player player ? player.getGameProfile().name() : entity.getName().getString();
        return "Dinnerbone".equals(name) || "Grumm".equals(name);
    }

    protected boolean isBodyVisible(TEntity entity) {
        boolean visible = !entity.isInvisible();
        boolean spectatorVisible = !visible && !entity.isInvisibleTo(net.minecraft.client.Minecraft.getInstance().player);
        boolean glowing = net.minecraft.client.Minecraft.getInstance().shouldEntityAppearGlowing(entity);
        return visible || spectatorVisible || glowing;
    }

    public boolean shouldShowName(TEntity entity, double distanceSq) {
        return entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity;
    }

    public final boolean addLayer(GeoLayerRenderer<T> layer) {
        return this.layerRenderers.add(layer);
    }

    @Deprecated
    public boolean shouldShowName(TEntity entity) {
        return shouldShowName(entity, this.entityRenderDispatcher.distanceToSqr(entity));
    }

    protected static float lerpAngle(float partialTick, float prev, float current) {
        return net.minecraft.util.Mth.rotLerp(partialTick, prev, current);
    }

}
