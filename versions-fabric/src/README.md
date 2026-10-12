# 源码目录结构

[简体中文]
| [[English]](./README_en-US.md)

本目录是 Fabric 侧独立工程的功能性源码目录, 该工程与仓库根目录的 Forge 侧基线彼此独立; 本目录之外的其余内容为辅助性与说明性内容

```
versions-fabric/src/main/java/com/virtualredstonewire/
├── VirtualRedstoneWire.java          # 模组主入口(ModInitializer, 显式注册各项)
├── ClientSetup.java                  # 客户端入口(ClientModInitializer, 显式注册客户端侧)
├── ServerEventHandler.java           # 服务端事件(世界加载/保存/卸载/邻居更新)
├── RedstoneDiagnostics.java          # 红石信号自动化诊断(调试用)
├── client/
│   ├── ClientCableCache.java         # 客户端链路缓存(版本化,只读,索引化)
│   ├── CableFullSyncBuffer.java      # 全量/追回响应分片缓冲(收齐后一次性应用)
│   ├── CableInteractGate.java        # 三物品右键按下沿闸门(长按不重复触发)
│   ├── CableClientQueue.java         # 操作任务队列(单在途/来源标记)
│   ├── CableClientEvents.java        # 连接/维度切换/断线重连
│   ├── CableUndoRedoManager.java     # 撤销/重做(双栈/快捷键/历史窗口页)
│   ├── CableActionBarHud.java        # 快捷栏上方信号飘浮提示
│   ├── gui/CableInfoScreen.java      # 线缆信息 GUI 面板
│   ├── gui/CableInfoScreenOpener.java # 信息面板打开器
│   ├── gui/CableUndoRedoHistoryScreen.java # 撤销/重做历史窗口页(仅放大镜时 Ctrl+A 打开)
│   ├── gui/ClientConfigScreen.java   # 配置界面索引页(配置文件列表)
│   ├── gui/ClientConfigSubScreen.java # 客户端配置文件子页
│   ├── gui/ServerConfigSubScreen.java # 服务端配置文件子页(网络包驱动,仅拥有者)
│   ├── gui/ConfigLabels.java         # 配置项显示名翻译
│   ├── gui/ConfigScreenTarget.java   # 配置响应目标静态分发
│   ├── gui/ServerConfigResponseTarget.java # 配置响应接收接口
│   ├── gui/ConfigTooltips.java       # 配置项悬浮提示
│   └── render/CableRenderer.java     # 线缆高亮渲染(3D方管梁+面亮点+连接线)
├── commands/
│   ├── VRedConfigCommand.java        # /vredconfig 配置界面入口命令(客户端)
│   └── VRedTestCommand.java          # /vredtest 诊断命令
├── config/
│   ├── ConfigSpec.java               # 自建配置设施(分段读写/条目对象)
│   ├── ServerConfig.java             # 服务端配置(距离/变更表/日志)
│   └── ClientConfig.java             # 客户端配置(聊天反馈/撤销重做)
├── data/
│   ├── CableLink.java                # 链路数据模型(纯渲染载体)
│   ├── CableNetwork.java             # 电缆网络图(结点索引+存储式信号查询)
│   ├── CableNode.java                # 电缆拓扑结点(toWho/fromWho邻接表)
│   ├── CableNetworkManager.java      # 按维度管理网络/计数器/变更表/最近成功操作摘要
│   └── CableNetworkSavedData.java    # NBT 持久化
├── item/
│   ├── VirtualCableItem.java         # 虚拟线缆(选中/意图转换 add-del)
│   ├── CableCutterItem.java          # 线缆剪(批量 del 转换)
│   └── CableMagnifierItem.java       # 放大镜(下蹲面板/右键信号飘浮)
├── mixin/
│   └── MixinLevel.java               # 覆写 getSignal + 注入邻居通知(DBW 存储式语义)
├── network/
│   ├── CableNetworkChannel.java      # 网络通道注册(按侧注册接收器)
│   ├── CableProtocol.java            # 协议常量/缩写键名/错误码
│   ├── CablePayload.java             # 网络载荷接口(通道标识+写入缓冲)
│   ├── CableOpPacket.java            # 操作请求(add/del/pull, JSON)
│   ├── CableMsgPacket.java           # 消息(d/f/r, JSON)
│   ├── CableOpPacketHandler.java     # 服务端请求处理与校验
│   ├── CableServerQueue.java         # 服务端 tick 队列与冲突处理
│   ├── CableChangeLog.java           # 双日志(操作/请求,可选)
│   ├── ProtocolHandshakePacket.java  # 协议版本握手(服务端下发版本)
│   ├── CableInfoRequestPacket.java   # 放大镜信号查询请求
│   ├── CableInfoResponsePacket.java  # 放大镜信号查询响应
│   ├── CableServerConfigRequestPacket.java # 服务端配置查询/修改请求(二进制)
│   └── CableServerConfigResponsePacket.java # 服务端配置查询/修改响应(二进制)
└── registry/
    ├── ModItems.java                 # 物品注册
    └── ModBlocks.java                # 方块注册(空实现, 保持两侧同构)
```

```
versions-fabric/src/main/java/com/virtualredstonewire/util/
├── TooltipLines.java                 # 物品提示多行工具
├── FaceNames.java                    # 输出面名称本地化工具
└── PressEdgeLatch.java               # 按下沿锁存(按住期间只放行一次)
```

```
versions-fabric/src/main/resources/
├── fabric.mod.json
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
versions-fabric/                  # Fabric 侧独立工程(效力等同根目录, 自带构建脚本, 目标清单与忽略规则)
├── .gitignore                    # 本工程自己的忽略规则, 其独有约定不写入根目录的 .gitignore
├── build.gradle                  # 本工程的构建配置(加载器/依赖/产物)
├── settings.gradle               # 本工程的工程名与仓库设置
├── gradle.properties             # 本工程的 Gradle 与模组版本参数
├── gradlew                       # 本工程的 Gradle wrapper 启动脚本
├── gradlew.bat                   # 本工程的 Gradle wrapper 启动脚本(Windows)
├── gradle/wrapper/               # Gradle wrapper 配置与 jar
├── src/                          # 本工程的基线源码(内部结构见本文件)
└── versions/                     # 本工程的多版本兼容目录
    ├── build_version.py          # 多版本适配产物生成脚本(读取同目录 versions.json 与各目标的 diff-baseline.json)
    ├── versions.json             # 多版本适配目标清单(目标标识, MC 与加载器版本, 加载器 API 版本, Gradle 与 jdk 版本, 资源包格式号), 人工维护, 脚本只读
    └── <目标标识>/                # 单个适配目标
        └── diff-baseline.json    # 该目标相对基线的差异清单(先 del 后 add), 人工维护, 脚本只读
```

`<目标标识>` 形如 `mc-1.20.1-fabric`, 即 `mc-<MC版本>-fabric`
