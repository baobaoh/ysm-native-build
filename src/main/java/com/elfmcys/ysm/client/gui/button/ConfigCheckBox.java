package com.elfmcys.ysm.client.gui.button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import com.elfmcys.ysm.config.spec.YsmConfigSpec;

/**
 * 26.1.2 移植：Checkbox 构造器不再公开，且 onPress 签名变化，
 * 改为通过 Builder + onValueChange 回调的工厂方法。
 */
public final class ConfigCheckBox {
    public static Checkbox create(int x, int y, String key, YsmConfigSpec.BooleanValue configSpec) {
        return Checkbox.builder(Component.translatable("gui.yes_steve_model.config." + key),
                        Minecraft.getInstance().font)
                .pos(x, y)
                .maxWidth(400)
                .selected(configSpec.get())
                .onValueChange((checkbox, selected) -> {
                    configSpec.set(selected);
                    // 立即落盘：界面可能通过「返回」按钮直接切走，不会走 onClose
                    com.elfmcys.ysm.config.ClientConfig.save();
                })
                .build();
    }
}
