# Changelog (Forge 1.20.1 line)

Versioning: `1.X.0` for a big update (new mod support), `1.X.Y` for a small update or fix.

## 1.4.1

* **Fixed: the Immersive Engineering Fluid Pump kept putting cobblestone where it emptied a block.** IE 1.20.1 has no
  setting to turn that off. The hook is now written exactly as the game's own code names the call (it no longer
  depends on the game's name mapping), and it has its own, stricter code check: the pump must still simulate the drain,
  check the tank, drain for real, place the cobblestone and fill the tank, in that order, or the hook switches itself
  off with a log line. It skips the cobblestone when the block held a finite fluid a moment ago, and also when the pump
  is working on a finite fluid at all (its work list can be older than the water, which keeps moving).
  * Switch: `immersive_engineering.noCobbleForFiniteFluids` (default true).
* **Fixed: water stuck at the end of a pipe.** When a pump sat in a pond with a pipe attached and the pond dropped to
  the pipe, the water inside the waterlogged pipe stayed there until the pipe was broken. Fluid Compat now has the
  submerged pipes module: pipes (Immersive Engineering, GregTech and Mekanism fluid pipes, plus anything you add) are
  registered with Flowing Fluids as blocks that never push water out of their cell, Flowing Fluids' waterlog flow mode is
  set to IGNORE in memory (nothing is written to its config file), and the water left inside a waterlogged pipe is
  removed once none of its non-pipe neighbours holds water. Water is only removed there, never created. Only pipes
  near players are checked, a few chunks per tick, and the work shrinks while the server lags.
  * Section `flowing_fluids_pipes`: `enabled`, `registerAsNonDisplacers`, `waterlogFlowMode`, `cleanOrphanPipeWater`,
    `cleanupIntervalTicks`, `cleanupChunkRadius`, `blockClasses`, `extraBlocks`; also in the "Mod Compat List" tab.
    Datapacks can add blocks to the tag `fluidcompat:fluid_passable_blocks`.
* The thin water hook of the Mekanism Elements Seawater Pump uses the same exact naming, for the same reason.
* The log no longer says anything about the game's name mapping; "hook allowed" means the code check passed and
  "for the first time" means the game really used the hook.

## 1.4.0

* **Changed: the log files moved to `logs/fluidcompat/`** (`fluidcompat.log`, `fluidcompat-previous.log`, and
  `fluidcompat-rolled.log` after a rollover). Same names, same behaviour. Files that older versions left directly in
  `logs/` are not touched.
* **New: Immersive Engineering support (Fluid Pump).**
  * Its "two source blocks next to the pump give 1000 mB for free" rule is switched off for finite fluids, and its area
    search no longer skips water, so the pump drains the pond with its own search and the world pays for every bucket.
  * Thin Flowing Fluids layers, which the pump ignored, are drained too: each thin block gives exactly the levels it
    holds, and the pump's tank receives what the world really gave.
  * All hooks work together: the code check looks at every call they use, and if IE changes any of them every hook stays
    off. A hook that fails falls back to the pump's own code and switches itself off after 5 failures in a row.
  * Switches in the `immersive_engineering` section: `finitePump`, `noCobbleForFiniteFluids`, `drainThinFluids`.
* **New: GregTech CEu Modern support (Pump).** The pump only takes source blocks and walked past thin layers. Once its
  source blocks are gone, the thin water around the pump head is drained and the tank receives exactly what the world
  gave up. The pump pays the same energy as for a bucket.
  * Switches in the `gtceu_pump` section: `enabled`, `drainLeftovers` (reach is `sweep.reach`).
* The thin layer rule for machines that walk blocks themselves is now one shared class.

## 1.3.1

* **Fixed: the Mekanism Elements Seawater Pump stopped after a cycle or two, even in an ocean.** It only runs while the
  block below it is a full water source, and every cycle the world pays for the seawater out of that block, which then
  becomes a thin layer. The pump now also accepts a thin water layer. The world still pays for every unit.
  * Switch: `mekanism_elements_seawater_pump.drainThinWater` (default true).
* **Fixed: the Alex's Caves Drain starved when thin water stopped short of it.** It takes fluid from the nearest fluid
  within `alexscaves_drain.intakeReach` blocks (default 3, 0 = original) and keeps checking slowly while it is on.
* **New: Fluidlogged support (optional).** Blocks you list in `fluidlogged.blocks` (ids or `#tags`, the Alex's Caves
  Copper Valve by default, applied as soon as you save the file) are flooded when the connected Flowing Fluids water
  next to them adds up to one block, thin layers included. Exactly that water is taken out of the world first, so
  nothing is duplicated. Switches: `enabled`, `makeFluidloggable`, `makePermeable`, `floodIntervalTicks`, `maxBlocksPerPass`.
* Every code check now says which part it could not find when it refuses, and a hook whose config never loaded is
  reported as such.

## 1.3.0

* **New: Alex's Caves support (Drain).** The original counted every liquid block it touched as one bucket and poured
  whole blocks, so with thin layers it made water. Now it drains exactly the levels the cells below can hold through
  Flowing Fluids and pours exactly what it got; the Copper Valve above it still works. Anything else (lava with finite
  lava off, other fluids) keeps the original behaviour.
  * Section `alexscaves_drain`: `enabled`, `maxLevelsPerCycle`, `intervalTicks`, `fillSpread`, `worldEffects`.
