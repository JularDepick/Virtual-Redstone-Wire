# Source Directory Structure

[简体中文](./README.md)
| [English]

This directory is the mod's functional source directory; the remaining content in the repository root is auxiliary and explanatory.

```
src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java          # Main mod class
├── ClientSetup.java                  # Client initialization
├── ServerEventHandler.java           # Server events (world load/save/unload/block update)
├── RedstoneDiagnostics.java          # Redstone signal diagnostics (debug only)
├── client/
│   ├── ClientCableCache.java         # Client-side link cache (versioned, read-only, indexed)
│   ├── CableFullSyncBuffer.java      # Chunked full/recatch-up response buffer (applied once complete)
│   ├── CableInteractGate.java        # Press-edge gate for the three items' right click (no repeat while held)
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
│   ├── gui/ConfigTooltips.java       # Config entry hover tooltip
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
├── TooltipLines.java                 # Multi-line item tooltip helper
├── FaceNames.java                    # Localized output-face name helper
└── PressEdgeLatch.java               # Press-edge latch (fires once per hold)
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

Auxiliary project directories (not source):

```
versions/build_version.py                 # Multi-version artifact generation script (reads the versions.json next to it and each target's diff-baseline.json)
versions/versions.json                    # Multi-version target manifest (target ids, MC and loader versions, Gradle and jdk versions, resource pack format number), maintained by hand, read-only for scripts
versions/.sync/<shared layer>/            # Patch content shared across versions (each layer is stored only once)
versions/<target id>/diff-baseline.json   # That target's diff against the baseline (del first, then add), maintained by hand, read-only for scripts
versions/<target id>/patch/<layer>/       # Patch content unique to this target
```

`<target id>` looks like `mc-1.20_1.20.1-forge`, that is `mc-<start MC version>_<end MC version>-<loader name>`. When the lower or upper bound of one segment is unknown, that position is left empty, but the underscore is kept to separate the start from the end; when the start and the end are identical, they are merged into a single value, e.g. `mc-1.20.6-forge`.
