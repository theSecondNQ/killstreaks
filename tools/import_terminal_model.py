"""Package the user's original terminal OBJ, MTL and PNG files without editing them.
中文：原样打包用户的终端 OBJ、MTL 和 PNG，不编辑几何、材质或图像。
"""
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets'
ASSETS = ROOT / 'src/main/resources/assets/killstreakcod'
MODELS = ASSETS / 'models'


def copy_original(name, destination):
    source = SOURCE / name
    if not source.is_file():
        raise FileNotFoundError(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    if source_hash != hashlib.sha256(destination.read_bytes()).hexdigest():
        raise ValueError('Original terminal file changed during copying: ' + name)
    return {'source': source.relative_to(ROOT).as_posix(),
            'resource': destination.relative_to(ROOT / 'src/main/resources').as_posix(),
            'sha256': source_hash, 'bytes': source.stat().st_size}


def write_model(name, data):
    destination = MODELS / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main():
    lines = (SOURCE / 'terminal.obj').read_text(encoding='utf-8-sig').splitlines()
    libraries = [line.split(maxsplit=1)[1] for line in lines if line.startswith('mtllib ')]
    if libraries != ['terminal.mtl']:
        raise ValueError('Expected the original terminal.mtl beside terminal.obj.')
    material_lines = (SOURCE / 'terminal.mtl').read_text(encoding='utf-8-sig').splitlines()
    textures = sorted({line.split(maxsplit=1)[1] for line in material_lines if line.startswith('map_Kd ')})
    for name in textures:
        if Path(name).name != name or '/' in name or '\\' in name or not name.endswith('.png'):
            raise ValueError('Expected an adjacent original PNG: ' + name)
        if not (SOURCE / name).is_file():
            raise FileNotFoundError(SOURCE / name)

    # Keep the existing item display transforms; the original OBJ geometry is never rewritten.
    # 保留现有物品展示变换，原始 OBJ 几何文件始终只进行逐字节复制。
    current_model = MODELS / 'block/headquarters_terminal.json'
    display = json.loads(current_model.read_text(encoding='utf-8-sig')).get('display', {})
    originals = [copy_original(name, MODELS / 'block/headquarters_terminal' / name)
                 for name in ('terminal.obj', 'terminal.mtl')]
    originals.extend(copy_original(name, ASSETS / 'textures/block/headquarters_terminal' / name)
                     for name in textures)
    for section in ('full', 'lower', 'upper'):
        model = {'parent': 'minecraft:block/block', 'loader': 'killstreakcod:terminal_obj',
                 'model': 'killstreakcod:models/block/headquarters_terminal/terminal.obj', 'part': section,
                 'textures': {'particle': 'killstreakcod:block/headquarters_terminal/laptop_body'},
                 'render_type': 'minecraft:cutout'}
        if section == 'full':
            model['display'] = display
        suffix = '' if section == 'full' else '_' + section
        write_model('block/headquarters_terminal' + suffix + '.json', model)
    write_model('item/headquarters_terminal.json', {'parent': 'killstreakcod:block/headquarters_terminal'})

    report = {'sourceFiles': originals, 'sourceBytesPreserved': True, 'geometryOrTextureEditing': False,
              'vertices': sum(line.startswith('v ') for line in lines),
              'textureCoordinates': sum(line.startswith('vt ') for line in lines),
              'normals': sum(line.startswith('vn ') for line in lines),
              'objects': sum(line.startswith('o ') for line in lines),
              'faces': sum(line.startswith('f ') for line in lines)}
    destination = ROOT / 'build/reports/headquarters-terminal/model-import.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"Packaged {len(originals)} byte-identical original files; "
          f"{report['objects']} objects, {report['faces']} faces.")


if __name__ == '__main__':
    main()
