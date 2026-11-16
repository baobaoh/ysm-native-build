package com.elfmcys.ysm.dev;

import com.elfmcys.ysm.YesSteveModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 仅开发环境使用的探针：进世界后自动摆好姿势、抓两张截图（第一人称 + 第三人称）然后退出。
 * <p>
 * 目的：第一人称手臂、第三人称模型、界面参考实体这类「只能看画面」的问题没法靠编译和日志判断，
 * 自动跑一遍截图，就能在无人值守的情况下验证渲染修改是否真的生效。
 * 玩家安装的 jar 里这段逻辑不会注册（{@code isDevelopmentEnvironment()} 为 false）。
 */
@Environment(EnvType.CLIENT)
public final class RenderProbe {
    private static final int TICK_EMPTY_HAND = 40;
    private static final int TICK_FP_SHOT = 70;
    /**
     * 免责声明界面：玩家第一次按 Y 看到的就是它，必须能勾选 + 点关闭才进得去模型界面。
     * 这一串把「勾选 -> 关闭 -> 落到 PlayerModelScreen 且配置写下去」整条走完。
     */
    private static final int TICK_DISCLAIMER_OPEN = 100;
    private static final int TICK_DISCLAIMER_SHOT = 104;
    private static final int TICK_DISCLAIMER_CHECK = 108;
    private static final int TICK_DISCLAIMER_BUTTON = 112;
    private static final int TICK_DISCLAIMER_VERIFY = 118;
    private static final int TICK_GUI_OPEN = 130;
    /** 打开界面后模拟点一次模型缩略图，验证「点选换模型」这条主流程。 */
    private static final int TICK_GUI_CLICK = 140;
    private static final int TICK_CLICK_SHOT = 160;
    private static final int TICK_GUI_SHOT = 155;
    private static final int TICK_EXTRA_OPEN = 170;
    private static final int TICK_EXTRA_SHOT = 190;
    private static final int TICK_CONFIG_OPEN = 205;
    private static final int TICK_CONFIG_SHOT = 225;
    /** 点一下配置界面的复选框再点回去：验证开关能生效并落盘（最终值回到原样，不影响后续截图）。 */
    private static final int TICK_CONFIG_TOGGLE_ON = 230;
    private static final int TICK_CONFIG_TOGGLE_OFF = 234;
    private static final int TICK_GUI_CLOSE = 240;
    /** 动画轮盘：Z 键那条链路的界面，走 pi p 之外的另一套绘制。 */
    private static final int TICK_ROULETTE_OPEN = 245;
    private static final int TICK_ROULETTE_SHOT = 252;
    private static final int TICK_ROULETTE_CLOSE = 256;
    private static final int TICK_SWITCH_THIRD = 260;
    private static final int TICK_TP_SHOT = 285;
    /** 隔 15 tick 再抓两张：三张画面若有差异，说明骨骼动画确实在推进（玩家是静止的，相机不动）。 */
    private static final int TICK_TP_SHOT2 = 300;
    private static final int TICK_TP_SHOT3 = 315;
    /**
     * 第三人称下挥一次手，并在挥动过程中连抓三张。
     * <p>
     * 目的是验证「攻击 / 挖掘没有动画」这条玩家反馈：原版挥臂时长 6 tick，
     * 所以从起手后的第 1 / 3 / 6 tick 各抓一张，任一帧与静止帧不同即说明骨骼动画在跟。
     */
    private static final int TICK_SWING_TP = 318;
    private static final int TICK_SWING_TP_SHOT1 = 319;
    private static final int TICK_SWING_TP_SHOT2 = 321;
    private static final int TICK_SWING_TP_SHOT3 = 324;
    private static final int EXIT_DELAY_TICKS = 20;

    /** -Dysm.armSweep="x,y,z;x,y,z;..."：逐个应用偏移并各抓一张 ysm-arm-<i>，用于目视标定手臂位置。 */
    private static final String[][] SWEEP = parseSweep(System.getProperty("ysm.armSweep", ""));
    private static int sweepIndex;
    private static int sweepTicks;

    private static boolean inWorld;
    private static int ticks;
    private static int exitTicks;

