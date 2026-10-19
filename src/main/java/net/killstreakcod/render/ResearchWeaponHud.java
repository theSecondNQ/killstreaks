package net.killstreakcod.render;

import net.killstreakcod.universal.ResearchCombat;
import net.killstreakcod.universal.StreakWeaponProfiles;

import java.util.stream.IntStream;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/**
 * Supplemental armament uses the deployed mission's capabilities, not the current research menu selection.
 * 附加武器使用已部署任务的能力快照，不读取研究界面中后来更改的装配。
 */
public final class ResearchWeaponHud {
    private ResearchWeaponHud() {}

    public static boolean capability(int flag) {
        return (TacticalClient.state.capabilities() & flag) != 0;
    }

    public static int[] gunshipSlots() {
        return IntStream.range(0, 5).filter(index -> index < 3 || index == 3 && capability(ResearchCombat.COORDINATION)
                || index == 4 && capability(ResearchCombat.IRON_MAIDEN)).toArray();
    }

    public static String name(int slot) {
        return switch (slot) {
            case 0 -> "25 MM";
            case 1 -> "40 MM";
            case 2 -> "105 MM";
            case 3 -> I18n.get("hud.killstreakcod.research_designator");
            default -> I18n.get("hud.killstreakcod.research_rocket_pod");
        };
    }

    public static KeyMapping key(int slot) {
        return switch (slot) {
            case 0 -> KillstreakKeyMappings.WEAPON_1;
            case 1 -> KillstreakKeyMappings.WEAPON_2;
            case 2 -> KillstreakKeyMappings.WEAPON_3;
            case 3 -> KillstreakKeyMappings.WEAPON_4;
            default -> KillstreakKeyMappings.WEAPON_5;
        };
    }

    public static String ammo(int slot) {
        if (slot < 3)
            return TacticalClient.reload(slot) > 0 ? I18n.get("hud.killstreakcod.load")
                    : TacticalClient.state.ammo()[slot] + "/" + StreakWeaponProfiles.MAGAZINE[slot];
        if (slot == 4)
            return TacticalClient.state.rockets() + "/32";
        return TacticalClient.state.weapon() == slot && TacticalClient.cooldown() > 0 ? I18n
                .get("hud.killstreakcod.research_designator_cooldown", (int) Math.ceil(TacticalClient.cooldown() / 20D))
                : I18n.get("hud.killstreakcod.weapon_ready");
    }

    public static String rocketCount() {
        return capability(ResearchCombat.UNLIMITED_ROCKETS) ? I18n.get("hud.killstreakcod.research_rockets_infinite")
                : I18n.get("hud.killstreakcod.rockets_count", TacticalClient.state.rockets());
    }

    public static KeyMapping fireKey() {
        var options = Minecraft.getInstance().options;
        return TacticalClient.state.mode().equals("gunship") && TacticalClient.state.weapon() == 4 ? options.keyUse
                : options.keyAttack;
    }

    public static void status(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (capability(ResearchCombat.DISABLED)) {
            String text = I18n.get("hud.killstreakcod.research_disabled");
            graphics.drawString(mc.font, text, (graphics.guiWidth() - mc.font.width(text)) / 2, 44, 0xfff07363, false);
        }
        if (TacticalClient.state.mode().equals("cruise") && capability(ResearchCombat.HELLFIRE)) {
            String text = I18n.get("hud.killstreakcod.research_secondary_rockets",
                    mc.options.keyUse.getTranslatedKeyMessage().getString(), TacticalClient.state.rockets());
            graphics.drawString(mc.font, text, 12, 48, 0xffecf0e9, false);
        }
    }
}
