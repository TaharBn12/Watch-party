package com.watchparty.webrtc.dto;

import java.util.UUID;

public record WebRtcSignalPayload(
        UUID fromUserId,
        UUID targetUserId,
        String sdpType,
        String sdp,
        WebRtcIceCandidatePayload candidate
) {
}
