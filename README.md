# WebDisplays (Theatrical Fork)

## Download

# Join the Discord to get the mod

### [**JAMSM — Just a Minecraft Stage Mod**](https://discord.gg/7hHhfJQDYz)

**Releases are distributed on Discord — not on CurseForge or Modrinth.**

👉 **[https://discord.gg/7hHhfJQDYz](https://discord.gg/7hHhfJQDYz)**

---

**WebDisplays for scene modpacks** — a maintained fork built for large-scale stage builds with [**Theatrical**](https://www.curseforge.com/minecraft/mc-mods/theatrical), [**Theatrical: Extra Lights**](https://modrinth.com/mod/theatrical-extra-lights), and [**Stage Equipment & Furnitures (S.E.F)**](https://www.curseforge.com/minecraft/mc-mods/stage-equipment-furnitures).

Place in-world browser screens on walls, LED panels, and set pieces. Link multiple blocks into one display, drive them from a single URL, and integrate them into complex scenery without treating every panel as a separate browser.

> **This is not the original WebDisplays mod.**  
> Original mod by [montoyo](https://www.curseforge.com/minecraft/mc-mods/webdisplays), later maintained by CinemaMod Group.  
> This fork is based on **[J-Diablo](https://github.com/J8-Diablo)'s [1.20.1 fork](https://github.com/J8-Diablo/webdisplays)**, which this project continued and extended for theatrical scene modpacks. Documentation and releases are in **English**.

---

## Recommended companion mods

| Mod | Link | Role |
|-----|------|------|
| **Theatrical** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/theatrical) | Stage lighting, moving heads, show control |
| **Theatrical: Extra Lights** | [Modrinth](https://modrinth.com/mod/theatrical-extra-lights) | Additional fixtures (LED panels, lasers, etc.) |
| **Stage Equipment & Furnitures (S.E.F)** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stage-equipment-furnitures) | Stage furniture, equipment, set dressing |

This WebDisplays fork is designed to work **inside modpacks built around these mods** — video walls on stage sets, decor screens, and production scenery.

---

## Example scenes

### Full stage setup

A complete show scene using linked screens, lighting, and set dressing:

<img width="1324" height="720" alt="Complete theatrical scene example" src="https://github.com/user-attachments/assets/404d1808-0a38-4385-99dd-a392f7bc55c2" />

### Screens embedded in walls & decor

Screens integrated into walls, props, and multi-block surfaces:

<img width="1102" height="616" alt="Screen integration in walls and decor" src="https://github.com/user-attachments/assets/4b13aa74-32c5-42a3-84ea-85a0e961528e" />

---

## Credits & lineage

| Role | Author / project |
|------|------------------|
| Original mod | [montoyo](https://www.curseforge.com/minecraft/mc-mods/webdisplays) |
| 1.20.1 multiloader base | [J-Diablo / J8-Diablo](https://github.com/J8-Diablo) — [webdisplays fork](https://github.com/J8-Diablo/webdisplays) (from CinemaMod) |
| **This fork** | Maintained for **Theatrical / S.E.F** scene modpacks |

If you want the vanilla, general-purpose mod, use the **[official WebDisplays on CurseForge](https://www.curseforge.com/minecraft/mc-mods/webdisplays)** — not this repository.

---

## Requirements

- **Minecraft** 1.20.1  
- **Forge** 47.3.0+ (primary target for this fork)  
- **[MCEF](https://modrinth.com/mod/mcef)** (Minecraft Chromium Embedded Framework) — required for any WebDisplays build  

Fabric module exists in the repo but Forge is the build validated for theatrical modpacks.

---

## Install

### Players (recommended)

1. Join **[JAMSM Discord](https://discord.gg/7hHhfJQDYz)** and download the latest release.  
2. Install **MCEF** for 1.20.1 Forge.  
3. Drop both JARs into your `mods` folder.  
4. Launch with **Java 17**.

### Build from source (developers)

```bash
# JDK 17 required
./gradlew :forge:build
```

Output: `forge/build/reobfJar/output.jar`

---

## Why this fork exists

Standard WebDisplays works well for small setups, but **stage and show modpacks** need:

- **Multi-block video walls** (many panels, one browser, one URL)
- **Odd-shaped panels** (half blocks, triangles) for tight set geometry
- **Stable audio** when breaking or rebuilding parts of a wall
- **Predictable linking** (one “main” panel controls URL, volume, resolution)
- **Performance** when dozens of screens exist in a loaded scene

This fork focuses on those workflows alongside **Theatrical**, **Extra Lights**, and **S.E.F** production builds.

---

## What's changed in this fork

### Multi-screen linking (major rework)

- **Single shared browser** per linked group (`LinkedScreenGroup.ensureGroupBrowser()`)
- **Origin / slave model**: one main panel drives URL, volume, and resolution for the whole group
- **Server-side sync**: URL, volume, and resolution propagate to all linked panels automatically
- **Auto-promotion**: if the main panel is broken, another panel becomes origin — the wall keeps working
- **Immediate audio cutoff** when a panel is removed (no ghost sound from released browsers)
- **Smarter client updates**:
  - Group rebuild only when structure changes (dirty flag), not a full rebuild every tick
  - Browser sync only when refs are out of date
  - Removed legacy 30-second browser cleanup polling
- **Volume optimization**: cached browser volume — no JavaScript reinjection every tick when nothing changed
- **Redesigned URL / link GUI**: role labels (Standalone / Origin / Slave), help text, one-click **Auto** link ID
- **In-world indicators**: green corner badge = origin, gray = linked slave

### Shaped screen blocks

- **Half** and **triangle** screen blocks with correct block models (not stretched full squares)
- Custom geometry only on the front face, with proper **facing** on placement
- Inventory models for half/triangle items

### Compatibility & stability

- **MCEF 2.1.6 runtime fix**: fallback when `MCEFClient.addAudioHandler()` is missing on published JARs
- Safer browser release on block break (`releaseBrowser()` instead of raw `browser.close()` on shared instances)
- Improved tracking cleanup when screens are turned off client-side

### Audio

- OS playback path via `BrowserVolumeManager` (HTML5 / Web Audio volume injection)
- Distance-based auto-volume still supported; linked groups use the **origin panel's** settings

---

## Multi-screen quick guide

1. Open the **URL menu** on the first panel of your wall.  
2. Click **Auto** to generate a link ID, then **Sync** to enable linking → **OK**.  
3. On each additional panel: same link ID + **Sync** (they become slaves).  
4. Set **URL, volume, and resolution** only on the **green-badge origin** panel.  
5. Breaking the origin promotes another panel automatically.

**Link modes**

| Mode | Behavior |
|------|----------|
| **Joined** | Panels tile into one continuous surface |
| **Spaced** | Panels keep gaps; layout respects block spacing |

---

## Project structure

```
webdisplays-1.20.1/
├── common/     Shared game logic (screens, linking, rendering)
├── forge/      Forge 1.20.1 build (recommended for modpacks)
└── fabric/     Fabric module (present; less tested for theatrical packs)
```

---

## Links

| Resource | URL |
|----------|-----|
| **Download (Discord)** | **[https://discord.gg/7hHhfJQDYz](https://discord.gg/7hHhfJQDYz)** |
| Theatrical | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/theatrical) |
| Theatrical: Extra Lights | [Modrinth](https://modrinth.com/mod/theatrical-extra-lights) |
| S.E.F | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stage-equipment-furnitures) |
| J-Diablo (upstream base) | [GitHub profile](https://github.com/J8-Diablo) · [webdisplays fork](https://github.com/J8-Diablo/webdisplays) |
| Original WebDisplays | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/webdisplays) |
| MCEF | [Modrinth](https://modrinth.com/mod/mcef) |
| Legacy wiki (outdated) | [montoyo wiki](https://montoyo.net/wdwiki/index.php?title=Main_Page) |

---

## License

MIT — same as upstream WebDisplays. See upstream repositories for full author credits.
