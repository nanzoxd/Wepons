# Wepons

Paper 1.21.x plugin (Java 21). Converted from the customswords Skript.

## Build
    mvn clean package
Jar: `target/Wepons-1.0.0.jar` -> put in `plugins/`. Remove the Skript file and AltarSMP is not needed.

## Commands
- `/wepons`  - one GUI with every weapon, no holder names. Click to take one. (op by default: `wepons.use`)
- `/holder`  - every weapon with its holders, green = online, gray = offline. (everyone by default: `wepons.holder`)

Holders are saved in `plugins/Wepons/holders.yml`, so offline players still show.

## Keybinds
- **F** - Ability 1
- **F with an item in your offhand** - Ability 2 (or Shift + F, set `keybinds.ability-2: sneak`)
- Monarch Crown: F with both hands empty.

## Weapons
Relique blades: Fortune Blade, Flame Blade, Bloom Blade.
Altar SMP S2: Ancient Blade (kill progression), Dragonrend, Tidebreaker, Wither Symbiote, Omen.
Plus the original swords, axes, bow, maces, pickaxes, shield, trident and Monarch Crown (netherite).

## Resource pack
`wepons.zip` holds every model: Relique weapons, Hoplite weapons, the Relique blades (9201-9203) and the
Altar SMP S2 models remapped to 9301-9305 (Ancient Blade, Dragonrend, Tidebreaker, Wither Symbiote, Omen).
Model numbers already match `config.yml`.

## Lore
- Header icons are vanilla item textures drawn inline with Minecraft's sprite text component (nether star, echo shard,
  clock, bone). They come from the client's own item atlas, so every player sees them with or without the pack.
  Needs a 1.21.9+ client and server (the pom uses paper-api 1.21.11).
- All lore text is small caps (vanilla Unicode letters, `WeaponRegistry.sc`).
