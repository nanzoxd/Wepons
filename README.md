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
- **Shift + F** - Ability 2 (a weapon with one ability fires it on F or Shift + F)
- Monarch Crown: F with both hands empty.

## Weapons
Relique blades: Fortune Blade, Flame Blade, Bloom Blade.
Altar SMP S2: Ancient Blade (kill progression), Dragonrend, Tidebreaker, Wither Symbiote, Omen.
Plus the original swords, axes, bow, maces, pickaxes, shield, Lumberjack Axe and Monarch Crown (netherite).

## Enchantments
Every weapon lists its enchantments in the lore. `/wepons enchants` opens a dialog with all enchantments used on the weapons
(needs Minecraft 1.21.6+); clicking one shows its limit and which weapons carry it. Permission `wepons.enchants` (default: everyone).

## Visual effects and sounds
`VisualFX` gives every weapon its own sweep arc (shape follows where you aim), swing / hit / critical sounds, hit particles,
ground impacts for heavy weapons, and ability animations. It only reads combat events, so damage and abilities are unchanged.
The vanilla sweep particle comes from the client/server itself and cannot be removed without a packet library.

## Level II
Only the Strength Sword and Weaving Sword have it. Kills 1 and 2 unlock the sword's ability, kills 3 and 4 finish the task
and give Level II: abilities recharge 20% faster and the effects are stronger. Amounts are in `config.yml`.

## Impact Sword streak
Impact Bubble unlocks after 2 kills in a row with the sword. Dying before it is unlocked resets the count.

## Kill unlocks
Ancient Blade, Strength Sword and Weaving Sword unlock abilities by player kills (2 for Strength and Weaving) (`kill-unlocks` in `config.yml`).

## Lumberjack Axe
Chops a whole natural tree in one swing. Player-placed logs are remembered and never chopped, and a log cluster
only counts as a tree if natural leaves touch it, so houses are safe. Model number 9126 (vanilla axe until added).

## Resource pack
`wepons.zip` holds every model: Relique weapons, Hoplite weapons, the Relique blades (9201-9203) and the
Altar SMP S2 models remapped to 9301-9305 (Ancient Blade, Dragonrend, Tidebreaker, Wither Symbiote, Omen).
Model numbers already match `config.yml`.

## Lore
- Header icons are vanilla item textures drawn inline with Minecraft's sprite text component (nether star, echo shard,
  clock, bone). They come from the client's own item atlas, so every player sees them with or without the pack.
  Needs a 1.21.9+ client and server (the pom uses paper-api 1.21.11).
- All lore text is small caps (vanilla Unicode letters, `WeaponRegistry.sc`).
