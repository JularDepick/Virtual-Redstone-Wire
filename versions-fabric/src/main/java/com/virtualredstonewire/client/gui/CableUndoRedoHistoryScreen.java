package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.client.CableUndoRedoManager;
import com.virtualredstonewire.network.CableOpPacket;
import com.virtualredstonewire.network.CableProtocol;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 撤销/重做历史窗口页。
 */
public class CableUndoRedoHistoryScreen extends Screen
{
    private static final int MARGIN = 12;
    private static final int PADDING = 12;
    private static final int LINE_HEIGHT = 11;
    private static final int HEADER_HEIGHT = 16;
    private static final int COL_GAP = 8;
    private static final int CLOSE_SIZE = 12;
    private static final int BG_COLOR = 0xF0101010;
    private static final int HEADER_COLOR = 0xFF2A2A2A;
    private static final int DIVIDER_COLOR = 0xFF444444;
    private static final int BORDER_COLOR = 0xFF666666;
    private static final int TITLE_COLOR = 0xCCCCCC;
    private static final int CLOSE_COLOR = 0xFF888888;
    private static final int CLOSE_HOVER_COLOR = 0xFFFF4444;
    private static final int UNDO_TITLE_COLOR = 0x55FFFF;
    private static final int REDO_TITLE_COLOR = 0xFFFF55;
    private static final int ENTRY_COLOR = 0xCCCCCC;
    private static final int EMPTY_COLOR = 0x888888;

    private double scrollOffset;
    private int maxScroll;
    private int closeX;
    private int closeY;

    public CableUndoRedoHistoryScreen()
    {
        super(Component.translatable("screen.virtual_redstone_wire.history_title"));
    }

    /** 关闭按钮命中框在 init 中确定，避免首帧渲染前命中框残留在 (0,0) */
    @Override
    protected void init()
    {
        int pw = Math.max(80, this.width - MARGIN * 2);
        closeX = MARGIN + pw - CLOSE_SIZE - 4;
        closeY = MARGIN + 4;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);

        int px = MARGIN;
        int py = MARGIN;
        int pw = Math.max(80, this.width - MARGIN * 2);
        int ph = Math.max(40, this.height - MARGIN * 2);

        /* 面板主体背景（直接绘制，避免依赖背景回调导致主体透明）*/
        g.fill(px, py, px + pw, py + ph, BG_COLOR);
        g.fill(px, py, px + pw, py + HEADER_HEIGHT, HEADER_COLOR);
        g.drawCenteredString(this.font, this.title, px + pw / 2, py + 4, TITLE_COLOR);
        g.fill(px, py + HEADER_HEIGHT, px + pw, py + HEADER_HEIGHT + 1, DIVIDER_COLOR);
        g.fill(px, py, px + pw, py + 1, BORDER_COLOR);
        g.fill(px, py + ph - 1, px + pw, py + ph, BORDER_COLOR);
        g.fill(px, py, px + 1, py + ph, BORDER_COLOR);
        g.fill(px + pw - 1, py, px + pw, py + ph, BORDER_COLOR);

        closeX = px + pw - CLOSE_SIZE - 4;
        closeY = py + 4;
        boolean hoveringClose = mouseX >= closeX && mouseX < closeX + CLOSE_SIZE
            && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE;
        g.drawString(this.font, "X", closeX, closeY,
            hoveringClose ? CLOSE_HOVER_COLOR : CLOSE_COLOR);

        /* 两列固定列宽（面板内宽等分）*/
        int colW = Math.max(40, (pw - PADDING * 2 - COL_GAP) / 2);
        int colX1 = px + PADDING;
        int colX2 = colX1 + colW + COL_GAP;
        int headerY = py + HEADER_HEIGHT + 4;
        int listTop = headerY + LINE_HEIGHT + 2;
        int listBottom = py + ph - PADDING;

