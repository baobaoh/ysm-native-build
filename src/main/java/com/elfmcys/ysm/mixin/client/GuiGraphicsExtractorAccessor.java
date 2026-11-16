package com.elfmcys.ysm.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 反混淆 26.1.2 的 GuiGraphics 没有公开 PiP 提交方法，
 * 通过访问器拿到内部的 GuiRenderState 与剪刀栈，自行提交
 * {@link net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState}。
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsExtractorAccessor {
    @Accessor("guiRenderState")
    GuiRenderState ysm$guiRenderState();
}
