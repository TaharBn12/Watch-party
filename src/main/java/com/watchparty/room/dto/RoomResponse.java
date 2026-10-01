package com.watchparty.room.dto;

import com.watchparty.common.model.VideoType;
import com.watchparty.room.entity.ControlMode;
import com.watchparty.room.entity.PlaybackStatus;
import com.watchparty.room.entity.RoomEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String inviteCode,
        String name,
        UUID hostId,
        boolean publicRoom,
        boolean protectedRoom,
        ControlMode controlMode,
        String currentVideoUrl,
        VideoType currentVideoType,
        PlaybackStatus playbackStatus,
        BigDecimal playbackPositionSeconds,
        Instant playbackUpdatedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
    public static RoomResponse from(RoomEntity room) {
        return new RoomResponse(
                room.getId(),
                room.getInviteCode(),
                room.getName(),
                room.getHost() == null ? null : room.getHost().getId(),
                room.isPublicRoom(),
                room.hasPassword(),
                room.getControlMode(),
                room.getCurrentVideoUrl(),
                room.getCurrentVideoType(),
                room.getPlaybackStatus(),
                room.getPlaybackPositionSeconds(),
                room.getPlaybackUpdatedAt(),
                room.getCreatedAt(),
                room.getUpdatedAt(),
                room.getClosedAt()
        );
    }
}
