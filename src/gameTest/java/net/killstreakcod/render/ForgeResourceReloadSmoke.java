package net.killstreakcod.render;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

/** Opt-in resource/key persistence probe, confined to the isolated client smoke directory. */
final class ForgeResourceReloadSmoke {
    private static int reloads;
    private static CompletableFuture<Void> pending;

    private ForgeResourceReloadSmoke() {}

    static boolean check(Minecraft minecraft) throws Exception {
        if (pending != null) {
            if (!pending.isDone()) {
                return false;
            }
            pending.join();
            pending = null;
        }
        require("key.keyboard.i".equals(minecraft.options.keyUp.saveString()), "Forward binding changed");
        require("key.keyboard.p".equals(KillstreakKeyMappings.OPEN_TERMINAL.saveString()),
                "Terminal binding changed");
        require("zh_cn".equals(minecraft.options.languageCode), "Language changed");
        require("连杀奖励终端".equals(I18n.get("item.killstreakcod.killstreak_controller")),
                "Mod Chinese translation missing");
        require("资源重载探针".equals(I18n.get("menu.singleplayer")), "Selected resource pack lost");
        require(minecraft.getResourcePackRepository().getSelectedIds().contains("file/resource-probe"),
                "Selected pack disabled");
        var getFontSet = Font.class.getDeclaredMethod("getFontSet", ResourceLocation.class);
        getFontSet.setAccessible(true);
        FontSet font = (FontSet) getFontSet.invoke(minecraft.font, Minecraft.DEFAULT_FONT);
        for (int codepoint : "资源重载探针连杀奖励终端单人游戏Options".codePoints().toArray()) {
            require(font.getGlyphInfo(codepoint, false) != SpecialGlyphs.MISSING,
                    "Missing glyph: " + Integer.toHexString(codepoint));
        }
        minecraft.options.save();
        Files.copy(minecraft.gameDirectory.toPath().resolve("options.txt"),
                minecraft.gameDirectory.toPath().resolve("resource-options-" + reloads + ".txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (reloads++ < 3) {
            pending = minecraft.reloadResourcePacks();
            return false;
        }
        Files.writeString(minecraft.gameDirectory.toPath().resolve("resource-smoke.json"),
                "{\"reloads\":3,\"checks\":4,\"customKeysPreserved\":true,"
                + "\"chineseTranslations\":true,\"sampleGlyphsPresent\":true,"
                + "\"selectedPackPreserved\":true}\n");
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
