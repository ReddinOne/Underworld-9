#!/usr/bin/env python3
"""Post-build check: inspect Gradle's compiled mod JAR before upload."""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
props = {}
for line in (ROOT / 'gradle.properties').read_text().splitlines():
    if '=' in line and not line.lstrip().startswith(('#','!')):
        key, val = line.split('=', 1)
        props[key.strip()] = val.strip()
version = props['mod_version']
base = props['archives_base_name']
expected = ROOT / 'build/libs' / f'{base}-{version}.jar'
if not expected.is_file():
    print(f'FAIL: expected compiled mod JAR {expected.relative_to(ROOT)}; actual JAR files: '
          + repr([p.name for p in (ROOT / 'build/libs').glob('*.jar')]), file=sys.stderr)
    sys.exit(1)
with zipfile.ZipFile(expected) as archive:
    bad = archive.testzip()
    if bad:
        sys.exit(f'FAIL: corrupt JAR entry {bad}')
    names = set(archive.namelist())
    required = (
        'fabric.mod.json',
        'dev/unstable/underworld/UnderworldMod.class',
        'dev/unstable/underworld/UnderworldStructures.class',
        'dev/unstable/underworld/UnderworldLayout.class',
        'dev/unstable/underworld/UnderworldTerrain.class',
        'dev/unstable/underworld/UnderworldGuardians.class',
        'data/underworld/dimension/underworld.json',
        'data/underworld/dimension_type/underworld.json',
        'data/underworld/worldgen/biome/wasteland.json',
        'data/underworld/loot_table/chests/forgotten_relics.json',
        'data/underworld/loot_table/chests/forbidden_treasury.json',
    )
    missing = [item for item in required if item not in names]
    if missing:
        sys.exit(f'FAIL: JAR missing required files: {missing}')
    try:
        for name in names:
            if name.endswith('.json'):
                json.loads(archive.read(name).decode('utf-8-sig'))
        meta = json.loads(archive.read('fabric.mod.json'))
    except (ValueError, UnicodeError) as exc:
        sys.exit(f'FAIL: invalid JSON inside compiled JAR: {exc}')
    if meta.get('version') != version:
        sys.exit(f"FAIL: JAR version {meta.get('version')!r} differs from gradle.properties {version!r}")
    if meta.get('id') != 'underworld':
        sys.exit('FAIL: incorrect mod id')
print(f'PASS: built JAR {expected.name}, required classes/resources, valid JSON, matching version')
