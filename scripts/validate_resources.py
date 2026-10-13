#!/usr/bin/env python3
"""Offline structural preflight for Minecraft 26.3 resources.

This is not Minecraft's datapack decoder. `gradle build` and a real game/server
startup are still required to verify all registry and API details.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def check(ok: bool, message: str) -> None:
    if not ok:
        ERRORS.append(message)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except FileNotFoundError:
        ERRORS.append(f"Required JSON missing: {path.relative_to(ROOT)}")
    except json.JSONDecodeError as exc:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
        context = ' | '.join(f'{i+1}: {lines[i][:100]}' for i in range(max(0,exc.lineno-2), min(len(lines),exc.lineno+1)))
        ERRORS.append(f"JSON invalid: {path.relative_to(ROOT)}: {exc.msg} at line {exc.lineno}, col {exc.colno}. {context}")
    return None


def properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except FileNotFoundError:
        ERRORS.append(f"Missing {path.name}")
        return result
    for line_no, line in enumerate(lines, 1):
        clean = line.strip()
        if not clean or clean.startswith(("#", "!")):
            continue
        if '=' not in clean:
            ERRORS.append(f"{path.name}:{line_no}: expected key=value, got {clean[:70]!r}")
            continue
        key, value = map(str.strip, clean.split('=', 1))
        if key in result:
            ERRORS.append(f"{path.name}:{line_no}: duplicate key {key}")
        result[key] = value
    return result


def run() -> int:
    props = properties(ROOT / 'gradle.properties')
    version = props.get('mod_version', '')
    minecraft = props.get('minecraft_version', '')
    base = props.get('archives_base_name', '')
    check(bool(re.fullmatch(r'\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?', version)),
          f"gradle.properties: invalid mod_version={version!r}, expected version like 3.3.2")
    check(minecraft == '26.3', f"gradle.properties: target must be Minecraft 26.3; found {minecraft!r}")
    check(bool(re.fullmatch(r'[a-z0-9][a-z0-9-]*', base)),
          f"gradle.properties: invalid archives_base_name={base!r}")

    workflow = ROOT / '.github/workflows/build.yml'
    try:
        workflow_text = workflow.read_text(encoding='utf-8')
    except FileNotFoundError:
        workflow_text = ''
        ERRORS.append(f'Missing {workflow.relative_to(ROOT)}')
    check('gradle build' in workflow_text, 'Build workflow must execute gradle build')
    check('scripts/validate_resources.py' in workflow_text, 'Build workflow must validate resources')
    # Gradle generates the versioned JAR. The artifact path MUST NOT hardcode
    # a particular version, otherwise a harmless version bump breaks the build.
    check('build/libs/*.jar' in workflow_text,
          'Build workflow must upload build/libs/*.jar, not a fixed version-specific JAR filename')

    resources = ROOT / 'src/main/resources'
    data = resources / 'data/underworld'
    mod = load_json(resources / 'fabric.mod.json')
    dim = load_json(data / 'dimension/underworld.json')
    dim_type = load_json(data / 'dimension_type/underworld.json')
    biome = load_json(data / 'worldgen/biome/wasteland.json')
    loot = load_json(data / 'loot_table/chests/forgotten_relics.json')
    treasury = load_json(data / 'loot_table/chests/forbidden_treasury.json')

    if isinstance(mod, dict):
        check(mod.get('id') == 'underworld', 'fabric.mod.json: mod id must be underworld')
        check(mod.get('version') == '${version}',
              'fabric.mod.json: version must be ${version} so Gradle substitutes mod_version automatically')
        depends = mod.get('depends', {})
        check(isinstance(depends, dict) and depends.get('minecraft') == '~26.3',
              'fabric.mod.json: expected Minecraft ~26.3 dependency')
        check(isinstance(mod.get('entrypoints'), dict), 'fabric.mod.json: missing entrypoints')
    if isinstance(dim, dict):
        check(dim.get('type') == 'underworld:underworld', 'dimension/underworld.json: incorrect dimension type ID')
        gen = dim.get('generator', {})
        check(isinstance(gen, dict) and gen.get('type') == 'minecraft:flat', 'Dimension generator must be empty flat')
        settings = gen.get('settings', {}) if isinstance(gen, dict) else {}
        check(isinstance(settings, dict) and settings.get('layers') == [], 'Dimension must have no vanilla terrain layers')
        check(isinstance(settings, dict) and settings.get('biome') == 'underworld:wasteland', 'Dimension biome must be underworld:wasteland')
    if isinstance(dim_type, dict):
        check(dim_type.get('min_y') == -192 and dim_type.get('height') == 512,
              'Dimension bounds must be min_y=-192, height=512')
        check(dim_type.get('skybox') == 'none' and dim_type.get('has_skylight') is False,
              'Underworld must have no skybox or sunlight')
    if isinstance(biome, dict):
        attrs = biome.get('attributes', {})
        check(isinstance(attrs, dict) and 'minecraft:visual/sky_color' not in attrs,
              'Biome should not restore an Overworld-style sky_color')
    if isinstance(treasury, dict):
        check(treasury.get('type') == 'minecraft:chest',
              'Treasury loot table must use minecraft:chest context')
        treasure_str = json.dumps(treasury)
        check('minecraft:netherite_ingot' in treasure_str and 'minecraft:golden_apple' in treasure_str,
              '3.5 rare treasury must retain netherite ingots and multiple golden apples')
    check(not (ROOT / 'src/main/java/dev/unstable/underworld/UnderworldVisualRegions.java').exists(),
          'Visual sub-biomes from v3.5 must not be installed')
    if isinstance(loot, dict):
        check(loot.get('type') == 'minecraft:chest', 'Loot table must use minecraft:chest context')
        pools = loot.get('pools')
        check(isinstance(pools, list) and len(pools) >= 3, 'Loot table must have at least 3 pools')
        items = []
        if isinstance(pools, list):
            for pi, pool in enumerate(pools):
                if not isinstance(pool, dict):
                    ERRORS.append(f'loot pool {pi}: expected object')
                    continue
                check('functions' not in pool and 'conditions' not in pool,
                      f'loot pool {pi}: legacy 26.2 fields functions/conditions')
                entries = pool.get('entries')
                if not isinstance(entries, list) or not entries:
                    ERRORS.append(f'loot pool {pi}: missing entries')
                    continue
                for ei, entry in enumerate(entries):
                    if not isinstance(entry, dict):
                        ERRORS.append(f'loot pool {pi} entry {ei}: expected object')
                        continue
                    check(not ({'functions', 'conditions'} & entry.keys()),
                          f'loot pool {pi} entry {ei}: obsolete functions/conditions fields')
                    if entry.get('type') == 'minecraft:item':
                        name = entry.get('name')
                        check(isinstance(name, str) and bool(re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', name)),
                              f'loot pool {pi} entry {ei}: invalid item identifier {name!r}')
                        items.append(name)
                    modifiers = entry.get('modifier', [])
                    if isinstance(modifiers, dict):
                        modifiers = [modifiers]
                    if not isinstance(modifiers, list):
                        ERRORS.append(f'loot pool {pi} entry {ei}: modifier must be object or list')
                        continue
                    for mi, modifier in enumerate(modifiers):
                        check(isinstance(modifier, dict) and isinstance(modifier.get('type'), str),
                              f'loot pool {pi} entry {ei} modifier {mi}: missing type')
                        if isinstance(modifier, dict):
                            check('function' not in modifier and 'conditions' not in modifier,
                                  f'loot pool {pi} entry {ei} modifier {mi}: legacy loot function keys')
        check('minecraft:chain' not in items, 'Loot table includes removed minecraft:chain ID, use minecraft:iron_chain')
        for item in ('minecraft:echo_shard', 'minecraft:sculk_sensor', 'minecraft:gilded_blackstone', 'minecraft:ancient_debris'):
            check(item in items, f'Loot table is missing expected thematic item {item}')

    for path in sorted(resources.rglob('*.json')):
        # Already-parsed files don't matter; re-reading costs virtually nothing.
        load_json(path)
    check(not (resources / 'data/minecraft/dimension/the_nether.json').exists(),
          'Unexpected vanilla Nether dimension override')
    check(not (resources / 'data/minecraft/dimension_type/the_nether.json').exists(),
          'Unexpected vanilla Nether dimension-type override')
    for name in ('UnderworldMod', 'UnderworldMath', 'UnderworldPlacement', 'UnderworldTerrain', 'UnderworldStructures'):
        check((ROOT / f'src/main/java/dev/unstable/underworld/{name}.java').exists(),
              f'Missing Java class {name}.java')

    if ERRORS:
        print(f'FAIL: {len(ERRORS)} validation issue(s)', file=sys.stderr)
        for index, issue in enumerate(ERRORS, 1):
            print(f'  {index}. {issue}', file=sys.stderr)
        return 1
    print(f'PASS: Minecraft {minecraft}; mod {version}; JSON syntax, dimension, loot and build metadata validated')
    print('NOTE: Only Gradle compilation and an actual game launch can validate Minecraft API and datapack registries.')
    return 0


if __name__ == '__main__':
    raise SystemExit(run())
