"""Stage installed render mods and a shader pack for an isolated regression client."""
from pathlib import Path
import argparse
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('instance', type=Path)
parser.add_argument('--no-shaders', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1] / 'build/render-compat'
for artifact, version, pattern in [('embeddium', '0.3.31-mc1.20.1', 'embeddium-0.3.31+mc1.20.1.jar'),
                                   ('oculus', '1.8.0-mc1.20.1', 'oculus-mc1.20.1-1.8.0.jar')]:
    source = args.instance / 'mods' / pattern
    destination = root / 'maven/local/render/compat' / artifact / version
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination / f'{artifact}-{version}.jar')
    (destination / f'{artifact}-{version}.pom').write_text(
        '<project><modelVersion>4.0.0</modelVersion><groupId>local.render.compat</groupId>'
        f'<artifactId>{artifact}</artifactId><version>{version}</version></project>', encoding='utf-8')
game = root / 'game'
(game / 'config').mkdir(parents=True, exist_ok=True)
(game / 'shaderpacks').mkdir(exist_ok=True)
shader = args.instance / 'shaderpacks/photon_v1.4.zip'
shutil.copy2(shader, game / 'shaderpacks' / shader.name)
(game / 'config/oculus.properties').write_text(
    f'enableShaders={str(not args.no_shaders).lower()}\nshaderPack={shader.name}\n', encoding='utf-8')
(game / 'config/fml.toml').write_text('earlyWindowControl = false\n', encoding='utf-8')
(game / 'options.txt').write_text(
    'renderDistance:4\nsimulationDistance:5\nmaxFps:60\npauseOnLostFocus:false\n'
    'soundCategory_master:0.0\nshowAutosaveIndicator:false\n', encoding='utf-8')
print(f'Staged Embeddium, Oculus and {shader.name}: {game}')
