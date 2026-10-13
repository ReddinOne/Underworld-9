# Unstable Underworld 3.6.1 — Minecraft Java 26.3 (Fabric)

This revision is **based on v3.4** (not v3.5's terrain). Terrain shape, void, performance queue, sky, Adventure Mode, coordinates and Nether world generation are unchanged.

## New ruins based on the Unstable Universe Underworld wiki

The source description of the Underworld refers to a dark-gray **Castle**, **Fields**, **graveyard**, **portal lobby**, great chasms, and restrained green accents. The 8 v3.4 procedural footprints have been enhanced/reinterpreted as:

1. Soul Keeper's gateway — broken Gothic arch, twin keeps and green highlights
2. Dark observatory — radial columns, central emerald memorial
3. Keeper's watchtower — tall ruined battlements and barred windows
4. The Castle — four keeps, perimeter court, roof collapse, green crown
5. Graveyard in the Fields — chapel, tombs, grave markers and crypt
6. Shattered archive — sculk archives, beams, arches and green relics
7. Chasm bridge — asymmetric bastions and broken overhead crossing
8. Portal lobby — multiple ruined obsidian arch frames, four-way corridor, central monument

These are **interpretations**, not exact reconstructed structures from screenshot pixel data; the wiki's image files were not available for direct inspection, and the mod is not affiliated with the series.

## Other changes

- **2% portal probability per eligible generated ruin**, independent and deterministic for all 8 families (not 2% per chest nor per chunk). One obsidian portal appears in the center of qualifying ruins; it links to the Overworld using v3.4's routing.
- **Loot from v3.5 preserved**: the common `forgotten_relics` table and rare `forbidden_treasury` table. Up to 6 separate chest locations per ruin (chance varies). Chests are oriented inward with free approach/headroom and are placed *after* all masonry.
- Foundations follow local terrain. Per-column blackstone support fills from natural surface up to a leveled courtyard. Ruins are excluded from void columns, and the site validator rejects broken land.
- **Initial fall can hurt or kill**. Removed the high-level Resistance status granted during entry. Vanilla fall damage operates after the dimension transition; the motion is preserved and the accumulated fall distance begins at transition. Armor, Feather Falling and other vanilla effects apply normally.
- **No 3.5 visual provinces, paths or biome changes.** Only `underworld:wasteland` remains. No vanilla Nether worldgen changes.
- Same v3.4 chunk queue (maximum two chunks per tick with soft time budget), no automatic rewriting of v3.4-marked chunks.

## Build on GitHub

Commit these extracted files to your repository root, then run **Actions → Build Minecraft 26.3 mod**. The generated JAR is uploaded under **Artifacts → unstable-underworld-26.3**. Install the JAR in server and client with matching Fabric Loader/API.

## Limitations / safety

- **Back up your world** before upgrading.
- Existing chunks marked as v3.4 are deliberately left untouched; new structure designs appear only in **newly generated Underworld chunks**. If you need to replace already-generated ruins, use a world backup and regenerate only selected Underworld chunks with a region editor. Never delete Nether chunks to do this.
- An exceptionally long fall without protection will commonly be fatal now. This is an intentional change; take armor/potions or another landing strategy.
- Local validations (JSON, math/terrain, structure placement, loot, chest geometry, portal rate) do not replace an actual Fabric build or in-game launch. GitHub Actions runs the full compilation.

## Source notes

- [The Underworld, Unstable Universe Wiki](https://unstable-universe-mc.fandom.com/wiki/The_Underworld): overall place, Castle, Fields, graveyard and portal lobby.
- [Spoke — Exploring Minecraft's Forgotten Dimension](https://www.youtube.com/watch?v=lA1ohDqw8XA): atmosphere and narrative references.

## Guardianes de las ruinas (v3.6.1)

- Cada estructura aceptada tiene una tirada determinista de **50 %** para alojar **un esqueleto wither**.
- Lleva **casco, peto, pantalones y botas de netherita** con **Protección II**,
  cada uno con **adorno de flujo en esmeralda**, además de una **espada de netherita**.
- El guardián aparece únicamente en el chunk central de la ruina, sobre un bloque
  sólido y con tres bloques de espacio vertical libre; nunca dentro de muros o cofres.
- La aparición se registra una sola vez en la región guardada (marcador a Y=-188).
  No reaparecerá tras morir ni al recargar el chunk; tampoco se generan guardianes
  adicionales al pasear por la estructura.
- Las ruinas antiguas de v3.6 también pueden recibir su guardián al cargarse por
  primera vez con esta versión, sin reconstruir su terreno ni modificar sus cofres.
- La colocación se procesa en el hilo principal con una cola limitada a dos
  comprobaciones por tick. En dificultad Pacífico los monstruos pueden desaparecer.
- El resto de la v3.6 (2 % de portales, botín y terreno) permanece intacto.

### Validación

`GuardianRollTest` comprueba 100.000 tiradas, y `check_guardian.py` verifica el
 equipamiento y las salvaguardas de generación. La compilación real de Fabric y
 la ejecución dentro de Minecraft deben verificarse en GitHub Actions y en una
 copia de seguridad del mundo.
