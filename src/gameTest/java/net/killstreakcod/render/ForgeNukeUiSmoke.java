package net.killstreakcod.render;

import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/** Real MP warning raster checks in an isolated client. 中文：隔离客户端中的多人模式核弹 UI 绘制检查。 */
final class ForgeNukeUiSmoke {
    private ForgeNukeUiSmoke() {}

    static void check(Minecraft minecraft) throws Exception {
        Path output = minecraft.gameDirectory.toPath().resolve("nuke-ui-smoke");
        Files.createDirectories(output);
        int frames = 0;
        for (int time : new int[] { 0, 99, 100, 149, 150, 300, 700, 1500, 5000, 8500, 9800, 9925, 10000 }) {
            try (ThermalRenderState ignored = new ThermalRenderState()) {
                var target = new TextureTarget(640, 360, true, Minecraft.ON_OSX);
                var view = RenderSystem.getModelViewStack();
                view.pushPose();
                try {
                    view.setIdentity();
                    view.translate(0, 0, -11000);
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(
                            new Matrix4f().setOrtho(0, minecraft.getWindow().getGuiScaledWidth(),
                                    minecraft.getWindow().getGuiScaledHeight(), 0, 1000, 21000),
                            VertexSorting.ORTHOGRAPHIC_Z);
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                    target.setClearColor(.2F, .2F, .2F, 1);
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    RenderSystem.disableDepthTest();
                    var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
                    String sample = "核弹来袭 字体 ABC 123";
                    graphics.drawString(minecraft.font, sample, 5, 70, 0xffffffff, false);
                    graphics.flush();
                    NukeUiRenderer.render(graphics, time, (10000 - time) / 50F, "NQ_Snvw");
                    graphics.drawString(minecraft.font, sample, 5, 85, 0xffffffff, false);
                    graphics.flush();
                    try (var image = Screenshot.takeScreenshot(target)) {
                        image.writeToFile(output.resolve("nuke-" + time + ".png"));
                        int changed = 0, brightTimer = 0;
                        for (int y = 0; y < 40; y++)
                            for (int x = 0; x < 640; x++) {
                                int pixel = image.getPixelRGBA(x, y);
                                if ((pixel & 0xffffff) != 0x333333)
                                    changed++;
                                if (x >= 300 && x < 335 && y >= 24 && y < 34 && (pixel & 255) > 150
                                        && (pixel >> 8 & 255) > 150 && (pixel >> 16 & 255) > 150)
                                    brightTimer++;
                            }
                        if (time < 10000 && changed < 30 || time == 10000 && changed != 0)
                            throw new AssertionError("Nuke UI visibility differs at " + time + ": " + changed);
                        if (time < 100 && brightTimer != 0 || time >= 100 && time <= 9800 && brightTimer == 0)
                            throw new AssertionError("Live timer visibility differs at " + time + ": " + brightTimer);
                        int delta = Math.round(15 * 360F / minecraft.getWindow().getGuiScaledHeight());
                        int firstY = Math.round(70 * 360F / minecraft.getWindow().getGuiScaledHeight());
                        int height = Math.round(9 * 360F / minecraft.getWindow().getGuiScaledHeight());
                        for (int y = firstY; y < firstY + height; y++)
                            for (int x = 5; x < 500; x++)
                                if (image.getPixelRGBA(x, y) != image.getPixelRGBA(x, y + delta))
                                    throw new AssertionError("Font rendering differs after nuke UI at " + time);
                    }
                    frames++;
                } finally {
                    view.popPose();
                    RenderSystem.applyModelViewMatrix();
                    target.destroyBuffers();
                }
            }
        }
        Files.writeString(output.resolve("result.json"), "{\"frames\":" + frames
                + ",\"nativeVisibility\":true,\"liveTimer\":true,\"fontRenderingPreserved\":true}\n");
    }
}
