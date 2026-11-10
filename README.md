# Fluid Compat (Forge 1.20.1)

An unofficial intermediary compatibility mod that helps **Flowing Fluids** understand other mods better.
This is the Forge 1.20.1 line. It is a separate project from the NeoForge 1.21.1 line and has its own version numbers.

**Status of version 1.3.1:** the Seawater Pump keeps running on thin Flowing Fluids water, the Alex's Caves Drain
no longer starves when thin water stops short of it, and a new optional Fluidlogged module floods listed blocks
(the Copper Valve by default) from the water next to them. 1.3.0 ran in a game (Drain and Seawater Pump hooks reached,
all five mixin configs loaded).

What it contains:

- the Fluid Compat API and core (the Flowing Fluids drain backend, the Vanilla fallback backend, config, own log file);
- the leftover-fluid sweep (`api.sweep`): lets a machine that only sees full source blocks drain the thin, partially
  filled water Flowing Fluids leaves behind, and water that arrives later, under a work budget with its own guard;
- the Flowing Fluids bug-fix patches (negative removal amount, keep blocks that hold no fluid, fast fluid interaction
  checks, water wheel listeners where Flowing Fluids still has that bug), each guarded by a bytecode check;
- a start-up probe that writes a "GATE 0" verdict to `logs/fluidcompat.log`;
- machine compat: see the table below.

## Supported mods

| Mod | Tested against | What it does |
|---|---|---|
| Mekanism | 10.4.16.80 (1.20.1) | The Electric Pump removes the water source it pumps (water takes the pickup path Mekanism already uses for lava), and drains thin leftover water, including water that flows in later |
| Mekanism Elements | 3.0.1 (1.20.1) | The Seawater Pump drains the water source below it for the seawater it makes |
| Alex's Caves | 2.0.2 (1.20.1) | The Drain moves exactly the fluid it takes: it drains through Flowing Fluids and pours out only the levels it removed, instead of counting every thin layer as a full block. It also takes fluid from a few blocks around the cell above it, so thin water that stops short of it still gets drained |
| Fluidlogged | 2.0.1-beta.2 (1.20.1) | Optional. Floods the blocks you list (the Alex's Caves Copper Valve by default) from the Flowing Fluids water next to them, thin layers included, taking exactly that water out of the world |

## The sweep guard

Leftover sweeping is cheap by design (a few block reads, then one drain through Flowing Fluids' own capped collector),
and it can still be switched off by a guard: a cap on sweeps per tick, a cost limit, and an emergency stop when the
server lags while sweeps run. All limits are in `config/fluidcompat-compat.toml`, section `[sweep]`, and in the YACL
screen.

## Requirements

- Minecraft 1.20.1, Forge 47.x, Java 17
- Flowing Fluids (Forge 1.20.1, 1.0.6 or newer): required, never bundled
- YetAnotherConfigLib 3.6.0 or newer (optional): adds the settings screen (Mods > Fluid Compat > Config)

## Build (IntelliJ IDEA)

1. Put the Flowing Fluids Forge 1.20.1 jar and the YetAnotherConfigLib Forge 1.20.1 jar into `libs/` (keep their original file names).
2. Open this folder as a Gradle project. Set the Gradle JVM and the Project SDK to Java 17.
3. Run `./gradlew build`. The mod jar is `build/libs/fluidcompat-forge-1.20.1-<version>.jar`.
4. For the IDE run configurations use the Gradle task `runClient` (it starts with Flowing Fluids because of `modImplementation`).

## Settings

- `config/fluidcompat-common.toml`: finite water / lava, drain percent (same keys as the 1.21.1 line)
- `config/fluidcompat-compat.toml`: master switch, logging, Auto Balanced Compat, `flowing_fluid_patches`

## License

MIT, see `LICENSE.txt`. Credits are in `CREDITS.md`.
