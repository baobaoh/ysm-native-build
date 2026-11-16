package com.elfmcys.ysm.natives.render;

import com.elfmcys.ysm.natives.NativeProfiler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

class FallbackVertexWriter {
    private static final int STRIDE = 8;

    private static final int INDEX_COLOR = 0;
    private static final int INDEX_NORMAL = 1;
    private static final int INDEX_X = 2;
    private static final int INDEX_Y = 3;
    private static final int INDEX_Z = 4;
    private static final int INDEX_TEX_U = 5;
    private static final int INDEX_TEX_V = 6;
    private static final int INDEX_LIGHT = 7;

    private static float[] VERTEX_DATA;
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();
    private static final Vector3f POS_SCRATCH = new Vector3f();
    /** 调试用：>0 时只写前 N 个顶点（N 会向下取到 4 的倍数），用来二分定位「写多了就整批消失」。 */
    private static final int LIMIT = Integer.getInteger("ysm.vertexLimit", 0) & ~3;
    /** 调试用：强制每个顶点都用洋红色、不写自定义法线，用来判断「颜色/法线」是否是元凶。 */
    private static final boolean MAGENTA = Boolean.getBoolean("ysm.magenta");
    private static int DIAG_TRANSFORM;
    private static int DIAG_WRITE;
    private static int DIAG_CAMERA;

    /**
     * 上一帧手臂几何（顶点数 &lt; 200 的小几何）在「模型空间」里的包围盒。
     * native 输出的顶点已经带上骨骼动画，位置每帧都会变，所以第一人称手臂不能靠固定常量对齐，
     * 必须用实测包围盒把几何搬到手上。
     */
    static float armCenterX;
    static float armCenterY;
    static float armCenterZ;
    static float armExtent;
    /** 手臂几何的最高点（模型空间）：手臂是从肩往下长的，「最高点≈肩关节」，拿它当旋转支点比包围盒中心稳得多。 */
    static float armMaxY;
    static boolean armBoundsValid;

    /**
     * 是否允许在这一帧锁存包围盒。渲染器每帧按「手臂是否处于静止姿态」设置：
     * 挥臂到一半时顶点被骨骼拉到别处，此时锁存会把手臂中心记错，之后整段动画都跟着歪。
     */
    private static boolean armLatchAllowed = true;

    public static void setArmLatchAllowed(boolean allowed) {
        armLatchAllowed = allowed;
    }

