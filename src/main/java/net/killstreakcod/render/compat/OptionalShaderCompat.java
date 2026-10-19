package net.killstreakcod.render.compat;

import java.lang.reflect.Method;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;

/**
 * Query Iris through its public API without linking optional shader classes.
 * 通过 Iris 的公开 API 查询状态，不直接链接可选光影类。
 */
public final class OptionalShaderCompat {
    private static boolean initialized;
    private static boolean failed;
    private static Object irisApi;
    private static Method shaderPackInUse;
    private static Method renderingShadowPass;

    private OptionalShaderCompat() {}

    /**
     * Display compensation only; an unavailable optional API must not alter the vanilla canvas.
     * 只做显示补偿；可选 API 不可用时，不应改变原版画布。
     */
    public static boolean shaderPackActive() {
        if (!initialize())
            return false;
        try {
            return shaderPackInUse != null && ((Boolean) shaderPackInUse.invoke(irisApi));
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            fail(failure);
            return false;
        }
    }

    /**
     * Never cache this result across world and shadow passes in the same frame.
     * 同一帧内的世界渲染与阴影渲染阶段不能共用此查询结果缓存。
     */
    public static boolean shadowPass() {
        if (!initialize())
            return true;
        try {
            return renderingShadowPass != null && ((Boolean) renderingShadowPass.invoke(irisApi));
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            fail(failure);
            return true;
        }
    }

    private static boolean initialize() {
        if (failed)
            return false;
        if (initialized)
            return true;
        try {
            initialized = true;
            if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus"))
                return true;
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi", true,
                    OptionalShaderCompat.class.getClassLoader());
            irisApi = api.getMethod("getInstance").invoke(null);
            shaderPackInUse = api.getMethod("isShaderPackInUse");
            renderingShadowPass = api.getMethod("isRenderingShadowPass");
            if (irisApi == null || shaderPackInUse.getReturnType() != boolean.class
                    || renderingShadowPass.getReturnType() != boolean.class)
                throw new IllegalStateException("Unexpected Iris shader API");
            return true;
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            fail(failure);
            return false;
        }
    }

    private static void fail(Throwable failure) {
        if (!failed)
            LogUtils.getLogger().warn(
                    "Killstreaks: Iris shader API unavailable; thermal entity replacement disabled to protect shadow rendering",
                    failure);
        failed = true;
        irisApi = null;
        shaderPackInUse = null;
        renderingShadowPass = null;
    }
}
