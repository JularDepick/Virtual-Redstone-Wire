<div align="center">

# Virtual Redstone Wire

[![Version](https://img.shields.io/badge/Version-0.5.3-red)](https://github.com/JularDepick/Virtual-Redstone-Wire/releases)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-MIT-yellow)](./LICENSE)

[简体中文](./README.md)
| [English]

</div>

A Minecraft mod for long-distance redstone signal transmission that does not rely on physical redstone blocks, supporting one-to-one, one-to-many, and many-to-one connections.

---

# Usage Guide

This mod heavily depends on Forge, but has no prerequisite mod dependencies and is not tied to any other mod or modpack.

Currently supports Minecraft 1.20.1 only. For compatibility with other versions, please [submit an Issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) or [contribute](#contributing).

Installation: put the downloaded mod jar into your `.minecraft/mods` folder (Forge 1.20.1 required).

| Item | Crafting | Usage | Notes |
|:---:|:---:|:---:|:---:|
| Virtual Cable | Redstone + Iron Ingot | Right-click an input block, then an output block, to create a one-way redstone link | Signal strength propagates along the link; right-click the same block again to cancel; one input can feed multiple outputs, one output can take multiple inputs (highest signal wins) |
| Cable Cutter | Iron Ingot + Stick x2 | Right-click an input block to cut all links originating from it | Only affects links where this block is the input (source) |
| Cable Magnifier | Redstone + Glass Pane + Stick | While held, highlights all links (blue is input, yellow is output, red is link path); sneak-right-click a block to inspect its links; right-click a block to show its redstone signal strength and position above the hotbar | Link highlighting & signal query |

## Redstone Behavior

- Virtual cables transmit redstone signal strength (0-15)
- The input block's redstone power is queried using the standard redstone API
- Signal propagates along the link to the output, queried through the in-memory network graph (zero space occupation)
- Input changes are reflected at the output in real-time
- Supports one-to-many broadcast and many-to-one merging (max signal)
- Links are directional: the signal exits from the clicked face of the output block (face-accurate); a redstone lamp placed on the output block itself lights up
- Works when the source is placed after the link: placing/removing a signal source or flipping a lever refreshes the link signal in real-time
- Chat feedback is disabled by default; can be enabled in-game from the Mods menu or in the config file
- Link data lookups are index-accelerated: creating/removing links stays responsive even with many links

## Undo & Redo

Undo/redo lets you revert or replay your most recent successful link operations:

- While holding a Virtual Cable, Cable Cutter, or Cable Magnifier (main or off hand), press `Ctrl+Z` to undo and `Ctrl+Y` to redo (keybinds can be changed in the Controls settings)
- Undo cancels the most recent successful link operation: a created link is removed, a removed link is restored; redo re-executes the most recently undone operation
- Press `Ctrl+Z` repeatedly to step back through multiple operations; performing a new operation after undoing clears the redo history
- Operation history is client-side only: the undo and redo stacks share a size limit (default 20, adjustable 10-100 in the Mods menu client config), cleared on game exit
- When there is nothing to undo/redo or an operation is rejected, a chat message shows the reason (controlled by the client "Undo/Redo feedback" option, enabled by default; after an empty-stack prompt, further presses are ignored for 3 seconds)
- Links removed in batch by the Cable Cutter can be undone as a whole (one undo restores all of them)

## Operation History

While holding the Cable Magnifier, press `Ctrl+A` to open the operation history screen and review the operations you can currently undo or redo:

- Shows undoable and redoable operations in two columns (operation type and endpoint coordinates); scroll with the mouse wheel when the list overflows
- Press `Ctrl+Z` / `Ctrl+Y` inside the screen to undo/redo directly; the page refreshes in real time and chat feedback is suppressed while it is open
- Close with the button at the panel's top-right corner, `ESC`, or `Ctrl+A` again (keybinds can be changed in the Controls settings)

## Usage Tip

- Hold the Cable Magnifier in your left (off) hand while using the Virtual Cable or Cable Cutter in your right (main) hand: the magnifier keeps all links highlighted (blue input, yellow output, red path), so you can see the link layout while building or removing links

# Runtime Files

## Config Files

- Client config: `config/virtual_redstone_wire-client.toml` (in the game directory's config folder; chat feedback, undo/redo history limit and feedback toggle, adjustable in-game from the Mods menu)
- Server config: `<world folder>/serverconfig/virtual_redstone_wire-server.toml`
  - Singleplayer/LAN: `saves/<save name>/serverconfig/`
  - Dedicated server: `<server world folder>/serverconfig/` (e.g. `world/serverconfig/`)
  - Contents: max link distance / magnifier render distance / sync change table size / operation & request logs; adjustable in-game from the Mods menu (owner-only)

## Data Files

- Link data is stored per-dimension (auto-saved, with a `.dat_old` backup kept in the same folder):
  - Overworld: `<world folder>/data/virtual_redstone_wire_network.dat`
  - Nether: `<world folder>/DIM-1/data/virtual_redstone_wire_network.dat`
  - End: `<world folder>/DIM1/data/virtual_redstone_wire_network.dat`
  - Other dimensions: `<world folder>/<dimension subfolder>/data/virtual_redstone_wire_network.dat`

## Log Files (in the world folder root, output-only)

- Operation log: `<world folder>/Virtual_Redstone_Wire-Operations.log` (add/del execution and rejection; with player/both split mode a `Virtual_Redstone_Wire-Operations_<player>.log` is also written)
- Request log: `<world folder>/Virtual_Redstone_Wire-Requests.log` (debug level, raw requests with final status codes, disabled by default)

# Tech Stack

| Component | Version |
|:---:|:---:|
| Minecraft | 1.20.1 |
| Forge | 47.4.22 |
| JDK | 17 |
| Gradle | 8.14.3 |
| ForgeGradle | 6.x |
| Mapping | official |
| Mod ID | virtual_redstone_wire |

# Source Directory Structure

```
src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java          # Main mod class
├── ClientSetup.java                  # Client initialization
├── ServerEventHandler.java           # Server events (world load/save/unload/block update)
├── RedstoneDiagnostics.java          # Redstone signal diagnostics (debug only)
├── client/
│   ├── ClientCableCache.java         # Client-side link cache (versioned, read-only, indexed)
│   ├── CableClientQueue.java         # Operation queue (single in-flight)
│   ├── CableClientEvents.java        # Full sync on connect/dimension change
│   ├── CableUndoRedoManager.java     # Undo/redo (dual stacks, keybinds, history screen)
│   ├── CableActionBarHud.java        # Signal popup above the hotbar
│   ├── gui/CableInfoScreen.java      # Cable info GUI panel
│   ├── gui/CableInfoScreenOpener.java
│   ├── gui/CableUndoRedoHistoryScreen.java # Operation history screen (undoable/redoable)
│   ├── gui/ClientConfigScreen.java   # Mods menu config index (config file list)
│   ├── gui/ClientConfigSubScreen.java # Client config file sub-page
│   ├── gui/ServerConfigSubScreen.java # Server config file sub-page (owner-only)
│   ├── gui/ConfigLabels.java         # Config display-name translation
│   ├── gui/ConfigScreenTarget.java   # Config response target dispatch
│   ├── gui/ServerConfigResponseTarget.java # Config response receiver interface
│   └── render/CableRenderer.java     # 3D rendering (tube beams + face dots + lines)
├── commands/
│   └── VRedTestCommand.java          # /vredtest diagnostics command
├── config/
│   ├── ServerConfig.java             # Server config (distance/change table/log)
│   └── ClientConfig.java             # Client config (chat feedback/undo redo)
├── data/
│   ├── CableLink.java                # Link data model (render-only carrier)
│   ├── CableNetwork.java             # Network graph (node index + stored signal query)
│   ├── CableNode.java                # Topology node (toWho/fromWho adjacency)
│   ├── CableNetworkManager.java      # Per-dimension network/counter/change table
│   └── CableNetworkSavedData.java    # NBT persistence
├── item/
│   ├── VirtualCableItem.java         # Virtual cable (create/delete links, retain selection)
│   ├── CableCutterItem.java          # Cable cutter (delete origin links only)
│   └── CableMagnifierItem.java       # Magnifier (sneak: panel / right-click: signal)
├── mixin/
│   └── MixinLevel.java               # Overrides getSignal only (DBW stored-signal semantics)
├── network/
│   ├── CableNetworkChannel.java      # Network channel registration
│   ├── CableProtocol.java            # Protocol constants/abbreviated keys/error codes
│   ├── CableOpPacket.java            # Operation request (add/del/pull, JSON)
│   ├── CableMsgPacket.java           # Message (d/f/r, JSON)
│   ├── CableOpPacketHandler.java     # Server request processing and validation
│   ├── CableServerQueue.java         # Server tick queue and conflict handling
│   ├── CableChangeLog.java           # Dual logs (operation/request, optional)
│   ├── CableInfoRequestPacket.java   # Magnifier signal query request
│   ├── CableInfoResponsePacket.java  # Magnifier signal query response
│   ├── CableServerConfigRequestPacket.java # Server config query/change request
│   └── CableServerConfigResponsePacket.java # Server config query/change response
└── registry/
    ├── ModItems.java                 # Item registration
    └── ModBlocks.java                # Block registration
```

```
src/main/java/com/virtualredstonewire/util/
└── TooltipLines.java                 # Multi-line item tooltip helper
```

```
src/main/resources/
├── META-INF/mods.toml
├── mixins.virtual_redstone_wire.json    # Mixin configuration
├── pack.mcmeta
├── virtual_redstone_wire.png            # Mod Logo
├── assets/virtual_redstone_wire/
│   ├── lang/{en_us,zh_cn}.json
│   ├── models/item/*.json
│   └── textures/
│       └── item/{virtual_cable,cable_cutter,cable_magnifier}.png
└── data/virtual_redstone_wire/recipes/
    ├── virtual_cable.json
    ├── cable_cutter.json
    └── cable_magnifier.json
```

# Build & Run

Make sure the environment meets these requirements:

| Component | Requirement |
|:---:|:---:|
| JDK | 17 (Eclipse Temurin / Adoptium recommended) |
| Gradle | 8.14.3 |
| Minecraft | 1.20.1 (Forge 47.4.22) |

Verify the environment:

```bash
java -version
# Should show openjdk version "17.x.x"

gradlew --version
# Should show Gradle 8.14.3
```

Build:

```bash
gradlew build
# Output: build/libs/VirtualRedstoneWire-v<version>-forge-mc1.20.1.jar
```

# Changelog

See [CHANGELOG.md](./CHANGELOG.md)

# Contributing

See [CONTRIBUTING_en-US.md](./CONTRIBUTING_en-US.md)

# License

This project is open source under the [MIT License](./LICENSE).

# Copyright

Copyright (c) 2026 JularDepick

See the [COPYRIGHT file](./COPYRIGHT)

# Development Reference

This mod's development partially references the interaction effects and implementation logic of the Drive By Wire mod.

# Looking for Collaboration

- Missing multi-version Minecraft compatibility
- Missing a proper item texture asset solution

# Related Links

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/virtual-redstone-wire
- Modrinth: https://modrinth.com/mod/virtual-redstone-wire
