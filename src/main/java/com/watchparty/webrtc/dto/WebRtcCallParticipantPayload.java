package com.watchparty.webrtc.dto;

import java.util.UUID;

public record WebRtcCallParticipantPayload(
        UUID userId,
        boolean audioEnabled,
        boolean videoEnabled
) {
}
