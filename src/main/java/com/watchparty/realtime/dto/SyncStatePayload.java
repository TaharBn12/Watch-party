package com.watchparty.realtime.dto;

import com.watchparty.member.dto.RoomMemberResponse;
import com.watchparty.message.dto.MessageResponse;
import com.watchparty.playlist.dto.PlaylistItemResponse;
import com.watchparty.room.dto.RoomResponse;

import java.util.List;

public record SyncStatePayload(
        RoomResponse room,
        List<RoomMemberResponse> members,
        List<MessageResponse> recentMessages,
        List<PlaylistItemResponse> playlist
) {
}
