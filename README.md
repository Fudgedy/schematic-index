<p align="center">
  <img src="assets/banner.png" alt="The Schematic Index" width="800">
</p>

<p align="center">
  <a href="https://modrinth.com/mod/the-schematic-index"><img src="https://img.shields.io/modrinth/dt/the-schematic-index?logo=modrinth&label=Downloads&color=2A7A5B&style=for-the-badge" alt="Modrinth downloads"></a>
  <a href="https://discord.gg/schematicindex"><img src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white&style=for-the-badge" alt="Discord"></a>
  <a href="https://modrinth.com/mod/the-schematic-index/versions"><img src="https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1%20%7C%2026.2-3C3C3C?logo=minecraft&style=for-the-badge" alt="Minecraft versions"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-lightgrey?style=for-the-badge" alt="MIT licence"></a>
</p>

<p align="center">
  <a href="https://modrinth.com/mod/the-schematic-index"><b>Download on Modrinth</b></a> ·
  <a href="https://discord.gg/schematicindex"><b>Join the Discord</b></a> ·
  <a href="#building-from-source"><b>Build from source</b></a>
</p>

---

The Schematic Index is a Fabric client mod that puts a community schematic catalogue inside Minecraft.
Browse builds in a post-like grid, inspect them in a 3D preview, and download them straight into
[Litematica](https://modrinth.com/mod/litematica) , all without ever exiting minecraft. It also turns images into
mapart schematics ( Flat + Staircased ), and gives everyone running the mod a small nametag badge with cosmetics to unlock.

<p align="center">
  <img src="assets/browse.png" alt="The browse tab: a grid of schematic cards with categories, tags, search and sorting" width="900">
</p>

## Features

### Catalogue
- Categories, tags, search and sorting (trending, newest, most downloaded, most liked, highest rated).
- Likes, star ratings, collections, a history tab and a following feed with a notification when a
  creator you follow posts something new.
- Every download lands in the schematics folder of the running client, or a folder you pick.
- Right-click a post for quick actions, or download a whole collection in one go.

### 3D preview
Orbit and zoom around a build, or use spectator mode to inspect it as if you were actually in spectator mode in minecraft. Cut away layers with the slider,
hide close blocks while orbiting, save a PNG of the view, and read the full material list before you
download.

<p align="center">
  <img src="assets/preview.png" alt="A post open in the 3D previewer with its stats and material list" width="900">
</p>

### Mapart
Pick or paste an image and get a Litematica schematic back: every map colour, classic and flat
staircasing, dithering, colour adjustments, editable block palettes with import and export, split
file saves for larger maps, per-map material lists, an in game corner overlay for placing the mapart correctly, and a temporary in-world load to check it before downloading.

<p align="center">
  <img src="assets/mapart.png" alt="The mapart tab with the palette editor, preview and material list" width="900">
</p>

### Nametag cosmetics
Everyone running the mod gets a small badge next to their name, visible to other users on the same
server. Colours, gradients, tags and effects are unlocked with Shards, the in-mod currency earned
through a daily streak, weekly quests and inviting friends.

<p align="center">
  <img src="assets/nametag.png" alt="A player with a coloured nametag and tag" width="440">
  <img src="assets/cosmetics.png" alt="The cosmetics tab: colour palette, gradient builder and presets" width="440">
</p>

<p align="center">
  <img src="assets/shards.png" alt="The Shards tab: daily streak, invite a friend and weekly quests" width="700">
</p>

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Drop [Litematica](https://modrinth.com/mod/litematica) and [MaLiLib](https://modrinth.com/mod/malilib)
   into your `mods` folder.
3. Download The Schematic Index from [Modrinth](https://modrinth.com/mod/the-schematic-index) and add it too.
4. Launch the game. The catalogue opens from Litematica's main menu (`M` by default) or with its own
   key under Options > Controls > Schematic Index.

<p align="center">
  <img src="assets/litematica-menu.png" alt="Litematica's main menu with The Schematic Index and Open Schematics Folder buttons" width="500">
</p>

The catalogue itself is served by a hosted backend that is not part of this repository.

## Supported versions

| Folder | Minecraft | Java | MaLiLib | Litematica |
|---|---|---|---|---|
| `26.2/` | 26.2 | 25 | 0.29.3 | 0.28.4 |
| `26.1/` | 26.1.2 | 25 | 0.28.11 | 0.27.12 |
| `1.21.11/` | 1.21.11 | 21 | 0.27.16 | 0.26.12 |

`26.2/` is the source of truth. The other two folders are ports of it that differ only where the
Minecraft API changed; a change lands in `26.2/` first and is carried across with the same edit.

## Building from source

Each version folder is a standalone Gradle project.

```sh
cd 26.2
./gradlew build
```

The jar is written to `build/libs/`. Gradle downloads a matching JDK through the toolchain resolver
if the one on your `PATH` is too old; to point at one explicitly, set `JAVA_HOME` first
(Java 25 for `26.2` and `26.1`, Java 21 for `1.21.11`).

To run a development client with the mod loaded:

```sh
./gradlew runClient
```

## Contributing

Bug reports and pull requests are more than welcome. For anything larger than a small fix, open an issue or say
hello in a support ticket in the [Discord](https://discord.gg/schematicindex) first, so that the work is not copied. Make your change in `26.2/` and, where the same code exists, apply it to `26.1/` and `1.21.11/` as well.

If you have found a security issue within the mod or server side, I kindly ask that you please report it privately through Discord rather than a public issue, use the same support ticket system.

## Licence

[MIT](LICENSE) © Fudgedy
