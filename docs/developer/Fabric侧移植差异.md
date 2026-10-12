# Fabric 侧移植差异

本文件记录 Fabric 侧实现与 Forge 侧基线之间**必须存在的差异**,以及每处的理由与性质
两侧源码同名同构,逐文件对照时,差异只应出现在本文件列出的位置;出现未列出的差异即为移植错误

## 阅读口径

| 性质 | 含义 |
|:---:|:---|
| 等价替换 | 同一语义换一个平台接口,行为不变 |
| 机制替换 | 平台缺少对应机制,改用另一种实现达到同一效果,行为等价但时机或载体不同 |
| 语义重建 | 平台缺少某个加载器钩子,按原版判据还原其语义,**必须实测确认** |
| 有意分歧 | 两侧行为有意不同,且不应被"修回一致" |

## 一,平台接口替换

| 关注点 | Forge 侧 | Fabric 侧 | 性质 |
|:---:|:---|:---|:---:|
| 模组入口 | `@Mod` 构造器 + 事件总线 | `ModInitializer` / `ClientModInitializer` | 等价替换 |
| 事件订阅 | `@Mod.EventBusSubscriber` + `@SubscribeEvent` 自动注册 | 入口显式调用各 `register()` | 机制替换 |
| 物品注册 | `DeferredRegister` + `ForgeRegistries` | `Registry.register` + `BuiltInRegistries` | 等价替换 |
| 创造模式标签页 | `BuildCreativeModeTabContentsEvent` 订阅类 | `ItemGroupEvents.modifyEntriesEvent` | 等价替换 |
| 服务端事件 | `ServerStartedEvent` / `TickEvent` / `LevelEvent` | `ServerLifecycleEvents` / `ServerTickEvents` / `ServerWorldEvents` | 等价替换 |
| 命令注册 | `RegisterCommandsEvent` | `CommandRegistrationCallback` | 等价替换 |
| 客户端 tick | `TickEvent.ClientTickEvent` | `ClientTickEvents.END_CLIENT_TICK` | 等价替换 |
| 连接与断线 | `ClientPlayerNetworkEvent.LoggingOut` | `ClientPlayConnectionEvents.DISCONNECT` | 等价替换 |
| 按键注册 | `RegisterKeyMappingsEvent` | `KeyBindingHelper.registerKeyBinding` | 等价替换 |
| HUD 渲染 | `RenderGuiOverlayEvent.Post` + `VanillaGuiOverlay.HOTBAR` | `HudRenderCallback` | 机制替换 |
| 世界几何渲染 | `RenderLevelStageEvent` 的 AFTER_TRANSLUCENT_BLOCKS 阶段 | `WorldRenderEvents.AFTER_TRANSLUCENT` | 机制替换 |
| 相机位置 | 事件对象直接给出 | `Minecraft.gameRenderer.getMainCamera()` | 等价替换 |
| 配置 | `ForgeConfigSpec` + 加载器代管读写 | 自建 `ConfigSpec`,模组自行读写 | 机制替换 |
| 网络 | `SimpleChannel` 统一登记编解码器并泛型投递 | 按通道标识注册接收器,投递逐类型进行 | 机制替换 |
| 配置界面入口 | 注册进 Mods 菜单 | 客户端命令 `/vredconfig` | 有意分歧 |

## 二,被删掉的包装层

以下三层在 Forge 侧是必需的,在 Fabric 侧**多余**,因此直接删除而非改写:

| 包装 | Forge 侧为何需要 | Fabric 侧为何不需要 |
|:---:|:---|:---|
| `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` | 物品类为通用代码,需避免专用服务端解析客户端类 | 该调用已在 `level.isClientSide` 守卫内,客户端类只在执行到该行时才被链接 |
| `ctx.get().enqueueWork(...)` | 网络回调不在主线程,需回主线程执行 | 接收器本就运行在游戏主线程 |
| 渲染阶段的 `event.getStage()` 判定 | 事件覆盖多个阶段,需自行筛选 | 所用回调本身就对应那一阶段 |

## 三,机制替换的细节

### 3.1 工作副本的存档写回

Forge 侧有逐维度保存事件,Fabric 侧基线所用的 API 版本(1.20.1 线)没有保存事件;该事件在 1.20.4 起才存在,因此做 1.20.4 及以后的 Fabric 目标时这一处可以改回事件驱动,当前基线改为**逐 tick 让存档载体指向实时网络并置脏**,由原版按自己的周期落盘
行为等价,但触发时机不同:Forge 是"保存前写回一次",Fabric 是"始终保持指向"

### 3.2 邻居方块更新的挂载

Forge 侧有 `BlockEvent.NeighborNotifyEvent` 直接给出被通知的邻居集合;Fabric 侧没有对应事件,改为在 `Level.updateNeighborsAtExceptFromFacing` 的**尾部**注入,通知集合取"除被跳过那一面之外的全部方向"
挂尾部而非头部:原版先改方块状态再通知邻居,尾部读到的是已生效的信号

### 3.3 按键的修饰键

Forge 侧用 `KeyModifier.CONTROL` 表达"按住 Ctrl",控制菜单会自动渲染成 `Ctrl + Z`
Fabric 侧的原版按键没有修饰键概念,因此注册裸键并在 tick 处理内用 `Screen.hasControlDown()` 判定;语言文件里三个快捷键的显示文案相应加上"按住 Ctrl"
两侧文案**有意不同**:Forge 侧再加提示是冗余

## 四,需要实测确认的项

| 项 | 说明 |
|:---:|:---|
| 弱充能判据的还原 | Forge 侧 `Level.getSignal` 会在方块需要检查弱充能时把直连信号计入最大值,其判据是加载器钩子 `shouldCheckWeakPower`,原版没有,Fabric 侧按其默认实现所用的原版判据 `isRedstoneConductor` 还原**这是本次移植唯一的语义重建,必须实测** |
| 邻居更新注入的实际效果 | 挂载点已由生成的 refmap 证明解析正确,但行为需实测 |
| 协议握手的时机 | 见 4.1 |

### 4.1 协议版本校验

Forge 侧由通道在建立连接时校验两端协议版本,不一致直接拒绝连接
Fabric 侧没有这层机制,改为显式补一个握手:服务端在玩家加入时下发自己的协议版本,客户端比对不一致即断开并提示
**只做单向**:协议不一致的客户端一旦自行断开,就不会继续按不兼容格式解析报文,危险方向已覆盖
与 Forge 的差别是校验发生在加入之后而非连接阶段

## 五,两侧产物类面

Forge 侧基线产物 76 个模组类,Fabric 侧 84 个,差额全部可解释:

| 只在 Forge 侧 | 原因 |
|:---:|:---|
| `ModItems$TabRegistration` | 事件总线订阅类,Fabric 侧由 `registerItemGroups()` 取代 |

| 只在 Fabric 侧 | 用途 |
|:---:|:---|
| `config/ConfigSpec` 及其 5 个内部条目类 | 自建配置设施 |
| `network/CablePayload` | 保持投递方法泛型形状的接口 |
| `network/ProtocolHandshakePacket` | 协议版本握手 |
| `commands/VRedConfigCommand` | 配置界面入口, 替代 Forge 侧的 Mods 菜单 |

## 六,维护要求

- 新增版本兼容目标时,两侧的分叉点需**分别**判定:原版侧断点两侧共用,加载器侧断点各不相同
- 修改本文件所列任一处时,必须同时更新本文件;未列入本文件的差异应视为缺陷
