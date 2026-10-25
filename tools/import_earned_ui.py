"""Import the recovered earned-splash layers and native property tracks.
中文：导入已核对的连杀获取提示图层与原生属性轨道，不执行反编译 Lua。
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil

from PIL import Image

REWARDS = dict(zip(
    ('uav', 'airdrop', 'precision_airstrike', 'directional_uav', 'scrambler_drone_guard',
     'toma_strike', 'cruise_predator', 'pac_sentry', 'chopper_gunner', 'gunship', 'fuel_airstrike',
     'juggernaut', 'sentry_gun', 'hover_jet', 'airdrop_multiple', 'assault_drone', 'auto_drone',
     'cluster_spike', 'airdrop_escort', 'nuke_multi'),
    ('uav', 'care_package', 'precision_airstrike', 'advanced_uav', 'counter_uav', 'mortar_strike',
     'cruise_missile', 'wheelson_hs', 'chopper_gunner', 'gunship', 'sae', 'juggernaut', 'sentry_gun',
     'vtol_jet', 'emergency_airdrop', 'bomb_drone', 'stealth_bomber', 'cluster_mine', 'overwatch_helo', 'mgb')))
WIDGETS = {'KillstreakEarnedSplash': 'splash', 'KillstreakEarnedSplashContent': 'content'}
COLORS = {'styleGuideAccentMain': 0x79C370, 'styleGuideAccentSupport': 0x368947,
          'styleGuideCool05': 0x141313}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def literal(value):
    if isinstance(value, (int, float)):
        return value
    expression = value['expression']
    if expression.startswith('SWATCHES.IW9StyleGuide.'):
        return COLORS[expression.rsplit('.', 1)[-1]]
    match = re.fullmatch(r'\(_1080p \* (-?[\d.]+)\)', expression)
    if not match:
        raise ValueError('Unresolved native value: ' + expression)
    return float(match[1])


def import_assets(source, project):
    index = source / 'reverse_engineering/earned_ui_index'
    resources = project / 'src/main/resources/assets/killstreakcod'
    images = {row['name']: row for row in read(index.parent / 'earned_ui_lui/ui_image_index.json')}
    metadata = read(resources / 'mw/notice-assets.json')
    copied = []

    def image(name, section, key):
        row = images[name]
        original = source / row['delivery_png']
        digest = hashlib.sha256(original.read_bytes()).hexdigest()
        if digest != row['png_sha256']:
            raise ValueError('Source image hash differs: ' + name)
        with Image.open(original) as bitmap:
            bitmap = bitmap.convert('RGBA')
            bounds = bitmap.getchannel('A').getbbox()
            if bitmap.size != (row['width'], row['height']) or bounds is None:
                raise ValueError('Invalid source image: ' + name)
        destination = resources / 'textures/gui/mwii' / (name + '.png')
        shutil.copy2(original, destination)
        metadata[section][key] = dict(name=name, source=row['delivery_png'], sourceSha256=digest,
                                     width=row['width'], height=row['height'], bounds=list(bounds))
        copied.append(name)

    rewards = {}
    for row in read(index / 'mp_earned_splash_resources.json'):
        reward = REWARDS[row['ref']]
        image(row['image_name'], 'icons', reward)
        rewards[reward] = {key: row[key] for key in ('ref', 'name_zh', 'name_en', 'image_name', 'sound_alias_full64')}
    for name in ('hud_splash_accent_diamond', 'hud_splash_notched_background_5',
                 'hud_splash_accent_chevrons_single', 'widg_gradient_left_to_right', 'hud_earned_streak_backer'):
        image(name, 'layers', name)
    layouts = {}
    for row in read(index / 'earned_layouts.json'):
        if row['widget'] in WIDGETS and row['in_constructor'] and row['element']:
            key = WIDGETS[row['widget']] + '.' + row['element']
            layouts[key] = row['anchors_offsets_1080p1']
    tracks = {}
    for row in read(index / 'animation_tracks.json'):
        if row['widget_name'] not in WIDGETS:
            continue
        key = WIDGETS[row['widget_name']] + '.' + row['element_expression'].rsplit('.', 1)[-1]
        channels = {}
        for track in row['tracks']:
            for channel in track['property_channels']:
                name = channel['property']['expression'].rsplit('.', 1)[-1]
                steps = []
                for step in channel['steps']:
                    native = step['native_fields']
                    steps.append(dict(duration=native['duration'], value=literal(native['value']),
                                      easing=native.get('easing', {}).get('expression', 'linear').rsplit('.', 1)[-1]))
                channels[name] = steps
        tracks.setdefault(key, {})[row['sequence']] = channels
    result = dict(format=1, source='earned_ui_index / earned_ui_lui', referenceSize=[1920, 1080],
                  top=200, contentWidth=500, colors=COLORS,
                  manager=read(index / 'killstreak_manager_defaults.json')['value'],
                  callbacks=dict(introContent=200, introHint=350, outroIcon=100,
                                 outroDivider=200, outroBanner=300, hide=400),
                  layouts=layouts, tracks=tracks, rewards=rewards,
                  caveats=['Original font and shader unavailable; Minecraft font is used.',
                           'Missing OutroChevronFlicker is not aliased to OutroChevronBlink.',
                           'Recovered sound aliases are evidence; no new audio was delivered.'])
    save(resources / 'mw/notice-assets.json', metadata)
    save(resources / 'mw/earned-splash.json', result)
    print(f'Imported {len(rewards)} rewards, {len(copied)} original PNGs, {len(tracks)} animated elements.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    import_assets(args.source, args.project)
