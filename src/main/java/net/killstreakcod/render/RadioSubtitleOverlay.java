package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeRadioSubtitles;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** 独立绘制无线电字幕，兼容接管原版 HUD 的模组，并隔离其遗留的透明度、裁剪和深度状态。 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class RadioSubtitleOverlay {
    private RadioSubtitleOverlay() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void replacedHud(RenderGuiEvent.Pre event) {
        // Pre 被取消时不会有 Post；本模组遥控 HUD 和其他替代 HUD 都通过这里补画字幕。
        if (event.isCanceled())
            render(event.getGuiGraphics());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void normalHud(RenderGuiEvent.Post event) {
        render(event.getGuiGraphics());
    }

    public static void render(GuiGraphics graphics) {
        if (!NativeRadioSubtitles.visible())
            return;
        float[] color = RenderSystem.getShaderColor().clone();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND),
                scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int[] clip = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, clip);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        graphics.pose().pushPose();
        try {
            graphics.flush();
            graphics.pose().setIdentity();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableScissor();
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            NativeRadioSubtitles.render(graphics);
            UiLayout.flush(graphics);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunc);
            if (blend)
                RenderSystem.enableBlend();
            else
                RenderSystem.disableBlend();
            if (depth)
                RenderSystem.enableDepthTest();
            else
                RenderSystem.disableDepthTest();
            if (scissor)
                RenderSystem.enableScissor(clip[0], clip[1], clip[2], clip[3]);
            else
                RenderSystem.disableScissor();
        }
    }
}
