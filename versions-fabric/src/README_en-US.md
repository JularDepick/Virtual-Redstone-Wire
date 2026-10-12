# Source Directory Structure

[简体中文](./README.md)
| [English]

This directory is the functional source directory of the Fabric side standalone project; that project and the Forge baseline in the repository root are independent of each other. The remaining content outside this directory is auxiliary and explanatory.

```
versions-fabric/src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java          # Main mod class (ModInitializer, explicit registration)
├── ClientSetup.java                  # Client entry point (ClientModInitializer, explicit client-side registration)
├── ServerEventHandler.java           # Server events (world load/save/unload/neighbor update)
├── RedstoneDiagnostics.java          # Redstone signal diagnostics (debug only)
├── client/
│   ├── ClientCableCache.java         # Client-side link cache (versioned, read-only, indexed)
│   ├── CableFullSyncBuffer.java      # Chunked full/recatch-up response buffer (applied once complete)
│   ├── CableInteractGate.java        # Press-edge gate for the three items' right click (no repeat while held)
│   ├── CableClientQueue.java         # Operation queue (single in-flight / origin tag)
│   ├── CableClientEvents.java        # Connect / dimension change / disconnect reset
│   ├── CableUndoRedoManager.java     # Undo/redo (dual stacks, keybinds, history screen)
│   ├── CableActionBarHud.java        # Signal popup above the hotbar
│   ├── gui/CableInfoScreen.java      # Cable info GUI panel
│   ├── gui/CableInfoScreenOpener.java # Info panel opener
│   ├── gui/CableUndoRedoHistoryScreen.java # Undo/redo history screen (Ctrl+A with the magnifier only)
│   ├── gui/ClientConfigScreen.java   # Config index screen (config file list)
│   ├── gui/ClientConfigSubScreen.java # Client config file sub-page
│   ├── gui/ServerConfigSubScreen.java # Server config file sub-page (packet-driven, owner-only)
│   ├── gui/ConfigLabels.java         # Config display-name translation
│   ├── gui/ConfigScreenTarget.java   # Config response target dispatch
│   ├── gui/ServerConfigResponseTarget.java # Config response receiver interface
│   ├── gui/ConfigTooltips.java       # Config entry hover tooltip
│   └── render/CableRenderer.java     # Cable highlight rendering (tube beams + face dots + lines)
├── commands/
│   ├── VRedConfigCommand.java        # /vredconfig config screen entry command (client)
│   └── VRedTestCommand.java          # /vredtest diagnostics command
├── config/
│   ├── ConfigSpec.java               # Self-built config facility (sectioned read/write, entry objects)
│   ├── ServerConfig.java             # Server config (distance/change table/log)
│   └── ClientConfig.java             # Client config (chat feedback/undo redo)
├── data/
│   ├── CableLink.java                # Link data model (render-only carrier)
│   ├── CableNetwork.java             # Network graph (node index + stored signal query)
│   ├── CableNode.java                # Topology node (toWho/fromWho adjacency)
│   ├── CableNetworkManager.java      # Per-dimension network/counter/change table/last successful op
│   └── CableNetworkSavedData.java    # NBT persistence
├── item/
│   ├── VirtualCableItem.java         # Virtual cable (create/delete links, retain selection)
│   ├── CableCutterItem.java          # Cable cutter (delete origin links only)
│   └── CableMagnifierItem.java       # Magnifier (sneak: panel / right-click: signal)
├── mixin/
│   └── MixinLevel.java               # Overrides getSignal + injects neighbor notify (DBW stored-signal semantics)
├── network/
│   ├── CableNetworkChannel.java      # Network channel registration (per-side receiver registration)
│   ├── CableProtocol.java            # Protocol constants/abbreviated keys/error codes
│   ├── CablePayload.java             # Network payload interface (channel id + buffer write)
│   ├── CableOpPacket.java            # Operation request (add/del/pull, JSON)
│   ├── CableMsgPacket.java           # Message (d/f/r, JSON)
│   ├── CableOpPacketHandler.java     # Server request processing and validation
│   ├── CableServerQueue.java         # Server tick queue and conflict handling
│   ├── CableChangeLog.java           # Dual logs (operation/request, optional)
│   ├── ProtocolHandshakePacket.java  # Protocol version handshake (server sends its version)
│   ├── CableInfoRequestPacket.java   # Magnifier signal query request
│   ├── CableInfoResponsePacket.java  # Magnifier signal query response
│   ├── CableServerConfigRequestPacket.java # Server config query/change request (binary)
│   └── CableServerConfigResponsePacket.java # Server config query/change response (binary)
└── registry/
    ├── ModItems.java                 # Item registration
    └── ModBlocks.java                # Block registration (empty, kept for parity with the Forge side)
```

```
versions-fabric/src/main/java/com/virtualredstonewire/util/
├── TooltipLines.java                 # Multi-line item tooltip helper
├── FaceNames.java                    # Localized output-face name helper
└── PressEdgeLatch.java               # Press-edge latch (fires once per hold)
```

```
versions-fabric/src/main/resources/
├── fabric.mod.json
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
versions-fabric/                  # Fabric side standalone project (equal in standing to the repository root, with its own build scripts, target manifest and ignore rules)
├── .gitignore                    # This project's own ignore rules; its specific conventions are not written into the root .gitignore
├── build.gradle                  # This project's build configuration (loader/dependencies/artifact)
├── settings.gradle               # This project's project name and repository settings
├── gradle.properties             # This project's Gradle and mod version parameters
├── gradlew                       # This project's Gradle wrapper launcher
├── gradlew.bat                   # This project's Gradle wrapper launcher (Windows)
├── gradle/wrapper/               # Gradle wrapper configuration and jar
├── src/                          # This project's baseline source (see this file for its internal structure)
└── versions/                     # This project's multi-version compatibility directory
    ├── build_version.py          # Multi-version artifact generation script (reads the versions.json next to it and each target's diff-baseline.json)
    ├── versions.json             # Multi-version target manifest (target ids, MC and loader versions, loader API version, Gradle and jdk versions, resource pack format number), maintained by hand, read-only for scripts
    └── <target id>/              # A single adaptation target
        └── diff-baseline.json    # That target's diff against the baseline (del first, then add), maintained by hand, read-only for scripts
```

`<target id>` looks like `mc-1.20.1-fabric`, that is `mc-<MC version>-fabric`.
