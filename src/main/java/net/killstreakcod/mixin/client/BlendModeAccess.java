package net.killstreakcod.mixin.client;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Preserve ShaderInstance's blend cache together with the actual OpenGL blend state. */
@Mixin(BlendMode.class)
public interface BlendModeAccess {
    @Accessor("lastApplied")
    static BlendMode killstreak$getLastApplied() {
        throw new AssertionError("BlendMode accessor was not applied");
    }

    @Accessor("lastApplied")
    static void killstreak$setLastApplied(BlendMode mode) {
        throw new AssertionError("BlendMode accessor was not applied");
    }
}
