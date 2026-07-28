package com.virtualredstonewire.data;

import com.google.common.hash.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import java.nio.charset.StandardCharsets;

public class CableLink
{
    private final String id;
    private final BlockPos from;
    private final BlockPos to;
    private final Direction toFace;

    public CableLink(BlockPos from, BlockPos to, Direction toFace)
    {
        this.from = from.immutable();
        this.to = to.immutable();
        this.toFace = toFace;
        this.id = generateId(from, to, toFace);
    }

    public CableLink(String id, BlockPos from, BlockPos to, Direction toFace)
    {
        this.id = id;
        this.from = from.immutable();
        this.to = to.immutable();
        this.toFace = toFace;
    }

    public static String generateId(BlockPos from, BlockPos to, Direction face)
    {
        String input = from.getX() + "," + from.getY() + "," + from.getZ() + "|"
            + to.getX() + "," + to.getY() + "," + to.getZ() + "|"
            + face.getName();
        return Hashing.sha512().hashString(input, StandardCharsets.UTF_8).toString();
    }

    public String getId() { return id; }
    public BlockPos getFrom() { return from; }
    public BlockPos getTo() { return to; }
    public Direction getToFace() { return toFace; }

    public double getFromCenterX() { return from.getX() + 0.5; }
    public double getFromCenterY() { return from.getY() + 0.5; }
    public double getFromCenterZ() { return from.getZ() + 0.5; }

    public double getFaceCenterX()
    {
        double cx = to.getX() + 0.5;
        double cy = to.getY() + 0.5;
        double cz = to.getZ() + 0.5;
        switch (toFace)
        {
            case DOWN:  return cx;
            case UP:    return cx;
            case NORTH: return cx;
            case SOUTH: return cx;
            case WEST:  return to.getX();
            case EAST:  return to.getX() + 1.0;
        }
        return cx;
    }

    public double getFaceCenterY()
    {
        double cx = to.getX() + 0.5;
        double cy = to.getY() + 0.5;
        double cz = to.getZ() + 0.5;
        switch (toFace)
        {
            case DOWN:  return to.getY();
            case UP:    return to.getY() + 1.0;
            case NORTH: return cy;
            case SOUTH: return cy;
            case WEST:  return cy;
            case EAST:  return cy;
        }
        return cy;
    }

    public double getFaceCenterZ()
    {
        double cx = to.getX() + 0.5;
        double cy = to.getY() + 0.5;
        double cz = to.getZ() + 0.5;
        switch (toFace)
        {
            case DOWN:  return cz;
            case UP:    return cz;
            case NORTH: return to.getZ();
            case SOUTH: return to.getZ() + 1.0;
            case WEST:  return cz;
            case EAST:  return cz;
        }
        return cz;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof CableLink that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }
}
