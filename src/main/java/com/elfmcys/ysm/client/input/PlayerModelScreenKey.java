package com.elfmcys.ysm.client.input;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.gui.ConfigScreen;
import com.elfmcys.ysm.client.gui.DisclaimerScreen;
import com.elfmcys.ysm.client.gui.PlayerModelScreen;
import com.elfmcys.ysm.config.ClientConfig;
import com.elfmcys.ysm.config.ServerConfig;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.util.InputCheckUtil;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class PlayerModelScreenKey {
    public static final net.minecraft.client.KeyMapping PLAYER_MODEL_KEY = new net.minecraft.client.KeyMapping("key.yes_steve_model.player_model.desc",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            YsmKeyCategories.YSM);

    public static void register() {
        YsmInputDispatcher.registerKeyHandler(PlayerModelScreenKey::onKeyboardInput);
    }

    public static void onKeyboardInput(YsmKeyEvent event) {
        if (!InputCheckUtil.isInGame()) {
            return;
        }
        // 不要求 Alt：移植时把 Forge 的 KeyModifier 写成“必须按 Alt”，导致裸按 Y 无反应。
        // KeyMapping.matches 只比对键位，不比对修饰键，这里直接用它。
        if (event.getAction() == GLFW.GLFW_PRESS && InputCheckUtil.keyIsMatch(event, PLAYER_MODEL_KEY)) {
            YesSteveModel.LOGGER.info("[YSM-INPUT] model key pressed");
            if (!YesSteveModel.isAvailable()) {
                YesSteveModel.sendUnavailableMessage();
                return;
            }
            if (NetworkHandler.isRemoteChannelPresent() && !ServerConfig.CAN_SWITCH_MODEL.get()) {
                YsmInputDispatcher.openScreen(new ConfigScreen(null));
                return;
            }
            if (ClientConfig.DISCLAIMER_SHOW.get()) {
                YsmInputDispatcher.openScreen(new DisclaimerScreen());
            } else {
                YsmInputDispatcher.openScreen(new PlayerModelScreen());
            }
        }
    }
}
