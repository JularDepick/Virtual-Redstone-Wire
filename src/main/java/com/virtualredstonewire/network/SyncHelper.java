package com.virtualredstonewire.network;

import com.virtualredstonewire.data.CableLink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SyncHelper
{
    public static List<CableSyncPacket.CableSyncEntry> toEntries(Collection<CableLink> links)
    {
        List<CableSyncPacket.CableSyncEntry> entries = new ArrayList<>();
        for (CableLink link : links)
        {
            entries.add(new CableSyncPacket.CableSyncEntry(
                link.getFrom(), link.getTo(), link.getToFace()));
        }
        return entries;
    }
}
