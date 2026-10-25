package net.killstreakcod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** 明确的绘制入口赋予各控件稳定标识；无自定义布局时直接使用原版绘制路径。 */
public final class UiDraw {
    private UiDraw() {}

    public static void fill(GuiGraphics graphics, String id, int x, int y, int right, int bottom, int color) {
        UiLayout.draw(graphics, id,
                new UiLayout.Box(Math.min(x, right), Math.min(y, bottom), Math.max(1, Math.abs(right - x)),
                        Math.max(1, Math.abs(bottom - y))),
                () -> graphics.fill(x, y, right, bottom, UiLayout.color(color)));
    }

    public static void hLine(GuiGraphics graphics, String id, int x, int right, int y, int color) {
        fill(graphics, id, Math.min(x, right), y, Math.max(x, right) + 1, y + 1, color);
    }

    public static void vLine(GuiGraphics graphics, String id, int x, int y, int bottom, int color) {
        fill(graphics, id, x, Math.min(y, bottom), x + 1, Math.max(y, bottom) + 1, color);
    }

    public static void renderOutline(GuiGraphics graphics, String id, int x, int y, int w, int h, int color) {
        UiLayout.draw(graphics, id, new UiLayout.Box(x, y, w, h),
                () -> graphics.renderOutline(x, y, w, h, UiLayout.color(color)));
    }

    public static int drawString(GuiGraphics graphics, String id, Font font, String text, int x, int y, int color) {
        return drawString(graphics, id, font, text, x, y, color, true);
    }

    public static int drawString(GuiGraphics graphics, String id, Font font, String text, int x, int y, int color,
            boolean shadow) {
        if (text == null || text.isEmpty())
            return x;
        UiLayout.draw(graphics, id, new UiLayout.Box(x, y, font.width(text), font.lineHeight), () -> {
            if ((UiLayout.color(color) >>> 24) >= 4)
                graphics.drawString(font, text, x, y, UiLayout.color(color), shadow);
        });
        return x + font.width(text);
    }

    public static int drawString(GuiGraphics graphics, String id, Font font, String text, float x, float y, int color,
            boolean shadow) {
        return drawString(graphics, id, font, text, Math.round(x), Math.round(y), color, shadow);
    }

    public static int drawString(GuiGraphics graphics, String id, Font font, Component text, int x, int y, int color) {
        return drawString(graphics, id, font, text, x, y, color, true);
    }

    public static int drawString(GuiGraphics graphics, String id, Font font, Component text, int x, int y, int color,
            boolean shadow) {
        return drawString(graphics, id, font, text.getVisualOrderText(), x, y, color, shadow);
    }

    public static int drawString(GuiGraphics graphics, String id, Font font, FormattedCharSequence text, int x, int y,
            int color) {
        return drawString(graphics, id, font, text, x, y, color, true);
    }

    public static int drawString(GuiGraphics graphics, String id, Font font, FormattedCharSequence text, int x, int y,
            int color, boolean shadow) {
        UiLayout.draw(graphics, id, new UiLayout.Box(x, y, font.width(text), font.lineHeight), () -> {
            if ((UiLayout.color(color) >>> 24) >= 4)
                graphics.drawString(font, text, x, y, UiLayout.color(color), shadow);
        });
        return x + font.width(text);
    }

    public static void drawCenteredString(GuiGraphics graphics, String id, Font font, String text, int x, int y,
            int color) {
        drawString(graphics, id, font, text, x - font.width(text) / 2, y, color, true);
    }

    public static void drawCenteredString(GuiGraphics graphics, String id, Font font, Component text, int x, int y,
            int color) {
        drawString(graphics, id, font, text, x - font.width(text) / 2, y, color, true);
    }

    public static void drawCenteredString(GuiGraphics graphics, String id, Font font, FormattedCharSequence text, int x,
            int y, int color) {
        drawString(graphics, id, font, text, x - font.width(text) / 2, y, color, true);
    }

    private static void texture(GuiGraphics graphics, String id, ResourceLocation texture, int x, int y, int w, int h,
            Runnable draw) {
        UiLayout.draw(graphics, id + ":" + texture.getPath(), new UiLayout.Box(x, y, w, h), () -> {
            float factor = UiLayout.opacity();
            if (factor == 1) {
                draw.run();
                return;
            }
            float[] color = RenderSystem.getShaderColor().clone();
            graphics.flush();
            graphics.setColor(color[0], color[1], color[2], color[3] * factor);
            try {
                draw.run();
                graphics.flush();
            } finally {
                graphics.setColor(color[0], color[1], color[2], color[3]);
            }
        });
    }

    public static void blit(GuiGraphics graphics, String id, ResourceLocation texture, int x, int y, int u, int v,
            int w, int h) {
        texture(graphics, id, texture, x, y, w, h, () -> graphics.blit(texture, x, y, u, v, w, h));
    }

    public static void blit(GuiGraphics graphics, String id, ResourceLocation texture, int x, int y, float u, float v,
            int w, int h, int tw, int th) {
        texture(graphics, id, texture, x, y, w, h, () -> graphics.blit(texture, x, y, u, v, w, h, tw, th));
    }

    public static void blit(GuiGraphics graphics, String id, ResourceLocation texture, int x, int y, int z, float u,
            float v, int w, int h, int tw, int th) {
        texture(graphics, id, texture, x, y, w, h, () -> graphics.blit(texture, x, y, z, u, v, w, h, tw, th));
    }

    public static void blit(GuiGraphics graphics, String id, ResourceLocation texture, int x, int y, int w, int h,
            float u, float v, int sw, int sh, int tw, int th) {
        texture(graphics, id, texture, x, y, w, h, () -> graphics.blit(texture, x, y, w, h, u, v, sw, sh, tw, th));
    }
}
