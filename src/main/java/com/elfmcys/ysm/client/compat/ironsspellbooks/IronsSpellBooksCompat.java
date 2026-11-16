package com.elfmcys.ysm.client.compat.ironsspellbooks;

import com.elfmcys.ysm.client.animation.molang.CtrlBinding;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.world.entity.LivingEntity;

/**
 * Iron's Spellbooks 在 Fabric 26.1.2 上不存在，保留空实现以维持调用点结构。
 */
public class IronsSpellBooksCompat {
    public static void init() {
    }

    public static boolean isInstalled() {
        return false;
    }

    public static void addBinding(CtrlBinding binding) {
    }

    /**
     * 未安装 Iron's Spellbooks 时必须返回 {@code null}（=「这一帧不归我管」）。
     * <p>
     * 这里曾经返回 {@code PlayState.CONTINUE}，而 {@code SwingPredicate} 的写法是
     * {@code if (playState != null) return playState;} —— 于是整个 swing / use 分支被永久短路，
     * 表现为「攻击、挖掘完全没有动画」。返回 null 才等价于「兼容层不介入」。
     */
    public static PlayState playAnimation(AnimationEvent<CustomHumanoidEntity<?>> event, LivingEntity entity) {
        return null;
    }
}
