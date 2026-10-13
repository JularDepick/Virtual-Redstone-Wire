# Virtual Redstone Wire

[English]
| [[简体中文](#虚拟红石线缆)]

A Minecraft mod for long-distance redstone signal transmission that does not rely on physical redstone blocks, supporting one-to-one, one-to-many, and many-to-one connections.

---

![![](https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/intro/intro_cover.png)](https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/intro/intro_video.gif)

# Mod Usage

## Prerequisites

| Item | Requirement |
|:---:|:---:|
| Minecraft | 1.20 - 1.21.8 |
| Mod loader | Forge / Fabric |
| Prerequisite mod | Fabric API is required on the Fabric side; the Forge side needs no other mod dependencies |
| Artifact | Download the artifact that matches your Minecraft version; each file name declares the range it supports |

> For the mapping between Minecraft game versions and mod loader versions, see [Supported Versions](#supported-versions)

## Mod Items

### Cable

- Item ID: `virtual_redstone_wire:virtual_cable`
- Crafting: 1 Redstone + 1 Iron Ingot
- Usage:
  - Right-click the block you want as the input to select it, then right-click the block you want as the output to create a one-way redstone link

    <img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/11.png" alt="Right-click an input block with the Cable to select it" />

    <img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/12.png" alt="Right-click an output block to create a one-way link" />

  - Right-click the block currently selected as the input to deselect it
  - One input can be linked to several outputs, and one output can accept several inputs
  - Power the input block with any redstone source; the output block delivers the signal through the chosen face

### Cable Cutter

- Item ID: `virtual_redstone_wire:cable_cutter`
- Crafting: 2 Sticks + 1 Iron Ingot
- Usage: Right-click an input block to cut every link leaving it (only links where that block is the input are affected)

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/21.png" alt="Using the Cable Cutter on an input block to cut every link leaving it" />

- Afterwards, that input block has no outgoing links

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/22.png" alt="Cable Cutter after cutting every outgoing link of the input block" />

### Cable Magnifier

- Item ID: `virtual_redstone_wire:cable_magnifier`
- Crafting: 1 Stick + 1 Glass Pane + 1 Redstone
- Usage:
  - While held, highlights every link around you: blue for the input, yellow for the output, red for the link path

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/31.png" alt="Cable Magnifier highlighting every link with a blue input, a yellow output and a red link path" />

  - Right-click a block to read its coordinates and its redstone signal strength

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/32.png" alt="Right-click a block with the Cable Magnifier to read its coordinates and redstone signal strength" />

  - Sneak-right-click a block to open its link info panel

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/33.png" alt="Sneak-right-click a block with the Cable Magnifier to open its link info panel" />

## Undo and Redo

- While holding any mod item in the main hand or the off hand, press `Ctrl+Z` to undo and `Ctrl+Y` to redo; both keybinds can be changed
- Operation history is kept in client memory only and is cleared when you leave the game
- Links removed in a batch by the Cable Cutter are undone or redone as a whole

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/41.png" alt="Undo or redo a link operation with Ctrl+Z / Ctrl+Y while holding a mod item" />

## Operation History

- While holding the Cable Magnifier, press `Ctrl+A` to open the operation history screen; the keybind can be changed
- The screen lists the operations you can currently undo or redo in two columns (operation type and endpoint coordinates); scroll with the mouse wheel when the list overflows
- Press `Ctrl+Z` / `Ctrl+Y` inside the screen to undo/redo directly, with results refreshing in real time and chat feedback suppressed while it is open
- Close it with the button at the panel's top-right corner, `ESC`, or `Ctrl+A` again

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/42.png" alt="Operation history screen listing the operations that can be undone or redone" />

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

| Minecraft | Java | Forge version | Mod file name suffix |
|:---:|:---:|:---:|:---:|
| `1.20` | `17` | `46.x` | `mc-1.20_1.20.1-forge` |
| **`1.20.1`** | **`17`** | **`47.x`** | **`mc-1.20.1-forge`** |
| `1.20.1` | `17` | `47.x` | `mc-1.20_1.20.1-forge` |
| `1.20.2` | `17` | `48.x` | `mc-1.20.2-forge` |
| `1.20.3` | `17` | `49.0.x` | `mc-1.20.3_1.20.4-forge` |
| `1.20.4` | `17` | `49.2.x` | `mc-1.20.3_1.20.4-forge` |
| `1.20.6` | `21` | `50.2.x` | `mc-1.20.6-forge` |
| `1.21` | `21` | `51.0.x` | `mc-1.21_1.21.1-forge` |
| `1.21.1` | `21` | `51.0.x` | `mc-1.21_1.21.1-forge` |
| `1.21.3` | `21` | `53.1.x` | `mc-1.21.3-forge` |
| `1.21.4` | `21` | `54.1.x` | `mc-1.21.4-forge` |
| `1.21.5` | `21` | `55.1.x` | `mc-1.21.5-forge` |
| `1.21.6` | `21` | `56.0.x` | `mc-1.21.6-forge` |
| `1.21.7` | `21` | `57.0.x` | `mc-1.21.7-forge` |
| `1.21.8` | `21` | `58.1.x` | `mc-1.21.8-forge` |

> Forge does not support Minecraft 1.20.5 and 1.21.2

## Fabric

| Minecraft | Java | Fabric version | Fabric API version | Mod file name suffix |
|:---:|:---:|:---:|:---:|:---:|
| `1.20` | `17` | `0.16.x` | `0.83.0+1.20` | `mc-1.20_1.20.1-fabric` |
| **`1.20.1`** | **`17`** | **`0.16.x`** | **`0.92.12+1.20.1`** | **`mc-1.20.1-fabric`** |
| `1.20.1` | `17` | `0.16.x` | `0.83.0+1.20` | `mc-1.20_1.20.1-fabric` |
| `1.20.2` | `17` | `0.16.x` | `0.91.6+1.20.2` | `mc-1.20.2-fabric` |
| `1.20.3` | `17` | `0.16.x` | `0.91.1+1.20.3` | `mc-1.20.3_1.20.4-fabric` |
| `1.20.4` | `17` | `0.16.x` | `0.91.1+1.20.3` | `mc-1.20.3_1.20.4-fabric` |
| `1.20.5` | `21` | `0.16.x` | `0.97.8+1.20.5` | `mc-1.20.5_1.20.6-fabric` |
| `1.20.6` | `21` | `0.16.x` | `0.97.8+1.20.5` | `mc-1.20.5_1.20.6-fabric` |
| `1.21` | `21` | `0.16.x` | `0.101.2+1.21` | `mc-1.21_1.21.1-fabric` |
| `1.21.1` | `21` | `0.16.x` | `0.101.2+1.21` | `mc-1.21_1.21.1-fabric` |
| `1.21.2` | `21` | `0.16.x` | `0.106.1+1.21.2` | `mc-1.21.2_1.21.3-fabric` |
| `1.21.3` | `21` | `0.16.x` | `0.106.1+1.21.2` | `mc-1.21.2_1.21.3-fabric` |
| `1.21.4` | `21` | `0.16.x` | `0.107.0+1.21.4` | `mc-1.21.4-fabric` |
| `1.21.5` | `21` | `0.16.x` | `0.114.1+1.21.5` | `mc-1.21.5-fabric` |
| `1.21.6` | `21` | `0.16.x` | `0.128.2+1.21.6` | `mc-1.21.6-fabric` |
| `1.21.7` | `21` | `0.16.x` | `0.128.2+1.21.7` | `mc-1.21.7_1.21.8-fabric` |
| `1.21.8` | `21` | `0.16.x` | `0.128.2+1.21.7` | `mc-1.21.7_1.21.8-fabric` |

> This mod does not plan to support the NeoForge mod loader for now; you can load this mod on NeoForge through a bridge mod. Or you can [open a new issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) to request support.

> This mod does not plan to support 26.x game versions for now. But you can [open a new issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) to request support.

# Language Support

The languages currently supported by the mod are:
- Simplified Chinese
- English

> The mod language follows the game language automatically.

# Notes

> Chat feedback is disabled by default and can be enabled in the client config

> Right-click actions trigger once when the button is pressed; holding the button down does not repeat them (release and press again to trigger again). They do not trigger when no block is targeted, and the left button triggers nothing

> This mod is inspired by Drive-By-Wire cable logic and works in a vanilla world; apart from Fabric API on the Fabric side, it has no other mod dependencies

> **For version ranges this mod does not yet cover, or to report issues and request features, visit the GitHub repository**: [https://github.com/JularDepick/Virtual-Redstone-Wire](https://github.com/JularDepick/Virtual-Redstone-Wire)

---

# 虚拟红石线缆

[简体中文]
| [[English](#virtual-redstone-wire)]

用于红石信号远程传递的 Minecraft 模组，不依赖实体红石方块，支持一对一、一对多、多对一连接。

---

![![](https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/intro/intro_cover.png)](https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/intro/intro_video.gif)

# 模组使用

## 前置要求

| 项 | 要求 |
|:---:|:---:|
| Minecraft | 1.20 - 1.21.8 |
| 模组加载器 | Forge / Fabric |
| 前置模组 | Fabric 需要 Fabric API; Forge 侧无其他模组依赖 |
| 产物选择 | 请下载与你的 Minecraft 版本匹配的产物, 每个文件名已声明其支持区间 |

> Minecraft游戏版本与模组加载器版本对照表见[支持的版本](#支持的版本)

## 模组物品

### 线缆

- 物品ID: `virtual_redstone_wire:virtual_cable`
- 合成: 1红石 + 1铁锭
- 使用:
  - 手持物品右键选中输入端方块后,再次右键选中输出端方块以创建单向红石链路

    <img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/11.png" alt="手持线缆右键输入端方块以选中" />

    <img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/12.png" alt="右键输出端方块创建单向链路" />

  - 手持物品右键选中输入端方块后,再次右键取消选中
  - 输入端可连接多个输出端,输出端可接收多个输入端
  - 用任意红石源为输入端方块供能, 输出端方块将从指定面射出信号

### 线缆剪

- 物品ID: `virtual_redstone_wire:cable_cutter`
- 合成: 2木棍 + 1铁锭
- 使用: 右键输入端方块切断其全部出链 (仅作用于该方块作为输入端的链路)

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/21.png" alt="线缆剪使用：右键输入端方块，切断其全部出链" />

- 使用后, 该输入端方块不再有出链

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/22.png" alt="线缆剪使用后，输入端方块的全部出链被移除" />

### 线缆放大镜

- 物品ID: `virtual_redstone_wire:cable_magnifier`
- 合成: 1木棍 + 1玻璃板 + 1红石
- 使用:
  - 手持时高亮显示全部链路(输入端蓝色,输出端黄色,链路红色)

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/31.png" alt="手持线缆放大镜高亮全部链路，蓝框为输入，黄框为输出，红线为路径" />

  - 手持物品右键方块查看其坐标和红石信号强度

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/32.png" alt="手持线缆放大镜右键方块，查看其坐标和红石信号强度" />

  - 手持物品潜行右键方块打开其链路信息面板

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/33.png" alt="手持线缆放大镜潜行右键方块，打开其链路信息面板" />

## 撤销与重做

- 主手或副手持有任一本模组物品时, 按 `Ctrl+Z` 撤销, `Ctrl+Y` 重做 (可更改按键绑定)
- 操作历史仅保存在客户端内存,退出游戏清空
- 线缆剪批量删除的链路可整体撤销或重做

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/41.png" alt="手持模组物品按 Ctrl+Z/Y 撤销或重做链路操作" />

## 查看操作历史

- 手持放大镜时按 `Ctrl+A` 打开操作历史窗口页(可更改按键绑定)
- 窗口页分两列列出当前可撤销与可重做的操作(操作类型与起终点坐标), 列表超出时可用鼠标滚轮滚动查看
- 窗口页内可直接按 `Ctrl+Z` / `Ctrl+Y` 撤销或重做, 结果实时刷新, 且窗口页打开期间不弹出聊天栏提示
- 退出方式: 面板右上角关闭按钮、`ESC`、再次按 `Ctrl+A`

<img src="https://raw.githubusercontent.com/JularDepick/Virtual-Redstone-Wire/main/curseforge/42.png" alt="操作历史窗口页列出可撤销与可重做的链路操作" />

## 红石行为

- 虚拟线缆传输的是 0-15 的红石信号强度
- 输入端方块检测其所在位置的红石信号
- 输入端的红石变化会实时反映到输出端
- 支持一对多广播, 多对一合并取最大值
- 链路信号单向传播
- 信号从输出方块的指定面发出,方向精确

# 模组配置

## 客户端

- 配置文件: `config/virtual_redstone_wire-client.toml`

| 配置项 | 默认值 | 取值范围 | 含义 |
|:---:|:---:|:---:|:---:|
| `enableChatFeedback` | `false` | `true` / `false` | 是否在聊天栏显示链路操作的结果反馈 |
| `undoHistorySize` | `20` | `10` - `100` | 撤销栈与重做栈各自的条数上限 |
| `undoRedoFeedback` | `true` | `true` / `false` | 是否显示撤销与重做的各类提示 |

## 服务端

- 配置文件: `<世界存档>/serverconfig/virtual_redstone_wire-server.toml`

| 配置项 | 默认值 | 取值范围 | 含义 |
|:---:|:---:|:---:|:---:|
| `maxLinkDistance` | `256` | `1` - `1024` | 单条链路允许的最大距离, 单位为方块 |
| `magnifierRenderDistance` | `512` | `64` - `1024` | 手持放大镜时链路高亮渲染的最大距离, 单位为方块 |
| `networkChangeLogSize` | `200` | `200` - `1000` | 增量同步所用的近期变更表容量 |
| `operationLogEnabled` | `true` | `true` / `false` | 是否记录每一条已生效或被拒绝的增删操作 |
| `operationLogSplit` | `total` | `total` / `both` / `player` | 操作日志的文件分割方式, 依次为合并写入, 合并与按玩家分文件同时写, 仅按玩家分文件 |
| `operationLogFile` | `Virtual_Redstone_Wire-Operations.log` | 自定义文件名 | 操作日志的文件名, 按玩家分文件时在文件名主体后追加玩家名称 |
| `requestLogEnabled` | `false` | `true` / `false` | 是否记录收到的每一条请求原文与最终结果码, 属调试级 |
| `requestLogFile` | `Virtual_Redstone_Wire-Requests.log` | 自定义文件名 | 请求日志的文件名 |

# 模组数据

## 链路数据

| 世界维度 | 数据文件 |
|:---:|:---:|
| 主世界 | `<世界存档>/data/virtual_redstone_wire_network.dat` |
| 下界 | `<世界存档>/DIM-1/data/virtual_redstone_wire_network.dat` |
| 末地 | `<世界存档>/DIM1/data/virtual_redstone_wire_network.dat` |

## 日志数据

- 操作日志: `<世界存档>/Virtual_Redstone_Wire-Operations.log` 和 `<世界存档>/Virtual_Redstone_Wire-Operations_<玩家名称>.log`
- 请求日志: `<世界存档>/Virtual_Redstone_Wire-Requests.log`

# 支持的版本

`mc-1.20.1-forge` 与 `mc-1.20.1-fabric` 是两侧的 1.20.1 基线产物,其余兼容版本如下:

## Forge

| Minecraft | Java | Forge版本 | 模组文件名称后缀 |
|:---:|:---:|:---:|:---:|
| `1.20` | `17` | `46.x` | `mc-1.20_1.20.1-forge` |
| **`1.20.1`** | **`17`** | **`47.x`** | **`mc-1.20.1-forge`** |
| `1.20.1` | `17` | `47.x` | `mc-1.20_1.20.1-forge` |
| `1.20.2` | `17` | `48.x` | `mc-1.20.2-forge` |
| `1.20.3` | `17` | `49.0.x` | `mc-1.20.3_1.20.4-forge` |
| `1.20.4` | `17` | `49.2.x` | `mc-1.20.3_1.20.4-forge` |
| `1.20.6` | `21` | `50.2.x` | `mc-1.20.6-forge` |
| `1.21` | `21` | `51.0.x` | `mc-1.21_1.21.1-forge` |
| `1.21.1` | `21` | `51.0.x` | `mc-1.21_1.21.1-forge` |
| `1.21.3` | `21` | `53.1.x` | `mc-1.21.3-forge` |
| `1.21.4` | `21` | `54.1.x` | `mc-1.21.4-forge` |
| `1.21.5` | `21` | `55.1.x` | `mc-1.21.5-forge` |
| `1.21.6` | `21` | `56.0.x` | `mc-1.21.6-forge` |
| `1.21.7` | `21` | `57.0.x` | `mc-1.21.7-forge` |
| `1.21.8` | `21` | `58.1.x` | `mc-1.21.8-forge` |

> Forge 不支持 1.20.5 和 1.21.2 两个游戏版本

## Fabric

| Minecraft | Java | Fabric版本 | Fabric API 版本 | 模组文件名称后缀 |
|:---:|:---:|:---:|:---:|:---:|
| `1.20` | `17` | `0.16.x` | `0.83.0+1.20` | `mc-1.20_1.20.1-fabric` |
| **`1.20.1`** | **`17`** | **`0.16.x`** | **`0.92.12+1.20.1`** | **`mc-1.20.1-fabric`** |
| `1.20.1` | `17` | `0.16.x` | `0.83.0+1.20` | `mc-1.20_1.20.1-fabric` |
| `1.20.2` | `17` | `0.16.x` | `0.91.6+1.20.2` | `mc-1.20.2-fabric` |
| `1.20.3` | `17` | `0.16.x` | `0.91.1+1.20.3` | `mc-1.20.3_1.20.4-fabric` |
| `1.20.4` | `17` | `0.16.x` | `0.91.1+1.20.3` | `mc-1.20.3_1.20.4-fabric` |
| `1.20.5` | `21` | `0.16.x` | `0.97.8+1.20.5` | `mc-1.20.5_1.20.6-fabric` |
| `1.20.6` | `21` | `0.16.x` | `0.97.8+1.20.5` | `mc-1.20.5_1.20.6-fabric` |
| `1.21` | `21` | `0.16.x` | `0.101.2+1.21` | `mc-1.21_1.21.1-fabric` |
| `1.21.1` | `21` | `0.16.x` | `0.101.2+1.21` | `mc-1.21_1.21.1-fabric` |
| `1.21.2` | `21` | `0.16.x` | `0.106.1+1.21.2` | `mc-1.21.2_1.21.3-fabric` |
| `1.21.3` | `21` | `0.16.x` | `0.106.1+1.21.2` | `mc-1.21.2_1.21.3-fabric` |
| `1.21.4` | `21` | `0.16.x` | `0.107.0+1.21.4` | `mc-1.21.4-fabric` |
| `1.21.5` | `21` | `0.16.x` | `0.114.1+1.21.5` | `mc-1.21.5-fabric` |
| `1.21.6` | `21` | `0.16.x` | `0.128.2+1.21.6` | `mc-1.21.6-fabric` |
| `1.21.7` | `21` | `0.16.x` | `0.128.2+1.21.7` | `mc-1.21.7_1.21.8-fabric` |
| `1.21.8` | `21` | `0.16.x` | `0.128.2+1.21.7` | `mc-1.21.7_1.21.8-fabric` |

> 本模组暂不考虑兼容 NeoForge 模组加载器,玩家可以通过桥接模组在 NeoForge上加载本模组。或者你可以 [新建Issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) 请求适配。

> 本模组暂不考虑支持 26.x 游戏版本。但是你可以 [新建Issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) 请求适配。

# 多语言支持

当前模组支持的语言有:
- 简体中文
- English

> 模组语言随游戏语言自动变更

# 附注

> 聊天反馈默认关闭, 可在客户端配置中启用

> 右键操作在按下时触发一次, 按住不放不会重复触发（松开后可再次触发）; 未瞄准方块时不触发; 左键不触发任何操作

> 本模组受 Drive-By-Wire 线缆逻辑启发, 可在原版世界使用; 除 Fabric 侧需要 Fabric API 外无其他模组依赖

> **如需本模组尚未覆盖的版本区间, 反馈问题或建议功能, 请前往 GitHub 仓库**: [https://github.com/JularDepick/Virtual-Redstone-Wire](https://github.com/JularDepick/Virtual-Redstone-Wire)
