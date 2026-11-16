package com.elfmcys.ysm.client.renderer;

import com.elfmcys.ysm.capability.PlayerAnimatableCapability;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.fabric.EntityCapabilityHolder;
import com.elfmcys.ysm.client.entity.CustomFirstPersonArmEntity;
import com.elfmcys.ysm.geckolib3.core.util.Color;
import com.elfmcys.ysm.geckolib3.geo.CustomTranslucentRenderType;
import com.elfmcys.ysm.geckolib3.geo.GeoEntityRenderer;
import com.elfmcys.ysm.natives.render.NativeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.math.Axis;

/**
 * 第一人称手臂渲染器。
 *
 * <p>手臂模型（{@code models/arm.json}）本身包含左右两条手臂的骨骼
 * （LeftArm/LeftForeArm/LeftHandLocator 与 RightArm/RightForeArm/RightHandLocator），
 * 每帧只渲染一次整体。
 * 由 {@code ItemInHandRendererMixin} 在 {@code renderHandsWithItems} 开头调用，
 * 成功后由同一 mixin 拦掉原版手臂（手持物品仍由原版负责绘制）。
 *
 * <p>拿不到模型或模型尚未加载时返回 false，调用方保留原版手臂——
 * 宁可显示原版手臂，也不要在第一人称里把手臂弄丢。
 *
 * <p>摆放参数见 {@link ArmPoseState}（默认值为进游戏目视调整的结果，接近官方观感），
 * 关键是<b>不</b>乘模型的 widthScale/heightScale——官方第一人称直调
 * {@link NativeRenderer#render}，不像第三人称那样过 {@code preRender} 的缩放。
 * （参考：官方 1.21.1 为 {@code translate(0, -1.5, 0)}；官方 26.1 neoforge 为每只手
 * {@code translate(±0.25, 1.8, 0)} + {@code scale(-1,-1,1)}。）
 */
public class CustomFirstPersonArmRenderer extends GeoEntityRenderer<net.minecraft.client.player.LocalPlayer, CustomFirstPersonArmEntity> {
    private CustomFirstPersonArmEntity armEntity;

    /**
     * 本帧是否已接管手臂；renderPlayerArm 据此决定是否跳过原版手臂
     */
    private boolean hideVanillaArm;

    /**
     * 本帧是否真的画出了手臂模型
     */
    private boolean drawnThisFrame;

    /**
     * 上一次用于锁存包围盒的手臂几何模型，用于换模型后重算手臂缩放
     */
    private Object lastArmModel;

    /**
     * 临时诊断：每种结果只报一次，用来定位第一人称手臂卡在哪一步
     */
    private static final java.util.Set<String> LOGGED_OUTCOMES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicInteger DIAG_CB =
            new java.util.concurrent.atomic.AtomicInteger();
    /** 手臂摆放数值的周期取样计数（每 120 帧打一条，用于观察包围盒锁存后的真实落点）。 */
    private static final java.util.concurrent.atomic.AtomicInteger LAYOUT_DIAG =
            new java.util.concurrent.atomic.AtomicInteger();

    private static void diag(String outcome) {
        if (com.elfmcys.ysm.util.Diag.enabled() && LOGGED_OUTCOMES.add(outcome)) {
            com.elfmcys.ysm.util.Diag.info("[YSM-ARM] {}", outcome);
        }
    }

    public CustomFirstPersonArmRenderer() {
        super(YsmRendererContexts.get());
    }

