package com.elfmcys.ysm.client.renderer;

/**
 * 第一人称手臂摆放的目视微调参数，可在游戏内用 {@code /ysmclient armpose} 实时调整。
 *
 * <p>x/y/z 是<b>屏幕空间</b>的微调：x/y 以 NDC（-1 左/下 … 1 右/上）为单位平移手臂锚点，
 * z 调整手臂离相机的距离（正 = 更近）。这样调整结果不受 FOV 和窗口宽高比影响
 * （早先用固定相机空间偏移，玩家把 FOV 调到 30 时整只手会跑到屏幕右侧之外）。
 * yaw/pitch 是链条前的相机空间旋转。
 * sx/sy/sz 是模型空间缩放，默认 (1,-1,-1)（绕 X 180°，把手臂 -y 延伸方向
 * 对齐到原版部件的 +y），正常不需要动。
 *
 * <p>肩关节对齐偏移（±0.072, 1.861, 0）是固定常量，在渲染器里。
 */
public final class ArmPoseState {
    private static volatile float x = 0.0F;
    private static volatile float y = 0.0F;
    private static volatile float z = 0.0F;
    private static volatile float sx = 1.0F;
    private static volatile float sy = -1.0F;
    private static volatile float sz = -1.0F;
    private static volatile float yaw = 0.0F;
    private static volatile float pitch = 0.0F;
    /** 绕手臂自身长轴的扭转角（度）：手臂朝前伸之后，「沿手臂旋转」就是这个轴。 */
    private static volatile float roll = 12.0F;
    /** 屏幕上手臂的倾斜角（度）：绕视线方向滚转，从右侧水平躺变成斜指准星。 */
    private static volatile float tilt = 25.0F;

    private ArmPoseState() {
    }

    public static float x() {
        return x;
    }

    public static float y() {
        return y;
    }

    public static float z() {
        return z;
    }

    public static float sx() {
        return sx;
    }

    public static float sy() {
        return sy;
    }

    public static float sz() {
        return sz;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static float roll() {
        return roll;
    }

    public static float tilt() {
        return tilt;
    }

    /**
     * 按轴名设置，未知轴名返回 false
     */
    public static boolean set(String axis, float value) {
        switch (axis) {
            case "x" -> x = value;
            case "y" -> y = value;
            case "z" -> z = value;
            case "sx" -> sx = value;
            case "sy" -> sy = value;
            case "sz" -> sz = value;
            case "yaw" -> yaw = value;
            case "pitch" -> pitch = value;
            case "roll" -> roll = value;
            case "tilt" -> tilt = value;
            default -> {
                return false;
            }
        }
        return true;
    }

    public static String describe() {
        return String.format("x=%.3f y=%.3f z=%.3f sx=%.1f sy=%.1f sz=%.1f yaw=%.1f pitch=%.1f roll=%.1f tilt=%.1f",
                x, y, z, sx, sy, sz, yaw, pitch, roll, tilt);
    }
}
