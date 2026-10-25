"""Inventory, package and compare the current production release without launching Minecraft.
中文：核对、打包并比较当前生产发行文件，不启动 Minecraft。
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
LANGUAGE_OVERLAY = None
REPORTS = ROOT / 'build/reports'
LIBS = ROOT / 'build/libs'
MOD_ID = 'killstreakcod'
MOD_JAR_RE = re.compile(
    r'^(?:killstreakcod(?:-forge-\d+\.\d+\.\d+)?|killstreaks-(?:neoforge|forge)-\d+\.\d+\.\d+)-'
    r'(?P<version>\d+\.\d+\.\d+(?:\.\d+)?)\.jar$', re.IGNORECASE)
MAX_JAR_BYTES = 100_000_000
BUILD_SCRIPT = (ROOT / 'build.gradle').read_text(encoding='utf-8-sig')
VERSION = re.search(r"version = '([^']+)'", BUILD_SCRIPT).group(1)
ARCHIVE_NAME = re.search(r"base\.archivesName = '([^']+)'", BUILD_SCRIPT).group(1)
JAR = LIBS / f'{ARCHIVE_NAME}-{VERSION}.jar'
SOURCE = LIBS / f'{ARCHIVE_NAME}-{VERSION}-source.zip'
SOURCE_ROOTS = ['src/main', 'src/uiPreview', 'gradle', 'tools', 'docs', 'build.gradle', 'settings.gradle',
                'gradle.properties', 'gradlew', 'gradlew.bat', 'killstreakcod.mcreator', 'README.md', '.gitignore',
                '.editorconfig', 'assets/terminal.obj', 'assets/terminal.mtl']
SOURCE_ROOTS += [path.relative_to(ROOT).as_posix() for path in sorted((ROOT / 'assets').glob('laptop_*.png'))]

# 需求文档存在时随源码收录，发行不依赖工程外已移走的参考文档。
if (ROOT / '新系统.docx').is_file():
    SOURCE_ROOTS.append('新系统.docx')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def save(name, data):
    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def source_files():
    files = {}
    for name in SOURCE_ROOTS:
        root = ROOT / name
        require(root.exists(), 'Missing source root: ' + name)
        entries = root.rglob('*') if root.is_dir() else [root]
        for path in sorted(entries):
            if not path.is_file() or '__pycache__' in path.parts or path.suffix in {'.pyc', '.pyo', '.ref', '.log'}:
                continue
            require(not path.is_symlink(), 'Source link is not supported: ' + str(path))
            files[path.relative_to(ROOT).as_posix()] = path
    # 源码归档采用构建时同一份语言增量，不改写镜像工程内的 resources。
    if LANGUAGE_OVERLAY is not None:
        require(LANGUAGE_OVERLAY.is_dir(), 'Language overlay directory is missing.')
        files.update({f'src/main/resources/assets/{MOD_ID}/lang/' + p.name: p
                      for p in LANGUAGE_OVERLAY.glob('*.json')})
    return files


def artifact(path):
    return {'path': str(path), 'sha256': sha(path), 'bytes': path.stat().st_size}


def resource_files():
    """Merge separately supplied language files. 中文：合并独立语言资源，不改写源码镜像的 resources。"""
    files = {p.relative_to(RESOURCES).as_posix(): p for p in RESOURCES.rglob('*') if p.is_file()}
    if LANGUAGE_OVERLAY is not None:
        require(LANGUAGE_OVERLAY.is_dir(), 'Language overlay directory is missing.')
        files.update({f'assets/{MOD_ID}/lang/' + p.name: p for p in LANGUAGE_OVERLAY.glob('*.json')})
    return files


def localization_inventory():
    """Check language coverage and arguments. 中文：核对语言覆盖、字幕路由及格式参数。"""
    languages = {}
    for key, path in sorted(resource_files().items()):
        if not key.startswith(f'assets/{MOD_ID}/lang/') or path.suffix != '.json':
            continue
        def unique(pairs):
            result = {}
            for key, value in pairs:
                require(key not in result, f'Duplicate language key: {path.name}: {key}')
                result[key] = value
            return result
        languages[path.stem] = json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique)
    full_locales = ('en_us', 'zh_cn', 'zh_tw', 'zh_hk', 'lzh')
    require(set(full_locales) <= languages.keys(), 'English, Chinese variants and Literary Chinese are required.')
    english = languages['en_us']
    ui_keys = {key for key in english if not key.startswith('radio.killstreakcod.')}
    mixed_traditional = re.compile(
        r'并|稍后|之后|后退|關系|重復|剩余|其余|游戲|默認|服務器|文件夾|鼠標|'
        r'這里|那里|哪里|里面|干得|干掉|沖擊')

    def arguments(value):
        positions, implicit = [], 0
        for match in re.finditer(r'%(?:(\d+)\$)?s|%%', value):
            if match.group(0) == '%%':
                positions.append('percent')
            else:
                if not match.group(1):
                    implicit += 1
                positions.append(match.group(1) or str(implicit))
        return Counter(positions)

    for code, entries in languages.items():
        require(ui_keys <= entries.keys(), 'Incomplete UI language: ' + code)
        require(entries.keys() <= english.keys(), 'Unknown language keys: ' + code)
        if code in full_locales:
            require(entries.keys() == english.keys(), 'Incomplete UI or radio translation: ' + code)
        if code in ('zh_tw', 'zh_hk'):
            for key, value in entries.items():
                require(mixed_traditional.search(value) is None,
                        f'Mixed or unconverted Traditional Chinese: {code}: {key}')
        require(entries['mod.killstreakcod.name'] == 'Killstreak', 'Mod display name differs: ' + code)
        for key, value in entries.items():
            require(isinstance(value, str) and value.strip() and '\ufffd' not in value,
                    f'Invalid localized text: {code}: {key}')
            require(arguments(value) == arguments(english[key]), f'Format arguments differ: {code}: {key}')
        require(entries['screen.killstreakcod.phone.confirm'].count('\n') == 1,
                'Phone confirmation must retain two lines: ' + code)
    for path in (ROOT / 'src/main/java').rglob('*.java'):
        source = path.read_text(encoding='utf-8-sig')
        for key in re.findall(r'(?:translatable(?:WithFallback)?|I18n\.get|\.translation)\("([^"]+)"', source):
            if MOD_ID in key and not key.endswith(('.', '_')):
                require(key in english, 'Missing translation used by source: ' + key)
    captions = read(RESOURCES / f'assets/{MOD_ID}/mw/radio-captions.json')
    count = 0
    for row in captions['clips'].values():
        if row.get('scope') != 'mp_dialogue_script_route':
            continue
        label = row.get('displaySpeaker', row['speaker'])
        encoded = label.encode('utf-16-be')
        label_hash = 0
        for index in range(0, len(encoded), 2):
            label_hash = (31 * label_hash + int.from_bytes(encoded[index:index + 2], 'big')) & 0xffffffff
        keys = ['radio.killstreakcod.line.' + row['subtitleLocalizeId'],
                'radio.killstreakcod.speaker.' + format(label_hash, 'x')]
        for code in full_locales:
            require(all(key in languages[code] for key in keys), 'Untranslated radio caption: ' + code)
        require(languages['zh_cn'][keys[0]] == row['text'], 'Original Chinese radio text changed.')
        require(languages['zh_cn'][keys[1]] == label, 'Original Chinese radio speaker changed.')
        count += 1
    require(languages['zh_cn']['itemGroup.killstreakcod'] == '连杀奖励', 'Chinese creative tab name changed.')
    workspace_languages = read(ROOT / 'killstreakcod.mcreator')['language_map']
    require(languages.keys() <= workspace_languages.keys(), 'MCreator language map is incomplete.')
    return {'uiKeys': len(ui_keys), 'locales': {code: len(entries) for code, entries in languages.items()},
            'radioClips': count, 'radioLocales': list(full_locales), 'fullTranslationLocales': list(full_locales),
            'radioFallbackLocale': 'en_us', 'placeholdersMatch': True}


def inventory(baseline=None):
    require(JAR.is_file(), 'Build the production JAR first.')
    require(JAR.stat().st_size < MAX_JAR_BYTES, 'Production JAR must be below 100 MB.')
    resources = resource_files()
    java = {p.relative_to(ROOT / 'src/main/java').with_suffix('.class').as_posix(): p
            for p in (ROOT / 'src/main/java').rglob('*.java')}
    for key, path in java.items():
        package = re.search(r'^package\s+([^;]+);', path.read_text(encoding='utf-8-sig'), re.M)
        expected = key.rsplit('/', 1)[0].replace('/', '.')
        require(package is not None and package.group(1) == expected, 'Package does not match source directory: ' + str(path))
    for path in [*java.values(), *resources.values(), ROOT / 'build.gradle']:
        require(path.stat().st_mtime_ns <= JAR.stat().st_mtime_ns, 'Source is newer than JAR: ' + str(path))
    for key, path in resources.items():
        if path.suffix == '.json':
            read(path)
    mesh_formats = Counter()
    for path in RESOURCES.rglob('*.mesh.gz'):
        with gzip.open(path, 'rb') as mesh:
            magic, version = struct.unpack('>II', mesh.read(8))
        require(magic == 0x4d574d33 and 1 <= version <= 4, 'Unsupported mesh format: ' + str(path))
        mesh_formats[str(version)] += 1

    def texture_references(node):
        if isinstance(node, dict):
            for key, value in node.items():
                # Archived source textures are provenance, while texture fields are live resources.
                # 中文：sourceTexture 字段记录已归档来源，texture 等字段才是运行时引用。
                if not key.startswith('sourceTexture'):
                    texture_references(value)
        elif isinstance(node, list):
            for value in node:
                texture_references(value)
        elif isinstance(node, str) and re.fullmatch(r'killstreakcod:textures/[^\s]+\.png', node):
            location = node.split(':', 1)[1]
            require((RESOURCES / f'assets/{MOD_ID}' / location).is_file(), 'Missing runtime texture: ' + node)

    for path in (RESOURCES / f'assets/{MOD_ID}/mw').rglob('*.json'):
        texture_references(read(path))
    metadata = tomllib.loads((RESOURCES / 'META-INF/neoforge.mods.toml').read_text(encoding='utf-8-sig'))
    mod = next(m for m in metadata['mods'] if m['modId'] == MOD_ID)
    require(mod['version'] == VERSION, 'Metadata and build versions differ.')
    localization = localization_inventory()
    sounds = read(RESOURCES / f'assets/{MOD_ID}/sounds.json')
    sound_files = 0
    for event, entry in sounds.items():
        for sound in entry.get('sounds', []):
            name = sound if isinstance(sound, str) else sound['name']
            namespace, separator, location = name.partition(':')
            namespace, location = (namespace, location) if separator else ('minecraft', name)
            if namespace != MOD_ID:
                continue
            if isinstance(sound, dict) and sound.get('type') == 'event':
                require(location in sounds, 'Missing sound event: ' + name)
            else:
                require((RESOURCES / f'assets/{MOD_ID}/sounds/{location}.ogg').is_file(), 'Missing sound file: ' + name)
                sound_files += 1
    with zipfile.ZipFile(JAR) as jar:
        require(jar.testzip() is None, 'Production archive CRC failed.')
        names = {name for name in jar.namelist() if not name.endswith('/')}
        classes = {name for name in names if name.endswith('.class')}
        for key, path in resources.items():
            require(key in names and jar.read(key) == path.read_bytes(), 'Packaged resource differs: ' + key)
        extra = names - set(resources) - classes - {'META-INF/MANIFEST.MF'}
        require(not extra, 'Unexpected JAR entries: ' + str(sorted(extra)))
        for key in classes:
            owner = key.split('$', 1)[0] + '.class' if '$' in key else key
            require(owner in java, 'Compiled class has no production source: ' + key)
        for key in java:
            require(key in classes, 'Production class missing: ' + key)
        for path in RESOURCES.glob('*.mixins.json'):
            definition = read(path)
            targets = [definition['plugin']] if definition.get('plugin') else []
            targets += [definition['package'] + '.' + name for side in ['mixins', 'client', 'server']
                        for name in definition.get(side, [])]
            for target in targets:
                require(target.replace('.', '/') + '.class' in classes, 'Missing mixin or plugin: ' + target)
        comparison = None
        if baseline:
            cleanup = read(ROOT / 'docs/CLEANUP.json')
            with zipfile.ZipFile(baseline) as old:
                prior = {name for name in old.namelist() if not name.endswith('/')}
                old_classes = {name for name in prior if name.endswith('.class')}
                require(classes == old_classes, 'Runtime class membership changed.')
                for key in classes:
                    require(jar.read(key) == old.read(key), 'Runtime class changed: ' + key)
                removed = prior - names
                require(removed <= set(cleanup['removedSourceResources']), 'Unexpected removed runtime resource.')
                require(not names - prior, 'Unexpected added runtime entries.')
                for key in names - {'META-INF/MANIFEST.MF', 'META-INF/neoforge.mods.toml'}:
                    require(jar.read(key) == old.read(key), 'Retained runtime entry changed: ' + key)
                before = tomllib.loads(old.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'))
                for item in before['mods']:
                    if item['modId'] == MOD_ID:
                        item['version'] = VERSION
                require(before == metadata, 'Unexpected metadata changes.')
                comparison = {'jar': artifact(Path(baseline)), 'unchangedRuntimeClasses': len(classes),
                              'removedUnusedEntries': len(removed), 'retainedRuntimeBytesUnchanged': True}
    log = ROOT / 'build/logs/build.log'
    build_confirmed = log.is_file() and 'BUILD SUCCESSFUL' in log.read_text(encoding='utf-8-sig')
    report = {'version': VERSION, 'jar': artifact(JAR), 'issues': [], 'buildLogConfirmsSuccess': build_confirmed,
              'jarBudgetBytes': MAX_JAR_BYTES, 'meshFormats': dict(sorted(mesh_formats.items())),
              'productionJavaFiles': len(java), 'compiledClasses': len(classes), 'resourceFiles': len(resources),
              'importedModels': len(list(RESOURCES.rglob('*.mesh.gz'))), 'soundFileReferences': sound_files,
              'baseline': comparison, 'localization': localization,
              'languageOverlay': str(LANGUAGE_OVERLAY) if LANGUAGE_OVERLAY is not None else None, 'testsRun': False, 'minecraftLaunched': False}
    save('package-inventory.json', report)
    print(json.dumps(report, ensure_ascii=True, indent=2))


def checked_inventory():
    report = read(REPORTS / 'package-inventory.json')
    require(report['version'] == VERSION and not report['issues'], 'Run inventory for the current version.')
    require(report['jar']['sha256'] == sha(JAR), 'Production JAR differs from inventory.')
    return report


def package():
    report = checked_inventory()
    paths = source_files()
    with zipfile.ZipFile(SOURCE, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for key, path in paths.items():
            archive.write(path, key)
    (LIBS / 'SHA256SUMS.txt').write_text(
        report['jar']['sha256'] + '  ' + JAR.name + '\n' + sha(SOURCE) + '  ' + SOURCE.name + '\n', encoding='utf-8')
    save('source-package.json', {'version': VERSION, 'source': artifact(SOURCE), 'entries': len(paths)})
    print(f'Packaged {len(paths)} source files: {SOURCE.name} ({SOURCE.stat().st_size:,} bytes)')


def verify():
    report = checked_inventory()
    paths = source_files()
    with zipfile.ZipFile(SOURCE) as archive:
        require(set(archive.namelist()) == set(paths), 'Source archive membership differs.')
        for key, path in paths.items():
            require(archive.read(key) == path.read_bytes(), 'Source archive file differs: ' + key)
    result = {'version': VERSION, 'jar': report['jar'], 'source': artifact(SOURCE),
              'sourceEntries': len(paths), 'sourceMatchesWorkspace': True, 'testsRun': False, 'minecraftLaunched': False}
    installation = REPORTS / 'instance-update.json'
    if installation.is_file():
        record = read(installation)
        require(record['version'] == VERSION, 'Installation record is from another version.')
        require(sha(record['installed']['path']) == record['installed']['sha256'] == report['jar']['sha256'], 'Installed JAR differs.')
        if record['backup']:
            require(sha(record['backup']['path']) == record['backup']['sha256'], 'Old JAR backup differs.')
        instance = Path(record['instance'])
        expected = {row['name']: row['sha256'] for row in record['retainedMods']}
        actual = {p.name: sha(p) for p in (instance / 'mods').glob('*.jar') if not MOD_JAR_RE.fullmatch(p.name)}
        require(actual == expected, 'Other active mods changed.')
        active = [p for p in (instance / 'mods').glob('*.jar') if MOD_JAR_RE.fullmatch(p.name)]
        require(len(active) == 1 and active[0].name == Path(record['installed']['path']).name
                and active[0].name == JAR.name,
                'Duplicate or incorrect active release.')
        retained = {row['relative']: row['sha256'] for row in record['retainedFiles']}
        actual_files = {p.relative_to(instance).as_posix(): sha(p) for folder in ['config', MOD_ID + '-skins']
                        for p in (instance / folder).rglob('*') if p.is_file()}
        for path in (instance / 'saves').glob('*/serverconfig/killstreakcod-server.toml'):
            actual_files[path.relative_to(instance).as_posix()] = sha(path)
        require(actual_files == retained, 'Config or skin files changed.')
        result.update({'installed': record['installed'], 'backup': record['backup'],
                       'unchangedOtherMods': len(expected), 'unchangedConfigAndSkinFiles': len(retained)})
    save('delivery-verification.json', result)
    print(json.dumps(result, ensure_ascii=True, indent=2))


def main():
    global LANGUAGE_OVERLAY
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['inventory', 'package', 'verify'])
    parser.add_argument('--baseline', type=Path, help='Optional prior JAR for a byte-identical runtime cleanup.')
    parser.add_argument('--language-overlay', type=Path, help='Language JSON directory also supplied to Gradle.')
    args = parser.parse_args()
    LANGUAGE_OVERLAY = args.language_overlay.resolve() if args.language_overlay else None
    if args.command == 'inventory':
        inventory(args.baseline)
    elif args.command == 'package':
        package()
    else:
        verify()


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        raise SystemExit(1)
