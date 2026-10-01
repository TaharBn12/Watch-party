package com.watchparty.realtime.service;

import com.watchparty.member.dto.RoomMemberResponse;
import com.watchparty.member.service.RoomMemberService;
import com.watchparty.message.dto.MessageResponse;
import com.watchparty.message.service.MessageService;
import com.watchparty.playlist.dto.PlaylistItemResponse;
import com.watchparty.playlist.service.PlaylistService;
import com.watchparty.realtime.dto.SyncStatePayload;
import com.watchparty.room.dto.RoomResponse;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.service.RoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SyncStateService {

    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final MessageService messageService;
    private final PlaylistService playlistService;

    public SyncStateService(
            RoomService roomService,
            RoomMemberService roomMemberService,
            MessageService messageService,
            PlaylistService playlistService
    ) {
        this.roomService = roomService;
        this.roomMemberService = roomMemberService;
        this.messageService = messageService;
        this.playlistService = playlistService;
    }

    @Transactional(readOnly = true)
    public SyncStatePayload buildSyncState(UUID roomId, UUID requesterId) {
        RoomEntity room = roomService.getOpenRoom(roomId);
        return new SyncStatePayload(
                RoomResponse.from(room),
                roomMemberService.listActiveMembers(roomId, requesterId).stream()
                        .map(RoomMemberResponse::from)
                        .toList(),
                messageService.getRecentMessages(roomId, requesterId).stream()
                        .map(MessageResponse::from)
                        .toList(),
                playlistService.listPlaylist(roomId, requesterId).stream()
                        .map(PlaylistItemResponse::from)
                        .toList()
        );
    }
}
