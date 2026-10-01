package com.watchparty.webrtc.dto;

public record WebRtcCallStateCommand(
        Boolean audioEnabled,
        Boolean videoEnabled
) {
    public boolean audioEnabledValue() {
        return audioEnabled == null || audioEnabled;
    }

    public boolean videoEnabledValue() {
        return videoEnabled == null || videoEnabled;
    }
}
