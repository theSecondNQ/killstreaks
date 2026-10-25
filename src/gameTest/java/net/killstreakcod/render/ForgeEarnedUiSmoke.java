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

/** Actual GUI raster checks in the isolated smoke client. 中文：隔离客户端中的真实 GUI 绘制检查。 */
final class ForgeEarnedUiSmoke {
    private ForgeEarnedUiSmoke() {}

    static void check(Minecraft minecraft) throws Exception {
        Path output = minecraft.gameDirectory.toPath().resolve("earned-ui-smoke");
        Files.createDirectories(output);
        var projection = new Matrix4f().setOrtho(0, minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(), 0, 1000, 21000);
        int frames = 0;
        for (int time : new int[] { 100, 225, 450, 1000, 2050, 2150, 2350, 2400 }) {
            try (ThermalRenderState ignored = new ThermalRenderState()) {
                var target = new TextureTarget(640, 360, true, Minecraft.ON_OSX);
                var view = RenderSystem.getModelViewStack();
                view.pushPose();
                try {
                    view.setIdentity();
                    view.translate(0, 0, -11000);
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                    target.setClearColor(.2F, .2F, .2F, 1);
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    RenderSystem.disableDepthTest();
                    var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
                    String sample = "连杀奖励 字体 ABC 123";
                    graphics.drawString(minecraft.font, sample, 5, 5, 0xffffffff, false);
                    graphics.flush();
                    EarnedSplashRenderer.render(graphics, "chopper_gunner", "按下[P]使用", time);
                    graphics.drawString(minecraft.font, sample, 5, 20, 0xffffffff, false);
                    graphics.flush();
                    try (var image = Screenshot.takeScreenshot(target)) {
                        image.writeToFile(output.resolve("earned-" + time + ".png"));
                        int changed = 0;
                        for (int y = 48; y < 150; y++)
                            for (int x = 100; x < 640; x++)
                                if ((image.getPixelRGBA(x, y) & 0xffffff) != 0x333333)
                                    changed++;
                        if (time < 2400 && changed < 20 || time == 2400 && changed != 0)
                            throw new AssertionError("Earned splash visibility differs at " + time + ": " + changed);
                        // 顶部和底部两份同文应完全相同，捕获绘制后字体、着色或混合状态泄漏。
                        int delta = Math.round(15 * 360F / minecraft.getWindow().getGuiScaledHeight());
                        int firstY = Math.round(5 * 360F / minecraft.getWindow().getGuiScaledHeight());
                        int height = Math.round(9 * 360F / minecraft.getWindow().getGuiScaledHeight());
                        for (int y = firstY; y < firstY + height; y++)
                            for (int x = 5; x < 500; x++)
                                if (image.getPixelRGBA(x, y) != image.getPixelRGBA(x, y + delta))
                                    throw new AssertionError("Font rendering differs after earned UI at " + time);
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
                + ",\"nativeVisibility\":true,\"fontRenderingPreserved\":true}\n");
    }
}
