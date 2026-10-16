package net.killstreakcod.render;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Restores reversible byte planes before the ordinary mesh reader runs.
 * 在常规模型读取前还原可逆字节平面，不改变顶点精度、骨骼、UV 或三角面。
 */
final class ImportedMeshInput {
    private static final int MAGIC = 0x4d574d33;
    private static final int MAX_BYTES = 96 * 1024 * 1024;

    private ImportedMeshInput() {}

    /**
     * Version 1 streams directly; versions 2–4 restore each mesh locally without a shared vertex cache.
     * 版本 1 直接读取；版本 2 至 4 按网格还原，不增加跨模型顶点缓存。
     */
    static DataInputStream open(InputStream source) throws IOException {
        var input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(source)));
        try {
            input.mark(8);
            if (input.readInt() != MAGIC)
                throw new IOException("Unsupported imported mesh magic");
            int version = input.readInt();
            if (version < 1 || version > 4)
                throw new IOException("Unsupported imported mesh version: " + version);
            input.reset();
            if (version == 1)
                return input;
            byte[] data = input.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES)
                throw new IOException("Imported mesh exceeds decoded size limit");
            restore(data, version);
            input.close();
            return new DataInputStream(new ByteArrayInputStream(data));
        }
        catch (IOException | RuntimeException error) {
            input.close();
            throw error;
        }
    }

    private static void restore(byte[] data, int version) throws IOException {
        ByteBuffer cursor = ByteBuffer.wrap(data);
        try {
            cursor.position(8);
            int bones = count(cursor, 4096);
            for (int i = 0; i < bones; i++) {
                string(cursor);
                skip(cursor, 72);
            }
            skip(cursor, 17);
            int meshes = count(cursor, 512);
            for (int i = 0; i < meshes; i++) {
                string(cursor);
                string(cursor);
                skip(cursor, 2);
                int vertices = count(cursor, 2_000_000);
                int influences = count(cursor, 32);
                int indices = count(cursor, 6_000_000);
                restoreBlock(data, cursor, vertices, 36 + 8 * influences, version);
                restoreBlock(data, cursor, indices, 4, version);
            }
            if (cursor.hasRemaining())
                throw new IOException("Unexpected trailing imported mesh data");
            cursor.putInt(4, 1);
        }
        catch (BufferUnderflowException | IndexOutOfBoundsException error) {
            throw new IOException("Truncated packed imported mesh", error);
        }
    }

    private static void restoreBlock(byte[] data, ByteBuffer cursor, int count, int stride, int version)
            throws IOException {
        int start = cursor.position();
        skip(cursor, (long) count * stride);
        byte[] planes = Arrays.copyOfRange(data, start, cursor.position());
        for (int word = 0; word < stride / 4; word++) {
            int previous = 0;
            for (int row = 0; row < count; row++) {
                int value = 0;
                for (int b = 0; b < 4; b++)
                    value = value << 8 | planes[(word * 4 + b) * count + row] & 255;
                if (version == 3)
                    value ^= previous;
                else if (version == 4)
                    value += previous;
                previous = value;
                int at = start + row * stride + word * 4;
                data[at] = (byte) (value >>> 24);
                data[at + 1] = (byte) (value >>> 16);
                data[at + 2] = (byte) (value >>> 8);
                data[at + 3] = (byte) value;
            }
        }
    }

    private static int count(ByteBuffer cursor, int maximum) throws IOException {
        int value = cursor.getInt();
        if (value < 0 || value > maximum)
            throw new IOException("Invalid imported mesh count: " + value);
        return value;
    }

    private static void string(ByteBuffer cursor) throws IOException {
        skip(cursor, Short.toUnsignedInt(cursor.getShort()));
    }

    private static void skip(ByteBuffer cursor, long bytes) throws IOException {
        if (bytes < 0 || bytes > cursor.remaining())
            throw new IOException("Truncated packed imported mesh block");
        cursor.position(cursor.position() + (int) bytes);
    }
}
