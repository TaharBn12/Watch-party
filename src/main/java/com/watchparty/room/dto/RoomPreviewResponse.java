package com.watchparty.room.dto;

import com.watchparty.room.entity.RoomEntity;

import java.time.Instant;
import java.util.UUID;

public record RoomPreviewResponse(
        UUID id,
        String inviteCode,
        String name,
        UUID hostId,
        boolean publicRoom,
        boolean protectedRoom,
        Instant createdAt
) {
    public static RoomPreviewResponse from(RoomEntity room) {
        return new RoomPreviewResponse(
                room.getId(),
                room.getInviteCode(),
                room.getName(),
                room.getHost() == null ? null : room.getHost().getId(),
                room.isPublicRoom(),
                room.hasPassword(),
                room.getCreatedAt()
        );
    }
}
