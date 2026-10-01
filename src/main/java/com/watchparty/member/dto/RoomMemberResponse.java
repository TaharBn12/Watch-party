package com.watchparty.member.dto;

import com.watchparty.member.entity.MemberRole;
import com.watchparty.member.entity.RoomMemberEntity;

import java.time.Instant;
import java.util.UUID;

public record RoomMemberResponse(
        UUID id,
        UUID roomId,
        UUID userId,
        MemberRole role,
        boolean canControl,
        boolean connected,
        Instant joinedAt,
        Instant leftAt,
        Instant lastSeenAt
) {
    public static RoomMemberResponse from(RoomMemberEntity member) {
        return new RoomMemberResponse(
                member.getId(),
                member.getRoom() == null ? null : member.getRoom().getId(),
                member.getUser() == null ? null : member.getUser().getId(),
                member.getRole(),
                member.isCanControl(),
                member.isConnected(),
                member.getJoinedAt(),
                member.getLeftAt(),
                member.getLastSeenAt()
        );
    }
}
