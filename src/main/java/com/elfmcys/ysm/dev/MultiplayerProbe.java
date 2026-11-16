package com.elfmcys.ysm.dev;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapabilityProvider;
import com.elfmcys.ysm.capability.fabric.EntityCapabilityHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.player.Player;

/**
 * 联机验证探针：两个客户端分别以 {@code -Dysm.mpProbe=a} / {@code b} 启动，自动互相对视并截图。
 * <p>
 * 目的：验证「别的玩家的模型能不能正常显示」这条多人链路 —— 它走的不是自己的渲染路径，
 * 而是服务端下发模型目录 + 玩家 capability 同步 + 远端玩家绑定模型这几段，单人测试覆盖不到。
 * <p>
 * 另外两个单人角色（不需要第二台客户端）：
 * <ul>
 *   <li>{@code set} —— 给自己换一个模型然后退出，用来给「重连保持」造状态；</li>
 *   <li>{@code check} —— 只读：进服后报告自己当前的模型 ID 并截图，用来验证重进后选择是否还在。</li>
 * </ul>
 * 模型路径由 {@code -Dysm.mpModel} 指定，默认 {@code wine_fox/01_taisho_maid}。
 * <p>
 * 只在开发环境注册；正式 jar 里 {@code isDevelopmentEnvironment()} 为 false。
 */
@Environment(EnvType.CLIENT)
public final class MultiplayerProbe {
    /** "a" / "b" / "set" / "check"，空表示这一侧不启用。 */
    private static final String ROLE = System.getProperty("ysm.mpProbe", "");
    /** 要应用的模型路径（greedyString，可含 `/`，也可带 `#texture` 后缀）。 */
    private static final String MODEL = System.getProperty("ysm.mpModel", "wine_fox/01_taisho_maid");
    /** a / b 需要等对方出现；set / check 是单人流程，进档就开始计时。 */
    private static final boolean NEEDS_OTHER = "a".equals(ROLE) || "b".equals(ROLE);

    /**
     * 时间轴从「看见对方玩家」那一刻开始算，而不是从进世界开始算：
     * 两边客户端启动有先后，固定 tick 会在对方还没进来时就跑完，测到的永远是「没找到玩家」。
     */
    private static final int TICK_SETUP = 5;
    private static final int TICK_APPLY_MODEL = 20;
    private static final int TICK_REPORT = 60;
    private static final int TICK_SHOT1 = 80;
    private static final int TICK_SHOT2 = 100;
    /** B 侧先退，A 侧再多留一会，方便从日志里看到互相掉线的顺序。 */
    private static final int TICK_EXIT_B = 140;
    private static final int TICK_EXIT_A = 180;
    /** 单人角色（set/check）不需要等对方，报告完就走。 */
    private static final int TICK_SOLO_REPORT = 40;
    private static final int TICK_SOLO_SHOT = 120;
    private static final int TICK_SOLO_SHOT2 = 170;
    private static final int TICK_SOLO_EXIT = 220;
    /** 最多等 6 分钟对方出现，超时就退出，避免无人值守时一直挂着。 */
    private static final int MAX_WAIT_TICKS = 7200;

    private static boolean inWorld;
    private static int ticks;
    private static int waited;
    private static Player other;

    private MultiplayerProbe() {
    }

    public static void register() {
        if (ROLE.isEmpty()) {
            return;
        }
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            YesSteveModel.LOGGER.info("[YSM-MP] role={} joined server", ROLE);
            inWorld = true;
            ticks = 0;
            other = null;
            waited = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(MultiplayerProbe::tick);
    }