    private RenderProbe() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            YesSteveModel.LOGGER.info("[YSM-PROBE] joined world");
            inWorld = true;
            ticks = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(RenderProbe::tick);
    }

    private static void tick(Minecraft client) {
        if (exitTicks > 0 && --exitTicks == 0) {
            client.stop();
            return;
        }
        if (!inWorld || client.player == null || client.level == null) {
            return;
        }
        ticks++;
        // 手臂位置标定：每 20 tick 换一组偏移并抓一张图
        if (SWEEP.length > 0 && ticks >= TICK_FP_SHOT) {
            var step = ticks - TICK_FP_SHOT;
            if (step % 20 == 0) {
                sweepIndex = step / 20;
                if (sweepIndex < SWEEP.length) {
                    var p = SWEEP[sweepIndex];
                    com.elfmcys.ysm.client.renderer.ArmPoseState.set("x", Float.parseFloat(p[0]));
                    com.elfmcys.ysm.client.renderer.ArmPoseState.set("y", Float.parseFloat(p[1]));
                    com.elfmcys.ysm.client.renderer.ArmPoseState.set("z", Float.parseFloat(p[2]));
                    if (p.length > 3) {
                        com.elfmcys.ysm.client.renderer.ArmPoseState.set("pitch", Float.parseFloat(p[3]));
                    }
                    if (p.length > 4) {
                        com.elfmcys.ysm.client.renderer.ArmPoseState.set("roll", Float.parseFloat(p[4]));
                    }
                    if (p.length > 5) {
                        com.elfmcys.ysm.client.renderer.ArmPoseState.set("tilt", Float.parseFloat(p[5]));
                    }
                    YesSteveModel.LOGGER.info("[YSM-PROBE] armSweep #{} -> {}", sweepIndex,
                            com.elfmcys.ysm.client.renderer.ArmPoseState.describe());
                }
            }
            if (step % 20 == 6 && sweepIndex < SWEEP.length) {
                grab(client, "ysm-arm-" + sweepIndex);
                sweepTicks = sweepIndex;
            }
        }
        switch (ticks) {
            case 20 -> {
                var cmd = System.getProperty("ysm.probeCommand", "");
                if (!cmd.isBlank()) {
                    // 走集成服务器的命令栈并给到权限 4：测试存档没开作弊也能拿到 op 命令，
                    // 这样探针就能在不改动玩家存档设置的前提下把玩家换成某个内置模型。
                    // 多条命令用 ";;" 分隔，按顺序执行，用来一次性冒烟测试命令树。
                    var server = client.getSingleplayerServer();
                    if (server != null) {
                        for (var single : cmd.split(";;")) {
                            var trimmed = single.trim();
                            if (trimmed.isEmpty()) {
                                continue;
                            }
                            YesSteveModel.LOGGER.info("[YSM-PROBE] running command (op): /{}", trimmed);
                            server.execute(() -> server.getCommands().performPrefixedCommand(
                                    server.createCommandSourceStack()
                                            .withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
                                    trimmed));
                        }
                    }
                }
            }
            case TICK_EMPTY_HAND -> client.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            case 60 -> {
                // 挥一下手：走 attack 动画与手臂 attackValue 那条姿态路径，确认不会崩
                YesSteveModel.LOGGER.info("[YSM-PROBE] swinging main hand");
                client.player.swing(InteractionHand.MAIN_HAND);
            }
            case 64 -> grab(client, "ysm-swing");
            case 68 -> {
                // 视野压低到水平：之前探针把俯仰改成看天，之后存档里就一直保持抬头，
                // 第一人称手臂在这个视角下会被判定到画面外，截图看起来像「没有手」
                client.player.setXRot(0.0F);
            }
            case TICK_FP_SHOT -> {
                com.elfmcys.ysm.natives.render.NativeRenderer.resetDiag();
                grab(client, "ysm-fp");
            }
            case TICK_DISCLAIMER_OPEN -> {
                // 强制把「每次显示免责声明」打开，保证这一轮一定走到那个界面
                com.elfmcys.ysm.config.ClientConfig.DISCLAIMER_SHOW.set(true);
                YesSteveModel.LOGGER.info("[YSM-PROBE] opening DisclaimerScreen");
                client.setScreen(new com.elfmcys.ysm.client.gui.DisclaimerScreen());
            }
            case TICK_DISCLAIMER_SHOT -> grab(client, "ysm-disclaimer");
            case TICK_DISCLAIMER_CHECK -> clickWidget(client, "Checkbox",
                    child -> child instanceof net.minecraft.client.gui.components.Checkbox);
            case TICK_DISCLAIMER_BUTTON -> clickWidget(client, "AbstractButton",
                    child -> child instanceof net.minecraft.client.gui.components.AbstractButton
                            && !(child instanceof net.minecraft.client.gui.components.Checkbox));
            case TICK_DISCLAIMER_VERIFY -> {
                var screen = client.screen;
                YesSteveModel.LOGGER.info("[YSM-PROBE] after disclaimer: screen={} disclaimerShow={}",
                        screen == null ? "null" : screen.getClass().getSimpleName(),
                        com.elfmcys.ysm.config.ClientConfig.DISCLAIMER_SHOW.get());
            }
            case TICK_GUI_OPEN -> {
                // 打开模型选择界面：界面里的参考实体走 PiP 预览渲染，是另一条独立的渲染链路，
                // 只截世界画面覆盖不到它。
                YesSteveModel.LOGGER.info("[YSM-PROBE] opening PlayerModelScreen");
                client.setScreen(new com.elfmcys.ysm.client.gui.PlayerModelScreen());
            }
            case TICK_GUI_SHOT -> grab(client, "ysm-gui");
            case TICK_GUI_CLICK -> clickCatalogModel(client);
            case TICK_EXTRA_OPEN -> {
                YesSteveModel.LOGGER.info("[YSM-PROBE] opening ExtraPlayerConfigScreen");
                client.setScreen(new com.elfmcys.ysm.client.gui.ExtraPlayerConfigScreen());
            }
            case TICK_EXTRA_SHOT -> grab(client, "ysm-gui-extra");
            case TICK_CONFIG_OPEN -> {
                YesSteveModel.LOGGER.info("[YSM-PROBE] opening ConfigScreen");
                client.setScreen(new com.elfmcys.ysm.client.gui.ConfigScreen(null));
            }
            case TICK_CONFIG_SHOT -> grab(client, "ysm-gui-config");
            case TICK_CONFIG_TOGGLE_ON -> {
                clickWidget(client, "Checkbox", child -> child instanceof net.minecraft.client.gui.components.Checkbox);
                YesSteveModel.LOGGER.info("[YSM-PROBE] config DisableSelfModel={}",
                        com.elfmcys.ysm.config.ClientConfig.DISABLE_SELF_MODEL.get());
            }
            case TICK_CONFIG_TOGGLE_OFF -> {
                clickWidget(client, "Checkbox", child -> child instanceof net.minecraft.client.gui.components.Checkbox);
                YesSteveModel.LOGGER.info("[YSM-PROBE] config DisableSelfModel={} (restored)",
                        com.elfmcys.ysm.config.ClientConfig.DISABLE_SELF_MODEL.get());
            }
            case TICK_CLICK_SHOT -> grab(client, "ysm-click");
            case TICK_GUI_CLOSE -> client.setScreen(null);
            case TICK_ROULETTE_OPEN -> openRoulette(client);
            case TICK_ROULETTE_SHOT -> grab(client, "ysm-roulette");
            case TICK_ROULETTE_CLOSE -> client.setScreen(null);
            case TICK_SWITCH_THIRD -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case TICK_TP_SHOT -> grab(client, "ysm-tp");
            case TICK_TP_SHOT2 -> grab(client, "ysm-tp2");
            case TICK_TP_SHOT3 -> grab(client, "ysm-tp3");
            case TICK_SWING_TP -> {
                var player = client.player;
                YesSteveModel.LOGGER.info("[YSM-PROBE] third-person swing: swinging={} swingTime={} arm={}",
                        player.swinging, player.swingTime, player.swingingArm);
                player.swing(InteractionHand.MAIN_HAND);
                YesSteveModel.LOGGER.info("[YSM-PROBE] after swing(): swinging={} swingTime={} arm={}",
                        player.swinging, player.swingTime, player.swingingArm);
            }
            case TICK_SWING_TP_SHOT1 -> {
                YesSteveModel.LOGGER.info("[YSM-PROBE] swing+1: swinging={} swingTime={}",
                        client.player.swinging, client.player.swingTime);
                grab(client, "ysm-swingtp1");
            }
            case TICK_SWING_TP_SHOT2 -> {
                YesSteveModel.LOGGER.info("[YSM-PROBE] swing+3: swinging={} swingTime={}",
                        client.player.swinging, client.player.swingTime);
                grab(client, "ysm-swingtp2");
            }
            case TICK_SWING_TP_SHOT3 -> {
                YesSteveModel.LOGGER.info("[YSM-PROBE] swing+6: swinging={} swingTime={}",
                        client.player.swinging, client.player.swingTime);
                grab(client, "ysm-swingtp3");
            }
            case TICK_TP_SHOT3 + EXIT_DELAY_TICKS -> {
                inWorld = false;
                exitTicks = EXIT_DELAY_TICKS;
            }
            default -> {
            }
        }
    }

    /** 打开动画轮盘（Z 键那条链路的界面），用来验证轮盘界面本身能不能画出来。 */
    private static void openRoulette(Minecraft client) {
        var player = client.player;
        if (player == null) {
            YesSteveModel.LOGGER.warn("[YSM-PROBE] roulette: no player");
            return;
        }
        var cap = com.elfmcys.ysm.capability.fabric.EntityCapabilityHolder
                .get(player, com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider.CAP)
                .orElse(null);
        if (cap == null) {
            YesSteveModel.LOGGER.warn("[YSM-PROBE] roulette: player has no animatable capability");
            return;
        }
        var model = cap.getModelRenderTarget();
        if (model == null) {
            YesSteveModel.LOGGER.warn("[YSM-PROBE] roulette: no model render target");
            return;
        }
        var order = model.info().properties().extraAnimationOrderMap();
        YesSteveModel.LOGGER.info("[YSM-PROBE] roulette entries = {} (model={})", order.size(), cap.getModelId());
        if (order.isEmpty()) {
            return;
        }
        client.setScreen(new com.elfmcys.ysm.client.gui.AnimationRouletteScreen(cap.getModelId(), model, cap));
    }

    /**
     * 点当前界面里第一个命中的控件，用于走完免责声明那条流程。
     * 注意 1.21.11 的 {@code Button.Builder#build()} 返回的是 {@code Button.Plain}（简单名 "Plain"），
     * 所以按类名 "Button" 匹配一个都找不到，必须按类型判断。
     */
    private static void clickWidget(Minecraft client, String label,
                                    java.util.function.Predicate<Object> matcher) {
        var screen = client.screen;
        if (screen == null) {
            YesSteveModel.LOGGER.warn("[YSM-PROBE] clickWidget: no screen");
            return;
        }
        try {
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && matcher.test(child)) {
                    double cx = widget.getX() + widget.getWidth() / 2.0;
                    double cy = widget.getY() + widget.getHeight() / 2.0;
                    YesSteveModel.LOGGER.info("[YSM-PROBE] clicking {} ({}) at ({},{})",
                            label, widget.getClass().getSimpleName(), cx, cy);
                    screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(cx, cy,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                    return;
                }
            }
            YesSteveModel.LOGGER.warn("[YSM-PROBE] clickWidget: no {} in {}", label, screen.getClass().getSimpleName());
        } catch (Throwable t) {
            YesSteveModel.LOGGER.error("[YSM-PROBE] clickWidget failed", t);
        }
    }

    /** 在模型选择界面里找到缩略图按钮并点一下，验证「点选换模型」这条玩家主流程。 */
    private static void clickCatalogModel(Minecraft client) {
        var screen = client.screen;
        if (screen == null) {
            YesSteveModel.LOGGER.warn("[YSM-PROBE] no screen to click");
            return;
        }
        try {
            var widgets = screen.children();
            int index = 0;
            for (var child : widgets) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    var cls = widget.getClass().getSimpleName();
                    YesSteveModel.LOGGER.info("[YSM-PROBE] child#{} {} at ({},{}) {}x{}", index, cls,
                            widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
                    // 第二个缩略图按钮：第一个通常是「默认模型」，点它不会真正换模型
                    if (cls.contains("CatalogModelButton") && index > 0) {
                        double cx = widget.getX() + widget.getWidth() / 2.0;
                        double cy = widget.getY() + widget.getHeight() / 2.0;
                        YesSteveModel.LOGGER.info("[YSM-PROBE] clicking {} at ({},{})", cls, cx, cy);
                        // 1.21.11 的点击入口吃 MouseButtonEvent(record)，不再是裸坐标 + button 整数
                        screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(cx, cy,
                                new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                        return;
                    }
                }
                index++;
            }
            YesSteveModel.LOGGER.warn("[YSM-PROBE] no catalog model button found in {} children", index);
        } catch (Throwable t) {
            YesSteveModel.LOGGER.error("[YSM-PROBE] click probe failed", t);
        }
    }

    private static String[][] parseSweep(String spec) {
        if (spec == null || spec.isBlank()) {
            return new String[0][];
        }
        var parts = spec.split(";");
        var out = new String[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            out[i] = parts[i].split(",");
        }
        return out;
    }

    private static void grab(Minecraft client, String name) {
        try {
            YesSteveModel.LOGGER.info("[YSM-PROBE] grabbing screenshot {}", name);
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1,
                    message -> YesSteveModel.LOGGER.info("[YSM-PROBE] screenshot saved: {}", message.getString()));
        } catch (Throwable t) {
            YesSteveModel.LOGGER.error("[YSM-PROBE] screenshot failed", t);
        }
    }
}
