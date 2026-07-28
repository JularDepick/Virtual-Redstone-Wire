package com.virtualredstonewire.client;

import com.virtualredstonewire.data.CableLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

public class ClientCableCache
{
    private static final List<CableLink> cachedLinks = new ArrayList<>();

    public static void setLinks(Collection<CableLink> links)
    {
        cachedLinks.clear();
        cachedLinks.addAll(links);
    }

    public static void addLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        for (CableLink link : cachedLinks)
        {
            if (link.getId().equals(id)) return;
        }
        cachedLinks.add(new CableLink(from, to, toFace));
    }

    public static void removeLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        cachedLinks.removeIf(link -> link.getId().equals(id));
    }

    public static boolean hasLinkFrom(BlockPos from)
    {
        for (CableLink link : cachedLinks)
        {
            if (link.getFrom().equals(from)) return true;
        }
        return false;
    }

    public static boolean hasLinkAt(BlockPos pos)
    {
        for (CableLink link : cachedLinks)
        {
            if (link.getFrom().equals(pos) || link.getTo().equals(pos)) return true;
        }
        return false;
    }

    public static List<CableLink> getLinks()
    {
        return cachedLinks;
    }

    public static void clear()
    {
        cachedLinks.clear();
    }
}