    private static void tick(Minecraft client) {
        if (!inWorld || client.player == null || client.level == null) {
            return;
        }
        // 每 tick 把视角转向另一个玩家：无人值守时靠它保证对方在画面里
        if (NEEDS_OTHER) {
            if (lookAtOtherPlayer(client)) {
                ticks++;
            } else if (++waited > MAX_WAIT_TICKS) {
                YesSteveModel.LOGGER.warn("[YSM-MP] {} gave up waiting for the other player", ROLE);
                client.stop();
                return;
            }
        } else {
            ticks++;
        }

        switch (ticks) {
            case TICK_SETUP -> {
                if ("a".equals(ROLE)) {
                    // 上一轮实测踩到的坑：服务器开局是夜里，截图里只剩一块黑墙面 + 名牌；
                    // 直接 tp 到地面上又被地形挡住（一人掉坑里、一人站山坡后面）。
                    // 所以先在空中铺一块平整石台，再把两人放到台面上、间距 6 格，保证模型完整可见。
                    send(client, "time set noon");
                    send(client, "weather clear");
                    send(client, "fill -156 90 -61 -144 90 -49 minecraft:stone");
                    send(client, "tp ysm_a -147.5 91 -55.5 90 0");
                    send(client, "tp ysm_b -153.5 91 -55.5 -90 0");
                }
            }
            case TICK_APPLY_MODEL -> {
                if ("a".equals(ROLE) || "set".equals(ROLE)) {
                    YesSteveModel.LOGGER.info("[YSM-MP] {}: applying model {} to self", ROLE, MODEL);
                    send(client, "ysm model set @s " + MODEL);
                }
            }
            case TICK_SOLO_REPORT -> {
                if (!NEEDS_OTHER) {
                    send(client, "time set noon");
                    send(client, "weather clear");
                    client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    report(client);
                }
            }
            case TICK_SOLO_SHOT -> {
                if (!NEEDS_OTHER) {
                    grab(client, "ysm-mp-" + ROLE + "-1");
                }
            }
            case TICK_SOLO_SHOT2 -> {
                if (!NEEDS_OTHER) {
                    grab(client, "ysm-mp-" + ROLE + "-2");
                }
            }
            case TICK_SOLO_EXIT -> {
                if (!NEEDS_OTHER) {
                    YesSteveModel.LOGGER.info("[YSM-MP] {}: done, stopping client", ROLE);
                    client.stop();
                }
            }
            case TICK_REPORT -> {
                if (NEEDS_OTHER) {
                    report(client);
                }
            }
            case TICK_SHOT1 -> {
                if (NEEDS_OTHER) {
                    grab(client, "ysm-mp-" + ROLE + "-1");
                }
            }
            case TICK_SHOT2 -> {
                if (NEEDS_OTHER) {
                    grab(client, "ysm-mp-" + ROLE + "-2");
                }
            }
            case TICK_EXIT_B -> {
                if ("b".equals(ROLE)) {
                    YesSteveModel.LOGGER.info("[YSM-MP] b: done, stopping client");
                    client.stop();
                }
            }
            case TICK_EXIT_A -> {
                if ("a".equals(ROLE)) {
                    YesSteveModel.LOGGER.info("[YSM-MP] a: done, stopping client");
                    client.stop();
                }
            }
            default -> {
            }
        }
    }

    private static void send(Minecraft client, String command) {
        if (client.player != null) {
            client.player.connection.sendCommand(command);
        }
    }

    /** 把本地玩家的视角对准另一个玩家（水平 + 俯仰）。返回是否已经找到对方。 */
    private static boolean lookAtOtherPlayer(Minecraft client) {
        var self = client.player;
        if (other == null || !other.isAlive()) {
            other = client.level.players().stream().filter(p -> p != self).findFirst().orElse(null);
            if (other == null) {
                return false;
            }
            YesSteveModel.LOGGER.info("[YSM-MP] found other player: {}", other.getGameProfile().name());
        }
        double dx = other.getX() - self.getX();
        double dz = other.getZ() - self.getZ();
        double dy = other.getY() + other.getEyeHeight() - self.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        self.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F);
        self.setXRot((float) (-Math.toDegrees(Math.atan2(dy, horizontal))));
        return true;
    }

    /** 打印两侧各自看到的「自己」与「对方」的模型 ID —— 这是同步是否真的生效的直接证据。 */
    private static void report(Minecraft client) {
        var self = client.player;
        if (self == null) {
            return;
        }
        YesSteveModel.LOGGER.info("[YSM-MP] {} self={} model={} distance={}", ROLE,
                self.getGameProfile().name(), modelIdOf(self), other == null ? -1.0F : self.distanceTo(other));
        if (other != null) {
            YesSteveModel.LOGGER.info("[YSM-MP] {} other={} model={} pos=({},{},{})", ROLE,
                    other.getGameProfile().name(), modelIdOf(other),
                    String.format("%.1f", other.getX()), String.format("%.1f", other.getY()),
                    String.format("%.1f", other.getZ()));
        } else {
            YesSteveModel.LOGGER.warn("[YSM-MP] {} other player not found", ROLE);
        }
    }

    private static String modelIdOf(Player player) {
        var capability = EntityCapabilityHolder
                .get(player, PlayerAnimatableCapabilityProvider.CAP)
                .orElse(null);
        if (capability == null) {
            return "<no capability>";
        }
        var target = capability.getModelRenderTarget();
        if (target == null) {
            return "<no render target>";
        }
        return capability.getModelId();
    }

    private static void grab(Minecraft client, String name) {
        YesSteveModel.LOGGER.info("[YSM-MP] grabbing screenshot {}", name);
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1,
                component -> YesSteveModel.LOGGER.info("[YSM-MP] screenshot saved: {}", component.getString()));
    }
}