        String emptyText = Component.translatable(
            "screen.virtual_redstone_wire.history_empty").getString();
        List<List<String>> undoLines = wrapAll(
            buildLines(CableUndoRedoManager.getUndoSnapshot()), colW);
        List<List<String>> redoLines = wrapAll(
            buildLines(CableUndoRedoManager.getRedoSnapshot()), colW);

        /* 双列整体滚动：以较高一列为准计算滚动范围*/
        int viewH = Math.max(1, listBottom - listTop);
        int contentH = Math.max(rowCount(undoLines), rowCount(redoLines)) * LINE_HEIGHT;
        maxScroll = Math.max(0, contentH - viewH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        renderColumn(g, colX1, colW, headerY, listTop, listBottom,
            Component.translatable("screen.virtual_redstone_wire.undo_history").getString(),
            UNDO_TITLE_COLOR, undoLines, emptyText);
        renderColumn(g, colX2, colW, headerY, listTop, listBottom,
            Component.translatable("screen.virtual_redstone_wire.redo_history").getString(),
            REDO_TITLE_COLOR, redoLines, emptyText);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
    {
        scrollOffset = Math.max(0, Math.min(scrollOffset - scrollY * LINE_HEIGHT * 2, maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (mouseX >= closeX && mouseX < closeX + CLOSE_SIZE
            && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE)
        {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 窗口页内快捷键：Ctrl+A 关闭（与打开键一致）；Ctrl+Z / Ctrl+Y 直接撤销/重做并实时刷新本页 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_A)
        {
            this.onClose();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z)
        {
            CableUndoRedoManager.undo();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y)
        {
            CableUndoRedoManager.redo();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 渲染单列：表头水平居中、内容按滚动偏移绘制并裁剪到列表区 */
    private void renderColumn(GuiGraphics g, int x, int colW, int headerY, int listTop, int listBottom,
                              String title, int titleColor, List<List<String>> entries, String emptyText)
    {
        Font font = this.font;
        g.drawCenteredString(font, Component.literal(title), x + colW / 2, headerY, titleColor);

        g.enableScissor(x, listTop, x + colW, listBottom);
        if (entries.isEmpty())
        {
            g.drawCenteredString(font, Component.literal(emptyText),
                x + colW / 2, listTop, EMPTY_COLOR);
        }
        else
        {
            int y = listTop - (int) scrollOffset;
            for (List<String> entry : entries)
            {
                for (String line : entry)
                {
                    g.drawString(font, Component.literal(line), x, y, ENTRY_COLOR);
                    y += LINE_HEIGHT;
                }
            }
        }
        g.disableScissor();
    }

    /** 折行全部条目，返回每条目对应的多行结果 */
    private List<List<String>> wrapAll(List<String> entries, int maxWidth)
    {
        List<List<String>> result = new ArrayList<>();
        for (String entry : entries)
        {
            result.add(wrap(entry, maxWidth));
        }
        return result;
    }

    private static int rowCount(List<List<String>> wrapped)
    {
        int rows = 0;
        for (List<String> entry : wrapped)
        {
            rows += entry.size();
        }
        return rows;
    }

    /** 超宽文本按列宽逐字符折行（中英文通用），行首不保留空格 */
    private List<String> wrap(String text, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < text.length())
        {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (cur.length() > 0 && this.font.width(cur.toString() + ch) > maxWidth)
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
            /* 条目所属维度与缓存维度不一致时在行尾标注，说明该条目为何不可回放*/
            String dimSuffix = p.dimension != null
                && !p.dimension.equals(com.virtualredstonewire.client.ClientCableCache.getDimension())
                ? " @" + p.dimension : "";
            if (p.links.size() == 1)
            {
                CableOpPacket.Link l = p.links.get(0);
                lines.add(type + " [" + l.fx() + "," + l.fy() + "," + l.fz() + "] -> ["
                    + l.tx() + "," + l.ty() + "," + l.tz() + "] "
                    + com.virtualredstonewire.util.FaceNames.of(l.face()) + dimSuffix);
            }
            else
            {
                lines.add(type + " x" + p.links.size() + dimSuffix);
            }
        }
        return lines;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
