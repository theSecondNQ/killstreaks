"""Inventory, package and compare the current production release without launching Minecraft.
中文：核对、打包并比较当前生产发行文件，不启动 Minecraft。
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
REPORTS = ROOT / 'build/reports'
LIBS = ROOT / 'build/libs'
MOD_ID = 'killstreakcod'
VERSION = re.search(r"version = '([^']+)'", (ROOT / 'build.gradle').read_text(encoding='utf-8')).group(1)
JAR = LIBS / f'{MOD_ID}-{VERSION}.jar'
SOURCE = LIBS / f'{MOD_ID}-{VERSION}-source.zip'
SOURCE_ROOTS = ['src/main', 'gradle', 'tools', 'docs', 'build.gradle', 'settings.gradle',
                'gradle.properties', 'gradlew', 'gradlew.bat', 'killstreakcod.mcreator', 'README.md', '.gitignore',
                '.editorconfig']


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
    return files


def artifact(path):
    return {'path': str(path), 'sha256': sha(path), 'bytes': path.stat().st_size}


def inventory(baseline=None):
    require(JAR.is_file(), 'Build the production JAR first.')
    resources = {p.relative_to(RESOURCES).as_posix(): p for p in RESOURCES.rglob('*') if p.is_file()}
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
    metadata = tomllib.loads((RESOURCES / 'META-INF/neoforge.mods.toml').read_text(encoding='utf-8-sig'))
    mod = next(m for m in metadata['mods'] if m['modId'] == MOD_ID)
    require(mod['version'] == VERSION, 'Metadata and build versions differ.')
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
              'productionJavaFiles': len(java), 'compiledClasses': len(classes), 'resourceFiles': len(resources),
              'importedModels': len(list(RESOURCES.rglob('*.mesh.gz'))), 'soundFileReferences': sound_files,
              'baseline': comparison, 'testsRun': False, 'minecraftLaunched': False}
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
        actual = {p.name: sha(p) for p in (instance / 'mods').glob('*.jar') if not p.name.startswith(MOD_ID + '-')}
        require(actual == expected, 'Other active mods changed.')
        active = list((instance / 'mods').glob(MOD_ID + '-*.jar'))
        require(len(active) == 1 and active[0].name == JAR.name, 'Duplicate or incorrect active release.')
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['inventory', 'package', 'verify'])
    parser.add_argument('--baseline', type=Path, help='Optional prior JAR for a byte-identical runtime cleanup.')
    args = parser.parse_args()
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
