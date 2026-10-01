package com.watchparty.realtime.dto;

import java.time.Instant;

public record RealtimeErrorResponse(
        Instant timestamp,
        String code,
        String message
) {
    public static RealtimeErrorResponse of(String code, String message) {
        return new RealtimeErrorResponse(Instant.now(), code, message);
    }
}
