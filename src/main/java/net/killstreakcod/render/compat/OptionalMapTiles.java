package net.killstreakcod.render.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryUtil;

/** 读取地图模组已渲染的当前世界瓦片；不打开其界面、不改写地图或触发跨维度扫描。 */
public final class OptionalMapTiles {
    private record Tile(int x, int z, int width, int blocks, int[] pixels) {
        int color(int wx, int wz) {
            if (wx < x || wz < z || wx >= x + blocks || wz >= z + blocks)
                return 0;
            return pixels[(wz - z) * width / blocks * width + (wx - x) * width / blocks];
        }
    }

    public static final class Snapshot {
        private final String provider;
        private final int blocks, x, z, width, height;
        private final Tile[] tiles;

        private Snapshot(String provider, List<Tile> data) {
            this.provider = provider;
            blocks = data.isEmpty() ? 64 : data.get(0).blocks;
            x = data.stream().mapToInt(tile -> Math.floorDiv(tile.x, blocks)).min().orElse(0);
            z = data.stream().mapToInt(tile -> Math.floorDiv(tile.z, blocks)).min().orElse(0);
            width = data.stream().mapToInt(tile -> Math.floorDiv(tile.x, blocks)).max().orElse(x) - x + 1;
            height = data.stream().mapToInt(tile -> Math.floorDiv(tile.z, blocks)).max().orElse(z) - z + 1;
            tiles = new Tile[width * height];
            for (Tile tile : data)
                tiles[(Math.floorDiv(tile.z, blocks) - z) * width + Math.floorDiv(tile.x, blocks) - x] = tile;
        }

        public String provider() {
            return provider;
        }

        public int color(int x, int z) {
            int tx = Math.floorDiv(x, blocks) - this.x, tz = Math.floorDiv(z, blocks) - this.z;
            if (tx < 0 || tz < 0 || tx >= width || tz >= height)
                return 0;
            Tile tile = tiles[tz * width + tx];
            return tile == null ? 0 : tile.color(x, z);
        }
    }

    private record Window(int x, int z, int range) {
    }

    private record Cached(long time, Snapshot snapshot) {
    }

    private static final Snapshot EMPTY = new Snapshot("", List.of());
    private static final Map<Window, Cached> WINDOWS = new LinkedHashMap<>();
    private static final Map<String, Boolean> PRESENT = new HashMap<>();
    private static final Map<String, Boolean> WARNED = new HashMap<>();
    private static ClientLevel world;

    private record GpuTile(int id, int version, Tile tile) {
    }

    private static final Map<Long, GpuTile> GPU_TILES = new LinkedHashMap<>();
    private static int downloads;
    private static long downloadTick = Long.MIN_VALUE;

    private OptionalMapTiles() {}

    public static void clear() {
        WINDOWS.clear();
        GPU_TILES.clear();
        world = null;
        downloadTick = Long.MIN_VALUE;
    }

    public static Snapshot snapshot(int x, int z, int radius) {
        var mc = Minecraft.getInstance();
        if (world != mc.level) {
            world = mc.level;
            WINDOWS.clear();
            GPU_TILES.clear();
        }
        if (world == null)
            return EMPTY;
        Window window = new Window(Math.floorDiv(x, 64) * 64, Math.floorDiv(z, 64) * 64,
                Math.max(64, Math.min(512, (radius + 127) / 64 * 64)));
        long now = net.minecraft.Util.getMillis();
        Cached cached = WINDOWS.get(window);
        if (cached != null && now - cached.time < 750)
            return cached.snapshot;
        if (downloadTick != world.getGameTime()) {
            downloadTick = world.getGameTime();
            downloads = 4;
        }
        Snapshot result = capture(window);
        if (WINDOWS.size() >= 4 && !WINDOWS.containsKey(window))
            WINDOWS.remove(WINDOWS.keySet().iterator().next());
        WINDOWS.put(window, new Cached(now, result));
        return result;
    }

    private static boolean present(String name) {
        return PRESENT.computeIfAbsent(name, key -> {
            try {
                Class.forName(key, false, OptionalMapTiles.class.getClassLoader());
                return true;
            }
            catch (ClassNotFoundException | LinkageError absent) {
                return false;
            }
        });
    }

