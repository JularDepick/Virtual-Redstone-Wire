package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.network.CableOpPacket;
import com.virtualredstonewire.network.CableProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.5.2 撤销/重做历史可视化浮窗。
 * 手持放大镜（主手/副手）时按 Ctrl+A 切换显示/隐藏；不再手持放大镜时自动隐藏。
 * 固定全屏面板、两列固定列宽、条目文本超出列宽自动换行；HUD 方式渲染不阻塞输入。
 * 视觉样式复用放大镜查询面板（半透明背景/深色头部/边框）。
 * 只读读取历史栈快照渲染，不修改历史。
 * 细节见 docs/v0.5.2-撤销与重做历史可视化机制与规范.md。
 */
@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
public class CableUndoRedoHistoryHud
{
    private static final int SCREEN_MARGIN = 6;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 11;
    private static final int HEADER_HEIGHT = 16;
    private static final int COL_GAP = 8;
    // 复用放大镜面板配色
    private static final int BG_COLOR = 0xE0101010;
    private static final int HEADER_COLOR = 0xFF2A2A2A;
    private static final int DIVIDER_COLOR = 0xFF444444;
    private static final int BORDER_COLOR = 0xFF666666;
    private static final int TITLE_COLOR = 0xCCCCCC;
    private static final int UNDO_TITLE_COLOR = 0x55FFFF;
    private static final int REDO_TITLE_COLOR = 0xFFFF55;
    private static final int ENTRY_COLOR = 0xCCCCCC;
    private static final int EMPTY_COLOR = 0x888888;

    private CableUndoRedoHistoryHud() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event)
    {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!CableUndoRedoManager.isHistoryVisible()) return;
        if (!CableUndoRedoManager.isHoldingMagnifier(mc.player)) return;
        renderHistory(mc, event.getGuiGraphics());
    }

    private static void renderHistory(Minecraft mc, GuiGraphics g)
    {
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // 固定全屏面板（仅留边距，不随内容变化）
        int px = SCREEN_MARGIN;
        int py = SCREEN_MARGIN;
        int pw = Math.max(80, sw - SCREEN_MARGIN * 2);
        int ph = Math.max(40, sh - SCREEN_MARGIN * 2);

        g.fill(px, py, px + pw, py + ph, BG_COLOR);
        g.fill(px, py, px + pw, py + HEADER_HEIGHT, HEADER_COLOR);
        g.drawString(font, Component.translatable("screen.virtual_redstone_wire.history_title"),
            px + PADDING, py + 4, TITLE_COLOR);
        g.fill(px, py + HEADER_HEIGHT, px + pw, py + HEADER_HEIGHT + 1, DIVIDER_COLOR);
        g.fill(px, py, px + pw, py + 1, BORDER_COLOR);
        g.fill(px, py + ph - 1, px + pw, py + ph, BORDER_COLOR);
        g.fill(px, py, px + 1, py + ph, BORDER_COLOR);
        g.fill(px + pw - 1, py, px + pw, py + ph, BORDER_COLOR);

        // 两列固定列宽（面板内宽等分）
        int colW = Math.max(40, (pw - PADDING * 2 - COL_GAP) / 2);
        int colX1 = px + PADDING;
        int colX2 = colX1 + colW + COL_GAP;
        int contentTop = py + HEADER_HEIGHT + 4;
        int contentBottom = py + ph - PADDING;

        String undoTitle = Component.translatable("screen.virtual_redstone_wire.undo_history").getString();
        String redoTitle = Component.translatable("screen.virtual_redstone_wire.redo_history").getString();
        String emptyText = Component.translatable("screen.virtual_redstone_wire.history_empty").getString();

        renderColumn(font, g, colX1, colW, contentTop, contentBottom, undoTitle, UNDO_TITLE_COLOR,
            buildLines(CableUndoRedoManager.getUndoSnapshot()), emptyText);
        renderColumn(font, g, colX2, colW, contentTop, contentBottom, redoTitle, REDO_TITLE_COLOR,
            buildLines(CableUndoRedoManager.getRedoSnapshot()), emptyText);
    }

    /** 渲染单列：列标题 + 逐条换行渲染，超出内容区高度则裁剪 */
    private static void renderColumn(Font font, GuiGraphics g, int x, int colW, int top, int bottom,
                                     String title, int titleColor, List<String> entries, String emptyText)
    {
        int y = top;
        g.drawString(font, Component.literal(title), x, y, titleColor);
        y += LINE_HEIGHT + 2;

        if (entries.isEmpty())
        {
            g.drawString(font, Component.literal(emptyText), x, y, EMPTY_COLOR);
            return;
        }
        for (String entry : entries)
        {
            for (String line : wrap(font, entry, colW))
            {
                if (y + LINE_HEIGHT > bottom) return;
                g.drawString(font, Component.literal(line), x, y, ENTRY_COLOR);
                y += LINE_HEIGHT;
            }
        }
    }

    /** 超宽文本按列宽逐字符折行（中英文通用），行首不保留空格 */
    private static List<String> wrap(Font font, String text, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < text.length())
        {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (cur.length() > 0 && font.width(cur.toString() + ch) > maxWidth)
            {
                lines.add(cur.toString());
                cur.setLength(0);
                if (" ".equals(ch))
                {
                    i += Character.charCount(cp);
                    continue;
                }
            }
            cur.append(ch);
            i += Character.charCount(cp);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    /** 将历史栈快照转为显示行：单条显示起终点坐标，批量显示条数 */
    private static List<String> buildLines(List<CableOpPacket> list)
    {
        List<String> lines = new ArrayList<>();
        for (CableOpPacket p : list)
        {
            if (p.links.isEmpty()) continue;
            String type = CableProtocol.OP_ADD.equals(p.op)
                ? Component.translatable("message.virtual_redstone_wire.op_add").getString()
                : Component.translatable("message.virtual_redstone_wire.op_del").getString();
            if (p.links.size() == 1)
            {
                CableOpPacket.Link l = p.links.get(0);
                lines.add(type + " [" + l.fx() + "," + l.fy() + "," + l.fz() + "] -> ["
                    + l.tx() + "," + l.ty() + "," + l.tz() + "] " + l.face());
            }
            else
            {
                lines.add(type + " x" + p.links.size());
            }
        }
        return lines;
    }
}
