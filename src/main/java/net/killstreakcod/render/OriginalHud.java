package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Original exported UI layers; terrain, targets and counters are still live Minecraft data.
 * 使用原游戏导出的 UI 图层，地形、目标和计数器仍取 Minecraft 实时数据。
 */
public final class OriginalHud {
    private OriginalHud() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "textures/gui/mwii/" + name + ".png");
    }

    public static boolean layer(GuiGraphics graphics, String name, int x, int y, int width, int height, float opacity) {
        return drawTexture(graphics, texture(name), x, y, width, height, opacity);
    }

    /**
     * Crop only the verified transparent padding of an original UI image.
     * 只裁掉已确认的原 UI 图像透明留白。
     */
    public static void region(GuiGraphics graphics, String name, int x, int y, int width, int height, int u, int v,
            int sw, int sh, int tw, int th, float opacity) {
        if (width <= 0 || height <= 0 || opacity <= 0)
            return;
        graphics.flush();
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        int[] factors = blendFactors();
        float[] color = RenderSystem.getShaderColor().clone();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, Mth.clamp(opacity, 0, 1));
        try {
            graphics.blit(texture(name), x, y, width, height, u, v, sw, sh, tw, th);
        } finally {
            graphics.setColor(color[0], color[1], color[2], color[3]);
            restoreBlendFactors(factors);
            if (!blending)
                RenderSystem.disableBlend();
        }
    }

    private static boolean drawTexture(GuiGraphics graphics, ResourceLocation id, int x, int y, int width, int height,
            float opacity) {
        if (width <= 0 || height <= 0 || opacity <= 0
                || Minecraft.getInstance().getResourceManager().getResource(id).isEmpty())
            return false;
        graphics.flush();

        // GUI 图像会立即提交绘制，而此前的不透明实体批次可能已关闭混合，需要恢复混合状态。
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        int[] factors = blendFactors();
        float[] color = RenderSystem.getShaderColor().clone();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, Mth.clamp(opacity, 0, 1));
        try {
            graphics.blit(id, x, y, width, height, 0, 0, 1, 1, 1, 1);
        } finally {
            graphics.setColor(color[0], color[1], color[2], color[3]);
            restoreBlendFactors(factors);
            if (!blending)
                RenderSystem.disableBlend();
        }
        return true;
    }

    public static void remote(GuiGraphics graphics) {
        if (!TacticalClient.state.mode().equals("bomb_drone"))
            return;

        // 原全屏遮罩在透明度接近零处仍含浅色 RGB；只绘制深色传感器边缘，使实际视频画面保持清晰。
        int w = graphics.guiWidth(), h = graphics.guiHeight(), edge = Math.max(3, Math.min(w, h) / 14);
        for (int i = 0; i < edge; i++) {
            int opacity = (int) (66 * Math.pow(1 - i / (double) edge, 2)), color = opacity << 24 | 0x03090b;
            graphics.hLine(i, w - i - 1, i, color);
            graphics.hLine(i, w - i - 1, h - i - 1, color);
            graphics.vLine(i, i, h - i - 1, color);
            graphics.vLine(w - i - 1, i, h - i - 1, color);
        }
    }

    private static int[] blendFactors() {
        return new int[] { GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) };
    }

    private static void restoreBlendFactors(int[] factors) {
        RenderSystem.blendFuncSeparate(factors[0], factors[1], factors[2], factors[3]);
    }

}
