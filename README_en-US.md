<div align="center">

# Virtual Redstone Wire

[![Version](https://img.shields.io/badge/Version-1.0.0-red)](https://github.com/JularDepick/Virtual-Redstone-Wire/releases/tag/v1.0.0)
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

Usage: No consumption, reusable
1. Hold the Virtual Cable and right-click a block to set it as input (chat confirms selection)
2. Right-click another block to set it as output -- the link is created
3. An invisible Virtual Redstone Source block appears at the output face, emitting redstone signal
4. To cancel selection, right-click the same block again
5. One input can connect to multiple outputs (broadcast); multiple inputs can connect to the same output (max signal strength wins)

## Cable Cutter

Crafting: Iron Ingot + Stick x2

Usage: No consumption, reusable
- Right-click any block associated with cable links to remove all its connections
- Works on both input and output blocks

## Cable Magnifier

Crafting: Redstone + Glass Pane + Stick

Usage: No consumption, reusable
- When held: all links are visualized with colored highlights (blue outline=input, yellow outline=output, red line=link path)
- When right-clicked on a block: opens an info screen showing all associated links

## Redstone Behavior

- Virtual cables transmit redstone signal strength (0-15)
- The input block is queried for its current redstone power level
- Signal propagates along the link and is emitted by the Virtual Redstone Source at the output
- Input changes are reflected at the output in real-time
- Supports one-to-many broadcast and many-to-one merging (max signal)

# Tech Stack

| Component | Version / Notes |
|-----------|---------|
| Minecraft | 1.20.1 |
| Forge | 47.3.0 |
| JDK | 17 |
| Gradle | 8.14.3 |
| Mod ID | virtual-redstone-wire |

# Source Directory Structure

```
src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java        # Main mod class
├── ClientSetup.java                # Client initialization
├── ServerEventHandler.java         # World save/load/redstone events
├── registry/
│   ├── ModItems.java               # Item registration
│   └── ModBlocks.java              # Block registration
├── item/
│   ├── VirtualCableItem.java       # Virtual Cable item
│   ├── CableCutterItem.java        # Cable Cutter item
│   └── CableMagnifierItem.java     # Cable Magnifier item
├── block/
│   └── VirtualRedstoneSourceBlock.java  # Virtual Redstone Source block
├── data/
│   ├── CableLink.java              # Link data model
│   ├── CableNetwork.java           # Network graph (adjacency lists)
│   ├── CableNetworkManager.java    # In-memory manager
│   └── CableNetworkSavedData.java  # Persistence
├── redstone/
│   └── RedstoneCalculator.java     # Redstone signal calculator
├── network/
│   ├── CableNetworkChannel.java    # Network channel
│   ├── CableSyncPacket.java        # Full sync packet
│   ├── CableUpdatePacket.java      # Incremental update
│   └── CableQueryPacket.java       # Query response
├── config/
│   └── ModConfig.java              # Configuration
└── client/
    ├── ClientCableCache.java       # Client-side cache
    ├── render/CableRenderer.java   # 3D rendering
    └── gui/CableInfoScreen.java    # Info GUI
```

```
src/main/resources/
├── META-INF/mods.toml
├── pack.mcmeta
├── virtual_redstone_wire.png            # Mod Logo
├── assets/virtual-redstone-wire/
│   ├── lang/{en_us,zh_cn}.json
│   ├── models/item/*.json
│   ├── models/block/virtual_source.json
│   ├── blockstates/virtual_source.json
│   └── textures/item/{virtual_cable,cable_cutter,cable_magnifier}.png
└── data/virtual-redstone-wire/recipes/
    ├── virtual_cable.json
    ├── cable_cutter.json
    └── cable_magnifier.json
```

# Build

```bash
gradle build
# Output: build/libs/VirtualRedstoneWire-1.0.0.jar
```

# License

This project is open source under the [MIT License](./LICENSE).

# Copyright

(c) 2026 JularDepick
Contact: JularDepick@gmail.com | 1724834368@qq.com
GitHub: https://github.com/JularDepick/Virtual-Redstone-Wire
