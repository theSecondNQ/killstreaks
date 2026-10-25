package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.tablet.TabletDisplayMaterial;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Cached original indexed meshes. Source poses stay Z-up/cm until skinning is complete.
 * 缓存原资源索引网格；蒙皮计算完成前，源姿态保持 Z 轴向上、厘米单位。
 */
public final class ImportedCastModel {
    private static final Map<String, Optional<ImportedCastModel>> CACHE = new HashMap<>();
    private static final RenderType COCKPIT_GLASS = RenderType.entityTranslucent(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "textures/entity/thermal_white.png"));

    /**
     * Named passes keep first-person visibility and coordinate rules together.
     * 用命名渲染阶段集中表达第一人称可见性及坐标规则，避免调用方组合多个布尔开关。
     */
    private enum RenderPass {
        WORLD, SCENE, ARMS, LEFT_ARM, COCKPIT, DAMAGED_COCKPIT
    }

    /**
     * Each visibility variant owns its face indices, packed quads and unique vertex work set.
     * 每种可见性组合统一保存三角面、打包四边形和不重复的待变换顶点，加载时生成并跨帧复用。
     */
    private record Geometry(int[] faces, int[] quads, int[] vertices) {
        Geometry(int[] faces) {
            this(faces, ImportedQuadIndices.pack(faces), uniqueVertices(faces));
        }

        private static int[] uniqueVertices(int[] indices) {
            BitSet used = new BitSet();
            for (int index : indices)
                used.set(index);
            return used.stream().toArray();
        }
    }

    public record Bone(String name, int parent, Vector3f localPosition, Quaternionf localRotation, Vector3f scale,
            Vector3f worldPosition, Quaternionf worldRotation) {
        public Matrix4f localMatrix() {
            return new Matrix4f().translation(localPosition).rotate(localRotation).scale(scale);
        }

        public Matrix4f worldMatrix() {
            return new Matrix4f().translation(worldPosition).rotate(worldRotation).scale(scale);
        }

    }

    private final List<Bone> bones;
    private final Mesh[] meshes;
    private final float unitScale;
    private final boolean tabletAxis;
    private final Vector3f origin, boundsMin = new Vector3f(Float.POSITIVE_INFINITY),
            boundsMax = new Vector3f(Float.NEGATIVE_INFINITY);
    private final Matrix4f[] bindWorld, inverseBind, skin, lastGlobal;
    private final Matrix3f[] skinNormals;
    private final boolean[] changedBones;
    private long poseRevision;
    private final Vector3f positionScratch = new Vector3f(), normalScratch = new Vector3f();

    private ImportedCastModel(DataInputStream input) throws IOException {
        if (input.readInt() != 0x4d574d33 || input.readInt() != 1)
            throw new IOException("Unsupported imported mesh format");
        int boneCount = count(input, 4096);
        List<Bone> list = new ArrayList<>();
        for (int i = 0; i < boneCount; i++)
            list.add(new Bone(string(input), input.readInt(), vector(input), quaternion(input), vector(input),
                    vector(input), quaternion(input)));
        bones = List.copyOf(list);
        bindWorld = new Matrix4f[boneCount];
        inverseBind = new Matrix4f[boneCount];
        skin = new Matrix4f[boneCount];
        lastGlobal = new Matrix4f[boneCount];
        skinNormals = new Matrix3f[boneCount];
        changedBones = new boolean[boneCount];
        for (int i = 0; i < boneCount; i++) {
            bindWorld[i] = bones.get(i).worldMatrix();
            inverseBind[i] = new Matrix4f(bindWorld[i]).invert();
            skin[i] = new Matrix4f();
            lastGlobal[i] = new Matrix4f();
            skinNormals[i] = new Matrix3f();
        }
        unitScale = input.readFloat();
        tabletAxis = input.readUnsignedByte() != 0;
        origin = vector(input);
        meshes = new Mesh[count(input, 512)];
        for (int i = 0; i < meshes.length; i++)
            meshes[i] = new Mesh(input);
    }

    public static ImportedCastModel get(String id) {
        return CACHE.computeIfAbsent(id, key -> {
            var location = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                    "mw/models/" + key + ".mesh.gz");
            var resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty())
                return Optional.empty();
            try (var source = resource.get().open(); var stream = ImportedMeshInput.open(source)) {
                return Optional.of(new ImportedCastModel(stream));
            }
            catch (IOException | RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().error("Failed to load original mesh {}", key, error);
                return Optional.empty();
            }
        }).orElse(null);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public List<Bone> bindBones() {
        return bones;
    }

    public record SurfaceVertex(float x, float y, float z, float u, float v) {
    }

    /**
     * Read the same deformed and camera-transformed vertices just submitted for the LCD.
     * 读取刚提交给液晶屏绘制的同一批顶点，它们已完成形变及摄像机变换。
     */
    public List<SurfaceVertex> surface(String material) {
        for (Mesh mesh : meshes)
            if (mesh.materialName.equals(material) && mesh.count <= 16) {
                List<SurfaceVertex> result = new ArrayList<>();
                for (int i : mesh.full.faces) {
                    int p = i * 6, t = i * 8;
                    result.add(new SurfaceVertex(mesh.posed[p], mesh.posed[p + 1], mesh.posed[p + 2],
                            mesh.source[t + 6], mesh.source[t + 7]));
                }
                return result;
            }
        return List.of();
    }

    public Vector3f boundsMin() {
        return new Vector3f(boundsMin);
    }

    public Vector3f boundsMax() {
        return new Vector3f(boundsMax);
    }

    public Vector3f sourceOrigin() {
        return new Vector3f(origin);
    }

    public float sourceUnitScale() {
        return unitScale;
    }

    public void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> sourceGlobalBones) {
        renderInternal(pose, buffers, light, overlay, sourceGlobalBones, RenderPass.WORLD, Map.of(), null);
    }

    /**
     * Replace only named material images, retaining each source surface's transparency and lighting.
     * 只替换指定材质图像，保留各源表面的透明度和光照方式。
     */
    public void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> sourceGlobalBones, Map<String, ResourceLocation> materialTextures) {
        renderInternal(pose, buffers, light, overlay, sourceGlobalBones, RenderPass.WORLD, materialTextures, null);
    }

    public void renderScene(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> sourceGlobalBones) {
        renderInternal(pose, buffers, light, overlay, sourceGlobalBones, RenderPass.SCENE, Map.of(), null);
    }

    public void renderScene(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> sourceGlobalBones, Map<String, ResourceLocation> materialTextures) {
        renderInternal(pose, buffers, light, overlay, sourceGlobalBones, RenderPass.SCENE, materialTextures, null);
    }

    public void renderCockpit(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> sourceGlobalBones, Map<String, ResourceLocation> materialTextures,
            GunshipCabinLighting.Profile lamps) {
        renderInternal(pose, buffers, light, overlay, sourceGlobalBones, RenderPass.COCKPIT, materialTextures, lamps);
    }

    public void renderDamagedCockpit(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> sourceGlobalBones, Map<String, ResourceLocation> materialTextures,
            GunshipCabinLighting.Profile lamps) {
        renderInternal(pose, buffers, light, overlay, sourceGlobalBones, RenderPass.DAMAGED_COCKPIT, materialTextures,
                lamps);
    }

    public void renderFirstPersonArms(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> bones, boolean leftOnly) {
        renderInternal(pose, buffers, light, overlay, bones, leftOnly ? RenderPass.LEFT_ARM : RenderPass.ARMS, Map.of(),
                null);
    }

    public void renderCabin(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> sourceGlobalBones, GunshipCabinLighting.Profile lamps) {
        renderInternal(pose, buffers, light, overlay, sourceGlobalBones, RenderPass.SCENE, Map.of(), lamps);
    }

    private void renderInternal(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> globals, RenderPass pass, Map<String, ResourceLocation> materialTextures,
            GunshipCabinLighting.Profile lamps) {
        renderInternal(pose, buffers, light, overlay, globals, pass, materialTextures, lamps, -1);
    }

    /** 复用指定骨骼的原网格和材质，仅提交属于该骨骼的三角面。 */
    public void renderBone(String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> globals) {
        for (int bone = 0; bone < bones.size(); bone++)
            if (bones.get(bone).name().equals(name)) {
                renderInternal(pose, buffers, light, overlay, globals, RenderPass.SCENE, Map.of(), null, bone);
                return;
            }
    }

    private void renderInternal(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            Map<String, Matrix4f> globals, RenderPass pass, Map<String, ResourceLocation> materialTextures,
            GunshipCabinLighting.Profile lamps, int partBone) {
        boolean scene = pass != RenderPass.WORLD;
        boolean cockpit = pass == RenderPass.COCKPIT || pass == RenderPass.DAMAGED_COCKPIT;
        boolean damaged = pass == RenderPass.DAMAGED_COCKPIT;
        boolean animated = globals != null && !globals.isEmpty();
        if (animated)
            for (int i = 0; i < bones.size(); i++) {
                Matrix4f world = globals.get(bones.get(i).name());
                boolean changed = world != null && !world.equals(bindWorld[i]);
                if (changed) {
                    if (!changedBones[i] || !world.equals(lastGlobal[i])) {
                        poseRevision++;
                        lastGlobal[i].set(world);
                        skin[i].set(world).mul(inverseBind[i]);
                        skinNormals[i].set(skin[i]);
                        if (Math.abs(skinNormals[i].determinant()) < 1e-10F)
                            skinNormals[i].identity();
                        else
                            skinNormals[i].invert().transpose();
                    }
                } else if (changedBones[i]) {
                    skin[i].identity();
                    skinNormals[i].identity();
                    poseRevision++;
                }
                changedBones[i] = changed;
            }
        PoseStack.Pose drawPose = pose.last();
        boolean liveTablet = materialTextures != null && materialTextures.containsKey("m/offhand_vm_tablet_image_ui");
        for (Mesh mesh : meshes) {

            // 该源四边形是位于液晶屏前方 0.965 毫米的反射着色器覆盖层，使用不透明漫反射回退材质会遮住实时屏幕。
            if (liveTablet && mesh.materialName.equals("m/offhand_2h_tablet_screen_v0"))
                continue;
            if (cockpit && (mesh.hiddenInCockpit || mesh.damagedGlass && !damaged
                    || damaged && mesh.glass && !mesh.damagedGlass))
                continue;
            Geometry geometry = partBone < 0 ? mesh.geometry(pass) : mesh.boneGeometry(partBone);
            if (geometry.quads.length == 0)
                continue;
            boolean deform = animated && mesh.hasChangedInfluence(changedBones);
            float[] data = scene ? mesh.sceneVertices : mesh.vertices;
            if (deform) {
                data = mesh.deform(skin, skinNormals, scene, poseRevision);
            }
            mesh.transform(drawPose, data, geometry.vertices, animated && !deform);
            ResourceLocation replacement = materialTextures == null ? null : materialTextures.get(mesh.materialName);
            boolean window = cockpit && mesh.glass;

            // 导出的窗户漫反射贴图已烘焙不透明反射；第一人称改用轻淡中性的玻璃显示方式，保留原几何并让实时世界可见。
            boolean tabletImage = liveTablet && mesh.materialName.equals("m/offhand_vm_tablet_image_ui")
                    && replacement != null;
            if (tabletImage) {
                VertexConsumer lcd = buffers.getBuffer(TabletDisplayMaterial.type(replacement));

                // 使用文字着色器的顶点格式提交同一个蒙皮表面，保持深度检测，使原资源笔、手臂和外壳仍可遮挡显示屏。
                for (int index : geometry.quads)
                    mesh.lcdVertex(lcd, index);
                continue;
            }
            RenderType type = window ? COCKPIT_GLASS
                    : replacement == null ? mesh.type
                            : mesh.translucent ? RenderType.entityTranslucent(replacement)
                                    : RenderType.entityCutoutNoCull(replacement);
            VertexConsumer consumer = buffers.getBuffer(type);
            int packedLight = mesh.emissive ? 15728880 : light;
            int[] colors = window ? mesh.glassColors : mesh.colors;
            boolean fitPanel = cockpit && replacement != null && mesh.panelUvs != null;
            byte[] cabinLights = lamps == null || mesh.emissive ? null : mesh.cabinLights(lamps);
            for (int index : geometry.quads)
                mesh.vertex(consumer, index, localLight(packedLight, cabinLights, index, lamps), overlay, colors,
                        fitPanel);
        }
    }

    private static int localLight(int ambient, byte[] cabinLights, int index, GunshipCabinLighting.Profile lamps) {
        return cabinLights == null ? ambient : lamps.light(ambient, cabinLights[index]);
    }

    private void convert(float x, float y, float z, float nx, float ny, float nz, boolean scene, float[] out,
            int offset) {
        if (!scene) {
            x -= origin.x;
            y -= origin.y;
            z -= origin.z;
        }
        out[offset] = (tabletAxis && !scene ? y : -y) * unitScale;

        // 原屏幕在源资源正 Y 方向的 U 坐标为零；该旋转使物品 JSON 中朝向摄像机的半周旋转后，文字仍能正常阅读。
        if (tabletAxis && !scene) {
            out[offset + 1] = x * unitScale;
            out[offset + 2] = -z * unitScale;
            out[offset + 3] = ny;
            out[offset + 4] = nx;
            out[offset + 5] = -nz;
        } else {
            out[offset + 1] = z * unitScale;
            out[offset + 2] = -x * unitScale;
            out[offset + 3] = -ny;
            out[offset + 4] = nz;
            out[offset + 5] = -nx;
        }
    }

    private final class Mesh {
        final int count, influences;
        final String materialName;
        final float[] source, vertices, sceneVertices, posed, weights, panelUvs;
        final int[] joints, colors, glassColors, influencedBones;
        final Geometry full, fingerless, leftFingerless;
        private Map<Integer, Geometry> boneParts;
        private float[] deformed;
        private long deformedRevision = Long.MIN_VALUE;
        private boolean deformedScene;
        final RenderType type;
        final boolean emissive, translucent, glass, damagedGlass, hiddenInCockpit;
        private GunshipCabinLighting.Profile litProfile;
        private byte[] cachedCabinLights;

        byte[] cabinLights(GunshipCabinLighting.Profile profile) {
            if (cachedCabinLights == null || !profile.equals(litProfile)) {
                cachedCabinLights = new byte[count];
                for (int i = 0; i < count; i++) {
                    int at = i * 6;
                    cachedCabinLights[i] = (byte) profile.level(sceneVertices[at], sceneVertices[at + 1],
                            sceneVertices[at + 2]);
                }
                litProfile = profile;
            }
            return cachedCabinLights;
        }

        Mesh(DataInputStream input) throws IOException {
            materialName = string(input).toLowerCase(Locale.ROOT);
            glass = materialName.contains("glass");
            damagedGlass = materialName.contains("glass_dmg");
            hiddenInCockpit = materialName.contains("glass_inside") || materialName.contains("blurred_rotors");
            ResourceLocation texture = ResourceLocation.parse(string(input));
            translucent = input.readUnsignedByte() != 0;
            emissive = input.readUnsignedByte() != 0;
            type = translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
            count = count(input, 2_000_000);
            influences = count(input, 32);
            int faceCount = count(input, 6_000_000);
            source = new float[count * 8];
            vertices = new float[count * 6];
            sceneVertices = new float[count * 6];
            posed = new float[count * 6];
            colors = new int[count];
            joints = new int[count * influences];
            weights = new float[joints.length];
            boolean[] leftArm = new boolean[count], digits = new boolean[count];
            BitSet weighted = new BitSet(bones.size());
            for (int i = 0; i < count; i++) {
                int raw = i * 8, offset = i * 6;
                for (int j = 0; j < 8; j++)
                    source[raw + j] = input.readFloat();
                colors[i] = input.readInt();
                float total = 0;
                for (int j = 0; j < influences; j++) {
                    int n = i * influences + j;
                    joints[n] = input.readInt();
                    weights[n] = input.readFloat();
                    if ((joints[n] < 0 || joints[n] >= bones.size()) && weights[n] > 0)
                        throw new IOException("Mesh joint outside skeleton");
                    total += weights[n];
                    if (weights[n] > .05F && joints[n] >= 0 && joints[n] < bones.size()) {
                        String name = bones.get(joints[n]).name();
                        if (name.contains("_le"))
                            leftArm[i] = true;
                        if (fingerBone(name))
                            digits[i] = true;
                    }
                }
                if (total > 0)
                    for (int j = 0; j < influences; j++) {
                        int n = i * influences + j;
                        weights[n] /= total;
                        if (weights[n] > 0)
                            weighted.set(joints[n]);
                    }
                convert(source[raw], source[raw + 1], source[raw + 2], source[raw + 3], source[raw + 4],
                        source[raw + 5], false, vertices, offset);
                convert(source[raw], source[raw + 1], source[raw + 2], source[raw + 3], source[raw + 4],
                        source[raw + 5], true, sceneVertices, offset);
                boundsMin.set(Math.min(boundsMin.x, vertices[offset]), Math.min(boundsMin.y, vertices[offset + 1]),
                        Math.min(boundsMin.z, vertices[offset + 2]));
                boundsMax.set(Math.max(boundsMax.x, vertices[offset]), Math.max(boundsMax.y, vertices[offset + 1]),
                        Math.max(boundsMax.z, vertices[offset + 2]));
            }

            // 导出的屏幕面板是独立图像，并非原始渲染目标图集；只在替换指定的攻击直升机座舱屏幕材质时适配完整画布。
            if (materialName.contains("ahotel64_hud_")) {
                float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY,
                        maxV = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < count; i++) {
                    minU = Math.min(minU, source[i * 8 + 6]);
                    maxU = Math.max(maxU, source[i * 8 + 6]);
                    minV = Math.min(minV, source[i * 8 + 7]);
                    maxV = Math.max(maxV, source[i * 8 + 7]);
                }
                panelUvs = new float[count * 2];
                for (int i = 0; i < count; i++) {
                    panelUvs[i * 2] = (source[i * 8 + 6] - minU) / Math.max(.00001F, maxU - minU);
                    panelUvs[i * 2 + 1] = (source[i * 8 + 7] - minV) / Math.max(.00001F, maxV - minV);
                }
            } else
                panelUvs = null;
            int[] faces = new int[faceCount];
            for (int i = 0; i < faceCount; i++) {
                faces[i] = input.readInt();
                if (faces[i] < 0 || faces[i] >= count)
                    throw new IOException("Mesh face outside vertex buffer");
            }
            int[] visible = new int[faceCount], leftVisible = new int[faceCount];
            int all = 0, one = 0;
            for (int i = 0; i + 2 < faceCount; i += 3) {
                int a = faces[i], b = faces[i + 1], c = faces[i + 2];
                if (digits[a] || digits[b] || digits[c])
                    continue;
                visible[all++] = a;
                visible[all++] = b;
                visible[all++] = c;
                if (leftArm[a] || leftArm[b] || leftArm[c]) {
                    leftVisible[one++] = a;
                    leftVisible[one++] = b;
                    leftVisible[one++] = c;
                }
            }
            int[] fingerlessFaces = all == faceCount ? faces : Arrays.copyOf(visible, all);
            full = new Geometry(faces);
            fingerless = fingerlessFaces == faces ? full : new Geometry(fingerlessFaces);
            leftFingerless = one == all ? fingerless : new Geometry(Arrays.copyOf(leftVisible, one));
            influencedBones = weighted.stream().toArray();
            if (glass) {
                glassColors = new int[count];
                Arrays.fill(glassColors, 0x16d4e8ef);
            } else
                glassColors = colors;
        }

        Geometry geometry(RenderPass pass) {
            return switch (pass) {
                case ARMS -> fingerless;
                case LEFT_ARM -> leftFingerless;
                default -> full;
            };
        }

        Geometry boneGeometry(int bone) {
            if (boneParts == null)
                boneParts = new HashMap<>();
            return boneParts.computeIfAbsent(bone, selected -> {
                int[] faces = new int[full.faces.length];
                int size = 0;
                for (int i = 0; i < full.faces.length; i += 3)
                    if (belongsToBone(full.faces[i], selected) && belongsToBone(full.faces[i + 1], selected)
                            && belongsToBone(full.faces[i + 2], selected)) {
                        faces[size++] = full.faces[i];
                        faces[size++] = full.faces[i + 1];
                        faces[size++] = full.faces[i + 2];
                    }
                return new Geometry(Arrays.copyOf(faces, size));
            });
        }

        private boolean belongsToBone(int vertex, int bone) {
            float weight = 0;
            for (int i = vertex * influences; i < (vertex + 1) * influences; i++)
                if (joints[i] == bone)
                    weight += weights[i];
            return weight >= .5F;
        }

        boolean hasChangedInfluence(boolean[] changed) {
            for (int bone : influencedBones)
                if (changed[bone])
                    return true;
            return false;
        }

        float[] deform(Matrix4f[] matrices, Matrix3f[] normals, boolean scene, long revision) {
            if (deformed == null)
                deformed = new float[count * 6];
            if (deformedRevision == revision && deformedScene == scene)
                return deformed;
            for (int i : full.vertices) {
                int raw = i * 8;
                float x = source[raw], y = source[raw + 1], z = source[raw + 2], nx = source[raw + 3],
                        ny = source[raw + 4], nz = source[raw + 5];
                float px = 0, py = 0, pz = 0, nnx = 0, nny = 0, nnz = 0, total = 0;
                for (int j = 0; j < influences; j++) {
                    int offset = i * influences + j;
                    float w = weights[offset];
                    if (w <= 0)
                        continue;
                    Matrix4f m = matrices[joints[offset]];
                    Matrix3f n = normals[joints[offset]];
                    px += (m.m00() * x + m.m10() * y + m.m20() * z + m.m30()) * w;
                    py += (m.m01() * x + m.m11() * y + m.m21() * z + m.m31()) * w;
                    pz += (m.m02() * x + m.m12() * y + m.m22() * z + m.m32()) * w;
                    nnx += (n.m00() * nx + n.m10() * ny + n.m20() * nz) * w;
                    nny += (n.m01() * nx + n.m11() * ny + n.m21() * nz) * w;
                    nnz += (n.m02() * nx + n.m12() * ny + n.m22() * nz) * w;
                    total += w;
                }
                if (total <= 0) {
                    px = x;
                    py = y;
                    pz = z;
                    nnx = nx;
                    nny = ny;
                    nnz = nz;
                }
                float length = (float) Math.sqrt(nnx * nnx + nny * nny + nnz * nnz);
                if (length > 0) {
                    nnx /= length;
                    nny /= length;
                    nnz /= length;
                }
                convert(px, py, pz, nnx, nny, nnz, scene, deformed, i * 6);
            }
            deformedRevision = revision;
            deformedScene = scene;
            return deformed;
        }

        /**
         * Reuse the existing skin workspace; indexed faces never transform shared vertices twice.
         * 复用现有蒙皮计算缓冲，避免索引面重复变换共享顶点。
         */
        void transform(PoseStack.Pose pose, float[] data, int[] used, boolean normalizeBindNormals) {
            for (int index : used) {
                int p = index * 6;
                pose.pose().transformPosition(data[p], data[p + 1], data[p + 2], positionScratch);
                float nx = data[p + 3], ny = data[p + 4], nz = data[p + 5];
                if (normalizeBindNormals) {
                    float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (length > 0) {
                        nx /= length;
                        ny /= length;
                        nz /= length;
                    }
                }
                pose.transformNormal(nx, ny, nz, normalScratch);
                posed[p] = positionScratch.x;
                posed[p + 1] = positionScratch.y;
                posed[p + 2] = positionScratch.z;
                posed[p + 3] = normalScratch.x;
                posed[p + 4] = normalScratch.y;
                posed[p + 5] = normalScratch.z;
            }
        }

        void vertex(VertexConsumer out, int index, int light, int overlay, int[] drawColors, boolean fitPanel) {
            int p = index * 6, t = index * 8;
            out.addVertex(posed[p], posed[p + 1], posed[p + 2], drawColors[index],
                    fitPanel ? panelUvs[index * 2] : source[t + 6], fitPanel ? panelUvs[index * 2 + 1] : source[t + 7],
                    overlay, light, posed[p + 3], posed[p + 4], posed[p + 5]);
        }

        void lcdVertex(VertexConsumer out, int index) {
            int p = index * 6, t = index * 8;
            out.addVertex(posed[p], posed[p + 1], posed[p + 2]).setColor(colors[index])
                    .setUv(source[t + 6], source[t + 7]).setLight(15728880);
        }
    }

    private static boolean fingerBone(String name) {
        return name.startsWith("j_index_") || name.startsWith("j_mid_") || name.startsWith("j_ring_")
                || name.startsWith("j_pinky_") || name.startsWith("j_thumb_") || name.startsWith("j_metaindex_")
                || name.startsWith("j_metamid_") || name.startsWith("j_metaring_") || name.startsWith("j_metapinky_");
    }

    private static int count(DataInputStream input, int max) throws IOException {
        int n = input.readInt();
        if (n < 0 || n > max)
            throw new IOException("Invalid imported array length " + n);
        return n;
    }

    private static String string(DataInputStream input) throws IOException {
        return new String(input.readNBytes(input.readUnsignedShort()), StandardCharsets.UTF_8);
    }

    private static Vector3f vector(DataInputStream input) throws IOException {
        return new Vector3f(input.readFloat(), input.readFloat(), input.readFloat());
    }

    private static Quaternionf quaternion(DataInputStream input) throws IOException {
        return new Quaternionf(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat());
    }
}
