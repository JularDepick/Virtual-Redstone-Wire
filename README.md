<div align="center">

# Virtual Redstone Wire (虚拟红石线缆)

[![Version](https://img.shields.io/badge/Version-0.5.3-red)](https://github.com/JularDepick/Virtual-Redstone-Wire/releases)
[![Copyright](https://img.shields.io/badge/Copyright-JularDepick-0066AA)](./COPYRIGHT)
[![License](https://img.shields.io/badge/License-MIT-yellow)](./LICENSE)

[简体中文]
| [[English]](./README_en-US.md)

</div>

为红石信号远程传递实现的Minecraft模组，不依赖实体红石方块，支持一对一、一对多、多对一连接。

---

# 模组使用教程

本模组高度依赖Forge，但无前置模组依赖，也不绑定任何其他模组或整合包。

安装：将下载的模组 jar 放入 `.minecraft/mods` 文件夹即可（需要 Forge 1.20.1）。

当前模组仅支持 Minecraft 1.20.1版本，如需做其他版本兼容适配，请 [提交Issue](https://github.com/JularDepick/Virtual-Redstone-Wire/issues/new) 或 [进行贡献](#贡献指南)。

| 物品 | 合成 | 操作 | 特性 |
|:---:|:---:|:---:|:---:|
| 线缆 | 红石 + 铁锭 | 右键输入端方块，再右键输出端方块，创建单向红石链路 | 信号强度沿链路传播；再次右键同一方块可取消选中；输入端可连接多个输出端，输出端可接收多个输入端（取最大信号） |
| 线缆剪 | 铁锭 + 木棍 x2 | 右键输入端方块，切断其全部出链 | 仅作用于该方块作为输入端（起点）的链路 |
| 线缆放大镜 | 红石 + 玻璃板 + 木棍 | 手持时高亮显示全部链路（蓝框是输入端，黄框是输出端，红线是链路路径）；潜行右键方块，查看其链路信息；右键方块，快捷栏上方显示其红石信号强度与坐标 | 链路高亮与信号查询 |

## 红石行为

- 虚拟线缆传输的是红石信号强度（0-15）
- 输入端方块检测其所在位置的红石信号（使用标准红石 API）
- 信号沿链路传递到输出端，通过内存网络图查询返回信号强度（无方块占用）
- 输入端的红石变化会实时反映到输出端
- 支持一对多广播、多对一合并（取最大值）
- 链路有方向：信号从输出方块的指定面射出（方向精确），红石灯放在输出端方块本身上即可点亮
- 支持先建链后放源：放置/移除信号源、拨动拉杆等操作会实时刷新链路信号
- 聊天栏操作反馈默认关闭，可在游戏内 Mods 菜单或配置文件中启用
- 链路数据查询已索引加速：链路数量较多时建链/删链操作响应更流畅

## 撤销与重做

撤销/重做可回退或重放最近成功的链路操作，避免误操作后手动重建：

- 手持线缆、线缆剪或放大镜（主手或副手任一）时，按 `Ctrl+Z` 撤销、`Ctrl+Y` 重做（可在游戏 Controls 设置中修改按键绑定）
- 撤销会取消最近一次成功的链路操作：创建的链路被移除，被移除的链路恢复；重做会重新执行最近一次被撤销的操作
- 连续按 `Ctrl+Z` 可逐步回退多次操作；撤销后执行新的操作会清空重做历史
- 操作历史仅保存在客户端内存：撤销/重做双栈共用上限（默认 20，可在游戏内 Mods 菜单客户端配置中调整，范围 10-100），退出游戏清空
- 无可撤销/重做或操作被拒绝时，聊天栏提示原因（受客户端"撤销/重做提示"开关控制，默认开启；空栈提示后 3 秒内按键不重复响应）
- 线缆剪批量删除的链路可整体撤销（一次撤销恢复全部被剪链路）

## 操作历史查看

手持放大镜时按 `Ctrl+A` 打开操作历史窗口页，查看当前可撤销与可重做的链路操作：

- 窗口页分两列显示可撤销、可重做的历史操作（操作类型与起终点坐标），列表超出时可用鼠标滚轮滚动查看
- 窗口页内可直接按 `Ctrl+Z` 撤销、`Ctrl+Y` 重做，结果在页面内实时刷新；窗口页打开期间不弹出聊天栏提示
- 退出方式：面板右上角关闭按钮、`ESC`、再次按 `Ctrl+A`（按键可在游戏 Controls 设置中修改）

## 使用建议

- 左手（副手）持放大镜，右手（主手）使用线缆或线缆剪：放大镜会持续高亮全部链路（蓝框输入、黄框输出、红线路径），边查看链路布局边建链/删链，操作更直观

# 运行时文件

## 配置文件

- 客户端配置：`config/virtual_redstone_wire-client.toml`（游戏目录下 config 文件夹，聊天反馈开关、撤销/重做历史上限与提示开关等；可在游戏内 Mods 菜单配置）
- 服务端配置：`<世界文件夹>/serverconfig/virtual_redstone_wire-server.toml`
  - 单机/局域网：`saves/<存档名>/serverconfig/`
  - 专用服务器：`<服务器世界文件夹>/serverconfig/`（如 `world/serverconfig/`）
  - 内容：链路最大距离/放大镜渲染距离/同步变更表容量/操作日志与请求日志；可在游戏内 Mods 菜单配置（仅存档拥有者）

## 数据文件

- 链路数据按维度独立存储（自动保存，同目录保留 `.dat_old` 备份）：
  - 主世界：`<世界文件夹>/data/virtual_redstone_wire_network.dat`
  - 下界：`<世界文件夹>/DIM-1/data/virtual_redstone_wire_network.dat`
  - 末地：`<世界文件夹>/DIM1/data/virtual_redstone_wire_network.dat`
  - 其他维度：`<世界文件夹>/<维度子目录>/data/virtual_redstone_wire_network.dat`

## 日志文件（世界文件夹根目录，均仅输出不恢复）

- 操作日志：`<世界文件夹>/Virtual_Redstone_Wire-Operations.log`（add/del 执行与拒绝；分割模式为 player/both 时生成 `Virtual_Redstone_Wire-Operations_<玩家名>.log`）
- 请求日志：`<世界文件夹>/Virtual_Redstone_Wire-Requests.log`（调试级，记录每次收到的原始请求与最终状态码，默认关闭）

# 技术栈

| 组件 | 版本 |
|:---:|:---:|
| Minecraft | 1.20.1 |
| Forge | 47.4.22 |
| JDK | 17 |
| Gradle | 8.14.3 |
| ForgeGradle | 6.x |
| 映射 | official |
| 模组 ID | virtual_redstone_wire |

# 源码目录结构

```
src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java          # 模组主入口
├── ClientSetup.java                  # 客户端初始化
├── ServerEventHandler.java           # 服务端事件(世界加载/保存/卸载/方块更新)
├── RedstoneDiagnostics.java          # 红石信号自动化诊断(调试用)
├── client/
│   ├── ClientCableCache.java         # 客户端链路缓存(版本化,只读,索引化)
│   ├── CableClientQueue.java         # 操作任务队列(单在途/来源标记)
│   ├── CableClientEvents.java        # 连接/维度切换全量同步
│   ├── CableUndoRedoManager.java     # 撤销/重做(双栈/快捷键/历史窗口页)
│   ├── CableActionBarHud.java        # 快捷栏上方信号飘浮提示
│   ├── gui/CableInfoScreen.java      # 线缆信息 GUI 面板
│   ├── gui/CableInfoScreenOpener.java
│   ├── gui/CableUndoRedoHistoryScreen.java # 操作历史窗口页(可撤销/可重做)
│   ├── gui/ClientConfigScreen.java   # Mods 菜单配置索引页(配置文件列表)
│   ├── gui/ClientConfigSubScreen.java # 客户端配置文件子页
│   ├── gui/ServerConfigSubScreen.java # 服务端配置文件子页(仅拥有者)
│   ├── gui/ConfigLabels.java         # 配置项显示名翻译
│   ├── gui/ConfigScreenTarget.java   # 配置响应目标分发
│   ├── gui/ServerConfigResponseTarget.java # 配置响应接收接口
│   └── render/CableRenderer.java     # 3D 渲染(方管梁+面亮点+连接线)
├── commands/
│   └── VRedTestCommand.java          # /vredtest 诊断命令
├── config/
│   ├── ServerConfig.java             # 服务端配置(距离/变更表/日志)
│   └── ClientConfig.java             # 客户端配置(聊天反馈/撤销重做)
├── data/
│   ├── CableLink.java                # 链路数据模型(纯渲染载体)
│   ├── CableNetwork.java             # 电缆网络图(结点索引+存储式信号查询)
│   ├── CableNode.java                # 电缆拓扑结点(toWho/fromWho 邻接表)
│   ├── CableNetworkManager.java      # 按维度管理 CableNetwork/计数器/变更表
│   └── CableNetworkSavedData.java    # NBT 持久化
├── item/
│   ├── VirtualCableItem.java         # 虚拟线缆(创建/删除链路,保留选态)
│   ├── CableCutterItem.java          # 线缆剪(只删起点链路)
│   └── CableMagnifierItem.java       # 线缆放大镜(下蹲查看面板/右键查看信号)
├── mixin/
│   └── MixinLevel.java               # 仅覆写 getSignal (DBW 存储式语义)
├── network/
│   ├── CableNetworkChannel.java      # 网络通道注册
│   ├── CableProtocol.java            # 协议常量/缩写键名/错误码
│   ├── CableOpPacket.java            # 操作请求(add/del/pull, JSON)
│   ├── CableMsgPacket.java           # 消息(d/f/r, JSON)
│   ├── CableOpPacketHandler.java     # 服务端请求处理与校验
│   ├── CableServerQueue.java         # 服务端 tick 队列与冲突处理
│   ├── CableChangeLog.java           # 双日志(操作/请求,可选)
│   ├── CableInfoRequestPacket.java   # 放大镜信号查询请求
│   ├── CableInfoResponsePacket.java  # 放大镜信号查询响应
│   ├── CableServerConfigRequestPacket.java # 服务端配置查询/修改请求
│   └── CableServerConfigResponsePacket.java # 服务端配置查询/修改响应
└── registry/
    ├── ModItems.java                 # 物品注册
    └── ModBlocks.java                # 方块注册
```

```
src/main/java/com/virtualredstonewire/util/
└── TooltipLines.java                 # 物品提示多行工具
```

```
src/main/resources/
├── META-INF/mods.toml
├── mixins.virtual_redstone_wire.json    # Mixin 配置
├── pack.mcmeta
├── virtual_redstone_wire.png            # 模组 Logo
├── assets/virtual_redstone_wire/
│   ├── lang/{en_us,zh_cn}.json
│   ├── models/item/*.json
│   └── textures/
│       ├── block/virtual_source.png
│       └── item/{virtual_cable,cable_cutter,cable_magnifier}.png
└── data/virtual_redstone_wire/recipes/
    ├── virtual_cable.json
    ├── cable_cutter.json
    └── cable_magnifier.json
```

# 构建与运行

确保环境满足以下要求：

| 组件 | 要求 |
|:---:|:---:|
| JDK | 17 (推荐 Eclipse Temurin / Adoptium) |
| Gradle | 8.14.3 |
| Minecraft | 1.20.1 (Forge 47.4.22) |

验证环境：

```bash
java -version
# 输出应为 openjdk version "17.x.x"

gradlew --version
# 输出应包含 Gradle 8.14.3
```

构建：

```bash
gradlew build
# 输出在 build/libs/VirtualRedstoneWire-v<version>-forge-mc1.20.1.jar
```

# 变更日志

详见 [CHANGELOG.md](./CHANGELOG.md)

# 贡献指南

详见 [CONTRIBUTING.md](./CONTRIBUTING.md)

# 许可证

本项目基于 [MIT 许可证](./LICENSE) 开源。

# 版权信息

Copyright (c) 2026 JularDepick

详见 [COPYRIGHT文件](./COPYRIGHT)

# 开发参考

本模组开发部分参考了 Drive By Wire 模组的交互效果和实现逻辑。

# 需要协作

- 缺少多 Minecraft 版本兼容
- 缺少优秀的物品贴图素材方案

# 相关链接

- CurseForge模组链接：https://www.curseforge.com/minecraft/mc-mods/virtual-redstone-wire
- Modrinth模组链接：https://modrinth.com/mod/virtual-redstone-wire