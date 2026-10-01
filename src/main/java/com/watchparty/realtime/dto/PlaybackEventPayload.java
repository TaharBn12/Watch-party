package com.watchparty.realtime.dto;

import com.watchparty.common.model.VideoType;
import com.watchparty.room.entity.PlaybackStatus;
import com.watchparty.room.entity.RoomEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record PlaybackEventPayload(
        PlaybackStatus status,
        BigDecimal positionSeconds,
        Instant playbackUpdatedAt,
        String currentVideoUrl,
        VideoType currentVideoType
) {
    public static PlaybackEventPayload from(RoomEntity room) {
        return new PlaybackEventPayload(
                room.getPlaybackStatus(),
                room.getPlaybackPositionSeconds(),
                room.getPlaybackUpdatedAt(),
                room.getCurrentVideoUrl(),
                room.getCurrentVideoType()
        );
    }
}
