package net.killstreakcod.render.tablet;

import java.util.function.Function;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

/**
 * An opaque LCD using a stock text shader that Iris also recognizes in its hand pass.
 * 不透明液晶屏使用原版文字着色器，Iris 在手部渲染阶段也能识别该着色器。
 */
public final class TabletDisplayMaterial extends RenderType {
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(texture -> create(
            "killstreakcod_tablet_lcd", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536,
            false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false)).setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setLightmapState(LIGHTMAP).createCompositeState(false)));
    private static final int[] HIGHLIGHTS = new int[256];
    static {
        // Preserve shadows; roll off bright UI pixels before the shader pack's bloom and exposure.
        // 中文：保留阴影，在光影包执行辉光和曝光前压低 UI 的高亮像素。
        for (int peak = 0; peak < 256; peak++)
            HIGHLIGHTS[peak] = peak <= 96 ? peak : (int) Math.round(96 + 80 * (1 - Math.exp(-(peak - 96) / 112.0)));
    }

    private TabletDisplayMaterial() {
        super("unused", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536, false, false,
                () -> {
                }, () -> {
                });
    }

    public static RenderType type(ResourceLocation texture) {
        return TYPES.apply(texture);
    }

    /**
     * NativeImage uses ABGR. Scale all three channels equally to retain the source hue and alpha.
     * NativeImage 使用 ABGR 格式，三个颜色通道等比例缩放，以保留源图像色相及透明度。
     */
    public static void prepare(NativeImage source, NativeImage display, boolean shaderPack) {
        display.copyFrom(source);
        if (!shaderPack)
            return;
        for (int y = 0; y < source.getHeight(); y++)
            for (int x = 0; x < source.getWidth(); x++) {
                int color = source.getPixelRGBA(x, y), red = color & 255, green = (color >>> 8) & 255,
                        blue = (color >>> 16) & 255;
                int peak = Math.max(red, Math.max(green, blue));
                if (peak <= 96)
                    continue;
                int mapped = HIGHLIGHTS[peak];
                red = (red * mapped + peak / 2) / peak;
                green = (green * mapped + peak / 2) / peak;
                blue = (blue * mapped + peak / 2) / peak;
                display.setPixelRGBA(x, y, (color & 0xff000000) | (blue << 16) | (green << 8) | red);
            }
    }
}
