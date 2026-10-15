/**
 * Rebuild imported mesh index buffers and pixel textures without changing rigs or vertex attributes.
 * 中文：重建导入模型索引与像素材质，原骨骼、蒙皮、UV、坐标和插槽保持逐字节一致。
 * npm install --prefix build/pixel-model-tools --no-audit --no-fund meshoptimizer@1.3.0 sharp@0.35.5
 * node tools/pixel_models.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dependencyRoot = path.join(workspace, 'build/pixel-model-tools');
const require = createRequire(path.join(dependencyRoot, 'package.json'));
const sharp = require('sharp');
const { MeshoptSimplifier } = await import(pathToFileURL(path.join(dependencyRoot,
    'node_modules/meshoptimizer/index.js')));
await MeshoptSimplifier.ready;
const assets = path.join(workspace, 'src/main/resources/assets');
const reports = path.join(workspace, 'build/reports/pixel-models');
const stage = path.join(workspace, 'build/pixel-models/staged/assets');
const sha = data => crypto.createHash('sha256').update(data).digest('hex');
const integerBuffer = values => {
    const data = Buffer.alloc(values.length * 4);
    values.forEach((n, i) => data.writeUInt32BE(n, i * 4));
    return data;
};

export function readModel(data) {
    let offset = 0;
    const integer = () => { const n = data.readInt32BE(offset); offset += 4; return n; };
    const string = () => {
        const n = data.readUInt16BE(offset); offset += 2;
        const s = data.toString('utf8', offset, offset + n); offset += n; return s;
    };
    if (integer() !== 0x4d574d33 || integer() !== 1) throw new Error('Unsupported mesh header');
    const bones = integer();
    for (let i = 0; i < bones; i++) { string(); offset += 72; }
    offset += 17;
    const meshCount = integer(), header = data.subarray(0, offset), meshes = [];
    for (let m = 0; m < meshCount; m++) {
        const start = offset, material = string(), texture = string();
        const translucent = data[offset++], emissive = data[offset++];
        const prefix = data.subarray(start, offset);
        const vertices = integer(), influences = integer(), count = integer(), stride = 36 + influences * 8;
        const records = data.subarray(offset, offset + stride * vertices); offset += stride * vertices;
        const positions = new Float32Array(vertices * 3);
        const attributeStride = 2 + influences * 2;
        const attributes = new Float32Array(vertices * attributeStride);
        for (let i = 0; i < vertices; i++) {
            for (let k = 0; k < 3; k++) positions[i * 3 + k] = records.readFloatBE(i * stride + k * 4);
            attributes[i * attributeStride] = records.readFloatBE(i * stride + 24);
            attributes[i * attributeStride + 1] = records.readFloatBE(i * stride + 28);
            for (let k = 0; k < influences; k++) {
                const weight = records.readFloatBE(i * stride + 40 + k * 8);
                // Zero-weight joint IDs carry no skinning meaning and must not block simplification.
                attributes[i * attributeStride + 2 + k * 2] = weight > 0
                    ? records.readInt32BE(i * stride + 36 + k * 8) : 0;
                attributes[i * attributeStride + 3 + k * 2] = weight;
            }
        }
        const indices = new Uint32Array(count);
        for (let i = 0; i < count; i++) {
            indices[i] = integer();
            if (indices[i] >= vertices) throw new Error(`Invalid face index: ${material}`);
        }
        if (count % 3) throw new Error(`Incomplete triangle: ${material}`);
        meshes.push({ material, texture, translucent, emissive, prefix, vertices, influences, stride,
            records, positions, attributes, indices });
    }
    if (offset !== data.length) throw new Error('Unexpected trailing mesh bytes');
    return { bones, header, meshes };
}

function lodSettings(id) {
    if (id.endsWith('_lod_shadow')) return { ratio: 0.15, error: 0.02 };
    if (id.endsWith('_lod_far')) return { ratio: 0.18, error: 0.016 };
    if (id.includes('_lod')) return { ratio: 0.22, error: 0.012 };
    return { ratio: 0.25, error: 0.008 };
}

function rebuildModel(model, id) {
    const chunks = [model.header], surfaceStats = [];
    const settings = lodSettings(id);
    for (const mesh of model.meshes) {
        let indices = mesh.indices, error = 0;
        // Preserve small LCDs, attachment planes and already minimal pieces exactly.
        if (indices.length > 144 && mesh.influences <= 15 && !mesh.translucent
                && !/alpha|glass|blurred_rotor|hud_|lcd|screen/.test(mesh.material)) {
            const canonical = new Map(), remap = new Uint32Array(mesh.vertices);
            for (let v = 0; v < mesh.vertices; v++) {
                const record = mesh.records.subarray(v * mesh.stride, (v + 1) * mesh.stride);
                const key = record.toString('base64');
                if (!canonical.has(key)) canonical.set(key, v);
                remap[v] = canonical.get(key);
            }
            indices = Uint32Array.from(indices, index => remap[index]);
            const weights = [0.1, 0.1, ...Array.from({ length: mesh.influences }, () => [2, 1]).flat()];
            const target = Math.max(144, Math.floor(indices.length * settings.ratio / 3) * 3);
            [indices, error] = MeshoptSimplifier.simplifyWithAttributes(indices, mesh.positions, 3,
                mesh.attributes, weights.length, weights, null, target, settings.error,
                ['Regularize']);
        }
        if (!indices.length && mesh.indices.length) throw new Error(`Removed entire surface: ${id}`);
        let records = mesh.records, vertices = mesh.vertices;
        if (indices.length < mesh.indices.length) {
            const remap = new Map(), selected = [];
            indices = Uint32Array.from(indices, old => {
                if (!remap.has(old)) {
                    remap.set(old, remap.size);
                    selected.push(mesh.records.subarray(old * mesh.stride, (old + 1) * mesh.stride));
                }
                return remap.get(old);
            });
            records = Buffer.concat(selected);
            vertices = selected.length;
        }
        chunks.push(mesh.prefix, integerBuffer([vertices, mesh.influences, indices.length]),
            records, integerBuffer(Array.from(indices)));
        surfaceStats.push({ material: mesh.material, texture: mesh.texture, before: mesh.indices.length / 3,
            after: indices.length / 3, error });
    }
    const data = Buffer.concat(chunks), parsed = readModel(data);
    if (!parsed.header.equals(model.header)) throw new Error(`Skeleton/header changed: ${id}`);
    for (let i = 0; i < model.meshes.length; i++) {
        const before = model.meshes[i], after = parsed.meshes[i];
        if (!before.prefix.equals(after.prefix) || before.influences !== after.influences)
            throw new Error(`Material/skin format changed: ${id}`);
        // Every surviving vertex keeps its complete original record, including normals, UVs and weights.
        const sourceRecords = new Set();
        for (let v = 0; v < before.vertices; v++)
            sourceRecords.add(before.records.subarray(v * before.stride, (v + 1) * before.stride).toString('base64'));
        for (let v = 0; v < after.vertices; v++)
            if (!sourceRecords.has(after.records.subarray(v * after.stride, (v + 1) * after.stride).toString('base64')))
                throw new Error(`Vertex attributes changed: ${id}`);
    }
    return { data, surfaces: surfaceStats, before: surfaceStats.reduce((n, m) => n + m.before, 0),
        after: surfaceStats.reduce((n, m) => n + m.after, 0) };
}

async function writeStage(relative, data) {
    const target = path.resolve(stage, relative);
    if (!target.startsWith(stage + path.sep)) throw new Error('Staging path escaped assets');
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.writeFile(target, data);
}

async function main() {
    await fs.mkdir(reports, { recursive: true });
    const directory = path.join(assets, 'killstreakcod/mw/models');
    const files = (await fs.readdir(directory)).filter(n => n.endsWith('.mesh.gz')).sort();
    const models = [], textureIds = new Set(), originals = [];
    for (const file of files) {
        const relative = `killstreakcod/mw/models/${file}`;
        const source = await fs.readFile(path.join(assets, relative));
        const parsed = readModel(zlib.gunzipSync(source));
        parsed.meshes.forEach(m => textureIds.add(m.texture));
        const rebuilt = rebuildModel(parsed, file.slice(0, -8));
        const encoded = zlib.gzipSync(rebuilt.data, { level: 9 });
        await writeStage(relative, encoded);
        originals.push({ path: relative, sha256: sha(source), bytes: source.length });
        models.push({ id: file.slice(0, -8), bones: parsed.bones, before: rebuilt.before, after: rebuilt.after,
            beforeBytes: source.length, afterBytes: encoded.length, headerSha256: sha(parsed.header),
            sourceSha256: sha(source), outputSha256: sha(encoded), surfaces: rebuilt.surfaces });
        if (models.length % 50 === 0) console.log(`Rebuilt and validated ${models.length}/${files.length} models`);
    }

    // These model textures are also used by the procedural fallback geometry and item renderers.
    for (const name of ['tactical_atlas', 'tactical_lights', 'wheelson', 'support_helicopter'])
        textureIds.add(`killstreakcod:textures/entity/${name}.png`);
    const itemDirectory = path.join(assets, 'killstreakcod/models/item');
    for (const name of await fs.readdir(itemDirectory)) {
        const item = JSON.parse(await fs.readFile(path.join(itemDirectory, name), 'utf8'));
        for (const id of Object.values(item.textures || {})) {
            if (!id.startsWith('killstreakcod:')) continue;
            const [namespace, texture] = id.split(':');
            textureIds.add(`${namespace}:textures/${texture}.png`);
        }
    }

    const textures = [];
    for (const id of [...textureIds].sort()) {
        const relative = id.replace(':', '/'), source = await fs.readFile(path.join(assets, relative));
        const metadata = await sharp(source).metadata();
        const limit = relative.includes('/textures/item/') ? 64 : 128;
        const result = await sharp(source).resize({ width: limit, height: limit, fit: 'inside',
            withoutEnlargement: true }).png({ palette: true, colours: 64, dither: 0 }).toBuffer();
        const after = await sharp(result).metadata();
        if (Math.abs(metadata.width / metadata.height - after.width / after.height) > 0.02)
            throw new Error(`Texture aspect ratio changed: ${id}`);
        let mcmeta = {};
        try { mcmeta = JSON.parse(await fs.readFile(path.join(assets, relative + '.mcmeta'), 'utf8')); }
        catch (error) { if (error.code !== 'ENOENT') throw error; }
        if (mcmeta.animation) throw new Error(`Animated model atlas requires frame-aware handling: ${id}`);
        mcmeta.texture = { ...mcmeta.texture, blur: false };
        await writeStage(relative, result);
        await writeStage(relative + '.mcmeta', JSON.stringify(mcmeta) + '\n');
        originals.push({ path: relative, sha256: sha(source), bytes: source.length });
        textures.push({ id, before: [metadata.width, metadata.height], after: [after.width, after.height],
            beforeBytes: source.length, afterBytes: result.length, sourceSha256: sha(source), outputSha256: sha(result) });
    }

    const report = { status: 'staged', modelCount: models.length, textureCount: textures.length,
        beforeTriangles: models.reduce((n, m) => n + m.before, 0), afterTriangles: models.reduce((n, m) => n + m.after, 0),
        reducedModels: models.filter(m => m.after < m.before).length,
        skeletonHeadersUnchanged: true, survivingVertexAttributesUnchanged: true,
        localCreditsConsumed: 0, stage, originals, models, textures };
    await fs.writeFile(path.join(reports, 'rebuild.json'), JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify(Object.fromEntries(Object.entries(report).filter(([k]) => !['originals', 'models', 'textures'].includes(k)))));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
