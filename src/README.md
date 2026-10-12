# 源码目录结构

[简体中文]
| [[English]](./README_en-US.md)

本目录是模组的功能性源码目录, 根目录的其余内容为辅助性与说明性内容

```
src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java          # 模组主入口
├── ClientSetup.java                  # 客户端初始化
├── ServerEventHandler.java           # 服务端事件(世界加载/保存/卸载/方块更新)
├── RedstoneDiagnostics.java          # 红石信号自动化诊断(调试用)
├── client/
│   ├── ClientCableCache.java         # 客户端链路缓存(版本化,只读,索引化)
│   ├── CableFullSyncBuffer.java      # 全量/追回响应分片缓冲(收齐后一次性应用)
│   ├── CableInteractGate.java        # 三物品右键按下沿闸门(长按不重复触发)
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
│   ├── gui/ConfigTooltips.java       # 配置项悬浮提示
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
├── TooltipLines.java                 # 物品提示多行工具
├── FaceNames.java                    # 输出面名称本地化工具
└── PressEdgeLatch.java               # 按下沿锁存(按住期间只放行一次)
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
│       └── item/{virtual_cable,cable_cutter,cable_magnifier}.png
└── data/virtual_redstone_wire/recipes/
    ├── virtual_cable.json
    ├── cable_cutter.json
    └── cable_magnifier.json
```

项目辅助目录(非源码):

```
versions/build_version.py               # 多版本适配产物生成脚本(读取同目录 versions.json 与各目标的 diff-baseline.json)
versions/versions.json                  # 多版本适配目标清单(目标标识, MC 与加载器版本, Gradle 与 jdk 版本, 资源包格式号), 人工维护, 脚本只读
versions/.sync/<共用层>/                 # 跨版本共用的补丁内容(每层只存一份)
versions/<目标标识>/diff-baseline.json    # 该目标相对基线的差异清单(先 del 后 add), 人工维护, 脚本只读
versions/<目标标识>/patch/<层>/           # 该目标独有的补丁内容
```

`<目标标识>` 形如 `mc-1.20_1.20.1-forge`, 即 `mc-<起始MC版本>_<终止MC版本>-<加载器名>`, 某一段的上界或下界未知时该位置留空, 但保留下划线以区分起始与终止; 起始与终止一致时合并为单值, 如 `mc-1.20.6-forge`