    private static Snapshot capture(Window window) {
        for (String provider : List.of("JourneyMap", "Xaero", "Xaero Minimap", "VoxelMap")) {
            String type = provider.equals("JourneyMap") ? "journeymap.client.model.region.RegionImageCache"
                    : provider.equals("Xaero") ? "xaero.map.WorldMapSession"
                            : provider.equals("Xaero Minimap") ? "xaero.common.XaeroMinimapSession"
                                    : "com.mamiyaotaru.voxelmap.VoxelConstants";
            if (!present(type))
                continue;
            try {
                List<Tile> tiles = provider.equals("JourneyMap") ? journey(window)
                        : provider.equals("Xaero") ? xaero(window)
                                : provider.equals("Xaero Minimap") ? xaeroMinimap(window) : voxel(window);
                if (!tiles.isEmpty())
                    return new Snapshot(provider, List.copyOf(tiles));
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                if (WARNED.putIfAbsent(provider, true) == null)
                    com.mojang.logging.LogUtils.getLogger().warn(
                            "Killstreaks: {} map tiles unavailable; using terrain fallback", provider, unavailable);
            }
        }
        return EMPTY;
    }

    private static List<Tile> journey(Window window) throws ReflectiveOperationException {
        Object cache = Class.forName("journeymap.client.model.region.RegionImageCache").getField("INSTANCE").get(null);
        Object mapType = Class.forName("journeymap.client.model.map.MapType")
                .getMethod("day", net.minecraft.resources.ResourceKey.class).invoke(null, world.dimension());
        List<Tile> result = new ArrayList<>();
        for (Object set : new ArrayList<>((Collection<?>) call(cache, "getRegionImageSets"))) {
            Object coord = call(set, "getRegionCoord");
            if (!world.dimension().equals(field(coord, "dimension")))
                continue;
            int x = number(call(coord, "getMinChunkX")) * 16, z = number(call(coord, "getMinChunkZ")) * 16;
            if (!intersects(window, x, z, 512))
                continue;
            Object holder = set.getClass().getMethod("getExistingHolder", mapType.getClass()).invoke(set, mapType);
            if (holder != null && call(holder, "getImage") instanceof NativeImage image)
                result.add(copy(image, x, z, 512));
        }
        return result;
    }

    private static List<Tile> voxel(Window window) throws ReflectiveOperationException {
        Object instance = Class.forName("com.mamiyaotaru.voxelmap.VoxelConstants").getMethod("getVoxelMapInstance")
                .invoke(null);
        if (instance == null)
            return List.of();
        Object map = call(instance, "getPersistentMap");
        if (field(map, "world") != world)
            return List.of();
        List<Tile> result = new ArrayList<>();
        for (Object region : new ArrayList<>(((Map<?, ?>) field(map, "cachedRegions")).values())) {
            int blocks = number(call(region, "getWidth"));
            int x = number(call(region, "getX")) * blocks, z = number(call(region, "getZ")) * blocks;
            if (!intersects(window, x, z, blocks))
                continue;
            Object texture = field(region, "image");
            if (texture != null && call(texture, "getData") instanceof NativeImage image)
                result.add(copy(image, x, z, blocks));
        }
        return result;
    }

    private static List<Tile> xaero(Window window) throws ReflectiveOperationException {
        Object session = Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
        if (session == null)
            return List.of();
        Object processor = call(session, "getMapProcessor");
        if (call(processor, "getWorld") != world)
            return List.of();
        Method chunk = processor.getClass().getMethod("getMapChunk", int.class, int.class, int.class);
        List<Tile> result = new ArrayList<>();
        List<int[]> coordinates = new ArrayList<>();
        for (int x = Math.floorDiv(window.x - window.range, 64); x <= Math.floorDiv(window.x + window.range, 64); x++)
            for (int z = Math.floorDiv(window.z - window.range, 64); z <= Math.floorDiv(window.z + window.range,
                    64); z++)
                coordinates.add(new int[] { x, z });
        coordinates.sort(java.util.Comparator
                .comparingDouble(c -> Math.pow(c[0] * 64 - window.x, 2) + Math.pow(c[1] * 64 - window.z, 2)));
        for (int[] coordinate : coordinates) {
            int x = coordinate[0], z = coordinate[1];
            Object tile = chunk.invoke(processor, 0, x, z);
            if (tile == null)
                tile = chunk.invoke(processor, -1, x, z);
            if (tile == null)
                continue;
            Object texture = call(tile, "getLeafTexture");
            if (texture == null)
                continue;
            int id = number(call(texture, "getGlColorTexture"));
            if (id <= 0)
                continue;
            long key = (long) x << 32 | z & 0xffffffffL;
            int version = number(call(texture, "getTextureVersion"));
            Tile pixels = cachedTexture(id, version, key, x * 64, z * 64);
            if (pixels != null)
                result.add(pixels);
        }
        return result;
    }

