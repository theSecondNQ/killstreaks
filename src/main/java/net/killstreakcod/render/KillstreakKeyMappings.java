package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = KillstreakcodMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KillstreakKeyMappings {
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

    public static final KeyMapping FLARES = key("flares", GLFW.GLFW_KEY_C);
    public static final KeyMapping WEAPON_1 = key("weapon_1", GLFW.GLFW_KEY_1),
            WEAPON_2 = key("weapon_2", GLFW.GLFW_KEY_2), WEAPON_3 = key("weapon_3", GLFW.GLFW_KEY_3),
            WEAPON_4 = key("weapon_4", GLFW.GLFW_KEY_4), WEAPON_5 = key("weapon_5", GLFW.GLFW_KEY_5),
            ZOOM = key("zoom", GLFW.GLFW_KEY_Z);

    private KillstreakKeyMappings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(USE_SLOT_1);
        event.register(USE_SLOT_2);
        event.register(USE_SLOT_3);
        event.register(OPEN_TERMINAL);
        event.register(BONUS);
        event.register(MGB);
        event.register(THERMAL);
        event.register(CYCLE);
        event.register(EXIT);
        event.register(AUTO);
        event.register(BOOST);
        event.register(FLARES);
        event.register(WEAPON_1);
        event.register(WEAPON_2);
        event.register(WEAPON_3);
        event.register(WEAPON_4);
        event.register(WEAPON_5);
        event.register(ZOOM);
    }
}
