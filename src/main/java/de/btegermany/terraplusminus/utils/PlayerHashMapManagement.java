package de.btegermany.terraplusminus.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerHashMapManagement {

    /**
     * Covers any realistic server switch. Whoever does not arrive within it never will, and replaying
     * their teleport on some later join would drop them somewhere they no longer expect.
     */
    private static final long PENDING_TELEPORT_TIMEOUT_SECONDS = 30;

    private final Cache<UUID, String> players = CacheBuilder.newBuilder()
            .expireAfterWrite(PENDING_TELEPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    public void addPlayer(@NonNull UUID playerId, String coordinates) {
        players.put(playerId, coordinates);
    }

    public void removePlayer(@NonNull UUID playerId) {
        players.invalidate(playerId);
    }

    public @Nullable String getCoordinates(@NonNull UUID playerId) {
        return players.getIfPresent(playerId);
    }

}
