package com.watchparty.realtime.dto;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEnvelope<T>(
        RealtimeEventType type,
        UUID roomId,
        UUID senderId,
        Instant sentAt,
        T payload
) {
    public static <T> RealtimeEnvelope<T> of(RealtimeEventType type, UUID roomId, UUID senderId, T payload) {
        return new RealtimeEnvelope<>(type, roomId, senderId, Instant.now(), payload);
    }
}
