"""Import verified numeric aircraft HUD data and original PNGs, without executing Lua.
中文：导入已核对的空中载具 HUD 数值数据和原始 PNG，不执行重建的 Lua。
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/killstreakcod'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def number(value):
    """Accept arithmetic literals only. 中文：只接受数值和安全的算术字面量。"""
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return value
    if not isinstance(value, dict) or 'expression' not in value:
        return None
    text = value['expression'].replace('_1080p', '1').replace('_PT', '1')
    if not re.fullmatch(r'[0-9eE.\s()+*/+-]+', text):
        return None
    try:
        def evaluate(node):
            if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
                return node.value
            if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.USub, ast.UAdd)):
                return (-1 if isinstance(node.op, ast.USub) else 1) * evaluate(node.operand)
            if isinstance(node, ast.BinOp):
                a, b = evaluate(node.left), evaluate(node.right)
                if isinstance(node.op, ast.Add): return a + b
                if isinstance(node.op, ast.Sub): return a - b
                if isinstance(node.op, ast.Mult): return a * b
                if isinstance(node.op, ast.Div): return a / b
            raise ValueError('Non-literal arithmetic')
        return evaluate(ast.parse(text, mode='eval').body)
    except (SyntaxError, ValueError, ZeroDivisionError):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('extracted_root', type=Path)
    args = parser.parse_args()
    source = args.extracted_root.resolve()
    reverse = source / 'reverse_engineering'
    ui = reverse / 'aircraft_hud_ui'
    index = reverse / 'aircraft_hud_index'
    images = read(ui / 'ui_image_index.json')
    # Shared vehicle code also references cars and boats; import the two aircraft and their real HUD layers.
    # 共享载具代码还引用汽车与船；这里只导入两种飞机及实际需要的 HUD 图层。
    selected = [row for row in images if
                row['name'].startswith('overlay_gunship_') and row['name'] != 'overlay_gunship_damageglitch'
                or row['name'].startswith(('veh_diagram_gunship_', 'veh_diagram_choppergunner_',
                                          'tablet_chopper_gunner_', 'overlay_recon_drone_compass_'))
                or row['name'] in {'apache_icon_infinite', 'ui_tanto_logo'}]
    by_id = {row['asset_id']: row['name'] for row in images}
    image_names = {row['name'] for row in selected}
    existing = {hashlib.sha256(path.read_bytes()).hexdigest(): path for path in (ASSETS / 'textures').rglob('*.png')}
    runtime = {'format': 1, 'widgets': {}, 'images': {}, 'source': {
        'layouts': 'aircraft_hud_ui/widget_trees.json',
        'animations': 'aircraft_hud_index/animation_tracks.json',
        'nativeRuntimeRecovered': False,
        'timingPolicy': 'Native lengths are property-channel milliseconds, not measured full-scene durations.'}}
    report = {'images': [], 'widgets': []}
    for row in selected:
        original = source / row['delivery_png']
        digest = hashlib.sha256(original.read_bytes()).hexdigest()
        if digest != row['png_sha256']:
            raise ValueError('Original PNG differs: ' + row['name'])
        destination = existing.get(digest)
        reused = destination is not None
        if destination is None:
            destination = ASSETS / 'textures/gui/mwii/aircraft' / (row['name'] + '.png')
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, destination)
        texture = 'killstreakcod:' + destination.relative_to(ASSETS).as_posix()
        runtime['images'][row['name']] = {'texture': texture, 'width': row['width'], 'height': row['height']}
        report['images'].append({'name': row['name'], 'texture': texture, 'sha256': digest,
                                 'reused': reused, 'fullResolution': True})

    def material(value):
        if not isinstance(value, dict):
            return None
        match = re.fullmatch(r'material\((?:hash#)?([A-Za-z0-9_]+)\)', value.get('expression', ''))
        if not match:
            return None
        key = by_id.get(match[1], match[1])
        return key if key in image_names else None

    for tree in read(ui / 'widget_trees.json'):
        root = next((row for row in tree['elements'] if row['element_id'] is None and row.get('layout')), None)
        if root is None:
            continue
        anchors = root['layout'].get('normalized_at_1080p1')
        if anchors is None:
            continue
        width, height = anchors[5] - anchors[4], anchors[7] - anchors[6]
        if width <= 0 or height <= 0:
            continue
        widget = {'width': width, 'height': height, 'nodes': {}, 'sequences': {}, 'sourceLua': tree['lua_id']}
        if height == 1080:
            widget['fixedPixelFieldsCentered'] = True
        for element in tree['elements']:
            layout = element.get('layout')
            key = element['element_id']
            if key is None or layout is None or layout.get('normalized_at_1080p1') is None:
                continue
            node = {'anchors': layout['normalized_at_1080p1'], 'image': None,
                    'sourceInstruction': layout['instruction']}
            if element.get('child_widget_type'):
                node['widget'] = element['child_widget_type']
            for call in element.get('initialization_calls', []):
                method = call.get('function_expression', '').split('.')[-1]
                arguments = call.get('arguments', [])
                value = arguments[1] if len(arguments) > 1 else None
                literal = number(value)
                if method == 'setImage':
                    node['image'] = material(value)
                elif method in {'SetAlpha', 'SetScale', 'SetZRotation', 'SetFontSize'} and literal is not None:
                    node[{'SetAlpha': 'alpha', 'SetScale': 'scale', 'SetZRotation': 'rotation',
                          'SetFontSize': 'fontSize'}[method]] = literal
                elif method == 'setText' and isinstance(value, str):
                    node['text'] = value
                elif method == 'SetAlignment' and isinstance(value, dict):
                    node['alignment'] = value.get('expression', '').split('.')[-1]
                elif method == 'SetVisible' and value is False:
                    node['alpha'] = 0
            widget['nodes'][key] = node
        runtime['widgets'][tree['widget_name']] = widget
        report['widgets'].append(tree['widget_name'])

    imported_tracks = 0
    for animation in read(index / 'animation_tracks.json'):
        widget = runtime['widgets'].get(animation['widget_name'])
        expression = animation.get('element_expression', '')
        field = expression.split('.')[-1]
        if widget is None or field not in widget['nodes']:
            continue
        channels = widget['sequences'].setdefault(animation['sequence'], {}).setdefault(field, [])
        for track in animation['tracks']:
            for channel in track['property_channels']:
                steps = {}
                for step in channel['steps']:
                    native = step['native_fields']
                    duration = number(native.get('duration', 0))
                    if duration is None:
                        continue
                    converted = {'property': channel['property'], 'duration': duration}
                    if 'easing' in native:
                        converted['easing'] = native['easing']
                    literal = number(native.get('value'))
                    if literal is not None:
                        converted['numericValue'] = literal
                    else:
                        image = material(native.get('value'))
                        converted['value'] = {'expression': 'material(' + image + ')'} if image else native.get('value')
                    steps[str(len(steps) + 1)] = converted
                if steps:
                    channels.append(steps)
                    imported_tracks += 1
    lengths = read(index / 'key_sequence_lengths.json')
    runtime['sequenceLengths'] = lengths
    runtime['screenTargets'] = {'ApacheScreen02': {'slot': 3, 'width': 512, 'height': 512},
                                'ApacheScreen01': {'slot': 4, 'width': 256, 'height': 256},
                                'ApacheScreen03': {'slot': 5, 'width': 256, 'height': 256}}
    runtime['originalTexts'] = [{key: row[key] for key in ('asset_id', 'english', 'simplified_chinese')}
                                for row in read(ui / 'hud_texts_zh_en.json')]
    traditional = str.maketrans({'号': '號', '机': '機', '击': '擊', '伤': '傷', '损': '損', '严': '嚴',
                                '动': '動', '弹': '彈', '导': '導', '锁': '鎖', '标': '標', '缩': '縮',
                                '线': '線', '来': '來', '显': '顯', '开': '開', '关': '關', '视': '視',
                                '红': '紅', '统': '統', '侦': '偵', '装': '裝', '载': '載', '辆': '輛',
                                '离': '離', '满': '滿', '续': '續', '键': '鍵', '测': '測', '电': '電',
                                '扰': '擾', '体': '體', '无': '無', '发': '發', '现': '現', '灭': '滅',
                                '尽': '盡', '后': '後', '场': '場', '时': '時', '轮': '輪', '启': '啟',
                                '闭': '閉', '设': '設', '备': '備', '长': '長', '马': '馬', '达': '達',
                                '义': '義', '复': '復', '获': '獲', '应': '應', '态': '態'})
    literary = {'暗影1号': '暗影壹號', '攻击直升机': '攻擊直升機', '警告：受损严重': '警示：重創',
                '无法移动': '不可移行', '电量不足': '電將竭', '重置缩放': '復其遠近', '燃料': '燃料',
                '导弹锁定': '導彈鎖定', '目标：&&1': '所指：&&1', '缩放': '遠近', '在线': '已通',
                '燃料即将耗尽': '燃料將竭', '干扰弹': '干擾彈', '正在填装弹药': '正填彈藥',
                '长按退出': '長按以退', '热成像开关': '開合熱像', '前视红外系统': '前視紅外系統',
                '坐标': '方位', '探测到电磁脉冲器': '已察電磁脈衝', '没电了': '電已竭',
                '引擎失效': '引擎失效', '离开': '退出', '燃料用尽': '燃料已竭', '控制': '操控',
                '切换武器': '易械', '机炮': '機砲', '射击': '發射', '速度': '速率', '装弹': '填彈',
                '离线': '未通', '导弹来袭': '導彈來襲', '载具失控': '載具失御', '关闭载具': '閉載具'}
    for locale in sorted((ASSETS / 'lang').glob('*.json')):
        translations = read(locale)
        for row in runtime['originalTexts']:
            value = row['simplified_chinese'] if locale.stem in {'zh_cn', 'zh_tw', 'zh_hk', 'lzh'} else row['english']
            if locale.stem == 'lzh':
                value = literary.get(value, value.translate(traditional))
            elif locale.stem in {'zh_tw', 'zh_hk'}:
                value = value.translate(traditional)
            translations['hud.killstreakcod.aircraft.' + row['asset_id']] = value.replace('&&1', '%s')
        locale.write_text(json.dumps(translations, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (ASSETS / 'mw/aircraft-hud-ui.json').write_text(json.dumps(runtime, ensure_ascii=False, separators=(',', ':')) + '\n',
                                                 encoding='utf-8')
    report.update({'source': str(source), 'numericChannels': imported_tracks, 'originalTexts': len(runtime['originalTexts']),
                   'pngsReencoded': False, 'luaExecuted': False})
    output = ROOT / 'build/reports/aircraft-hud'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'import.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Imported {len(report["widgets"])} widgets, {imported_tracks} channels, {len(selected)} original image bindings.')


if __name__ == '__main__':
    main()