* **Fixed (1.2.0): the leftover sweep and the Seawater Pump hook were never loaded** because two of the four mixin
  configs were missing from the jar manifest. The log blamed the Mekanism pump. All configs are loaded now.

## 1.2.0

- New: Mekanism Elements compat. The Seawater Pump (Mekanism Elements 3.0.1) now drains the water source below it for
  the seawater it makes, instead of making seawater out of nothing. Config section `mekanism_elements_seawater_pump`
  (`enabled`, `drainPercent`, `minTicksBetweenIntakes`, `shortfallBehavior`).
- New: leftover-fluid sweep (`api.sweep`, `FluidCompatApi.sweeps()`). Mekanism's Electric Pump only looks at full source
  blocks, and Flowing Fluids spreads a draining pond into thin layers that stop moving, so the pump used to stop with
  about two levels of water left and never resumed. When the pump finds no source it now drains thin water next to it,
  searches outward within `sweep.reach` over several cycles, and keeps draining water that flows in later. The pump only
  receives what the world gave up, never a full bucket for a few levels, and its tank space is checked first.
- New: the sweep guard. A cap on sweeps per tick (shrunk by Auto Balanced Compat), a cost limit
  (`sweep.maxMicrosPerTick`), and an emergency stop when the server tick time is above `sweep.emergencyMspt` while
  sweeps run. A stop lasts `sweep.tripCooldownSeconds`; after `sweep.maxTripsPerSession` stops sweeping stays off until
  restart. Idle pumps back off (`sweep.idleBackoffMaxCycles`). State lives in the pump itself, so nothing can pile up.
- New config: `mekanism_electric_pump.drainLeftovers`, the whole `[sweep]` section, YACL groups for all of them.
- Filter Upgrade is respected: a sweep yields the same Heavy Water share per level that a full source would.

## 1.1.1

- Mekanism compat rebuilt from scratch. The 1.1.0 hook never applied: Mixin rejected its `@Redirect` handler ("invalid
  signature", the first parameter must be the exact owner type), so the pump kept duplicating water while the log said
  the hook was active. The new hook redirects the read of `Fluids.WATER` in the pump's water-or-not comparison, uses
  only vanilla types, and makes water take the pickup path Mekanism already uses for lava.
- The Mekanism log lines no longer overstate: "allowed" means only that the guard accepted the pump code; the line
  "hook reached for the first time" appears when a pump really runs through the hook. The start-up log also checks that
  the refmap carries the SRG id of `Fluids.WATER`.
- Fast interaction patch: the guard now accepts Forge's `LiquidBlock.getFluid()` call, which the real game showed in
  the `observed:` log line (the 1.0.0 guard only knew a direct field read and switched the patch off).
- New optional settings screen with YetAnotherConfigLib (Mods > Fluid Compat > Config): Core, Mod Compat List, Logging.
- Build warnings removed: `FMLJavaModLoadingContext`/`ModLoadingContext` are no longer used statically (the mod
  constructor takes the context), `ResourceLocation.fromNamespaceAndPath` replaces the deprecated constructor, and the
  "public target" notes of the Flowing Fluids mixins are suppressed with the processor's own token.
- `CompatMixinState` now keeps "guard allowed" and "hook reached" apart.


## 1.1.0

New mod support: Mekanism.

- Mekanism Electric Pump no longer duplicates water. Mekanism 10.4.x only removes a water source from the world when
  its own `general.pumpWaterSources` setting is on, and that setting is off by default. The hook answers "true" for that
  one read while Fluid Compat keeps water finite, so Mekanism's own pickup code removes the block. A bytecode guard
  switches the hook off, with a log line, if the pump code ever changes. Lava and other fluids are untouched: Mekanism
  already removes those sources itself.
- New config section `mekanism_electric_pump` in `fluidcompat-compat.toml` (`enabled`, `water`).
- When the fast interaction patch is skipped, the log now lists the calls found in the LiquidBlock methods, so the
  reason can be read from the log alone.
- Note: with Flowing Fluids 1.0.8 the water wheel listener patch is correctly skipped, the bug is fixed upstream.

## 1.0.0

First build of the 1.20.1 line. A skeleton: no machine compat yet.

- Ported the API (`api`, `api.event`, `api.compat`), the core service, the Flowing Fluids and Vanilla backends, the
  config files, the log file and Auto Balanced Compat from the NeoForge 1.21.1 line (2.2.3). Public names and
  signatures are the same. Not ported yet: saved per-machine data (Forge 1.20.1 has no attachment system) and the
  config screens.
- The four Flowing Fluids patches, adapted to Forge 1.20.1: the bytecode guards know both the SRG names a real game
  uses and the Mojang names of the IDE; the LiquidBlock patch uses a refmap.
- New start-up probe module (`flowing_fluids_probe`): reports the naming mode, the refmap, and whether Flowing
  Fluids offers every method and field the core calls.

### Platform differences from the NeoForge 1.21.1 line

- `FluidIntakeEvent.Post.getResult()` is `getIntakeResult()` here: Forge's `Event` already declares `getResult()` with
  another return type. Everything else in the public API keeps its name and signature.

