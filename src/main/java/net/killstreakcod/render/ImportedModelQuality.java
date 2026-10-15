package net.killstreakcod.render;

import net.killstreakcod.render.compat.OptionalShaderCompat;

/**
 * Use the existing source LODs earlier during costly shader passes. First-person props are separate.
 * 在开销较大的光影渲染阶段更早切换到已有低细节模型，第一人称道具单独处理。
 */
public final class ImportedModelQuality {
    private ImportedModelQuality() {}

    static int worldTier(double distanceSquared, boolean ground) {
        if (OptionalShaderCompat.shadowPass())
            return 2;
        if (OptionalShaderCompat.shaderPackActive()) {
            int far = ground ? 36 : 112, near = ground ? 10 : 32;
            return distanceSquared >= far * far ? 2 : distanceSquared >= near * near ? 1 : 0;
        }
        int near = ground ? 18 : 48;
        return distanceSquared >= near * near ? 1 : 0;
    }
}
