package com.watchparty.webrtc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebRtcIceCandidatePayload(
        @NotBlank(message = "ICE candidate مطلوب")
        @Size(max = 8000, message = "ICE candidate طويل جدًا")
        String candidate,

        @Size(max = 128, message = "sdpMid طويل جدًا")
        String sdpMid,

        Integer sdpMLineIndex,

        @Size(max = 256, message = "usernameFragment طويل جدًا")
        String usernameFragment
) {
}
