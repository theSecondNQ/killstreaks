package net.killstreakcod.mixin;

import java.util.List;
import java.util.Set;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gate optional targets using the early mod list, without loading game/mod classes.
 * 通过早期模组列表判断是否启用可选目标，不加载游戏或模组类。
 */
public final class SuperbWarfareMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled;

    @Override
    public void onLoad(String name) {
        enabled = FMLLoader.getLoadingModList().getMods().stream().anyMatch(
                mod -> mod.getModId().equals("superbwarfare"));
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!enabled)
            return false;
        try {
            ClassNode node = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider()
                    .getClassNode(target);
            return node.methods.stream().anyMatch(method -> method.name.equals("canShoot")
                    && method.desc.equals("(Lnet/minecraft/world/entity/Entity;)Z"))
                    && node.methods.stream().anyMatch(method -> method.name.equals("shoot")
                            && method.desc.equals("(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V"));
        }
        catch (ClassNotFoundException | java.io.IOException absent) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> targets, Set<String> others) {}

    @Override
    public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
