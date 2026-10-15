package net.killstreakcod.mixin.client;

import net.killstreakcod.render.KillstreakDisplayName;

import net.neoforged.neoforgespi.language.IModInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NeoForge's list, detail, search and sort paths otherwise use the literal metadata name.
 * 若不处理，NeoForge 的列表、详情、搜索和排序都会直接使用元数据中的固定名称。
 */
@Mixin(targets = { "net.neoforged.neoforge.client.gui.ModListScreen",
        "net.neoforged.neoforge.client.gui.ModListScreen$SortType",
        "net.neoforged.neoforge.client.gui.widget.ModListWidget$ModEntry" }, remap = false)
public abstract class ModDisplayNameMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforgespi/language/IModInfo;getDisplayName()Ljava/lang/String;", remap = false), remap = false, require = 1)
    private static String killstreakcod$localizedName(IModInfo info) {
        return KillstreakDisplayName.forMod(info);
    }
}
