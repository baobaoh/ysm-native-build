package com.elfmcys.ysm.natives.render;

import com.elfmcys.ysm.accessor.VertexBufferAccessor;
import com.elfmcys.ysm.buffer.NativeBuffer;
import com.elfmcys.ysm.buffer.annotation.Aligned;
import com.elfmcys.ysm.buffer.annotation.Borrowed;
import com.elfmcys.ysm.client.compat.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.Nullable;

public class NativeRenderer {
    private static final org.joml.Matrix4f IDENTITY = new org.joml.Matrix4f();
    private static final java.util.concurrent.atomic.AtomicInteger RDIAG =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger DIAG_BBOX =
            new java.util.concurrent.atomic.AtomicInteger();
    public static void render(com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                              NativeModelState modelState, int vertexCount,
                              int light, int overlay, int color, RenderContextType contextType) {
        var vb = setupVertexConsumer(vertexConsumer, vertexCount);
        var isUnknownType = vb.type == VertexFormatType.FALLBACK;

        var matBuffer = getMatBuffer(pose);
        var lightAndOverlay = packLightAndOverlay(light, overlay);
        var flags = packFlags(vb.type, contextType);

        Object bufferObj;
        int bufferFlags;
        if (!isUnknownType && vb.region != null) {
            bufferObj = vb.region.nio();
            bufferFlags = vb.region.size();
        } else {
            bufferObj = FallbackVertexWriter.getVertexData(vertexCount);
            java.util.Arrays.fill((float[]) bufferObj, 0, vertexCount * 8, 0f);
            bufferFlags = 0;
        }

        var result = nRender(bufferObj, bufferFlags, matBuffer.ptr(), modelState.get(),
                lightAndOverlay, packColor(color), flags, IrisCompat.getEntityId());
        if (com.elfmcys.ysm.util.Diag.enabled() && RDIAG.getAndIncrement() < 8) {
            com.elfmcys.ysm.util.Diag.info(
                    "[YSM-VB] type={} fallback={} regionNull={} vertexCount={} flags={} result={}",
                    vb.type, isUnknownType, vb.region == null, vertexCount, flags, result);
        }
        if (result) {
            if (isUnknownType || vb.region == null) {
                if (vertexCount < 200 && DIAG_BBOX.getAndIncrement() < 12) {
                    var d = FallbackVertexWriter.getVertexData(vertexCount);
                    float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                    float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                    for (int i = 0; i < vertexCount; i++) {
                        int o = i * 8;
                        float x = d[o + 2], y = d[o + 3], z = d[o + 4];
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    com.elfmcys.ysm.YesSteveModel.LOGGER.info(
                            "[YSM-BBOX] n={} x=[{},{}] y=[{},{}] z=[{},{}] ctx={} color={}",
                            vertexCount, minX, maxX, minY, maxY, minZ, maxZ, contextType, color);
                    if (DIAG_BBOX.get() <= 3) {
                        for (int i = 0; i < Math.min(3, vertexCount); i++) {
                            int o = i * 8;
                            com.elfmcys.ysm.YesSteveModel.LOGGER.info(
                                    "[YSM-V0 #{}] c={} n={} p=({},{},{}) uv=({},{}) light={}",
                                    i,
                                    Integer.toHexString(Float.floatToRawIntBits(d[o])),
                                    Integer.toHexString(Float.floatToRawIntBits(d[o + 1])),
                                    d[o + 2], d[o + 3], d[o + 4], d[o + 5], d[o + 6],
                                    Integer.toHexString(Float.floatToRawIntBits(d[o + 7])));
                        }
                    }
                }
                FallbackVertexWriter.write(vertexConsumer, pose, vertexCount, overlay);
            } else if (vb.accessor != null) {
                vb.accessor.ysm$advance(vertexCount);
            }
        }
    }


    /**
     * 上一次手臂级几何（顶点数 &lt; 200）在模型空间里的包围盒中心与最大边长。
     * 第一人称手臂用它把几何搬到手上：native 输出的是带骨骼动画的模型空间顶点，位置逐帧变化。
     */
    public static boolean isArmBoundsValid() {
        return FallbackVertexWriter.armBoundsValid;
    }

    public static float armBoundsCenterX() {
        return FallbackVertexWriter.armCenterX;
    }

    public static float armBoundsCenterY() {
        return FallbackVertexWriter.armCenterY;
    }

    public static float armBoundsCenterZ() {
        return FallbackVertexWriter.armCenterZ;
    }

    public static float armBoundsExtent() {
        return FallbackVertexWriter.armExtent;
    }

    /** 手臂几何最高点（≈肩关节），用作旋转与摆放的支点。 */
    public static float armBoundsMaxY() {
        return FallbackVertexWriter.armMaxY;
    }

    /** 换手臂模型后重新锁存包围盒，避免用旧模型的尺寸缩放新模型。 */
    public static void resetArmBounds() {
        FallbackVertexWriter.resetArmBounds();
    }

    /**
     * 是否允许本帧锁存手臂包围盒。只在手臂处于静止姿态时为 true：
     * 挥臂到一半时顶点被骨骼拉走，锁存下来会把中心记错，之后整段动画都跟着歪。
     */
    public static void setArmLatchAllowed(boolean allowed) {
        FallbackVertexWriter.setArmLatchAllowed(allowed);
    }

    /** 调试用：走 native 顶点完全相同的写入路径画一个手写方块。 */
    public static void writeTestQuad(com.mojang.blaze3d.vertex.VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, int overlay) {
        FallbackVertexWriter.writeTestQuad(consumer, pose, overlay);
    }

    @SuppressWarnings("resource")
    private static VertexBuffer setupVertexConsumer(com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer, int vertexCount) {
        if (vertexConsumer instanceof VertexBufferAccessor accessor && accessor.ysm$ok()) {
            var format = accessor.ysm$vertexFormat();
            var irisType = IrisCompat.determineVertexFormatType(format);
            if (irisType.isPresent()) {
                return new VertexBuffer(accessor, irisType.get(), accessor.ysm$reserve(vertexCount));
            }
            // 原版实体顶点格式（1.21.11 叫 NEW_ENTITY，36 字节：pos3f + color4B + uv2f + overlay2s + light2s + normal4B）
            // 与 native 侧 VanillaVertex 完全一致，可以省掉 Java 侧的逐顶点搬运，直接写进 BufferBuilder 的内存。
            if (format == com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY) {
                return new VertexBuffer(accessor, VertexFormatType.VANILLA, accessor.ysm$reserve(vertexCount));
            }
        }
        return new VertexBuffer(null, VertexFormatType.FALLBACK, null);
    }

    @Borrowed
    @Aligned(64)
    public static NativeBuffer getMatBuffer(com.mojang.blaze3d.vertex.PoseStack.Pose pose) {
        var buffer = MatBufferHolder.BUFFER;
        // JOML 按 buffer 的字节序写入；n 侧按原生小端读取，必须切到 nativeOrder
        var buf = buffer.nio().order(java.nio.ByteOrder.nativeOrder());
        // 实验开关：draw 阶段 RenderSystem 的 modelview 里带着一份相机旋转。若它确实参与最终
        // 顶点变换，就把 pose 左乘 view⁻¹ 抵消掉。取值来自 -Dysm.premultView=true
        boolean premult = Boolean.getBoolean("ysm.premultView");

        var view = RenderSystem.getModelViewMatrix();
        // 当前投影矩阵附带在 RenderSystem 的 slice 上（关卡透视/PiP 正交均覆盖）；
        // 拿不到时退回 renderLevel 捕获的矩阵
        var proj = ProjectionMatrixHolder.get();
        var projSlice = RenderSystem.getProjectionMatrixBuffer();
        if (projSlice != null && ((Object) projSlice) instanceof ProjectionMatrixCarrier carrier
                && carrier.ysm$getProjectionMatrix() != null) {
            proj = carrier.ysm$getProjectionMatrix();
        }

        if (premult) {
            new org.joml.Matrix4f(view).invert().mul(pose.pose()).get(buf);
        } else {
            pose.pose().get(buf);
        }
        // 1.21.11 的提交方式：EntityRenderDispatcher.submit / renderPlayerArm 传入的 pose 已经处于
        // 「相机相对」空间（submit 的 d/e/f 就是相对相机坐标），{@link RenderSystem#getModelViewMatrix()}
        // 里残留的相机旋转是给着色器用的，再乘一次就会把模型转到别处（手臂整条看不见、模型被放大摊开）。
        // 因此这里传单位阵，真正的相机变换已烘进 pose。
        IDENTITY.get(64, buf);
        proj.get(128, buf);
        pose.normal().get(192, buf);

        diagMatrices(pose, view, proj);

        return buffer;
    }

    /**
     * 临时诊断：把每次 draw 传给 native 的 4 个矩阵的关键分量打到日志里。
     * <p>
     * 目的：1.21.11 的实体提交改成「PoseStack 提交 + 延迟 draw」之后，{@code view}（来自
     * {@link RenderSystem#getModelViewMatrix()}）与 {@code proj}（renderLevel 捕获或 slice carrier）
     * 到底是不是世界矩阵无法静态确认；native 用它们算 clip-space 朝向/深度，
     * 一旦给错就会出现「肢体摊开、模型被放大或看不见」。
     * <p>
     * 只打前 {@link #DIAG_LIMIT} 次，绝不影响正常使用；问题定位后整段删掉。
     */
    private static final int DIAG_LIMIT = 6;
    private static final java.util.concurrent.atomic.AtomicInteger DIAG_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 诊断计数清零，让接下来几次 draw 一定被打进日志（探针在截图前调用）
     */
    public static void resetDiag() {
        DIAG_COUNT.set(0);
    }

    private static void diagMatrices(com.mojang.blaze3d.vertex.PoseStack.Pose pose, org.joml.Matrix4f view, org.joml.Matrix4f proj) {
        if (DIAG_COUNT.get() >= DIAG_LIMIT) {
            return;
        }
        int n = DIAG_COUNT.incrementAndGet();
        if (n > DIAG_LIMIT) {
            return;
        }
        var p = pose.pose();
        var slice = RenderSystem.getProjectionMatrixBuffer();
        boolean carrier = slice != null && ((Object) slice) instanceof ProjectionMatrixCarrier;
        com.elfmcys.ysm.util.Diag.info(
                "[YSM-MAT #{}] pose=[{}] view=[{}] proj=[{}] carrier={} holderSame={}",
                n,
                mat(p), mat(view), mat(proj), carrier, proj == ProjectionMatrixHolder.get());
        if (n <= 6) {
            var stack = new Throwable().getStackTrace();
            var sb = new StringBuilder("[YSM-MAT] caller #").append(n).append(':');
            for (int i = 2; i < Math.min(10, stack.length); i++) {
                sb.append("\n    at ").append(stack[i]);
            }
            com.elfmcys.ysm.util.Diag.info(sb.toString());
        }
    }

    private static String mat(org.joml.Matrix4f m) {
        return String.format(java.util.Locale.ROOT,
                "%.3f %.3f %.3f %.3f | %.3f %.3f %.3f %.3f | %.3f %.3f %.3f %.3f | %.3f %.3f %.3f %.3f",
                m.m00(), m.m01(), m.m02(), m.m03(),
                m.m10(), m.m11(), m.m12(), m.m13(),
                m.m20(), m.m21(), m.m22(), m.m23(),
                m.m30(), m.m31(), m.m32(), m.m33());
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    private static long packFlags(VertexFormatType v, RenderContextType c) {
        return ((long) v.id() << 2) | c.id();
    }

    private static long packLightAndOverlay(int lightUv, int overlayOv) {
        return ((long) lightUv << 32) | overlayOv;
    }

    static int packColor(int argb) {
        return ((argb >>> 16) & 0xFF) |
                (argb & 0xFF00) |
                ((argb & 0xFF) << 16) |
                (argb & 0xFF000000);
    }

    private static native boolean nRender(Object vertexBuffer, int vertexBufferFlag, long matPtr, long modelStatePtr,
                                          long lightAndOverlay, int color, long flags, long irisEntityId);

    private static final class MatBufferHolder {
        private static final NativeBuffer BUFFER = NativeBuffer.allocate(256, 64);
    }

    private record VertexBuffer(@Nullable VertexBufferAccessor accessor, VertexFormatType type, @Nullable NativeBuffer region){}
}
