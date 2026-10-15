package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Local cabin luminaires and a cached vertex light field; no moving world blocks.
 * 使用座舱局部灯具及缓存的顶点光照场，不在世界中移动发光方块。
 */
public final class GunshipCabinLighting {
    private static final float[][] SOURCES = { { 0, 3.65F, -.8F, 15, 5 }, { 0, 3.65F, 1.9F, 15, 5 },
            { -.25F, 1.7F, -1.6F, 12, 2.7F } };

    // AH64 坐标转换：源资源为正 X 向前、正 Z 向上，转为 Minecraft 的负 Z 向前、正 Y 向上。
    private static final float[][] CHOPPER_SOURCES = { { 0, -.70F, -2.50F, 15, 2.2F },
            { 0, -1.20F, -2.90F, 13, 1.3F } };
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/entity/thermal_white.png");

    public record Profile(float x, float y, float z, boolean chopper) {
        public Profile(float x, float y, float z) {
            this(x, y, z, false);
        }

        int level(float vx, float vy, float vz) {
            float result = chopper ? 0 : 6;
            vx += x;
            vy += y;
            vz += z;
            for (float[] lamp : chopper ? CHOPPER_SOURCES : SOURCES) {
                float dx = vx - lamp[0], dy = vy - lamp[1], dz = vz - lamp[2];
                float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                result = Math.max(result, lamp[3] - distance * 8 / lamp[4]);
            }
            return Math.clamp(Math.round(result), chopper ? 0 : 6, 15);
        }

        int light(int ambient, int local) {
            return chopper ? LightTexture.pack(Math.max(LightTexture.block(ambient), local), LightTexture.sky(ambient))
                    : GunshipCabinLighting.light(ambient, local);
        }
    }

    public static final Profile STATION = new Profile(0, 0, 0);
    public static final Profile CHOPPER = new Profile(0, 0, 0, true);

    public static int light(int ambient, int local) {
        return LightTexture.pack(Math.max(LightTexture.block(ambient), local), Math.min(LightTexture.sky(ambient), 3));
    }

    public static int operatorLight(int ambient) {
        return light(ambient, 12);
    }

    public static void renderChopperFixture(PoseStack pose, MultiBufferSource buffers) {
        var out = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE, false));
        float x = 0, y = -.70F, z = -2.50F, w = .10F, d = .014F;
        out.addVertex(pose.last().pose(), x - w, y, z - d).setColor(0xffc8e4e7).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.pack(15, 0))
                .setNormal(pose.last(), 0, -1, 0);
        out.addVertex(pose.last().pose(), x + w, y, z - d).setColor(0xffc8e4e7).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.pack(15, 0))
                .setNormal(pose.last(), 0, -1, 0);
        out.addVertex(pose.last().pose(), x + w, y, z + d).setColor(0xffc8e4e7).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.pack(15, 0))
                .setNormal(pose.last(), 0, -1, 0);
        out.addVertex(pose.last().pose(), x - w, y, z + d).setColor(0xffc8e4e7).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.pack(15, 0))
                .setNormal(pose.last(), 0, -1, 0);
    }

    private GunshipCabinLighting() {}
}
