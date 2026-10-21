package net.killstreakcod.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Resolve class bytes without loading client classes or requiring an installed optional mod.
 * 读取类字节码，不加载客户端类，也不要求可选模组已经安装。
 */
public final class OptionalMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String packageName) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(target) != null;
        }
        catch (ClassNotFoundException | java.io.IOException absent) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> targets, Set<String> others) {}

    @Override
    public List<String> getMixins() {
        // Register this client-only bridge in code so source-only ports do not need a copied resource JSON.
        // 通过代码注册客户端兼容补丁，使只同步源码的移植也能启用；目标类缺失时不加载兼容类。
        if (MixinEnvironment.getCurrentEnvironment().getSide() == MixinEnvironment.Side.CLIENT
                && (shouldApplyMixin("org.mods.gd656killicon.client.config.ElementConfigManager",
                        "net.killstreakcod.mixin.compat.Gd656ConfigEncodingMixin")
                        || shouldApplyMixin("org.mods.gd656killicon.client.config.ClientConfigManager",
                                "net.killstreakcod.mixin.compat.Gd656ConfigEncodingMixin")))
            return List.of("Gd656ConfigEncodingMixin");
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
