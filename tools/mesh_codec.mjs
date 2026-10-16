/**
 * Lossless imported mesh byte planes; every float and index keeps all 32 bits.
 * 中文：导入网格的无损字节平面编码；每个浮点数和索引均保留完整 32 位。
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';
import { pathToFileURL } from 'node:url';

const MAGIC = 0x4d574d33;
const MAX_BYTES = 96 * 1024 * 1024;

function blocks(data) {
    let offset = 8;
    const skip = bytes => {
        if (!Number.isSafeInteger(bytes) || bytes < 0 || offset + bytes > data.length)
            throw new Error('Truncated imported mesh block');
        offset += bytes;
    };
    const count = maximum => {
        if (offset + 4 > data.length) throw new Error('Truncated imported mesh count');
        const value = data.readInt32BE(offset); offset += 4;
        if (value < 0 || value > maximum) throw new Error('Invalid imported mesh count');
        return value;
    };
    const string = () => {
        if (offset + 2 > data.length) throw new Error('Truncated imported mesh string');
        const size = data.readUInt16BE(offset); offset += 2; skip(size);
    };
    if (data.length < 8 || data.length > MAX_BYTES || data.readInt32BE(0) !== MAGIC)
        throw new Error('Unsupported imported mesh header');
    const bones = count(4096);
    for (let i = 0; i < bones; i++) { string(); skip(72); }
    skip(17);
    const result = [], meshes = count(512);
    for (let i = 0; i < meshes; i++) {
        string(); string(); skip(2);
        const vertices = count(2_000_000), influences = count(32), indices = count(6_000_000);
        const stride = 36 + 8 * influences;
        result.push({ start: offset, count: vertices, stride }); skip(vertices * stride);
        result.push({ start: offset, count: indices, stride: 4 }); skip(indices * 4);
    }
    if (offset !== data.length) throw new Error('Unexpected trailing imported mesh data');
    return result;
}

export function decodeModel(input) {
    if (input.length < 8) throw new Error('Truncated imported mesh header');
    const version = input.readInt32BE(4);
    if (version < 1 || version > 4) throw new Error('Unsupported imported mesh version');
    const sections = blocks(input);
    if (version === 1) return input;
    const data = Buffer.from(input);
    for (const { start, count, stride } of sections) {
        for (let word = 0; word < stride / 4; word++) {
            let previous = 0;
            for (let row = 0; row < count; row++) {
                let value = 0;
                for (let b = 0; b < 4; b++) value = (value << 8) | input[start + (word * 4 + b) * count + row];
                if (version === 3) value ^= previous;
                else if (version === 4) value = (value + previous) | 0;
                previous = value;
                data.writeInt32BE(value, start + row * stride + word * 4);
            }
        }
    }
    data.writeInt32BE(1, 4);
    return data;
}

export function encodeModel(input) {
    const source = decodeModel(input), sections = blocks(source);
    let best = zlib.gzipSync(source, { level: 9 });
    for (let version = 2; version <= 4; version++) {
        const data = Buffer.from(source);
        for (const { start, count, stride } of sections) {
            for (let word = 0; word < stride / 4; word++) {
                let previous = 0;
                for (let row = 0; row < count; row++) {
                    const value = source.readInt32BE(start + row * stride + word * 4);
                    const encoded = version === 3 ? value ^ previous : version === 4 ? (value - previous) | 0 : value;
                    previous = value;
                    for (let b = 0; b < 4; b++)
                        data[start + (word * 4 + b) * count + row] = encoded >>> (24 - b * 8) & 255;
                }
            }
        }
        data.writeInt32BE(version, 4);
        const candidate = zlib.gzipSync(data, { level: 9 });
        if (candidate.length < best.length) best = candidate;
    }
    if (!decodeModel(zlib.gunzipSync(best)).equals(source)) throw new Error('Mesh round trip changed data');
    return best;
}

async function main() {
    const [operation, sourceArgument, targetArgument] = process.argv.slice(2);
    if (!['pack', 'unpack'].includes(operation) || !sourceArgument || !targetArgument)
        throw new Error('Usage: node tools/mesh_codec.mjs <pack|unpack> <source-directory> <stage-directory>');
    const source = path.resolve(sourceArgument), target = path.resolve(targetArgument);
    if (source === target || target.startsWith(source + path.sep)) throw new Error('Stage outside the source directory');
    await fs.mkdir(target, { recursive: true });
    const files = (await fs.readdir(source)).filter(name => name.endsWith('.mesh.gz')).sort();
    let before = 0, after = 0;
    for (const name of files) {
        const bytes = await fs.readFile(path.join(source, name));
        const raw = zlib.gunzipSync(bytes, { maxOutputLength: MAX_BYTES });
        const encoded = operation === 'pack' ? encodeModel(raw) : zlib.gzipSync(decodeModel(raw), { level: 9 });
        await fs.writeFile(path.join(target, name), encoded);
        before += bytes.length; after += encoded.length;
    }
    console.log(JSON.stringify({ operation, files: files.length, beforeBytes: before, afterBytes: after }));
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url)
    await main();