    /**
     * 渲染自定义手臂。
     *
     * @return true 表示已接管，调用方应隐藏原版手臂
     */
    public boolean renderArm(net.minecraft.client.player.LocalPlayer player, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                             net.minecraft.client.renderer.SubmitNodeCollector collector, int packedLight) {
        hideVanillaArm = false;

        PlayerAnimatableCapability cap = EntityCapabilityHolder
                .get(player, PlayerAnimatableCapabilityProvider.CAP).orElse(null);
        if (cap == null || cap.getModelRenderTarget() == null || player.isInvisible()) {
            diag("skip: no capability / no render target / player invisible");
            return false;
        }

        // 与原版一致：主手拿着物品时不画手臂（物品仍由原版管线绘制），
        // 主手空手时才接管原版手臂。官方是用手臂模型的 hold 动画 + 挂点层画物品，
        // 我们的挂点层还有缺口（见 known-issues/rendering.md），先按原版观感隐藏。
        if (!player.getMainHandItem().isEmpty()) {
            diag("skip: main hand holds item");
            return false;
        }

        if (armEntity == null || armEntity.getEntity() != player) {
            armEntity = new CustomFirstPersonArmEntity(player, cap);
        }
        armEntity.checkModelUpdate();
        // 手臂模型换了（玩家换模型 / 资源重载）就把锁存的包围盒丢掉重算，
        // 否则会拿旧模型的尺寸和中心去缩放、对中新模型，手臂会突然变大或跑到画面中间。
        // 用几何模型对象的身份做判据：换模型时 CustomFirstPersonArmEntity 会 setGeoModelInplace 换掉它，
        // 而 modelHash 的 equals 语义不可靠（Hash256），按值比较会导致每帧都判定为「换了」。
        var loadedArmModel = armEntity.getLoadedGeoModel();
        if (loadedArmModel != lastArmModel && NativeRenderer.isArmBoundsValid()) {
            NativeRenderer.resetArmBounds();
        }
        lastArmModel = loadedArmModel;
        if (armEntity.getLoadedGeoModel() == null || armEntity.getModelRenderTarget() == null) {
            // 模型还在加载：让原版手臂先顶着
            diag("skip: arm model not ready yet");
            return false;
        }

        var data = armEntity.update(partialTick);
        if (data == null || !data.modelState.isValid()) {
            diag("skip: render data null or model state invalid");
            return false;
        }

        // 贴图还没注册好（或模型没有可用贴图）时不要接管手臂：
        // 拿 MissingTexture 去画，屏幕上就是一块紫黑色方块。
        if (data.texture == null
                || data.texture.equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
            diag("skip: missing texture");
            return false;
        }

        // 探针：ysm.debugQuad=type → 用真实 RenderType 画手写方块；=debug → 用 debugQuads（无深度测试）
        var debugQuad = System.getProperty("ysm.debugQuad", "");
        var renderType = ("debug".equals(debugQuad) || "geomdbg".equals(debugQuad) || "both".equals(debugQuad))
                ? net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads()
                : ("type2".equals(debugQuad)
                ? net.minecraft.client.renderer.rendertype.RenderTypes.armorCutoutNoCull(net.minecraft.resources.Identifier.parse("minecraft:textures/block/stone.png"))
                : (data.modelState.hasTranslucentVertices()
                ? CustomTranslucentRenderType.create(data.texture)
                : net.minecraft.client.renderer.rendertype.RenderTypes.armorCutoutNoCull(data.texture)));
        // YSM 的手臂模型
        // 「模型空间」里，并且已经带上了骨骼动画——每帧包围盒都不一样，所以不能像原版那样套一条
        // 固定常量链（原版链是给「像素单位、pivot 在 ±5,2」的模型准备的，套过来会把手臂推到相机
        // 前方 3 格开外，被地形挡掉=看不见）。这里改成：用上一帧实测到的包围盒，把几何中心搬到
        // 相机右下方，再按最大边长缩放到手的大小。
        // ArmPoseState 仍是微调入口（/ysmclient armpose、-Pysm.armSweep）。
        float targetSize = 0.22F;
        // 注意：不能拿 FallbackVertexWriter 测出来的包围盒来定位这一版。
        // VANILLA 顶点路径接通之后，native 直接把顶点写进 BufferBuilder，fallback 写入器根本不会被调用，
        // 那个包围盒永远是 invalid（日志里 bounds=false），所以这里用 arm.json 里的固定几何：
        // 肩关节右臂 pivot y=29.775px、手掌 y=18.325px → 1.861 块与 0.72 块（GeoBuilder 已把像素除以 16）。
        final float armPivotY = 1.861F;
        final float armLength = 0.72F;
        boolean boundsValid = NativeRenderer.isArmBoundsValid();
        float extent = boundsValid ? Math.max(NativeRenderer.armBoundsExtent(), 1.0E-3F) : armLength;
        float centerX = boundsValid ? NativeRenderer.armBoundsCenterX() : 0.0F;
        float centerY = boundsValid ? NativeRenderer.armBoundsCenterY() : 0.0F;
        float centerZ = boundsValid ? NativeRenderer.armBoundsCenterZ() : 0.0F;

        poseStack.pushPose();
        try {
            boolean mainIsRight = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
            float invert = mainIsRight ? 1.0F : -1.0F;

            // 挥动角度留到平移之后再转（以手臂锚点为轴），否则绕相机原点旋转会把手臂甩出画面
            float attackValue = player.getAttackAnim(partialTick);
            float attackDegrees = -18.0F * Mth.sin(attackValue * (float) Math.PI);

            float scale = (targetSize / extent) * ArmPoseState.sx();
            // 相机空间摆放：x 向右、y 向上、-Z 向前。
            // 注意 y 不能太负：手臂离相机只有半格多，y=-0.58 算出来就掉到屏幕下方了。
            var inheritedMat = new org.joml.Matrix4f(poseStack.last().pose());
            // 目标点写在「基矩阵之后」的坐标系里，但 pose 上还叠着 ItemInHandRenderer 的基矩阵
            // （含手部旋转，以及随世界位置变化的平移）。直接加相机坐标会漂移：
            // final = R*(target - t) + t，t 一变手臂就换位置，表现就是
            // 「同一份代码在我这儿有手、在别人那儿没有」。
            // 所以只用基矩阵的旋转把目标点反解成局部平移，保证 final 恒等于 target。
            float targetX = -0.51F * invert + ArmPoseState.x();
            float targetY = 0.035F + ArmPoseState.y();
            float targetZ = -0.60F + ArmPoseState.z();
            String override = System.getProperty("ysm.armTarget");
            if (override != null) {
                String[] parts = override.split(",");
                targetX = Float.parseFloat(parts[0]);
                targetY = Float.parseFloat(parts[1]);
                targetZ = Float.parseFloat(parts[2]);
            }
            float pivotDrawY = boundsValid ? NativeRenderer.armBoundsMaxY() : armPivotY;
            var pivotInner = new org.joml.Vector3f(
                    -centerX * scale,
                    -pivotDrawY * 0.5F * scale * Math.abs(ArmPoseState.sy()),
                    -centerZ * scale);
            var armRotation = new org.joml.Matrix3f();
            if (ArmPoseState.tilt() != 0.0F) {
                armRotation.rotateZ((float) Math.toRadians(-invert * ArmPoseState.tilt()));
            }
            armRotation.rotateX((float) Math.toRadians(90.0));
            if (ArmPoseState.roll() != 0.0F) {
                armRotation.rotateZ((float) Math.toRadians(ArmPoseState.roll()));
            }
            if (attackDegrees != 0.0F) {
                armRotation.rotateX((float) Math.toRadians(attackDegrees));
            }
            if (ArmPoseState.pitch() != 0.0F) {
                armRotation.rotateX((float) Math.toRadians(ArmPoseState.pitch()));
            }
            if (ArmPoseState.yaw() != 0.0F) {
                armRotation.rotateY((float) Math.toRadians(ArmPoseState.yaw()));
            }
            armRotation.transform(pivotInner);
            var inverseRotation = new org.joml.Matrix3f(inheritedMat);
            inverseRotation.invert();
            var localOffset = new org.joml.Vector3f(
                    targetX - inheritedMat.m30(),
                    targetY - inheritedMat.m31(),
                    targetZ - inheritedMat.m32());
            inverseRotation.transform(localOffset);
            // 扣掉内部支点平移在最终平移里的那份贡献，保证 final 平移列恒等于 target。
            localOffset.sub(pivotInner);
            poseStack.translate(localOffset.x, localOffset.y, localOffset.z);
            float tilt = ArmPoseState.tilt();
            if (tilt != 0.0F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-invert * tilt));
            }
            // arm.json 里手臂自然下垂（沿 -Y），绕 X 转 +90° 把 -Y 掰成朝前 —— 这是玩家要的「向前」
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            if (ArmPoseState.roll() != 0.0F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(ArmPoseState.roll()));
            }
            if (attackDegrees != 0.0F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(attackDegrees));
            }
            if (ArmPoseState.pitch() != 0.0F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(ArmPoseState.pitch()));
            }
            if (ArmPoseState.yaw() != 0.0F) {
                poseStack.mulPose(Axis.YP.rotationDegrees(ArmPoseState.yaw()));
            }
            poseStack.scale(scale, scale * Math.abs(ArmPoseState.sy()), scale);
            // 支点用「手臂最高点」（≈肩关节）：包围盒中心会随待机动画摆动，锁存帧不同偏移就不同。
            float pivotY = boundsValid ? NativeRenderer.armBoundsMaxY() : armPivotY;
            poseStack.translate(-centerX, -pivotY * 0.5F, -centerZ);
            if (com.elfmcys.ysm.util.Diag.enabled() && LAYOUT_DIAG.incrementAndGet() % 120 == 1) {
                var m = poseStack.last().pose();
                com.elfmcys.ysm.util.Diag.info(
                        "[YSM-ARM] layout bounds={} scale={} finalT=({},{},{})"
                                + " base=({},{},{} | {},{},{} | {},{},{})",
                        boundsValid, scale,
                        m.m30(), m.m31(), m.m32(),
                        inheritedMat.m00(), inheritedMat.m01(), inheritedMat.m02(),
                        inheritedMat.m10(), inheritedMat.m11(), inheritedMat.m12(),
                        inheritedMat.m20(), inheritedMat.m21(), inheritedMat.m22());
            }
            if ("big".equals(debugQuad)) {
                poseStack.scale(20.0F, 20.0F, 20.0F);
            }

            collector.submitCustomGeometry(poseStack, renderType,
                    (pose, consumer) -> {
                        boolean quadOnly = "debug".equals(debugQuad) || "type".equals(debugQuad)
                                || "type2".equals(debugQuad);
                        boolean both = "both".equals(debugQuad);
                        boolean after = "after".equals(debugQuad);
                        if (quadOnly || both) {
                            // 传输链路探针：与 native 顶点无关，直接画一个方块；both 模式同时画
                            // native 几何，用来区分「缓冲区坏了」和「native 顶点数据坏了」
                            var p = pose.pose();
                            float s = both ? 0.15F : 0.5F;
                            float[][] quad = {{-s, -s}, {-s, s}, {s, s}, {s, -s}};
                            float[][] uvs = {{0.0F, 1.0F}, {0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}};
                            for (int i = 0; i < quad.length; i++) {
                                float[] xy = quad[i];
                                float ox = both ? -0.25F : 0.0F;
                                consumer.addVertex(p, xy[0] + ox, xy[1], 0.0F)
                                        .setColor(0xFFFF0000)
                                        .setUv(uvs[i][0], uvs[i][1])
                                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                        .setLight(0x00F000F0)
                                        .setNormal(0.0F, 1.0F, 0.0F);
                            }
                        }
                        if (quadOnly) {
                            return;
                        }
                        if ("wtestonly".equals(debugQuad)) {
                            NativeRenderer.writeTestQuad(consumer, pose, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                            return;
                        }
                        try {
                            NativeRenderer.render(
                                consumer, pose, data.modelState.getNativeState(),
                                data.modelState.getVertexCount(), packedLight,
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, Color.WHITE.getColor(), data.ctx.nativeType());
                        } catch (Throwable t) {
                            com.elfmcys.ysm.YesSteveModel.LOGGER.error("[YSM-ARM] native render threw", t);
                        }
                        if (com.elfmcys.ysm.util.Diag.enabled() && DIAG_CB.getAndIncrement() < 3) {
                            com.elfmcys.ysm.util.Diag.info(
                                    "[YSM-ARM] callback drawn: consumer={} verts={}",
                                    consumer.getClass().getName(), data.modelState.getVertexCount());
                        }
                        if (after) {
                            var p2 = pose.pose();
                            float s2 = 0.5F;
                            float[][] quad2 = {{-s2, -s2}, {-s2, s2}, {s2, s2}, {s2, -s2}};
                            float[][] uvs2 = {{0.0F, 1.0F}, {0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}};
                            for (int i = 0; i < quad2.length; i++) {
                                float[] xy = quad2[i];
                                consumer.addVertex(p2, xy[0], xy[1], 0.0F)
                                        .setColor(0xFFFF00FF)
                                        .setUv(uvs2[i][0], uvs2[i][1])
                                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                        .setLight(0x00F000F0)
                                        .setNormal(0.0F, 1.0F, 0.0F);
                            }
                        }
                        if ("wtest".equals(debugQuad)) {
                            NativeRenderer.writeTestQuad(consumer, pose, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                        }
                    });
        } finally {
            poseStack.popPose();
        }

        drawnThisFrame = true;
        hideVanillaArm = true;
        diag("drew custom arm");
        return true;
    }

    public boolean shouldHideVanillaArm() {
        return hideVanillaArm;
    }
}
