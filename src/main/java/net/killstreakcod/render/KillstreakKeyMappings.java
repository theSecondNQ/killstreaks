package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = KillstreakcodMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KillstreakKeyMappings {
    public static final KeyMapping PRIMARY_FIRE = mouse("primary_fire", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping SECONDARY_FIRE = mouse("secondary_fire", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final KeyMapping DRONE_DETONATE = mouse("drone_detonate", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping CRUISE_ACCELERATE = mouse("cruise_accelerate", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping MINIGUN_FIRE = mouse("minigun_fire", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final KeyMapping USE_SLOT_1 = new KeyMapping("key.killstreakcod.use_slot_1",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_3, "key.categories.killstreakcod");
    public static final KeyMapping USE_SLOT_2 = new KeyMapping("key.killstreakcod.use_slot_2",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_4, "key.categories.killstreakcod");
    public static final KeyMapping USE_SLOT_3 = new KeyMapping("key.killstreakcod.use_slot_3",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_5, "key.categories.killstreakcod");
    public static final KeyMapping OPEN_TERMINAL = new KeyMapping("key.killstreakcod.open_terminal",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.killstreakcod");
    public static final KeyMapping BONUS = key("bonus", GLFW.GLFW_KEY_6), MGB = key("mgb", GLFW.GLFW_KEY_N),
            THERMAL = key("thermal", GLFW.GLFW_KEY_V), CYCLE = key("weapon", GLFW.GLFW_KEY_X),
            EXIT = key("exit", GLFW.GLFW_KEY_G), AUTO = key("autopilot", GLFW.GLFW_KEY_B),
            BOOST = key("boost", GLFW.GLFW_KEY_R);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.killstreakcod." + name, InputConstants.Type.KEYSYM, code,
                "key.categories.killstreakcod");
    }

    private static KeyMapping mouse(String name, int button) {
        return new KeyMapping("key.killstreakcod." + name, InputConstants.Type.MOUSE, button,
                "key.categories.killstreakcod");
    }

    public static KeyMapping primary() {
        return switch (TacticalClient.state.mode()) {
            case "bomb_drone" -> DRONE_DETONATE;
            case "cruise" -> CRUISE_ACCELERATE;
            default -> PRIMARY_FIRE;
        };
    }

    public static final KeyMapping FLARES = key("flares", GLFW.GLFW_KEY_C);
    public static final KeyMapping EDIT_UI = key("edit_ui", GLFW.GLFW_KEY_F10);
    public static final KeyMapping WEAPON_1 = key("weapon_1", GLFW.GLFW_KEY_1),
            WEAPON_2 = key("weapon_2", GLFW.GLFW_KEY_2), WEAPON_3 = key("weapon_3", GLFW.GLFW_KEY_3),
            WEAPON_4 = key("weapon_4", GLFW.GLFW_KEY_4), WEAPON_5 = key("weapon_5", GLFW.GLFW_KEY_5),
            ZOOM = key("zoom", GLFW.GLFW_KEY_Z);

    private KillstreakKeyMappings() {}

    public static List<KeyMapping> all() {
        return List.of(PRIMARY_FIRE, SECONDARY_FIRE, MINIGUN_FIRE, DRONE_DETONATE, CRUISE_ACCELERATE, WEAPON_1,
                WEAPON_2, WEAPON_3, WEAPON_4, WEAPON_5, CYCLE, THERMAL, ZOOM, FLARES, AUTO, BOOST, EXIT, USE_SLOT_1,
                USE_SLOT_2, USE_SLOT_3, BONUS, MGB, OPEN_TERMINAL, EDIT_UI);
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        all().forEach(event::register);
    }
}
