package com.virtualredstonewire.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;

public class CableActionPacket
{
    private final boolean added;
    private final BlockPos from;
    private final BlockPos to;
    private final Direction toFace;
    private final boolean isCutter;

    public CableActionPacket(boolean added, BlockPos from, BlockPos to, Direction toFace)
    {
        this.added = added;
        this.from = from.immutable();
        this.to = to.immutable();
        this.toFace = toFace;
        this.isCutter = false;
    }

    private CableActionPacket(BlockPos from, boolean cutterTag)
    {
        this.added = false;
        this.from = from.immutable();
        this.to = from.immutable();
        this.toFace = null;
        this.isCutter = true;
    }

    public static CableActionPacket cutter(BlockPos pos)
    {
        return new CableActionPacket(pos, true);
    }

    public CableActionPacket(FriendlyByteBuf buf)
    {
        this.added = buf.readBoolean();
        this.from = buf.readBlockPos();
        this.to = buf.readBlockPos();
        this.isCutter = buf.readBoolean();
        if (!isCutter)
        {
            this.toFace = buf.readEnum(Direction.class);
        }
        else
        {
            this.toFace = null;
        }
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBoolean(added);
        buf.writeBlockPos(from);
        buf.writeBlockPos(to);
        buf.writeBoolean(isCutter);
        if (!isCutter)
        {
            buf.writeEnum(toFace != null ? toFace : Direction.NORTH);
        }
    }

    public boolean isAdded() { return added; }
    public boolean isCutter() { return isCutter; }
    public BlockPos getFrom() { return from; }
    public BlockPos getTo() { return to; }
    public Direction getToFace() { return toFace; }
}
