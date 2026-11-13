# Changelog (Forge 1.20.1 line)

## 1.4.0

- Changed: the log files moved into their own folder, `logs/fluidcompat/` (`fluidcompat.log`, `fluidcompat-previous.log`
  and, after a rollover, `fluidcompat-rolled.log`; same names, same behaviour). Files left directly in `logs/` by older
  versions are not touched.
- New: Immersive Engineering compat (10.2.0, Fluid Pump). IE's pump gave 1000 mB of water for free whenever the source
  block next to it had two source neighbours, and its area search skipped every fluid that can turn into a source, which is
  water, so it never drained a pond (a model of the rule made 74,000 buckets from nothing in 300 ponds). For finite fluids both
  now treat water as an ordinary fluid, so the pump's own area search drains the pond and the world pays for every bucket.
  Thin Flowing Fluids layers, which the pump ignored, are drained too: each thin block gives exactly the levels it holds
  (SIMULATE only reports, EXECUTE takes them out of the world through the normal intake, and the pump's tank receives what the
  world really gave, not what was promised). The cobblestone the pump put where it drained is skipped for finite fluids. IE has
  its own switches for that (server config `pump_placeCobble` and a per-pump toggle); this one is `noCobbleForFiniteFluids`
  and is on by default. All IE hooks are all or nothing (a guard checks every call they use), fall back to the pump's own code
  when anything throws, and switch themselves off for the session after 5 failures in a row. Config section
  `immersive_engineering` (`finitePump`, `noCobbleForFiniteFluids`, `drainThinFluids`). Needs a game restart.
- New: GregTech CEu Modern compat (7.5.3, electric Pump). The pump takes source blocks only (1000 mB each, then the block is
  set to air), so it walked past Flowing Fluids' thin layers and sat idle beside puddles that never move. When a pump cycle ran
  and its queue has nothing left, the thin fluid around the pump head is swept through the sweep service and the tank receives
  exactly the levels the world gave up (125 mB each); the pump pays the same energy as for a bucket. Source blocks keep
  GregTech's own, already finite, path. Config section `gtceu_pump` (`enabled`, `drainLeftovers`; reach is `sweep.reach`).
- New in the core: `core.thin.ThinFluidCells`, the thin-layer rule in one place for machines that walk cells themselves (one
  cell gives exactly the levels it holds, paid through the intake). Machines that cannot walk cells use the sweep service,
  which was already separate. Nothing in the public API changed.
- Log: the thin-water hooks of both modules say whether the refmap carries the vanilla methods they need.

## 1.3.1

- Fixed: the Mekanism Elements Seawater Pump stopped after one or two cycles in an ocean. It only runs while the cell
  below it is a full water source, and every cycle the world pays for its seawater out of that cell, leaving a thin
  layer. The pump now also accepts a thin water layer (`mekanism_elements_seawater_pump.drainThinWater`, default on).
  The world is still charged for every unit of seawater, so a dry cell makes the intake come back short and
  `shortfallBehavior` decides what the pump makes. Own mixin config and guard; a pump without it keeps working as in 1.3.0.
- Fixed: the Alex's Caves Drain did nothing while thin Flowing Fluids water stopped short of the cell above it, and
  went to sleep whenever that cell was dry. It now takes fluid from the nearest fluid cell within
  `alexscaves_drain.intakeReach` blocks (default 3, 0 = original) and, while switched on, keeps checking at a slow
  rate. Everything it takes is still poured out exactly, nothing more.
- New: optional Fluidlogged compat (2.0.1-beta.2, 1.20.1). Blocks listed in `fluidlogged.blocks` (block ids or
  `#tags`, the Alex's Caves Copper Valve by default, applied as soon as the file is saved) are flooded when the
  connected Flowing Fluids water next to them adds up to one block, thin layers included. Exactly 8 levels are
  taken out of the world first, all or nothing, then the block is waterlogged (or, without a waterlogged state, filled
  through Fluidlogged's own storage). A cell that already holds a fluid is never flooded again, so nothing is
  duplicated. Fluidlogged is also told the listed blocks can hold a fluid (`makeFluidloggable`) and optionally let fluid
  through (`makePermeable`). Listed blocks are found through a cheap palette check when a chunk loads and when one is
  placed; only a few are looked at per pass (`floodIntervalTicks`, `maxBlocksPerPass`).
- Log: every new guard says which part of the target it could not find when it refuses, and every module says "never
  got a verdict" when its mixin config was not loaded.

## 1.3.0

- New: Alex's Caves compat (2.0.2). The Drain moved fluid from above it to below it but counted every liquid cell it
  touched as one bucket and poured whole blocks, so with Flowing Fluids' thin layers it made water (a model of the
  original code gained water in 261 of 300 random ponds). Now, while the fluid is finite and Flowing Fluids drains
  it, the Drain plans where the fluid can go first, drains exactly that many levels through Flowing Fluids, and pours
  exactly what it got. Fluid stored inside waterlogged blocks (a Copper Valve above the Drain) is found through the
  free-standing water next to it. A Drain that has nothing to move or no room waits four times as long between looks.
  Anything else (lava with finite lava off, other fluids, Flowing Fluids disabled) keeps the original behaviour.
  Config section `alexscaves_drain` (`enabled`, `maxLevelsPerCycle`, `intervalTicks`, `fillSpread`, `worldEffects`).
- Fixed: the 1.2.0 jar listed only two of its four mixin configs. The leftover sweep and the Seawater Pump hook were
  never loaded, and the log blamed the Mekanism pump ("sweep is NOT allowed (the pump search is not what it
  expects)") although the guard never ran. All five configs are now in the jar manifest and the build.
- Log: a hook whose mixin config did not load now says so ("never got a verdict") instead of blaming the target mod,
  and the sweep guard writes which part of the Mekanism pump it could not find.
- Sweep guard: accepts a private call compiled as `invokespecial` and any Mekanism owner for the tank insert, so a
  harmless compiler or typing difference no longer switches the sweep off.

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
