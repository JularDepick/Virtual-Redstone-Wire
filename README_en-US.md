<div align="center">

# Virtual Redstone Wire

[![Version](https://img.shields.io/badge/Version-0.2.0-red)](https://github.com/JularDepick/Virtual-Redstone-Wire/releases/tag/v0.2.0)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-MIT-yellow)](./LICENSE)

[简体中文](./README.md)
| [English]

</div>

A Minecraft Forge 1.20.1 mod for wireless redstone signal transmission without physical redstone dust. Supports one-to-one, one-to-many, and many-to-one connections.

---

# Usage Guide

No mod dependencies required. Fully compatible with vanilla Minecraft.

## Virtual Cable

Crafting: Redstone + Iron Ingot
Usage: Right-click an input block, then an output block, to create a one-way redstone link
Notes: Signal strength propagates along the link; right-click the same block again to cancel; one input can feed multiple outputs, one output can take multiple inputs (highest signal wins)

## Cable Cutter

Crafting: Iron Ingot + Stick x2
Usage: Right-click an input block to cut all links originating from it
Notes: Only affects links where this block is the input (source)

## Cable Magnifier

Crafting: Redstone + Glass Pane + Stick
Usage: While held, highlights all links (blue is input, yellow is output, red is link path); sneak-right-click a block to inspect its links

## Redstone Behavior

- Virtual cables transmit redstone signal strength (0-15)
- The input block's redstone power is queried using the standard redstone API
- Signal propagates along the link to the output, queried through the in-memory network graph (zero space occupation)
- Input changes are reflected at the output in real-time
- Supports one-to-many broadcast and many-to-one merging (max signal)
- Links are directional: the signal exits from the clicked face of the output block (face-accurate); a redstone lamp placed on the output block itself lights up
- Works when the source is placed after the link: placing/removing a signal source or flipping a lever refreshes the link signal in real-time
- Chat feedback is disabled by default; can be enabled in the config file

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
│   ├── ClientCableCache.java         # Client-side link cache
│   ├── gui/CableInfoScreen.java      # Cable info GUI panel
│   ├── gui/CableInfoScreenOpener.java
│   └── render/CableRenderer.java     # 3D rendering (tube beams + face dots + lines)
├── commands/
│   └── VRedTestCommand.java          # /vredtest diagnostics command
├── config/
│   ├── ServerConfig.java             # Server config (distance limits)
│   └── ClientConfig.java             # Client config (chat feedback)
├── data/
│   ├── CableLink.java                # Link data model (render-only carrier)
│   ├── CableNetwork.java             # Network graph (node index + stored signal query)
│   ├── CableNode.java                # Topology node (toWho/fromWho adjacency)
│   ├── CableNetworkManager.java      # Per-dimension CableNetwork manager
│   └── CableNetworkSavedData.java    # NBT persistence
├── item/
│   ├── VirtualCableItem.java         # Virtual cable (create/delete links, retain selection)
│   ├── CableCutterItem.java          # Cable cutter (delete origin links only)
│   └── CableMagnifierItem.java       # Cable magnifier (sneak+right-click to inspect)
├── mixin/
│   └── MixinLevel.java               # Overrides getSignal only (DBW stored-signal semantics)
├── network/
│   ├── CableNetworkChannel.java      # Network channel registration
│   ├── CableActionPacket.java        # Client->Server action request (connect/cut)
│   ├── CableActionPacketHandler.java # Server packet processing + updateNeighborsAt
│   ├── CableSyncPacket.java          # Server->Client full sync
│   ├── CableRequestSyncPacket.java   # Client->Server sync request
│   └── SyncHelper.java               # Entry conversion utilities
└── registry/
    ├── ModItems.java                 # Item registration
    └── ModBlocks.java                # Block registration
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
│   └── textures/item/{virtual_cable,cable_cutter,cable_magnifier}.png
└── data/virtual_redstone_wire/recipes/
    ├── virtual_cable.json
    ├── cable_cutter.json
    └── cable_magnifier.json
```

# Build & Run

Make sure the environment meets these requirements:

| Component | Requirement |
|:---:|:---|
| JDK | 17 (Eclipse Temurin / Adoptium recommended) |
| Gradle | 8.14.3 |
| Minecraft | 1.20.1 (Forge 47.4.22) |

Verify the environment:

```bash
java -version
# Should show openjdk version "17.x.x"

gradle --version
# Should show Gradle 8.14.3
```

Build:

```bash
gradle build
# Output: build/libs/VirtualRedstoneWire-0.2.0.jar
```

# License

This project is open source under the [MIT License](./LICENSE).

# Copyright

(c) 2026 JularDepick
Contact: JularDepick@gmail.com | 1724834368@qq.com
GitHub: https://github.com/JularDepick/Virtual-Redstone-Wire
