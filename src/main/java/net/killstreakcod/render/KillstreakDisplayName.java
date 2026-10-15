package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Localize this mod's visible name after the client language resources have loaded.
 * 客户端语言资源加载后，再对本模组的可见名称进行本地化。
 */
public final class KillstreakDisplayName {
    private KillstreakDisplayName() {}

    public static String forMod(IModInfo info) {
        return KillstreakcodMod.MODID.equals(info.getModId()) ? I18n.get("mod.killstreakcod.name")
                : info.getDisplayName();
    }
}
