package com.watchparty.realtime.dto;

import java.util.UUID;

public final class RealtimeDestinations {

    public static final String USER_ERRORS_QUEUE = "/queue/errors";

    private RealtimeDestinations() {
    }

    public static String roomEvents(UUID roomId) {
        return "/topic/rooms/" + roomId + "/events";
    }

    public static String userRoomSync(UUID roomId) {
        return "/queue/rooms/" + roomId + "/sync";
    }

    public static String userRoomWebRtc(UUID roomId) {
        return "/queue/rooms/" + roomId + "/webrtc";
    }
}
