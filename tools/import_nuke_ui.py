"""Import MP NukeInbound layers, constructor state and independent property tracks.
中文：导入多人模式核弹来袭图层、构造状态和独立属性轨道，不执行反编译 Lua。
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil

from PIL import Image
from import_aircraft_hud import number


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def import_assets(source, project):
    reverse = source / 'reverse_engineering'
    index = reverse / 'nuke_ui_index'
    ui = reverse / 'nuke_ui_lui'
    assets = project / 'src/main/resources/assets/killstreakcod'
    images = {row['asset_id']: row for row in read(ui / 'ui_image_index.json')}
    names = {row['name']: row for row in images.values()}
    colors = {row['swatch']: sum(row['rgb8'][key] << shift for key, shift in
                               [('r', 16), ('g', 8), ('b', 0)])
              for row in read(index / 'original_ui_colors.json')}

    def value(raw):
        literal = number(raw)
        if literal is not None:
            return literal
        expression = raw['expression']
        if expression in colors:
            return colors[expression]
        material = re.fullmatch(r'material\((?:hash#)?([\w]+)\)', expression)
        if material:
            return images[material[1]]['name'] if material[1] in images else material[1]
        if expression.startswith(('LUI.Visibility.', 'LUI.Alignment.', 'BLEND_MODE.')):
            return expression.rsplit('.', 1)[-1]
        if expression.startswith('call@'):
            return 'native:' + expression
        raise ValueError('Unresolved NukeInbound value: ' + expression)

    tree = next(row for row in read(ui / 'mp_nuke_countdown_layout.json')['widget_trees']
                if row['widget_name'] == 'NukeWarning')
    nodes = []
    by_name = {}
    for element in tree['elements']:
        name = element['element_id']
        if name is None:
            continue
        node = dict(id=name, anchors=element['layout']['normalized_at_1080p1'],
                    initial={'Alpha': 1, 'RGB': 0xffffff, 'Visibility': 'Visible',
                             'Blend_Mode': 'blend', 'Alignment': 'Left', 'Text_Size': 28}, channels={})
        properties = {'SetAlpha': 'Alpha', 'SetRGBFromTable': 'RGB', 'SetRGBFromInt': 'RGB',
                      'setImage': 'Material', 'SetFontSize': 'Text_Size', 'SetAlignment': 'Alignment',
                      'SetBlendMode': 'Blend_Mode'}
        for call in element['initialization_calls']:
            method = call['function_expression'].rsplit('.', 1)[-1]
            args = call['arguments'][1:]
            if method in properties:
                node['initial'][properties[method]] = value(args[0])
            elif method == 'SetVisible':
                node['initial']['Visibility'] = 'Visible' if args[0] else 'Hidden'
        nodes.append(node)
        by_name[name] = node
    inbound = read(index / 'nuke_inbound_animation.json')
    for row in inbound['tracks']:
        node = by_name[row['element_expression'].rsplit('.', 1)[-1]]
        for track in row['tracks']:
            for channel in track['property_channels']:
                prop = channel['property']['expression'].rsplit('.', 1)[-1]
                node['channels'][prop] = [dict(duration=step['native_fields']['duration'],
                                              value=value(step['native_fields']['value']),
                                              easing=step['native_fields'].get('easing', {}).get(
                                                  'expression', 'linear').rsplit('.', 1)[-1])
                                          for step in channel['steps']]
    selected = sorted({row['image_name'] for row in read(index / 'mgb_nuke_inbound_material_bindings.json')})
    textures = {}
    for name in selected:
        row = names[name]
        original = source / row['delivery_png']
        digest = hashlib.sha256(original.read_bytes()).hexdigest()
        if digest != row['png_sha256']:
            raise ValueError('Original PNG hash differs: ' + name)
        with Image.open(original) as bitmap:
            if bitmap.size != (row['width'], row['height']):
                raise ValueError('Original PNG dimensions differ: ' + name)
        destination = assets / 'textures/gui/mwii/nuke' / (name + '.png')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(original, destination)
        textures[name] = dict(texture='killstreakcod:' + destination.relative_to(assets).as_posix(),
                              width=row['width'], height=row['height'], source=row['delivery_png'], sha256=digest)
    result = dict(format=1, source='nuke_ui_index / nuke_ui_lui', width=1920, height=104,
                  duration=inbound['duration_ms'], fadeStart=9800, fadeDuration=250,
                  nodes=nodes, images=textures,
                  textIds={'TacticalNukeText': '13701bb14bd3b816', 'Message': '0446bafa956d0207',
                           'Warning1': '0ff82291c143375f', 'Warning2': '0ff82291c143375f'},
                  limits=['MP timer type 0 only; CP, Ground War and DEFCON are separate modes.',
                          'Original font and full glitch/pixel-grid shaders are unavailable.',
                          'Native Text expressions are evidence; localized text and live time come from Minecraft.'])
    path = assets / 'mw/nuke-ui.json'
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Imported {len(nodes)} MP elements, {len(selected)} original PNGs, duration {inbound["duration_ms"]}ms.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1])
    arguments = parser.parse_args()
    import_assets(arguments.source, arguments.project)
