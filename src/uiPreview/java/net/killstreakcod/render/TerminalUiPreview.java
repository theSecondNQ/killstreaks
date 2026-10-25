package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.VoiceFaction;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.universal.HeadquartersTerminalMenu;
import net.killstreakcod.universal.ResearchCatalog;
import net.killstreakcod.universal.ResearchMask;
import net.killstreakcod.universal.ResearchStatePayload;
import net.killstreakcod.universal.TerminalStatePayload;
import net.killstreakcod.universal.WhitelistStatePayload;
import net.killstreakcod.universal.StreakServerConfig;
import net.killstreakcod.universal.TerminalSettingsStatePayload;
import net.killstreakcod.universal.NativePresentationConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** 使用隔离目录和示例快照导出真实界面 PNG，供人工检查布局；不连接服务器、不创建世界。 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class TerminalUiPreview {
    private static int stage, wait;
    private static boolean languageRequested, previewForeignUi;
    private static CompletableFuture<Void> reload;
    private static final int[][] SIZES = { { 1280, 800, 2 }, { 960, 720, 3 }, { 1920, 1080, 2 } };
    private static final String[] LANGUAGES = { "zh_cn", "en_us" };

    private TerminalUiPreview() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("killstreakcod.uiPreview"))
            return;
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
                && mc.getOverlay() == null)
            mc.setScreen(new TitleScreen());
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null)
            return;
        if (stage >= SIZES.length * LANGUAGES.length) {
            mc.stop();
            return;
        }
        String language = LANGUAGES[stage / SIZES.length];
        if (!languageRequested) {
            mc.options.languageCode = language;
            mc.getLanguageManager().setSelected(language);
            reload = mc.reloadResourcePacks();
            languageRequested = true;
            return;
        }
        if (reload != null && !reload.isDone())
            return;
        int[] size = SIZES[stage % SIZES.length];
        if (wait == 0) {
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]);
            mc.options.guiScale().set(size[2]);
            mc.resizeDisplay();
            wait = 16;
            return;
        }
        if (--wait > 1)
            return;
        export(mc, language + "-" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
        wait = 0;
        stage++;
        if (stage % SIZES.length == 0)
            languageRequested = false;
    }

    private static void export(Minecraft mc, String suffix) throws Exception {
        Path output = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(output);
        Path skins = mc.gameDirectory.toPath().resolve("killstreakcod-skins");
        Files.createDirectories(skins);
        var texture = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
        try (var source = mc.getResourceManager().getResourceOrThrow(texture).open()) {
            Files.write(skins.resolve("Alpha_operator.png"), source.readAllBytes());
        }
        Files.copy(skins.resolve("Alpha_operator.png"), skins.resolve("Bravo_operator_with_a_long_file_name.png"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        var terminalState = new TerminalStatePayload("uav", "cruise_missile", "gunship", false, 8, 1250, 5, false,
                new int[] { 2, 0, 1 }, new int[] { 2, 4, 12 }, true, 0, VoiceFaction.SPECGRU, true);
        var terminal = new KillstreakTerminalScreen(terminalState);
        capture(mc, terminal, output.resolve("loadout-" + suffix + ".png"));
        EditBox search = (EditBox) field(terminal, "search", null);
        search.setValue("uav");
        captureInitialized(mc, terminal, output.resolve("search-" + suffix + ".png"));
        search.setValue("not_a_reward");
        captureInitialized(mc, terminal, output.resolve("empty-" + suffix + ".png"));
        search.setValue("");
        var researched = ResearchCatalog.node("uav").bit().union(ResearchCatalog.node("counter_uav").bit());
        var research = new ResearchStatePayload(77, 2, researched, ResearchMask.EMPTY, ResearchMask.EMPTY, false, true,
                false, false, "", new int[StreakReward.SELECTABLE.size()], 0, false,
                new int[StreakReward.SELECTABLE.size()], 0);
        var previous = TacticalClient.research;
        TacticalClient.research = research;
        try {
            var headquarters = new HeadquartersTerminalScreen(new HeadquartersTerminalMenu(77, new Inventory(null)),
                    new Inventory(null), Component.translatable("block.killstreakcod.headquarters_terminal"));
            capture(mc, headquarters, output.resolve("research-" + suffix + ".png"));
        } finally {
            TacticalClient.research = previous;
        }
        var team = new KillstreakWhitelistScreen(terminal);
        team.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        UUID request = UUID.randomUUID();
        field(team, "request", request);
        int rows = (int) field(team, "rows", null);
        var teammates = members(rows, "Alpha_Teammate_", true);
        var online = members(rows, "Online_Player_", true);
        team.receiveState(new WhitelistStatePayload(request, 0, 2, rows + 2, 0, 2, rows + 1, rows, true, "", "",
                teammates, online));
        for (int i = 0; i < 5; i++)
            team.tick();
        captureInitialized(mc, team, output.resolve("teammates-" + suffix + ".png"));
        capture(mc, new OperatorSkinScreen(terminal), output.resolve("skins-" + suffix + ".png"));
        var settings = new TerminalSettingsScreen(terminal);
        settings.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        UUID settingsRequest = UUID.randomUUID();
        field(settings, "request", settingsRequest);
        var values = new LinkedHashMap<String, Double>();
        for (var setting : StreakServerConfig.settings())
            values.put(setting.id(), setting.initial());
        settings.receiveState(
                new TerminalSettingsStatePayload(settingsRequest, true, VoiceFaction.SPECGRU, "", values));
        for (var page : TerminalSettingsScreen.Page.values()) {
            field(settings, "page", page);
            settings.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            captureInitialized(mc, settings, output
                    .resolve("settings-" + page.name().toLowerCase(java.util.Locale.ROOT) + "-" + suffix + ".png"));
        }
        layoutPreviews(mc, terminal, terminalState, output, suffix);
    }

    private static void layoutPreviews(Minecraft mc, KillstreakTerminalScreen terminal, TerminalStatePayload data,
            Path output, String suffix) throws Exception {
        var previous = TacticalClient.terminal;
        int style = NativePresentationConfig.REWARD_RAIL_STYLE.get();
        boolean autoHide = NativePresentationConfig.REWARD_RAIL_AUTO_HIDE.get();
        TacticalClient.terminal = data;
        NativePresentationConfig.REWARD_RAIL_AUTO_HIDE.set(false);
        try {
            Screen hud = new Screen(Component.empty()) {
                @Override
                public void render(GuiGraphics graphics, int x, int y, float partial) {
                    TacticalHud.normal(graphics);
                    UiLayout.flush(graphics);
                }
            };
            for (int mode = 0; mode < 3; mode++) {
                NativePresentationConfig.REWARD_RAIL_STYLE.set(mode);
                capture(mc, hud, output.resolve("rail-" + mode + "-" + suffix + ".png"));
            }
            NativePresentationConfig.REWARD_RAIL_AUTO_HIDE.set(true);
            fieldStatic(RewardRail.class, "used", net.minecraft.Util.getMillis() - 4000);
            fieldStatic(RewardRail.class, "visible", 0F);
            fieldStatic(RewardRail.class, "world", mc.level);
            Screen activeScreen = mc.screen;
            try {
                mc.screen = null;
                capture(mc, hud, output.resolve("rail-collapsed-" + suffix + ".png"));
            } finally {
                mc.screen = activeScreen;
            }
            RewardRail.reveal();
            NativePresentationConfig.REWARD_RAIL_STYLE.set(1);
            for (int frame : new int[] { 0, 60, 120, 180 }) {
                fieldStatic(RewardRail.class, "visible", 0F);
                fieldStatic(RewardRail.class, "from", 0F);
                fieldStatic(RewardRail.class, "wasExpanded", true);
                fieldStatic(RewardRail.class, "transitionStarted", net.minecraft.Util.getMillis() - frame);
                capture(mc, hud, output.resolve("rail-expand-" + frame + "-" + suffix + ".png"));
            }
            Screen item = new Screen(Component.empty()) {
                @Override
                public void render(GuiGraphics graphics, int x, int y, float partial) {
                    graphics.fill(0, 0, width, height, 0xff20282b);
                    graphics.drawString(mc.font, "M134", width / 2 - 16, height / 2 - 38, 0xffe5eee6);
                    graphics.pose().pushPose();
                    graphics.pose().translate(width / 2F - 24, height / 2F - 24, 0);
                    graphics.pose().scale(3, 3, 3);
                    graphics.renderItem(new net.minecraft.world.item.ItemStack(KillstreakcodMod.M134_MINIGUN.get()), 0,
                            0);
                    graphics.pose().popPose();
                }
            };
            capture(mc, item, output.resolve("m134-icon-" + suffix + ".png"));
            Screen sentry = new Screen(Component.empty()) {
                @Override
                public void render(GuiGraphics graphics, int x, int y, float partial) {
                    graphics.fill(0, 0, width, height, 0xff20282b);
                    graphics.pose().pushPose();
                    graphics.pose().translate(width / 2F - 24, height / 2F - 24, 0);
                    graphics.pose().scale(3, 3, 3);
                    graphics.renderItem(new net.minecraft.world.item.ItemStack(KillstreakcodMod.SENTRY_GUN.get()), 0,
                            0);
                    graphics.pose().popPose();
                }
            };
            capture(mc, sentry, output.resolve("sentry-model-icon-" + suffix + ".png"));
            Screen clouds = new Screen(Component.empty()) {
                @Override
                public void render(GuiGraphics graphics, int x, int y, float partial) {
                    graphics.fill(0, 0, width, height, 0xff20282b);
                    graphics.flush();
                    var pose = graphics.pose();
                    pose.pushPose();
                    try {
                        float fit = Math.min(width / 210F, height / 140F);
                        pose.translate(width / 2F, height - 14, 600);
                        pose.scale(fit, -fit, fit);
                        var method = MgbWorldRenderer.class.getDeclaredMethod("cloud",
                                com.mojang.blaze3d.vertex.PoseStack.class,
                                net.minecraft.client.renderer.MultiBufferSource.class,
                                net.minecraft.world.phys.Vec3.class, net.minecraft.world.phys.Vec3.class, float.class,
                                int.class);
                        method.setAccessible(true);
                        int index = 0;
                        for (var point : net.killstreakcod.universal.NuclearPattern
                                .centers(net.minecraft.world.phys.Vec3.ZERO))
                            method.invoke(null, pose, mc.renderBuffers().bufferSource(),
                                    net.minecraft.world.phys.Vec3.ZERO, point, 100F, index++);
                        mc.renderBuffers().bufferSource().endBatch();
                    }
                    catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(failure);
                    } finally {
                        pose.popPose();
                    }
                }
            };
            capture(mc, clouds, output.resolve("nuclear-clouds-" + suffix + ".png"));
            for (String reward : List.of("uav", "gunship", "bomb_drone", "sentry_gun")) {
                Screen hands = new Screen(Component.empty()) {
                    @Override
                    public void render(GuiGraphics graphics, int x, int y, float partial) {
                        graphics.fill(0, 0, width, height, 0xff20282b);
                        graphics.flush();
                        var pose = graphics.pose();
                        pose.pushPose();
                        try {
                            float fit = Math.min(width / 1.6F, height / 1.1F);
                            pose.translate(width / 2F, height / 2F, 600);
                            pose.scale(fit, -fit, fit);
                            net.killstreakcod.render.tablet.SceneTabletScreen.begin(reward, "call");
                            OriginalStreakAnimations.render(reward, "call", reward.equals("bomb_drone") ? 18 : 14, pose,
                                    mc.renderBuffers().bufferSource(), 15728880,
                                    new net.killstreakcod.render.tablet.TabletHandModel(),
                                    ResourceLocation.fromNamespaceAndPath("minecraft",
                                            "textures/entity/player/wide/steve.png"));
                            mc.renderBuffers().bufferSource().endBatch();
                        } finally {
                            pose.popPose();
                        }
                    }
                };
                capture(mc, hands, output.resolve("hand-fit-" + reward + "-" + suffix + ".png"));
            }
            for (String family : List.of("spotter", "laser", "m13421")) {
                String model = family.equals("spotter") ? "designator_precision_vm"
                        : family.equals("laser") ? "designator_mortar_vm" : "m13421_vm";
                Screen equipment = equipmentPreview(mc, family, model, 0);
                capture(mc, equipment, output.resolve("hand-fit-" + family + "-" + suffix + ".png"));
                if (family.equals("m13421"))
                    capture(mc, equipmentPreview(mc, family, model, -55),
                            output.resolve("m134-muzzle-side-" + suffix + ".png"));
            }
            for (int fov : new int[] { 70, 110 })
                for (boolean left : new boolean[] { false, true })
                    capture(mc, muzzleProjectionPreview(mc, fov, left), output
                            .resolve("m134-world-muzzle-" + fov + (left ? "-left-" : "-right-") + suffix + ".png"));
            var editor = new UiLayoutEditorScreen(terminal);
            capture(mc, editor, output.resolve("layout-editor-" + suffix + ".png"));
            var buttons = UiLayout.elements("hud.normal");
            var button = buttons.stream().filter(e -> e.key().endsWith("/group/slot.0")).findFirst();
            if (button.isPresent()) {
                field(editor, "selected", button.get().key());
                var refresh = UiLayoutEditorScreen.class.getDeclaredMethod("updateFields");
                refresh.setAccessible(true);
                refresh.invoke(editor);
                captureInitialized(mc, editor, output.resolve("layout-editor-" + suffix + ".png"));
                UiLayout.change(button.get().key(), new UiLayout.Style(.02F, .02F, .9F, 1.15F, .6F, true));
                refresh.invoke(editor);
                captureInitialized(mc, editor, output.resolve("layout-custom-slot-" + suffix + ".png"));
            }
            for (String context : List.of("hud.gunship", "hud.chopper", "hud.cruise", "hud.bomb_drone", "hud.wheelson",
                    "hud.earned", "hud.nuke", "hud.scope", "hud.radio")) {
                field(editor, "context", context);
                field(editor, "selected", null);
                captureInitialized(mc, editor,
                        output.resolve("layout-" + context.replace('.', '-') + "-" + suffix + ".png"));
            }
            remoteHudPreviews(mc, output, suffix);
            radioCaptionPreviews(mc, output, suffix);
            UiLayout.cancel();
        } finally {
            UiLayout.cancel();
            TacticalClient.terminal = previous;
            NativePresentationConfig.REWARD_RAIL_STYLE.set(style);
            NativePresentationConfig.REWARD_RAIL_AUTO_HIDE.set(autoHide);
        }
    }

    /** 普通与遥控入口的人工界面预览：红色分别模拟外部 Pre、Post 监听器和注册叠层。 */
    private static void remoteHudPreviews(Minecraft mc, Path output, String suffix) throws Exception {
        var previous = TacticalClient.state;
        try {
            for (boolean remote : new boolean[] { false, true }) {
                TacticalClient.state = new net.killstreakcod.universal.TacticalViewPayload(remote ? 42 : -1,
                        remote ? "gunship" : "", 1200, 0, 100, false, 32, 3);
                UiLayout.beginEdit();
                Screen preview = new Screen(Component.empty()) {
                    @Override
                    public void render(GuiGraphics graphics, int x, int y, float partial) {
                        graphics.fill(0, 0, width, height, 0xff263137);
                        for (int line = 0; line < width; line += 32)
                            graphics.vLine(line, 0, height, 0xff2e3b42);
                        graphics.flush();
                        var layers = new net.neoforged.neoforge.client.gui.GuiLayerManager();
                        layers.add(ResourceLocation.fromNamespaceAndPath("foreign", "preview"),
                                (gui, tracker) -> foreignUi(gui, "REGISTERED FOREIGN OVERLAY", height - 32));
                        previewForeignUi = true;
                        try {
                            layers.render(graphics, mc.getTimer());
                        } finally {
                            previewForeignUi = false;
                        }
                    }
                };
                capture(mc, preview,
                        output.resolve("remote-hud-isolation-" + (remote ? "remote-" : "normal-") + suffix + ".png"));
                UiLayout.cancel();
            }
        } finally {
            previewForeignUi = false;
            TacticalClient.state = previous;
            UiLayout.cancel();
        }
    }

    private static void foreignUi(GuiGraphics graphics, String label, int y) {
        graphics.fill(8, y, graphics.guiWidth() - 8, y + 22, 0xffa82e39);
        graphics.drawString(Minecraft.getInstance().font, label, 15, y + 7, 0xffffffff, false);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void foreignPre(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (previewForeignUi)
            foreignUi(event.getGuiGraphics(), "FOREIGN PRE LISTENER", 8);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void foreignPost(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        if (previewForeignUi)
            foreignUi(event.getGuiGraphics(), "FOREIGN POST LISTENER", 36);
    }

    /** 使用实际音频对应的字幕导出 PNG，人工查看普通 HUD、替代 HUD 和遗留裁剪／透明度下的显示。 */
    private static void radioCaptionPreviews(Minecraft mc, Path output, String suffix) throws Exception {
        UiLayout.beginEdit();
        var alias = net.killstreakcod.audio.NativeAudio.voiceEvent("uav", "ready", VoiceFaction.SPECGRU);
        var voice = new net.minecraft.client.resources.sounds.SimpleSoundInstance(alias.getLocation(),
                net.minecraft.sounds.SoundSource.VOICE, 1, 1, net.minecraft.util.RandomSource.create(42), false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        voice.resolve(mc.getSoundManager());
        net.killstreakcod.audio.NativeRadioSubtitles.start("uav", "ready", voice.getSound().getLocation(), 100);
        try {
            for (String mode : List.of("normal", "replaced-hud", "inherited-state")) {
                Screen subtitles = new Screen(Component.empty()) {
                    @Override
                    public void render(GuiGraphics graphics, int x, int y, float partial) {
                        graphics.fill(0, 0, width, height, 0xff20282b);
                        graphics.drawString(mc.font, "Radio captions / " + mode, 12, 12, 0xffe5eee6);
                        graphics.flush();
                        graphics.pose().pushPose();
                        try {
                            if (mode.equals("inherited-state")) {
                                RenderSystem.setShaderColor(1, 1, 1, 0);
                                RenderSystem.enableScissor(0, 0, 1, 1);
                                graphics.pose().translate(width * 2, height * 2, 0);
                            }
                            if (mode.equals("replaced-hud")) {
                                var event = new net.neoforged.neoforge.client.event.RenderGuiEvent.Pre(graphics,
                                        mc.getTimer());
                                event.setCanceled(true);
                                RadioSubtitleOverlay.replacedHud(event);
                            } else
                                RadioSubtitleOverlay.render(graphics);
                        } finally {
                            graphics.pose().popPose();
                            RenderSystem.disableScissor();
                            RenderSystem.setShaderColor(1, 1, 1, 1);
                        }
                    }
                };
                capture(mc, subtitles, output.resolve("radio-caption-" + mode + "-" + suffix + ".png"));
            }
        } finally {
            net.killstreakcod.audio.NativeRadioSubtitles.reload();
        }
    }

    /** 直接绘制源动作中的设备、方块手掌和枪口效果，供人工比对握持接触面。 */
    /** 独立模型人工预览：实际手持投影和世界投影不同，绘出重投影弹迹与模型炮口的连接。 */
    private static Screen muzzleProjectionPreview(Minecraft mc, int fov, boolean left) {
        return new Screen(Component.empty()) {
            @Override
            public void render(GuiGraphics graphics, int x, int y, float partial) {
                graphics.fill(0, 0, width, height, 0xff20282b);
                graphics.flush();
                var frame = TargetingAnimationClips.sample("m13421", "fire", 4, false, null, null, 1);
                var model = ImportedCastModel.get("m13421_vm");
                if (frame == null || model == null)
                    return;
                var bones = new java.util.HashMap<String, Matrix4f>();
                frame.cameraBones.forEach((name, matrix) -> bones.put(name, new Matrix4f(matrix)
                        .m30(matrix.m30() * 2.54F).m31(matrix.m31() * 2.54F).m32(matrix.m32() * 2.54F)));
                var pose = new com.mojang.blaze3d.vertex.PoseStack();
                pose.translate(.02, -.015, 0);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(1.5F));
                if (left)
                    pose.scale(-1, 1, 1);
                float aspect = (float) mc.getWindow().getWidth() / mc.getWindow().getHeight();
                var handProjection = new Matrix4f().perspective((float) Math.toRadians(70), aspect, .05F, 256);
                var worldProjection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, .05F, 256);
                var muzzle = FirstPersonMuzzle.project(pose.last().pose(), new Matrix4f(), handProjection,
                        bones.get("tag_flash"), worldProjection, new Matrix4f(worldProjection).invert(),
                        net.minecraft.world.phys.Vec3.ZERO);
                var view = RenderSystem.getModelViewStack();
                view.pushMatrix().identity();
                RenderSystem.applyModelViewMatrix();
                try {
                    var buffers = mc.renderBuffers().bufferSource();
                    // 背景 GUI 已写入深度，先清深度再画世界弹迹，随后按原版手持顺序再次清深度。
                    RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                    RenderSystem.setProjectionMatrix(worldProjection,
                            com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
                    if (muzzle.isPresent()) {
                        var start = muzzle.get();
                        var end = new net.minecraft.world.phys.Vec3(0, 0, -160);
                        var direction = end.subtract(start).normalize();
                        var vertices = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lines());
                        vertices.addVertex((float) start.x, (float) start.y, (float) start.z)
                                .setColor(255, 225, 120, 255)
                                .setNormal((float) direction.x, (float) direction.y, (float) direction.z);
                        var head = start.add(direction.scale(20));
                        vertices.addVertex((float) head.x, (float) head.y, (float) head.z).setColor(255, 225, 120, 255)
                                .setNormal((float) direction.x, (float) direction.y, (float) direction.z);
                        buffers.endBatch();
                    }
                    RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                    RenderSystem.setProjectionMatrix(handProjection,
                            com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
                    model.renderScene(pose, buffers, 15728880,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, bones);
                    MinigunMuzzleEffects.render(pose, buffers, bones.get("tag_flash"), 24.25F);
                    buffers.endBatch();
                } finally {
                    view.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        };
    }

    private static Screen equipmentPreview(Minecraft mc, String family, String modelId, float angle) {
        return new Screen(Component.empty()) {
            @Override
            public void render(GuiGraphics graphics, int x, int y, float partial) {
                graphics.fill(0, 0, width, height, 0xff20282b);
                graphics.flush();
                var frame = TargetingAnimationClips.sample(family, family.equals("m13421") ? "fire" : "idle", 4, false,
                        null, null, 1);
                var model = ImportedCastModel.get(modelId);
                if (frame == null || model == null)
                    return;
                var bones = new java.util.HashMap<String, Matrix4f>();
                frame.cameraBones.forEach((name, matrix) -> bones.put(name, new Matrix4f(matrix)
                        .m30(matrix.m30() * 2.54F).m31(matrix.m31() * 2.54F).m32(matrix.m32() * 2.54F)));
                var pose = graphics.pose();
                pose.pushPose();
                try {
                    float fit = Math.min(width / 1.8F, height / 1.1F);
                    float centerX = 0, centerY = 0;
                    if (angle != 0) {
                        var rotation = new Matrix4f().rotationY((float) Math.toRadians(angle));
                        var lower = new org.joml.Vector3f(Float.POSITIVE_INFINITY);
                        var upper = new org.joml.Vector3f(Float.NEGATIVE_INFINITY);
                        for (String tag : List.of("tag_flash", "j_wrist_le", "j_wrist_ri")) {
                            var socket = bones.get(tag);
                            if (socket == null)
                                continue;
                            var placement = net.killstreakcod.render.tablet.TabletHandModel.sourceCameraMatrix(socket);
                            var point = placement.getTranslation(new org.joml.Vector3f());
                            rotation.transformPosition(point);
                            lower.min(point);
                            upper.max(point);
                            if (tag.equals("tag_flash")) {
                                point = placement.transformPosition(new org.joml.Vector3f(1, 0, 0));
                                rotation.transformPosition(point);
                                lower.min(point);
                                upper.max(point);
                            }
                        }
                        lower.add(-.2F, -.35F, 0);
                        upper.add(.2F, .15F, 0);
                        centerX = (lower.x + upper.x) / 2;
                        centerY = (lower.y + upper.y) / 2;
                        fit = Math.min(width * .86F / (upper.x - lower.x), height * .86F / (upper.y - lower.y));
                    }
                    pose.translate(width / 2F, height / 2F, 600);
                    pose.scale(fit, -fit, fit);
                    pose.translate(-centerX, -centerY, 0);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
                    var buffers = mc.renderBuffers().bufferSource();
                    new net.killstreakcod.render.tablet.TabletHandModel().renderTrackedHands(pose, buffers,
                            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png"),
                            15728880, bones, false);
                    model.renderScene(pose, buffers, 15728880,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                            family.equals("m13421") ? bones : frame.cameraBones);
                    if (family.equals("m13421"))
                        MinigunMuzzleEffects.render(pose, buffers, bones.get("tag_flash"), 24.25F);
                    buffers.endBatch();
                } finally {
                    pose.popPose();
                }
            }
        };
    }

    private static void fieldStatic(Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static List<WhitelistStatePayload.Member> members(int count, String prefix, boolean online) {
        List<WhitelistStatePayload.Member> result = new ArrayList<>();
        for (int i = 0; i < count; i++)
            result.add(new WhitelistStatePayload.Member(new UUID(0, i + 1), prefix + (i + 1), online && i != 1));
        return result;
    }

    private static Object field(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        if (value != null)
            field.set(owner, value);
        return field.get(owner);
    }

    private static void capture(Minecraft mc, Screen screen, Path path) throws Exception {
        screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        if (screen instanceof HeadquartersTerminalScreen headquarters)
            headquarters.receiveState(TacticalClient.research);
        captureInitialized(mc, screen, path);
    }

    private static void captureInitialized(Minecraft mc, Screen screen, Path path) throws Exception {
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            var target = mc.getMainRenderTarget();
            var view = RenderSystem.getModelViewStack();
            view.pushMatrix();
            try {
                view.identity().translate(0, 0, -11000);
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight(), 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                target.setClearColor(.035F, .055F, .065F, 1);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.disableDepthTest();
                var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                screen.render(graphics, -10, -10, 0);
                graphics.flush();
                try (var image = Screenshot.takeScreenshot(target)) {
                    image.writeToFile(path);
                }
            } finally {
                view.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }
}
