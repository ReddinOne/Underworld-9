#!/usr/bin/env python3
"""Guard against accidental loss of the Wither guardian equipment/spawn rules."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
code = (root / 'src/main/java/dev/unstable/underworld/UnderworldGuardians.java').read_text()
math = (root / 'src/main/java/dev/unstable/underworld/UnderworldLayout.java').read_text()
init = (root / 'src/main/java/dev/unstable/underworld/UnderworldMod.java').read_text()
checks = {
    'skeleton type': 'EntityType.WITHER_SKELETON.create',
    'helmet': 'Items.NETHERITE_HELMET',
    'chestplate': 'Items.NETHERITE_CHESTPLATE',
    'leggings': 'Items.NETHERITE_LEGGINGS',
    'boots': 'Items.NETHERITE_BOOTS',
    'sword': 'Items.NETHERITE_SWORD',
    'Protection II': 'Enchantments.PROTECTION), 2',
    'emerald trim': 'TrimMaterials.EMERALD',
    'flow pattern': 'TrimPatterns.FLOW',
    'persistence': 'setPersistenceRequired()',
    'saved attempt': 'ATTEMPT_Y = -188',
    'checked footing': 'isSolid()',
    'air clearance': 'feet.above(2)',
}
for label, part in checks.items():
    assert part in code, f'missing guardian requirement: {label}'
assert 'guardianRoll' in math, 'missing deterministic 50% math'
assert 'UnderworldGuardians.enqueue' in init and 'UnderworldGuardians.tick' in init
assert 'UnderworldGuardians.unload' in init
print('PASS: all netherite equipment, Protection II, emerald flow trim and once-only spawning checks')
