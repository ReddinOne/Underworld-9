"""Additional release checks; no Minecraft runtime needed."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=root/'src/main/resources/data/underworld'
loot=json.loads((data/'loot_table/chests/forgotten_relics.json').read_text())
ids=[e['name'] for p in loot['pools'] for e in p['entries'] if e['type']=='minecraft:item']
assert 'minecraft:chain' not in ids
assert 'minecraft:iron_chain' in ids
assert (data/'dimension/underworld.json').is_file()
assert (data/'dimension_type/underworld.json').is_file()
assert (data/'worldgen/biome/wasteland.json').is_file()
print('PASS: fixed item ID, custom loot present, dimension resources unchanged')