    private static void updateArmBounds(float[] vertexData, int vertexCount) {
        if (vertexCount <= 0) {
            return;
        }
        // 只钉住第一帧（静止姿态）的包围盒：手臂带骨骼动画，挥动时包围盒中心每帧都在跳，
        // 跟着它走会让手臂在出拳/挥手时被甩出画面。缩放与对中一旦定下来就保持不变，
        // 动画本身由 native 的骨骼 pose 表达，不需要再跟着顶点跑。
        if (armBoundsValid || !armLatchAllowed) {
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * STRIDE;
            float x = vertexData[offset + INDEX_X];
            float y = vertexData[offset + INDEX_Y];
            float z = vertexData[offset + INDEX_Z];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        armCenterX = (minX + maxX) * 0.5f;
        armCenterY = (minY + maxY) * 0.5f;
        armCenterZ = (minZ + maxZ) * 0.5f;
        armExtent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        armMaxY = maxY;
        armBoundsValid = true;
        if (!NAN_REPORTED && !(Float.isFinite(armCenterX) && Float.isFinite(armCenterY)
                && Float.isFinite(armCenterZ) && Float.isFinite(armExtent))) {
            NAN_REPORTED = true;
            com.elfmcys.ysm.YesSteveModel.LOGGER.warn(
                    "[YSM-BOUNDS] non-finite bbox center=({},{},{}) extent={} n={}",
                    armCenterX, armCenterY, armCenterZ, armExtent, vertexCount);
        }
    }

    private static boolean NAN_REPORTED;

    /**
     * 丢弃已锁存的包围盒。
     *
     * <p>包围盒是按「当前手臂模型」的第一帧锁存的，换模型（尤其中途 /ysm model set）后
     * 旧模型的尺寸和中心就不再适用：缩放会偏大或偏小、对中也偏掉，表现是手臂突然变大或跑到画面中间。
     * 调用方在检测到手臂模型变化时调用本方法，下一帧会重新锁存。</p>
     */
    public static void resetArmBounds() {
        armBoundsValid = false;
    }


    static float[] getVertexData(int vertexCount) {
        var size = vertexCount * STRIDE;
        if (VERTEX_DATA == null || VERTEX_DATA.length < size) {
            VERTEX_DATA = new float[size];
        }
        return VERTEX_DATA;
    }

    /**
     * 调试用：用 native 顶点完全相同的写入路径画一个手写方块。
     * 用来区分「写入路径坏了」和「native 顶点数据坏了」。
     */
    static void writeTestQuad(com.mojang.blaze3d.vertex.VertexConsumer vertexBuffer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, int overlayUv) {
        VERTEX_DATA = new float[4 * STRIDE];
        float[][] corners = {{-0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f}, {0.5f, -0.5f}};
        float[][] uvs = {{0f, 1f}, {0f, 0f}, {1f, 0f}, {1f, 1f}};
        for (int i = 0; i < 4; i++) {
            int o = i * STRIDE;
            VERTEX_DATA[o + INDEX_COLOR] = Float.intBitsToFloat(0xFFFF00FF);
            VERTEX_DATA[o + INDEX_NORMAL] = Float.intBitsToFloat(0x007F0000);
            VERTEX_DATA[o + INDEX_X] = corners[i][0];
            VERTEX_DATA[o + INDEX_Y] = corners[i][1];
            VERTEX_DATA[o + INDEX_Z] = 0f;
            VERTEX_DATA[o + INDEX_TEX_U] = uvs[i][0];
            VERTEX_DATA[o + INDEX_TEX_V] = uvs[i][1];
            VERTEX_DATA[o + INDEX_LIGHT] = Float.intBitsToFloat(0x00F000F0);
        }
        write(vertexBuffer, pose, 4, overlayUv);
        VERTEX_DATA = null;
    }

    /**
     * native 只做骨骼 pose 变换，输出的顶点仍在「提交时的 pose 空间」；1.21.11 的实体/手臂渲染
     * 是「提交几何 + 帧末统一 draw」，pose 必须由写入方应用（原版 {@code addVertex(pose, x, y, z)} 同理）。
     * 早期移植版直接写裸坐标，手臂因此画到了世界原点附近而不是相机前方，屏幕上完全看不到。
     */
    static void write(com.mojang.blaze3d.vertex.VertexConsumer vertexBuffer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, int vertexCount, int overlayUv) {
        try (var ignored = NativeProfiler.beginFallbackVertexWrite()) {
            var vertexData = VERTEX_DATA;
            var matrix = pose.pose();
            if (vertexCount < 200) {
                updateArmBounds(vertexData, vertexCount);
            }
            // 手臂几何在「变换后（相机空间）」的实际包围盒：判断它被放到哪儿、朝哪个方向，
            // 比一张张截图猜要可靠得多。
            if (DIAG_CAMERA < 6 && vertexCount < 200) {
                DIAG_CAMERA++;
                float cxMin = Float.MAX_VALUE, cxMax = -Float.MAX_VALUE;
                float cyMin = Float.MAX_VALUE, cyMax = -Float.MAX_VALUE;
                float czMin = Float.MAX_VALUE, czMax = -Float.MAX_VALUE;
                var probe = new Vector3f();
                for (int i = 0; i < vertexCount; i++) {
                    int o = i * STRIDE;
                    matrix.transformPosition(vertexData[o + INDEX_X], vertexData[o + INDEX_Y],
                            vertexData[o + INDEX_Z], probe);
                    cxMin = Math.min(cxMin, probe.x()); cxMax = Math.max(cxMax, probe.x());
                    cyMin = Math.min(cyMin, probe.y()); cyMax = Math.max(cyMax, probe.y());
                    czMin = Math.min(czMin, probe.z()); czMax = Math.max(czMax, probe.z());
                }
                com.elfmcys.ysm.YesSteveModel.LOGGER.info(
                        "[YSM-CAM] n={} x=[{},{}] y=[{},{}] z=[{},{}] size=({},{},{})",
                        vertexCount, cxMin, cxMax, cyMin, cyMax, czMin, czMax,
                        cxMax - cxMin, cyMax - cyMin, czMax - czMin);
            }
            if (DIAG_TRANSFORM < 4 && vertexCount < 200) {
                DIAG_TRANSFORM++;
                var p = matrix.transformPosition(vertexData[INDEX_X], vertexData[INDEX_Y], vertexData[INDEX_Z],
                        new Vector3f());
                com.elfmcys.ysm.YesSteveModel.LOGGER.info(
                        "[YSM-XFORM #{}] raw=({},{},{}) -> world=({},{},{})",
                        DIAG_TRANSFORM, vertexData[INDEX_X], vertexData[INDEX_Y], vertexData[INDEX_Z],
                        p.x(), p.y(), p.z());
            }
            for (int i = 0; i < vertexCount; i++) {
                if (LIMIT > 0 && i >= LIMIT) {
                    break;
                }
                var offset = i * STRIDE;

                var color = Float.floatToRawIntBits(vertexData[offset + INDEX_COLOR]);
                var r = (color & 0xFF) / 255f;
                var g = ((color >>> 8) & 0xFF) / 255f;
                var b = ((color >>> 16) & 0xFF) / 255f;
                var a = (color >>> 24) / 255f;

                // native 侧 normal 是 3 个有符号字节打包进 float 位模式（[-1,1] 用 127 放大），
                // 取回来后必须除以 127 才是单位法线；旧代码乘 127 会让法线长达 16000，
                // 光照/背面判定全部失真。
                var normal = Float.floatToRawIntBits(vertexData[offset + INDEX_NORMAL]);
                var normalX = ((byte) ((normal >> 16) & 0xFF)) / 127f;
                var normalY = ((byte) ((normal >> 8) & 0xFF)) / 127f;
                var normalZ = ((byte) (normal & 0xFF)) / 127f;
                // 法线也要跟着 pose（含模型的旋转/镜像）转过去，否则朝向与几何不一致
                if (pose.normal() != null) {
                    pose.transformNormal(normalX, normalY, normalZ, NORMAL_SCRATCH);
                    normalX = NORMAL_SCRATCH.x();
                    normalY = NORMAL_SCRATCH.y();
                    normalZ = NORMAL_SCRATCH.z();
                }

                // 与原版 Cube.compile 完全同构：自己 transformPosition 后写裸坐标。
                // 不用 addVertex(Matrix4fc, ...) 这个 default 方法，避免缓冲区实现差异。
                matrix.transformPosition(
                        vertexData[offset + INDEX_X],
                        vertexData[offset + INDEX_Y],
                        vertexData[offset + INDEX_Z],
                        POS_SCRATCH);
                vertexBuffer.addVertex(POS_SCRATCH.x(), POS_SCRATCH.y(), POS_SCRATCH.z())
                        .setColor(MAGENTA ? 1f : r, MAGENTA ? 0f : g, MAGENTA ? 1f : b, a)
                        .setUv(vertexData[offset + INDEX_TEX_U], vertexData[offset + INDEX_TEX_V])
                        .setOverlay(overlayUv)
                        .setLight(Float.floatToRawIntBits(vertexData[offset + INDEX_LIGHT]))
                        .setNormal(normalX, normalY, normalZ);
                if (DIAG_WRITE < 4 && vertexCount < 200) {
                    DIAG_WRITE++;
                    com.elfmcys.ysm.YesSteveModel.LOGGER.info(
                            "[YSM-W #{}] pos=({},{},{}) color={} uv=({},{}) light={} normal=({},{},{})",
                            i, POS_SCRATCH.x(), POS_SCRATCH.y(), POS_SCRATCH.z(),
                            Integer.toHexString(color),
                            vertexData[offset + INDEX_TEX_U], vertexData[offset + INDEX_TEX_V],
                            Integer.toHexString(Float.floatToRawIntBits(vertexData[offset + INDEX_LIGHT])),
                            normalX, normalY, normalZ);
                }
            }
        }
    }
}
