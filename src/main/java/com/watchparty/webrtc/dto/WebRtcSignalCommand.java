package com.watchparty.webrtc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WebRtcSignalCommand(
        @NotNull(message = "المستخدم الهدف مطلوب")
        UUID targetUserId,

        @Size(max = 32, message = "نوع SDP طويل جدًا")
        String sdpType,

        @Size(max = 200000, message = "SDP طويل جدًا")
        String sdp,

        @Valid
        WebRtcIceCandidatePayload candidate
) {
}
