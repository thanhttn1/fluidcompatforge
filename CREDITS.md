# Credits

- **Flowing Fluids** by Traben, GNU LGPL 3.0. Required dependency, never bundled. Fluid Compat only calls its public
  API and patches it with mixins; no Flowing Fluids code or assets are copied.
- **Alex's Caves** by Alexthe668 and Noonyeyz (its 1.20.1 `mods.toml` says GNU LGPL): one hook on the Drain. Nothing is
  copied; the hook only names a class and a method, and the Drain's sound message is sent by name.
- **Fluidlogged** by Leximon (MIT; the 1.20.1 build credits MoreThanHidden): optional. One hook adds the listed blocks to
  its "can hold a fluid" and "fluid passes through" answers, and its level extension is called by name to store water.
  Nothing is copied.
- **Mekanism Elements** (MIT): one hook on the Seawater Pump. Nothing is copied; the hook only names a class, a method and
  a call.
- **Mekanism** (MIT): one hook on the Electric Pump. Fluid Compat copies nothing from it; the hook only names a class,
  a method and a setting.
- **YetAnotherConfigLib** by isxander (LGPL-3.0-or-later): optional, only for the settings screen. Never bundled.
- **MultiLoader-Template** by Jared (CC0 1.0): the build scaffolding this project started from.
- **Minecraft Forge** (LGPL 2.1) and **ModDevGradle** by NeoForged: build and runtime platform.
- Fluid Compat itself is MIT licensed, by thanhtnt2.

Bug reports about Fluid Compat go to thanhtnt2, never to the authors of Flowing Fluids or of any other mod.
