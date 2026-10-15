package net.killstreakcod.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
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
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
