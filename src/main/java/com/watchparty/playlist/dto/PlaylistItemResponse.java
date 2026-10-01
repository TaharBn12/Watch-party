package com.watchparty.playlist.dto;

import com.watchparty.common.model.VideoType;
import com.watchparty.playlist.entity.PlaylistItemEntity;
import com.watchparty.playlist.entity.PlaylistStatus;

import java.time.Instant;
import java.util.UUID;

public record PlaylistItemResponse(
        UUID id,
        UUID roomId,
        UUID addedById,
        String url,
        VideoType videoType,
        String title,
        int position,
        PlaylistStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
    public static PlaylistItemResponse from(PlaylistItemEntity item) {
        return new PlaylistItemResponse(
                item.getId(),
                item.getRoom() == null ? null : item.getRoom().getId(),
                item.getAddedBy() == null ? null : item.getAddedBy().getId(),
                item.getUrl(),
                item.getVideoType(),
                item.getTitle(),
                item.getPosition(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getStartedAt(),
                item.getEndedAt()
        );
    }
}
