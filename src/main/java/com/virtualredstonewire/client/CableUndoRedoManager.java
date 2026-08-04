package com.virtualredstonewire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.item.CableCutterItem;
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

/**
 * v0.5.0 客户端撤销/重做管理器。
 * 双栈（撤销栈 A / 重做栈 B）仅存客户端内存，退出游戏清空，无持久化。
 * 撤销/重做请求复用 CableOpPacket 通道（Origin 区分来源），成功/失败判定由
 * CableClientQueue 回调（onOpConfirmed/onOpRejected），失败按类型分流：
 * 同 tick 相背放回原栈可重试，状态型失败从历史移除（均不触发追回）。
 * 空栈提示后进入 3 秒冷却期（撤销/重做共用），冷却期内静默忽略。
 * 全部提示（空栈/成功/失败）受 undoRedoFeedback 配置控制。
 * 实现细节见 docs/v0.5.0-客户端撤销与重做机制-实现方案.md。
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
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        // 无论是否手持都先消费点击：避免未手持时的按键事件在 KeyMapping 内部积压，
        // 待手持工具后 consumeClick 一次性逐个返还导致大量异常撤销/重做
        boolean undoPressed = UNDO_KEY.consumeClick();
        boolean redoPressed = REDO_KEY.consumeClick();
        if (!isHoldingTool(player)) return;
        if (undoPressed) undo();
        if (redoPressed) redo();
    }

    /** 手持条件：主手或副手持线缆/线缆剪 */
    private static boolean isHoldingTool(LocalPlayer player)
    {
        return player.getMainHandItem().getItem() instanceof VirtualCableItem
            || player.getOffhandItem().getItem() instanceof VirtualCableItem
            || player.getMainHandItem().getItem() instanceof CableCutterItem
            || player.getOffhandItem().getItem() instanceof CableCutterItem;
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
            // 状态型失败：assoc 已从原栈弹出，不压回即完成从历史移除
            sendMessage(mc, origin == CableClientQueue.Origin.UNDO
                ? "message.virtual_redstone_wire.undo_failed_state"
                : "message.virtual_redstone_wire.redo_failed_state", 0xFF5555);
        }
    }

    /** 清空双栈（断线/登出、换维度、全量/追回响应到达时） */
    public static void clearHistory()
    {
        UNDO_STACK.clear();
        REDO_STACK.clear();
    }

    /** 入栈并收缩到配置上限：丢弃栈底最旧条目，保留最新操作 */
    private static void pushWithLimit(Deque<CableOpPacket> stack, CableOpPacket packet)
    {
        int limit = ClientConfig.undoHistorySize.get();
        while (stack.size() >= limit)
        {
            stack.pollLast();
        }
        stack.push(packet);
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

    private static void sendMessage(Minecraft mc, String langKey, int color)
    {
        if (!ClientConfig.undoRedoFeedback.get()) return;
        if (mc.player != null)
        {
            mc.player.sendSystemMessage(Component.translatable(langKey)
                .withStyle(style -> style.withColor(color)));
        }
    }
}
