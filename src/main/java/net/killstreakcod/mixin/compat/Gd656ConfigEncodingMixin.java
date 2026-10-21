package net.killstreakcod.mixin.compat;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Match GD656's JSON preset encoding when the JVM default charset is not UTF-8.
 * GD656 的 JSON 预设使用 UTF-8；显式指定读写编码，避免 JVM 默认字符集不同造成中文乱码。
 */
@Pseudo
@Mixin(targets = { "org.mods.gd656killicon.client.config.ElementConfigManager",
        "org.mods.gd656killicon.client.config.ClientConfigManager" }, remap = false)
public abstract class Gd656ConfigEncodingMixin {
    // Match only the constructors that implicitly select the platform charset.
    // 只替换依赖平台默认字符集的单参数构造器，保留目标模组已明确指定编码的读写。
    @Redirect(method = "*", at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileReader;", remap = false), remap = false, require = 0)
    private static FileReader killstreakcod$readGdConfigUtf8(File file) throws IOException {
        return new FileReader(file, StandardCharsets.UTF_8);
    }

    @Redirect(method = "*", at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileWriter;", remap = false), remap = false, require = 0)
    private static FileWriter killstreakcod$writeGdConfigUtf8(File file) throws IOException {
        return new FileWriter(file, StandardCharsets.UTF_8);
    }
}
