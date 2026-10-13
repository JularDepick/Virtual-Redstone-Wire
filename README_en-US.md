<div align="center">

# Virtual Redstone Wire

[![Version](https://img.shields.io/badge/Version-1.1.0--alpha-red)](https://github.com/JularDepick/Virtual-Redstone-Wire/releases/tag/v1.1.0-alpha)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue)](./LICENSE)

[English]
| [[简体中文]](./README.md)

</div>

A Minecraft mod for long-distance redstone signal transmission that does not rely on physical redstone blocks, supporting one-to-one, one-to-many, and many-to-one connections.

---

![![](./intro/intro_cover.png)](./intro/intro_video.gif)

# Mod Usage

## Prerequisites

| Item | Requirement |
|:---:|:---:|
| Minecraft | 1.20 - 1.21.8 |
| Mod loader | Forge / Fabric |
| Prerequisite mod | Fabric API is required on the Fabric side |

> For the mapping between Minecraft game versions and mod loader versions, see [Supported Versions](#supported-versions)

## Mod Items

### Cable

- Item ID: `virtual_redstone_wire:virtual_cable`
- Crafting: 1 Redstone + 1 Iron Ingot
- Usage:
  - Right-click the block you want as the input to select it, then right-click the block you want as the output to create a one-way redstone link
  - Right-click the block currently selected as the input to deselect it
  - One input can be linked to several outputs, and one output can accept several inputs

### Cable Cutter

- Item ID: `virtual_redstone_wire:cable_cutter`
- Crafting: 2 Sticks + 1 Iron Ingot
- Usage: Right-click an input block to cut every link leaving it

### Cable Magnifier

- Item ID: `virtual_redstone_wire:cable_magnifier`
- Crafting: 1 Stick + 1 Glass Pane + 1 Redstone
- Usage:
  - While held, highlights every link around you: blue for the input, yellow for the output, red for the link path
  - Right-click a block to read its coordinates and its redstone signal strength
  - Sneak-right-click a block to open its link info panel

## Undo and Redo

- While holding any mod item in the main hand or the off hand, press `Ctrl+Z` to undo and `Ctrl+Y` to redo; both keybinds can be changed
- Operation history is kept in client memory only and is cleared when you leave the game
- Links removed in a batch by the Cable Cutter are undone or redone as a whole

## Operation History

While holding the Cable Magnifier, press `Ctrl+A` to open the operation history screen; the keybind can be changed

## Redstone Behavior

- Virtual cables carry a redstone signal strength from 0 to 15
- An input block reports the redstone signal at its own position
- Redstone changes at the input show up at the output in real time
- One-to-many broadcast and many-to-one merging are both supported, and the output side takes the highest incoming signal
- A signal always travels along a link in one direction
- The signal leaves the output block through the chosen face, so its direction is exact


# Mod Configuration

## Client

- Config file: `config/virtual_redstone_wire-client.toml`

| Option | Default | Range | Meaning |
|:---:|:---:|:---:|:---:|
| `enableChatFeedback` | `false` | `true` / `false` | Whether link operation results are reported in the chat |
| `undoHistorySize` | `20` | `10` - `100` | Maximum number of entries kept in the undo stack and in the redo stack |
| `undoRedoFeedback` | `true` | `true` / `false` | Whether undo and redo notifications are shown |

## Server

- Config file: `<world save>/serverconfig/virtual_redstone_wire-server.toml`

| Option | Default | Range | Meaning |
|:---:|:---:|:---:|:---:|
| `maxLinkDistance` | `256` | `1` - `1024` | Maximum distance allowed for a single link, in blocks |
| `magnifierRenderDistance` | `512` | `64` - `1024` | Maximum distance at which links are highlighted while you hold the Cable Magnifier, in blocks |
| `networkChangeLogSize` | `200` | `200` - `1000` | Capacity of the recent-change table used for incremental sync |
| `operationLogEnabled` | `true` | `true` / `false` | Whether every add or remove operation that was applied or rejected is recorded |
| `operationLogSplit` | `total` | `total` / `both` / `player` | How the operation log is split into files: one combined file, the combined file together with per-player files, or per-player files only |
| `operationLogFile` | `Virtual_Redstone_Wire-Operations.log` | Custom file name | File name of the operation log; with per-player files the player name is appended to the base name |
| `requestLogEnabled` | `false` | `true` / `false` | Whether every received request is recorded verbatim together with its final status code; debug level |
| `requestLogFile` | `Virtual_Redstone_Wire-Requests.log` | Custom file name | File name of the request log |


# Mod Data

## Link Data

| World dimension | Data file |
|:---:|:---:|
| Overworld | `<world save>/data/virtual_redstone_wire_network.dat` |
| Nether | `<world save>/DIM-1/data/virtual_redstone_wire_network.dat` |
| End | `<world save>/DIM1/data/virtual_redstone_wire_network.dat` |

## Log Data

- Operation log: `<world save>/Virtual_Redstone_Wire-Operations.log` and `<world save>/Virtual_Redstone_Wire-Operations_<player name>.log`
- Request log: `<world save>/Virtual_Redstone_Wire-Requests.log`


# Supported Versions

`mc-1.20.1-forge` and `mc-1.20.1-fabric` are the 1.20.1 baseline artifacts of the two sides; the other compatible versions are listed below:

## Forge

| Minecraft | Forge version | Mod file name suffix |
|:---:|:---:|:---:|
| `1.20` | `46.x` | `mc-1.20_1.20.1-forge` |
| **`1.20.1`** | **`47.x`** | **`mc-1.20.1-forge`** |
| `1.20.1` | `47.x` | `mc-1.20_1.20.1-forge` |
| `1.20.2` | `48.x` | `mc-1.20.2-forge` |
| `1.20.3` | `49.0.x` | `mc-1.20.3_1.20.4-forge` |
| `1.20.4` | `49.2.x` | `mc-1.20.3_1.20.4-forge` |
| `1.20.6` | `50.2.x` | `mc-1.20.6-forge` |
| `1.21` | `51.0.x` | `mc-1.21_1.21.1-forge` |
| `1.21.1` | `51.0.x` | `mc-1.21_1.21.1-forge` |
| `1.21.3` | `53.1.x` | `mc-1.21.3-forge` |
| `1.21.4` | `54.1.x` | `mc-1.21.4-forge` |
| `1.21.5` | `55.1.x` | `mc-1.21.5-forge` |
| `1.21.6` | `56.0.x` | `mc-1.21.6-forge` |
| `1.21.7` | `57.0.x` | `mc-1.21.7-forge` |
| `1.21.8` | `58.1.x` | `mc-1.21.8-forge` |

> Forge does not support Minecraft 1.20.5 and 1.21.2

## Fabric

| Minecraft | Fabric version | Fabric API version | Mod file name suffix |
|:---:|:---:|:---:|:---:|
| `1.20` | `0.16.x` | `0.83.0+1.20` | `mc-1.20_1.20.1-fabric` |
| **`1.20.1`** | **`0.16.x`** | **`0.92.12+1.20.1`** | **`mc-1.20.1-fabric`** |
| `1.20.1` | `0.16.x` | `0.83.0+1.20` | `mc-1.20_1.20.1-fabric` |
| `1.20.2` | `0.16.x` | `0.91.6+1.20.2` | `mc-1.20.2-fabric` |
| `1.20.3` | `0.16.x` | `0.91.1+1.20.3` | `mc-1.20.3_1.20.4-fabric` |
| `1.20.4` | `0.16.x` | `0.91.1+1.20.3` | `mc-1.20.3_1.20.4-fabric` |
| `1.20.5` | `0.16.x` | `0.97.8+1.20.5` | `mc-1.20.5_1.20.6-fabric` |
| `1.20.6` | `0.16.x` | `0.97.8+1.20.5` | `mc-1.20.5_1.20.6-fabric` |
| `1.21` | `0.16.x` | `0.101.2+1.21` | `mc-1.21_1.21.1-fabric` |
| `1.21.1` | `0.16.x` | `0.101.2+1.21` | `mc-1.21_1.21.1-fabric` |
| `1.21.2` | `0.16.x` | `0.106.1+1.21.2` | `mc-1.21.2_1.21.3-fabric` |
| `1.21.3` | `0.16.x` | `0.106.1+1.21.2` | `mc-1.21.2_1.21.3-fabric` |
| `1.21.4` | `0.16.x` | `0.107.0+1.21.4` | `mc-1.21.4-fabric` |
| `1.21.5` | `0.16.x` | `0.114.1+1.21.5` | `mc-1.21.5-fabric` |
| `1.21.6` | `0.16.x` | `0.128.2+1.21.6` | `mc-1.21.6-fabric` |
| `1.21.7` | `0.16.x` | `0.128.2+1.21.7` | `mc-1.21.7_1.21.8-fabric` |
| `1.21.8` | `0.16.x` | `0.128.2+1.21.7` | `mc-1.21.7_1.21.8-fabric` |

> This mod does not plan to support the NeoForge mod loader for now; you can load this mod on NeoForge through a bridge mod. Or you can [open a new issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) to request support.

> This mod does not plan to support 26.x game versions for now. But you can [open a new issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) to request support.


# Language Support

The languages currently supported by the mod are:
- Simplified Chinese
- English

> The mod language follows the game language automatically.


# Development Build Environment

## Environment Versions

| Baseline component | Version |
|:---:|:---:|
| Java | `JDK17` |
| Gradle | `8.14.3` |
| Minecraft | `1.20.1` |
| Forge | `47.4.23` |
| Fabric | `0.16.14` |
| Fabric API | `0.92.12+1.20.1` |

The environments of the compatible versions are listed in [versions/versions.json](./versions/versions.json) and [versions-fabric/versions/versions.json](./versions-fabric/versions/versions.json)

## Build Scripts

```bash
# List every Forge-side adaptation target together with the environment versions it needs
python versions/build_version.py list
# Generate the working copy of that Forge target
python versions/build_version.py prework mc-1.20.2-forge
# Generate and build that Forge target; the artifact stays in that target's working copy
python versions/build_version.py build mc-1.20.2-forge
# Build every matching Forge-side target in sequence by Java major version
python versions/build_version.py jdk-build 21
# Check the built mod jars under each Forge-side target directory and collect them all into the release artifact directory
python versions/build_version.py copy

# Build the Fabric baseline artifact; it lands in that project's own build output directory
cd versions-fabric && gradlew.bat build
# List every Fabric-side adaptation target together with the environment versions it needs
python versions-fabric/versions/build_version.py list
# Generate the working copy of that Fabric target
python versions-fabric/versions/build_version.py prework mc-1.20.2-fabric
# Generate and build that Fabric target; the artifact stays in that target's working copy
python versions-fabric/versions/build_version.py build mc-1.20.2-fabric
# Build every matching Fabric-side target in sequence by Java major version
python versions-fabric/versions/build_version.py jdk-build 21
# Check the built mod jars under each Fabric-side target directory and collect them all into the release artifact directory
python versions-fabric/versions/build_version.py copy
```

Artifact naming rules:
- File name: `VirtualRedstoneWire-v<mod version>-mc-<start MC version>_<end MC version>-<mod loader name>.jar`
- When the start and the end version are identical, they are merged into a single segment
- When one of the start and the end version is anonymous, that position is left empty and the underscore is kept
- The start and the end version may not both be anonymous

For other development information, see the [developer documentation](./docs/developer/README.md)


# Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md)


# License

This project is open source under the [Apache-2.0 License](./LICENSE).


# Copyright

Copyright (c) 2026 JularDepick

See the [COPYRIGHT file](./COPYRIGHT)


# Looking for Collaboration

- A good item texture asset plan is still missing
- Support for more versions is still missing; proposals and PRs are welcome
- Support for more languages is still missing; proposals and PRs are welcome


# Related Links

- CurseForge mod page: https://www.curseforge.com/minecraft/mc-mods/virtual-redstone-wire
- Modrinth mod page: https://modrinth.com/mod/virtual-redstone-wire
