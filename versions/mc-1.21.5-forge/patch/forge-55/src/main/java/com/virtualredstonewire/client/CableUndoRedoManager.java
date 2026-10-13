package com.virtualredstonewire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.client.gui.CableUndoRedoHistoryScreen;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.item.CableCutterItem;
import com.virtualredstonewire.item.CableMagnifierItem;
import com.virtualredstonewire.item.VirtualCableItem;
import com.virtualredstonewire.network.CableMsgPacket;
import com.virtualredstonewire.network.CableOpPacket;
import com.virtualredstonewire.network.CableProtocol;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 客户端撤销/重做管理器。
 * 空栈提示后进入 3 秒冷却期（撤销/重做共用），冷却期内静默忽略。
 * 增量追回响应不清空全部历史，由 invalidateByState() 按缓存状态定向失效；
 */
@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
public final class CableUndoRedoManager
{
    public static final KeyMapping UNDO_KEY = new KeyMapping(
        "key.virtual_redstone_wire.undo",
        KeyConflictContext.IN_GAME,
        KeyModifier.CONTROL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Z,
        "key.categories.virtual_redstone_wire");
    public static final KeyMapping REDO_KEY = new KeyMapping(
        "key.virtual_redstone_wire.redo",
        KeyConflictContext.IN_GAME,
        KeyModifier.CONTROL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Y,
        "key.categories.virtual_redstone_wire");
    public static final KeyMapping HISTORY_KEY = new KeyMapping(
        "key.virtual_redstone_wire.history",
        KeyConflictContext.IN_GAME,
        KeyModifier.CONTROL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_A,
        "key.categories.virtual_redstone_wire");

    private static final Deque<CableOpPacket> UNDO_STACK = new ArrayDeque<>();
    private static final Deque<CableOpPacket> REDO_STACK = new ArrayDeque<>();

    private static final long EMPTY_COOLDOWN_MS = 3000;
    private static long lastEmptyPromptTime = 0;

    private CableUndoRedoManager() {}

    /** 按键注册（MOD bus 的 RegisterKeyMappingsEvent，由 ClientSetup 调用） */
    public static void registerKeys(RegisterKeyMappingsEvent event)
    {
        event.register(UNDO_KEY);
        event.register(REDO_KEY);
        event.register(HISTORY_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        /* 无论是否手持都先消费点击：避免未手持时的按键事件在 KeyMapping 内部积压，*/
        /* 待手持工具后 consumeClick 一次性逐个返还导致大量异常撤销/重做*/
        boolean undoPressed = UNDO_KEY.consumeClick();
        boolean redoPressed = REDO_KEY.consumeClick();
        boolean historyPressed = HISTORY_KEY.consumeClick();
        Minecraft mc = Minecraft.getInstance();
        /* 手持放大镜时 Ctrl+A 打开历史窗口页（打开即释放鼠标，退出由窗口页的按钮/ESC/Ctrl+A 处理）*/
        /* 同一时刻仅响应一个本模组快捷键：打开窗口页后本 tick 不再处理撤销/重做*/
        if (historyPressed && isHoldingMagnifier(player) && mc.screen == null)
        {
            mc.setScreen(new CableUndoRedoHistoryScreen());
            return;
        }
        if (!isHoldingTool(player)) return;
        if (undoPressed) undo();
        if (redoPressed) redo();
    }

    /** 手持条件：主手或副手持线缆/线缆剪/放大镜任一 */
    public static boolean isHoldingTool(LocalPlayer player)
    {
        return player.getMainHandItem().getItem() instanceof VirtualCableItem
            || player.getOffhandItem().getItem() instanceof VirtualCableItem
            || player.getMainHandItem().getItem() instanceof CableCutterItem
            || player.getOffhandItem().getItem() instanceof CableCutterItem
            || player.getMainHandItem().getItem() instanceof CableMagnifierItem
            || player.getOffhandItem().getItem() instanceof CableMagnifierItem;
    }

    /** 历史窗口页当前是否打开（打开期间抑制聊天栏提示，改动在窗口页内可见） */
    public static boolean isHistoryScreenOpen()
    {
        return Minecraft.getInstance().screen instanceof CableUndoRedoHistoryScreen;
    }

    /** 手持条件：主手或副手持放大镜（历史窗口页触发条件） */
    public static boolean isHoldingMagnifier(LocalPlayer player)
    {
        return player.getMainHandItem().getItem() instanceof CableMagnifierItem
            || player.getOffhandItem().getItem() instanceof CableMagnifierItem;
    }

    /** 撤销：弹出栈顶原始请求，生成抵消请求（op 取反、links 复制）以 UNDO 来源入队 */
    public static void undo()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (UNDO_STACK.isEmpty())
        {
            promptEmpty(mc, true);
            return;
        }
        CableOpPacket p = UNDO_STACK.pop();
        if (!isCurrentDimension(p))
        {
            /* 条目属于其他维度：不回放（回放会把该维度坐标写成当前维度），按维度不符专用提示*/
            sendMessage(mc, "message.virtual_redstone_wire.undo_failed_dimension", 0xFF5555);
            return;
        }
        CableClientQueue.enqueue(cancelOf(mc, p), CableClientQueue.Origin.UNDO, p);
    }

