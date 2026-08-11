package org.rllabs.afterlight.relay;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.OptionalInt;
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
    private static final String PLATFORM_HEIGHTS_TAG = "platform_heights";
    private static final Factory<FarRelaySavedData> FACTORY =
            new Factory<>(FarRelaySavedData::new, FarRelaySavedData::load);

    private final EnumSet<RelaySite> initializedSites;
    private final EnumMap<RelaySite, Integer> platformHeights;

    public FarRelaySavedData() {
        this(EnumSet.noneOf(RelaySite.class), new EnumMap<>(RelaySite.class));
    }

    private FarRelaySavedData(
            EnumSet<RelaySite> initializedSites,
            EnumMap<RelaySite, Integer> platformHeights) {
        this.initializedSites = initializedSites;
        this.platformHeights = platformHeights;
    }

    public static FarRelaySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FarRelaySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EnumSet<RelaySite> sites = EnumSet.noneOf(RelaySite.class);
        EnumMap<RelaySite, Integer> heights = new EnumMap<>(RelaySite.class);
        if (tag.getInt(SCHEMA_TAG) == SCHEMA) {
            ListTag initialized = tag.getList(INITIALIZED_SITES_TAG, Tag.TAG_STRING);
            CompoundTag storedHeights = tag.getCompound(PLATFORM_HEIGHTS_TAG);
            for (Tag siteTag : initialized) {
                try {
                    RelaySite site = RelaySite.valueOf(siteTag.getAsString());
                    sites.add(site);
                    if (storedHeights.contains(site.name(), Tag.TAG_INT)) {
                        heights.put(site, storedHeights.getInt(site.name()));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new FarRelaySavedData(sites, heights);
    }

    public boolean isInitialized(RelaySite site) {
        return initializedSites.contains(site);
    }

    public boolean markInitialized(RelaySite site, int platformY) {
        boolean changed = initializedSites.add(site);
        if (!platformHeights.containsKey(site)) {
            platformHeights.put(site, platformY);
            changed = true;
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public OptionalInt platformY(RelaySite site) {
        Integer platformY = platformHeights.get(site);
        return platformY == null ? OptionalInt.empty() : OptionalInt.of(platformY);
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
        CompoundTag heights = new CompoundTag();
        for (Map.Entry<RelaySite, Integer> entry : platformHeights.entrySet()) {
            if (initializedSites.contains(entry.getKey())) {
                heights.putInt(entry.getKey().name(), entry.getValue());
            }
        }
        tag.put(PLATFORM_HEIGHTS_TAG, heights);
        return tag;
    }
}
