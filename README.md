# Wepons

Paper 1.21.x plugin (Java 21). Converted from the customswords Skript.

## Build
    mvn clean package
Jar: `target/Wepons-1.0.0.jar` -> put in `plugins/`. Remove the Skript file and AltarSMP is not needed.

## Commands
- `/wepons`  - one GUI with every weapon, no holder names. Click to take one. (op by default: `wepons.use`)
- `/holder`  - every weapon with its holders, green = online, gray = offline. (everyone by default: `wepons.holder`)

## Menu tabs
Top row: slot 1 **Crazy Mythicals**, middle slot **Relique Weapons** (all the weapons of this plugin that used to be under Kings SMP),
slot 9 **Kings SMP** (the items of the KingsSMP plugin, read live from `KingsSMP.jar`, soft-depend). Black glass only in the second row,
weapons below it (36 slots), no next/previous page. Without KingsSMP installed the Kings SMP tab is empty.
Tab positions and icons: `gui.tab-slots` and `gui.icons` in `config.yml`. In `/holder` the Kings SMP tab lists online carriers only.

Holders are saved in `plugins/Wepons/holders.yml`, so offline players still show.

## Keybinds
- **F** - Ability 1
- **Shift + F** - Ability 2 (a weapon with one ability fires it on F or Shift + F)
- Monarch Crown: F with both hands empty.

## Weapons
Relique blades: Fortune Blade, Flame Blade, Bloom Blade.
Altar SMP S2: Ancient Blade (kill progression), Dragonrend, Tidebreaker, Wither Symbiote, Omen.
Plus the original swords, axes, bow, maces, pickaxes, shield, Lumberjack Axe and Monarch Crown (netherite).

## Enchantment editor
`/wepons enchants` (permission `wepons.enchants`, default op) opens a dialog with one button per enchantment the weapon in your
hand can carry. Click one to open a slider (0 removes it, up to the limit) and press Apply. Limits are in `config.yml` under
`enchant-editor.limits` (default: vanilla max). Needs Minecraft 1.21.6+.

## Enchantments (old note)
Every weapon lists its enchantments in the lore. `/wepons enchants` opens a dialog with all enchantments used on the weapons
(needs Minecraft 1.21.6+); clicking one shows its limit and which weapons carry it. Permission `wepons.enchants` (default: everyone).

## Model animations
Full-sweep weapons swing a ghost copy of their own model; abilities add ice spikes (Glacier Blade), bone fangs (Reaper Scythe),
flying debris and orbiting blades (Berserker Blade), a gravity orbit (Graviton Axe), a lightning column (Thunder Blade) and a flying
model (Void Fang). All are short-lived display entities. Turn them off with `visuals.model-animations: false`;
lower the sweep particles with `visuals.sweep-density`. Only some weapons have sweeps: full (9), light (13), none (the rest).

## Model effects (update)
Ghost weapon models, dash afterimages, orbiting blades and the flying model are hidden from the player who caused them, so
nothing appears in your own face. Impact Bubble is now a red glass bubble (two spinning cubes) that follows you for its
duration. Void Pull draws a rope of linked segments to the pulled player. Dashes leave weapon afterimages and speed streaks.
Ice spikes and bone fangs are tapered stacks; Berserker Blade adds red debris and a blood ring on the ground.

## Signature set pieces
Every weapon has its own set piece built from display entities (`ModelKits.java` + `DisplayFX.java`): a spider web drawn on the ground
(Spider Blade, Widow's Axe), rotating shield plates (Guardian Blade), a tornado (Gale Blade), a rift in the air (Rift Blade, Phantom Blade),
a green helix with embers (Mender's Blade), falling food / gold / leaves / petals (Feast, Gilded, Timber, Blossom), glowing ground
fissures (Quake and Forge pickaxes), jagged sky bolts (Thunder Blade, Stormbreaker), wings (Sky Blade), a meteor (Comet Mace), a falling
anvil (Doom Hammer), a floating crown with rays (Monarch Crown), sonar pulses and sculk pillars (Ancient Blade), a block serpent
(Dragonrend, Tidebreaker), a thrown boomerang scythe (Reaper Scythe), black tendrils (Wither Symbiote), raining weapons (Omen), a
singularity (Void Fang, Graviton Axe) and a lotus bloom (Blossom Blade). They play when an ability is used, and a critical hit plays the
set piece too (once per 5s, `visuals.crit-signature`). The sweep arc only shows when a hit lands. A small cube burst on hit is only for the weapons listed in `visuals.hit-cubes-weapons` (default: Berserker Blade and Impact Sword).
`visuals.model-animations: false` switches all model effects off.

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

## Lore style
Crazy Mythicals lore is rebuilt at runtime in the Relique layout ("(Mythic)", a passive block and an ability block, small caps, wrapped
text) from the original lines in `CrazyLore.java`. Kings SMP items can get the same look (small caps, bold name, soft grey text) when
`kings-smp.restyle-lore` is true in `config.yml`. Default is false: Kings SMP items keep the exact lore KingsSMP gives them.

## Resource pack
`wepons-kings-pack.zip` = wepons-resourcepack + Kings-Weapons-Pack merged (namespaces kings, wepons, cw, cm, altar, pvpqt, minecraft).

`wepons-kings-pack.zip`: the Kings tooltip frames (assets/kings/textures/gui/sprites/tooltip/*_frame.png) are now animated 12-frame strips (a highlight runs around the border, 2 ticks per frame). The original static frames are in Kings-Weapons-Pack.zip.