    /** 重做：弹出重做栈顶原始请求，以 REDO 来源直接入队 */
    public static void redo()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (REDO_STACK.isEmpty())
        {
            promptEmpty(mc, false);
            return;
        }
        CableOpPacket p = REDO_STACK.pop();
        if (!isCurrentDimension(p))
        {
            /* 条目属于其他维度：不回放（回放会把该维度坐标写成当前维度），按维度不符专用提示*/
            sendMessage(mc, "message.virtual_redstone_wire.redo_failed_dimension", 0xFF5555);
            return;
        }
        CableClientQueue.enqueue(rebuilt(mc, p), CableClientQueue.Origin.REDO, p);
    }

    /** 抵消请求：op 取反，links 复制（避免与栈中请求共享可变列表），维度/玩家用当前值重建 */
    private static CableOpPacket cancelOf(Minecraft mc, CableOpPacket p)
    {
        ArrayList<CableOpPacket.Link> links = new ArrayList<>(p.links);
        return CableProtocol.OP_ADD.equals(p.op)
            ? CableOpPacket.del(links, mc.level.dimension(), mc.player.getName().getString())
            : CableOpPacket.add(links, mc.level.dimension(), mc.player.getName().getString());
    }

    /** 重做请求重建：原 op 与 links 内容，维度/玩家用当前值重建 */
    private static CableOpPacket rebuilt(Minecraft mc, CableOpPacket p)
    {
        ArrayList<CableOpPacket.Link> links = new ArrayList<>(p.links);
        return CableProtocol.OP_ADD.equals(p.op)
            ? CableOpPacket.add(links, mc.level.dimension(), mc.player.getName().getString())
            : CableOpPacket.del(links, mc.level.dimension(), mc.player.getName().getString());
    }

    /** 在途请求成功（CableClientQueue 回调）：NORMAL 入撤销栈并清空重做栈；UNDO/REDO 栈转移并提示 */
    public static void onOpConfirmed(CableClientQueue.Origin origin, CableOpPacket assoc)
    {
        if (assoc == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (origin == CableClientQueue.Origin.NORMAL)
        {
            pushWithLimit(UNDO_STACK, assoc);
            REDO_STACK.clear();
        }
        else if (origin == CableClientQueue.Origin.UNDO)
        {
            pushWithLimit(REDO_STACK, assoc);
            sendMessage(mc, "message.virtual_redstone_wire.undo_done", 0x55FF55);
        }
        else if (origin == CableClientQueue.Origin.REDO)
        {
            pushWithLimit(UNDO_STACK, assoc);
            sendMessage(mc, "message.virtual_redstone_wire.redo_done", 0x55FF55);
        }
    }

    /** 在途请求失败（CableClientQueue 回调）：同 tick 相背放回原栈可重试；状态型失败从历史移除 */
    public static void onOpRejected(CableClientQueue.Origin origin, CableOpPacket assoc, CableMsgPacket msg)
    {
        if (assoc == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean sameTick = msg != null && msg.code == CableProtocol.CODE_CONFLICT
            && "message.virtual_redstone_wire.reject_conflict_same_tick".equals(msg.message);
        if (sameTick)
        {
            if (origin == CableClientQueue.Origin.UNDO)
            {
                UNDO_STACK.push(assoc);
                sendMessage(mc, "message.virtual_redstone_wire.undo_failed", 0xFF5555);
            }
            else
            {
                REDO_STACK.push(assoc);
                sendMessage(mc, "message.virtual_redstone_wire.redo_failed", 0xFF5555);
            }
        }
        else
        {
            /* 状态型失败：assoc 已从原栈弹出，不压回即完成从历史移除；*/
            /* 服务端附带其他客户端并发操作摘要且触及该链路时，提示并发修改而非泛化的状态变化*/
            if (msg != null && CableMsgPacket.touches(msg.lastOp, assoc.links))
            {
                sendMessage(mc, origin == CableClientQueue.Origin.UNDO
                    ? "message.virtual_redstone_wire.undo_failed_concurrent"
                    : "message.virtual_redstone_wire.redo_failed_concurrent",
                    0xFF5555, msg.lastOp.player());
            }
            else
            {
                sendMessage(mc, origin == CableClientQueue.Origin.UNDO
                    ? "message.virtual_redstone_wire.undo_failed_state"
                    : "message.virtual_redstone_wire.redo_failed_state", 0xFF5555);
            }
        }
    }

    /** 清空双栈（断线/登出、换维度、全量与过期追回响应到达时） */
    public static void clearHistory()
    {
        UNDO_STACK.clear();
        REDO_STACK.clear();
        /* 联动取消排队中的撤销/重做请求，避免其成功后被回写进清空后的新历史*/
        CableClientQueue.onHistoryCleared();
    }

    /**
     * 定向失效：按缓存当前状态校验双栈，仅移除前提已失效的条目，
     */
    public static void invalidateByState()
    {
        UNDO_STACK.removeIf(p -> !isStateValid(p, true));
        REDO_STACK.removeIf(p -> !isStateValid(p, false));
    }

    /**
     * 条目前提校验：撤销栈要求抵消请求可成立（add 历史要求链路存在，del 历史要求链路不存在），
     * 重做栈要求原请求可成立（add 历史要求链路不存在，del 历史要求链路存在）；
     * 批量条目任一条不符即整条失效。
     */
    private static boolean isStateValid(CableOpPacket p, boolean undoAction)
    {
        if (p == null || p.links.isEmpty()) return false;
        /* 条目所属维度与缓存维度不一致时，缓存状态对该条目无意义，直接判为失效*/
        if (ClientCableCache.getDimension() == null
            || !ClientCableCache.getDimension().equals(p.dimension)) return false;
        boolean expectExists = CableProtocol.OP_ADD.equals(p.op) == undoAction;
        for (CableOpPacket.Link l : p.links)
        {
            if (ClientCableCache.hasLink(l.from(), l.to(), l.faceDir()) != expectExists) return false;
        }
        return true;
    }

    /** 条目所属维度是否与客户端当前维度一致（跨维度条目一律不回放） */
    private static boolean isCurrentDimension(CableOpPacket p)
    {
        Minecraft mc = Minecraft.getInstance();
        return p.dimension != null && mc.level != null
            && p.dimension.equals(mc.level.dimension().location().toString());
    }

    /** 只读快照：撤销栈从新到旧，供历史浮窗渲染（不修改历史） */
    public static List<CableOpPacket> getUndoSnapshot()
    {
        return new ArrayList<>(UNDO_STACK);
    }

    /** 只读快照：重做栈从新到旧，供历史浮窗渲染（不修改历史） */
    public static List<CableOpPacket> getRedoSnapshot()
    {
        return new ArrayList<>(REDO_STACK);
    }

    /** 入栈并收缩到配置上限：丢弃栈底最旧条目，保留最新操作 */
    private static void pushWithLimit(Deque<CableOpPacket> stack, CableOpPacket packet)
    {
        int limit = Math.max(1, ClientConfig.undoHistorySize.get());
        while (stack.size() >= limit)
        {
            stack.pollLast();
        }
        stack.push(packet);
    }

    /** 配置上限被调小时立即收缩双栈，避免"显示上限"与"实际栈长"短期不一致 */
    public static void trimToLimit()
    {
        int limit = Math.max(1, ClientConfig.undoHistorySize.get());
        while (UNDO_STACK.size() > limit)
        {
            UNDO_STACK.pollLast();
        }
        while (REDO_STACK.size() > limit)
        {
            REDO_STACK.pollLast();
        }
    }

    /** 空栈提示 + 3 秒冷却：冷却期内撤销/重做均静默忽略 */
    private static void promptEmpty(Minecraft mc, boolean undo)
    {
        long now = System.currentTimeMillis();
        if (now - lastEmptyPromptTime < EMPTY_COOLDOWN_MS) return;
        lastEmptyPromptTime = now;
        sendMessage(mc, undo
            ? "message.virtual_redstone_wire.undo_empty"
            : "message.virtual_redstone_wire.redo_empty", 0xFFFFFF);
    }

    private static void sendMessage(Minecraft mc, String langKey, int color, Object... args)
    {
        if (!ClientConfig.undoRedoFeedback.get()) return;
        /* 历史窗口页打开期间不弹聊天栏提示（撤销/重做结果在窗口页内实时可见）*/
        if (isHistoryScreenOpen()) return;
        if (mc.player != null)
        {
            mc.player.displayClientMessage(Component.translatable(langKey, args)
                .withStyle(style -> style.withColor(color)), false);
        }
    }
}