    private static List<Tile> xaeroMinimap(Window window) throws ReflectiveOperationException {
        Object session = Class.forName("xaero.common.XaeroMinimapSession").getMethod("getCurrentSession").invoke(null);
        if (session == null)
            return List.of();
        Object processor = call(session, "getMinimapProcessor");
        if (!world.dimension().equals(call(processor, "getLastMapDimension")))
            return List.of();
        Object writer = call(processor, "getMinimapWriter");
        Object[][] blocks = (Object[][]) call(writer, "getLoadedBlocks");
        if (blocks == null)
            return List.of();
        List<int[]> available = new ArrayList<>();
        for (Object[] row : blocks)
            if (row != null)
                for (Object chunk : row) {
                    if (chunk == null)
                        continue;
                    int x = number(call(chunk, "getX")) * 64, z = number(call(chunk, "getZ")) * 64;
                    if (!intersects(window, x, z, 64))
                        continue;
                    int id = number(chunk.getClass().getMethod("getGlTexture", int.class).invoke(chunk, 4));
                    if (id > 0)
                        available.add(new int[] { x, z, id });
                }
        available.sort(
                java.util.Comparator.comparingDouble(c -> Math.pow(c[0] - window.x, 2) + Math.pow(c[1] - window.z, 2)));
        List<Tile> result = new ArrayList<>();
        for (int[] chunk : available) {
            long key = ((long) Math.floorDiv(chunk[0], 64) << 32 | Math.floorDiv(chunk[1], 64) & 0xffffffffL)
                    ^ Long.MIN_VALUE;
            Tile tile = cachedTexture(chunk[2], (int) (world.getGameTime() / 200), key, chunk[0], chunk[1]);
            if (tile != null)
                result.add(tile);
        }
        return result;
    }

    private static Tile cachedTexture(int id, int version, long key, int x, int z) {
        GpuTile previous = GPU_TILES.get(key);
        if ((previous == null || previous.id != id || previous.version != version) && downloads > 0) {
            downloads--;
            int[] pixels = download(id);
            if (pixels != null) {
                if (GPU_TILES.size() >= 128 && previous == null)
                    GPU_TILES.remove(GPU_TILES.keySet().iterator().next());
                previous = new GpuTile(id, version, new Tile(x, z, (int) Math.sqrt(pixels.length), 64, pixels));
                GPU_TILES.put(key, previous);
            }
        }
        return previous == null ? null : previous.tile;
    }

    /** 只在 CPU 缓冲已释放时读回已有小瓦片；恢复纹理绑定和所有像素打包参数，不切换帧缓冲。 */
    private static int[] download(int texture) {
        if (!GL11.glIsTexture(texture))
            return null;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                buffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT), row = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipX = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS), skipY = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        ByteBuffer data = null;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width < 1 || width != height || width > 512)
                return null;
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            data = MemoryUtil.memAlloc(width * height * 4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            int[] pixels = new int[width * height];
            for (int i = 0; i < pixels.length; i++) {
                int offset = i * 4;
                pixels[i] = data.get(offset + 3) == 0 ? 0
                        : 0xff000000 | (data.get(offset) & 255) << 16 | (data.get(offset + 1) & 255) << 8
                                | data.get(offset + 2) & 255;
            }
            return pixels;
        } finally {
            if (data != null)
                MemoryUtil.memFree(data);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, row);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipX);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipY);
        }
    }

    private static boolean intersects(Window window, int x, int z, int size) {
        return x + size > window.x - window.range && z + size > window.z - window.range && x < window.x + window.range
                && z < window.z + window.range;
    }

    private static Tile copy(NativeImage image, int x, int z, int blocks) {
        synchronized (image) {
            int width = image.getWidth();
            if (width <= 0 || width > 1024 || image.getHeight() != width)
                throw new IllegalArgumentException("Invalid map tile dimensions");
            int[] pixels = new int[width * width];
            for (int i = 0; i < pixels.length; i++) {
                int c = image.getPixelRGBA(i % width, i / width);
                pixels[i] = c & 0xff00ff00 | (c & 255) << 16 | c >>> 16 & 255;
            }
            return new Tile(x, z, width, blocks, pixels);
        }
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static Object call(Object instance, String name) throws ReflectiveOperationException {
        return instance.getClass().getMethod(name).invoke(instance);
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass())
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            }
            catch (NoSuchFieldException next) {
            }
        throw new NoSuchFieldException(name);
    }
}
