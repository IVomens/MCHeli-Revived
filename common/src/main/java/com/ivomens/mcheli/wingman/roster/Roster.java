package com.ivomens.mcheli.wingman.roster;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Every wingman of one server, followers and autonomous aircraft alike, keyed by aircraft UUID in the
 * order they joined. It lives only as long as its server and is never saved. Used on the server
 * thread only.
 */
public final class Roster {

    private final Map<UUID, Wingman> entries = new LinkedHashMap<>();
    /** Read-only copy handed out by {@link #snapshot()}; rebuilt after a change. */
    private Map<UUID, Wingman> snapshot = Collections.emptyMap();
    private boolean snapshotStale;

    public void put(UUID aircraft, Wingman wingman) {
        this.entries.put(aircraft, wingman);
        this.snapshotStale = true;
    }

    public void remove(UUID aircraft) {
        if (this.entries.remove(aircraft) != null) {
            this.snapshotStale = true;
        }
    }

    /** Forgets every wingman. */
    public void clear() {
        this.entries.clear();
        this.snapshot = Collections.emptyMap();
        this.snapshotStale = false;
    }

    public @Nullable Wingman get(UUID aircraft) {
        return this.entries.get(aircraft);
    }

    public boolean contains(UUID aircraft) {
        return this.entries.containsKey(aircraft);
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /**
     * A read-only copy for iterating while the roster may change. The tick asks for it several times,
     * so the copy is shared until the roster changes.
     */
    public Map<UUID, Wingman> snapshot() {
        if (this.snapshotStale) {
            this.snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(this.entries));
            this.snapshotStale = false;
        }
        return this.snapshot;
    }

    /**
     * Wingmen following this leader. The leader queries match nothing for a null leader: autonomous
     * aircraft have none, and they must not be caught by a player who is on foot.
     */
    public int countFollowing(@Nullable Entity leader) {
        return followersOf(leader).size();
    }

    public void releaseFollowersOf(@Nullable Entity leader) {
        if (leader != null && this.entries.values().removeIf(w -> w.leader() == leader)) {
            this.snapshotStale = true;
        }
    }

    /** The wingmen following this leader, in roster order. */
    public List<Wingman> followersOf(@Nullable Entity leader) {
        List<Wingman> result = new ArrayList<>();
        if (leader == null) {
            return result;
        }
        for (Wingman w : this.entries.values()) {
            if (w.leader() == leader) {
                result.add(w);
            }
        }
        return result;
    }
}
