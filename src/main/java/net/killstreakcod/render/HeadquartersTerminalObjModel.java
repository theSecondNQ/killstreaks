package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Bakes the original terminal OBJ into ordinary block quads without rewriting its assets.
 * 将原始终端 OBJ 烘焙为普通方块面，不改写原模型或贴图文件。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HeadquartersTerminalObjModel extends SimpleUnbakedGeometry<HeadquartersTerminalObjModel> {
    private final ModelData model;
    private final String section;

    private HeadquartersTerminalObjModel(ModelData model, String section) {
        this.model = model;
        this.section = section;
    }

    @SubscribeEvent
    public static void registerLoader(ModelEvent.RegisterGeometryLoaders event) {
        IGeometryLoader<HeadquartersTerminalObjModel> loader = (json, context) -> read(json);
        event.register(ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "terminal_obj"), loader);
    }

    private static HeadquartersTerminalObjModel read(JsonObject json) {
        String section = GsonHelper.getAsString(json, "part", "full");
        if (!List.of("full", "lower", "upper").contains(section))
            throw new JsonParseException("Unknown terminal model part: " + section);
        ResourceLocation location = ResourceLocation.parse(GsonHelper.getAsString(json, "model"));
        try {
            return new HeadquartersTerminalObjModel(load(Minecraft.getInstance().getResourceManager(), location),
                    section);
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new JsonParseException("Cannot read terminal OBJ: " + location, exception);
        }
    }

    private static ModelData load(ResourceManager resources, ResourceLocation location) throws IOException {
        List<Vector3f> positions = new ArrayList<>();
        List<Vector2f> coordinates = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        Map<String, Material> materials = new LinkedHashMap<>();
        String material = "";
        try (var reader = resources.openAsReader(location)) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] values = line.strip().split("\\s+");
                switch (values[0]) {
                    case "v" -> positions.add(vector(values));
                    case "vt" ->
                        coordinates.add(new Vector2f(Float.parseFloat(values[1]), Float.parseFloat(values[2])));
                    case "vn" -> normals.add(vector(values));
                    case "mtllib" -> materials.putAll(materials(resources, relative(location, values[1])));
                    case "usemtl" -> material = values[1];
                    case "o" -> {
                        // Lists preserve the three distinct objects named "cube" in the original export.
                        // 使用列表保留原导出中三个同名 cube，避免按名称存储时覆盖底座。
                        if (!faces.isEmpty()) {
                            parts.add(new Part(List.copyOf(faces)));
                            faces.clear();
                        }
                    }
                    case "f" -> {
                        if (values.length < 4 || values.length > 5)
                            throw new IOException("Terminal faces must have three or four corners.");
                        Material texture = materials.get(material);
                        if (texture == null)
                            throw new IOException("Missing terminal material: " + material);
                        List<Corner> corners = new ArrayList<>();
                        for (int i = 1; i < values.length; i++) {
                            String[] indices = values[i].split("/", -1);
                            if (indices.length != 3 || indices[1].isEmpty() || indices[2].isEmpty())
                                throw new IOException("Terminal faces need original UV coordinates and normals.");
                            corners.add(new Corner(positions.get(index(indices[0], positions.size())),
                                    coordinates.get(index(indices[1], coordinates.size())),
                                    normals.get(index(indices[2], normals.size()))));
                        }
                        faces.add(new Face(texture, List.copyOf(corners)));
                    }
                    default -> {
                        // Object labels, comments and smoothing flags do not replace per-corner normals.
                        // 注释、分组及平滑标记不改变文件中逐顶点提供的原始法线。
                    }
                }
            }
        }
        if (!faces.isEmpty())
            parts.add(new Part(List.copyOf(faces)));
        if (parts.isEmpty())
            throw new IOException("The terminal OBJ has no faces.");
        return new ModelData(List.copyOf(parts));
    }

    private static Map<String, Material> materials(ResourceManager resources, ResourceLocation location)
            throws IOException {
        Map<String, Material> result = new LinkedHashMap<>();
        String name = "";
        try (var reader = resources.openAsReader(location)) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] values = line.strip().split("\\s+", 2);
                if (values[0].equals("newmtl")) {
                    name = values[1];
                } else if (values[0].equals("map_Kd")) {
                    String filename = values[1];
                    if (!filename.endsWith(".png") || filename.contains("/") || filename.contains("\\"))
                        throw new IOException("Unsupported terminal texture filename: " + filename);
                    String path = "block/headquarters_terminal/" + filename.substring(0, filename.length() - 4);
                    ResourceLocation texture = location.withPath(path);
                    if (resources.getResource(texture.withPath("textures/" + path + ".png")).isEmpty())
                        throw new IOException("Missing original terminal texture: " + filename);
                    result.put(name, new Material(TextureAtlas.LOCATION_BLOCKS, texture));
                }
            }
        }
        return result;
    }

    private static ResourceLocation relative(ResourceLocation model, String filename) {
        String directory = model.getPath().substring(0, model.getPath().lastIndexOf('/') + 1);
        return model.withPath(directory + filename);
    }

    private static Vector3f vector(String[] values) {
        return new Vector3f(Float.parseFloat(values[1]), Float.parseFloat(values[2]), Float.parseFloat(values[3]));
    }

    private static int index(String value, int size) throws IOException {
        int index = Integer.parseInt(value);
        index = index > 0 ? index - 1 : size + index;
        if (index < 0 || index >= size)
            throw new IOException("Terminal OBJ index outside the source array: " + value);
        return index;
    }

    @Override
    protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
            Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        Transformation transform = UnbakedGeometryHelper
                .composeRootTransformIntoModelState(state, context.getRootTransform()).getRotation()
                .blockCenterToCorner();
        float verticalOffset = section.equals("upper") ? -1 : 0;
        for (Part part : model.parts) {
            if (!selected(part))
                continue;
            for (Face face : part.faces) {
                TextureAtlasSprite texture = sprites.apply(face.material);
                QuadBakingVertexConsumer quad = new QuadBakingVertexConsumer();
                quad.setSprite(texture);
                quad.setTintIndex(-1);
                quad.setShade(true);
                for (int i = 0; i < 4; i++) {
                    Corner corner = face.corners.get(Math.min(i, face.corners.size() - 1));
                    // OBJ uses a centered X/Z origin; upper block quads use their own local Y origin.
                    // OBJ 的 X/Z 原点位于中心；上半方块的 Y 使用其所在格原点，尺寸和朝向不变。
                    Vector4f position = new Vector4f(corner.position.x + .5f, corner.position.y + verticalOffset,
                            corner.position.z + .5f, 1);
                    Vector3f normal = new Vector3f(corner.normal);
                    transform.transformPosition(position);
                    transform.transformNormal(normal);
                    quad.addVertex(position.x, position.y, position.z);
                    quad.setColor(255, 255, 255, 255);
                    // Wavefront V starts at the bottom, while Minecraft's texture atlas starts at the top.
                    // Wavefront 的 V 从底部起算，Minecraft 图集则从顶部起算；不翻转或重编码 PNG。
                    quad.setUv(texture.getU(corner.uv.x), texture.getV(1 - corner.uv.y));
                    quad.setLight(0);
                    quad.setNormal(normal.x, normal.y, normal.z);
                    if (i == 0)
                        quad.setDirection(Direction.getNearest(normal.x, normal.y, normal.z));
                }
                builder.addUnculledFace(quad.bakeQuad());
            }
        }
    }

    private boolean selected(Part part) {
        if (section.equals("full"))
            return true;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (Face face : part.faces) {
            for (Corner corner : face.corners) {
                min = Math.min(min, corner.position.y);
                max = Math.max(max, corner.position.y);
            }
        }
        if (min < 1 && max > 1)
            throw new JsonParseException("A terminal object crosses the block boundary; original faces cannot be cut.");
        return section.equals("upper") == (min >= 1);
    }

    private record Corner(Vector3f position, Vector2f uv, Vector3f normal) {
    }

    private record Face(Material material, List<Corner> corners) {
    }

    private record Part(List<Face> faces) {
    }

    private record ModelData(List<Part> parts) {
    }
}
