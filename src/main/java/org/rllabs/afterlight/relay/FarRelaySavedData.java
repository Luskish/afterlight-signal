package org.rllabs.afterlight.relay;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class FarRelaySavedData extends SavedData {
    public static final int SCHEMA = 1;

    private static final String DATA_NAME = "afterlight_far_relay";
    private static final String SCHEMA_TAG = "schema";
    private static final String INITIALIZED_SITES_TAG = "initialized_sites";
    private static final Factory<FarRelaySavedData> FACTORY =
            new Factory<>(FarRelaySavedData::new, FarRelaySavedData::load);

    private final EnumSet<RelaySite> initializedSites;

    public FarRelaySavedData() {
        this(EnumSet.noneOf(RelaySite.class));
    }

    private FarRelaySavedData(EnumSet<RelaySite> initializedSites) {
        this.initializedSites = initializedSites;
    }

    public static FarRelaySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FarRelaySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EnumSet<RelaySite> sites = EnumSet.noneOf(RelaySite.class);
        if (tag.getInt(SCHEMA_TAG) == SCHEMA) {
            ListTag initialized = tag.getList(INITIALIZED_SITES_TAG, Tag.TAG_STRING);
            for (Tag siteTag : initialized) {
                try {
                    sites.add(RelaySite.valueOf(siteTag.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new FarRelaySavedData(sites);
    }

    public boolean isInitialized(RelaySite site) {
        return initializedSites.contains(site);
    }

    public boolean markInitialized(RelaySite site) {
        if (!initializedSites.add(site)) {
            return false;
        }
        setDirty();
        return true;
    }

    public Set<RelaySite> initializedSites() {
        return Collections.unmodifiableSet(EnumSet.copyOf(initializedSites));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(SCHEMA_TAG, SCHEMA);
        ListTag initialized = new ListTag();
        for (RelaySite site : RelaySite.values()) {
            if (initializedSites.contains(site)) {
                initialized.add(StringTag.valueOf(site.name()));
            }
        }
        tag.put(INITIALIZED_SITES_TAG, initialized);
        return tag;
    }
}
