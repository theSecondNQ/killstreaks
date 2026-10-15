package net.killstreakcod.mixin;

import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Select the terrain hook from the early NeoForge mod list, before client classes initialize.
 * 在客户端类初始化前，根据 NeoForge 的早期模组列表选择地形注入点。
 */
public final class RenderingMixinPlugin implements IMixinConfigPlugin {
    private boolean sodiumTerrain;

    @Override
    public void onLoad(String packageName) {
        // ModLauncher forbids getClassNode(name, false). The loading list is already
        // populated when Mixin selects configs and also includes jar-in-jar mods.
        // 中文：ModLauncher 禁止调用 getClassNode(name, false)。Mixin 选择配置时，模组加载列表已填充完成，并包含 JAR 内
        // 嵌的模组。
        sodiumTerrain = FMLLoader.getLoadingModList().getMods().stream()
                .anyMatch(mod -> mod.getModId().equals("sodium"));
        if (sodiumTerrain)
            MixinService.getService().getLogger("killstreakcod").info(
                    "Sodium terrain renderer detected: using its camera origin and skipping vanilla terrain redirects.");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        // Sodium overwrites setupRender and already builds terrain around Camera.getPosition().
        // Injecting LocalPlayer redirects into that overwrite is both invalid and unnecessary.
        // 中文：Sodium 重写了 setupRender，且已围绕 Camera.getPosition() 构建地形；再向该重写方法注入 LocalPlayer 重定
        // 向既不合法，也没有必要。
        return !sodiumTerrain || !mixin.equals("net.killstreakcod.mixin.RemoteTerrainOriginMixin");
    }

    @Override
    public void acceptTargets(Set<String> targets, Set<String> others) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
