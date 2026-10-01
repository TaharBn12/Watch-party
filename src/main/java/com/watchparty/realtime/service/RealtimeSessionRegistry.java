package com.watchparty.realtime.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RealtimeSessionRegistry {

    private final ConcurrentMap<String, Set<UUID>> sessionRooms = new ConcurrentHashMap<>();

    public void register(String sessionId, UUID roomId) {
        if (!StringUtils.hasText(sessionId) || roomId == null) {
            return;
        }
        sessionRooms.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(roomId);
    }

    public void unregister(String sessionId, UUID roomId) {
        if (!StringUtils.hasText(sessionId) || roomId == null) {
            return;
        }

        Set<UUID> rooms = sessionRooms.get(sessionId);
        if (rooms == null) {
            return;
        }

        rooms.remove(roomId);
        if (rooms.isEmpty()) {
            sessionRooms.remove(sessionId);
        }
    }

    public Set<UUID> unregisterSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Set.of();
        }

        Set<UUID> rooms = sessionRooms.remove(sessionId);
        if (rooms == null || rooms.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(rooms);
    }
}
