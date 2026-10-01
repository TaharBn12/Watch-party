package com.watchparty.realtime.controller;

import com.watchparty.common.exception.UnauthorizedException;
import com.watchparty.member.dto.RoomMemberResponse;
import com.watchparty.member.entity.RoomMemberEntity;
import com.watchparty.member.service.RoomMemberService;
import com.watchparty.message.dto.MessageResponse;
import com.watchparty.message.entity.MessageEntity;
import com.watchparty.message.service.MessageService;
import com.watchparty.realtime.dto.ChangeVideoCommand;
import com.watchparty.realtime.dto.ChatCommand;
import com.watchparty.realtime.dto.ChatEventPayload;
import com.watchparty.realtime.dto.MemberEventPayload;
import com.watchparty.realtime.dto.PlaybackCommand;
import com.watchparty.realtime.dto.PlaybackEventPayload;
import com.watchparty.realtime.dto.RealtimeDestinations;
import com.watchparty.realtime.dto.RealtimeEnvelope;
import com.watchparty.realtime.dto.RealtimeErrorResponse;
import com.watchparty.realtime.dto.RealtimeEventType;
import com.watchparty.realtime.dto.SyncStatePayload;
import com.watchparty.realtime.service.RealtimeAuthorizationService;
import com.watchparty.realtime.service.RealtimeSessionRegistry;
import com.watchparty.realtime.service.SyncStateService;
import com.watchparty.room.entity.PlaybackStatus;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.service.RoomService;
import com.watchparty.security.SupabaseUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.UUID;

@Controller
@Validated
public class RealtimeRoomController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RealtimeAuthorizationService realtimeAuthorizationService;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final SyncStateService syncStateService;
    private final RoomMemberService roomMemberService;
    private final MessageService messageService;
    private final RoomService roomService;

    public RealtimeRoomController(
            SimpMessagingTemplate messagingTemplate,
            RealtimeAuthorizationService realtimeAuthorizationService,
            RealtimeSessionRegistry realtimeSessionRegistry,
            SyncStateService syncStateService,
            RoomMemberService roomMemberService,
            MessageService messageService,
            RoomService roomService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.realtimeAuthorizationService = realtimeAuthorizationService;
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.syncStateService = syncStateService;
        this.roomMemberService = roomMemberService;
        this.messageService = messageService;
        this.roomService = roomService;
    }

    @MessageMapping("/rooms/{roomId}/join")
    public void joinRoom(
            @DestinationVariable UUID roomId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.JOIN);

        RoomMemberEntity member = roomMemberService.markConnected(roomId, user.id());
        realtimeSessionRegistry.register(headerAccessor.getSessionId(), roomId);

        broadcast(roomId, RealtimeEventType.JOIN, user.id(), new MemberEventPayload(RoomMemberResponse.from(member)));
        sendSyncState(roomId, user);
    }

    @MessageMapping("/rooms/{roomId}/leave")
    public void leaveRoom(
            @DestinationVariable UUID roomId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.LEAVE);

        RoomMemberEntity member = roomMemberService.markDisconnected(roomId, user.id());
        realtimeSessionRegistry.unregister(headerAccessor.getSessionId(), roomId);

        broadcast(roomId, RealtimeEventType.LEAVE, user.id(), new MemberEventPayload(RoomMemberResponse.from(member)));
    }

    @MessageMapping("/rooms/{roomId}/chat")
    public void chat(
            @DestinationVariable UUID roomId,
            @Valid @Payload ChatCommand command,
            Principal principal
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.CHAT);

        MessageEntity message = messageService.sendChatMessage(roomId, user.id(), command.content());
        broadcast(roomId, RealtimeEventType.CHAT, user.id(), new ChatEventPayload(MessageResponse.from(message)));
    }

    @MessageMapping("/rooms/{roomId}/play")
    public void play(
            @DestinationVariable UUID roomId,
            @Valid @Payload PlaybackCommand command,
            Principal principal
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.PLAY);

        RoomEntity room = roomService.updatePlayback(user.id(), roomId, PlaybackStatus.PLAYING, command.positionSeconds());
        broadcast(roomId, RealtimeEventType.PLAY, user.id(), PlaybackEventPayload.from(room));
    }

    @MessageMapping("/rooms/{roomId}/pause")
    public void pause(
            @DestinationVariable UUID roomId,
            @Valid @Payload PlaybackCommand command,
            Principal principal
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.PAUSE);

        RoomEntity room = roomService.updatePlayback(user.id(), roomId, PlaybackStatus.PAUSED, command.positionSeconds());
        broadcast(roomId, RealtimeEventType.PAUSE, user.id(), PlaybackEventPayload.from(room));
    }

    @MessageMapping("/rooms/{roomId}/seek")
    public void seek(
            @DestinationVariable UUID roomId,
            @Valid @Payload PlaybackCommand command,
            Principal principal
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.SEEK);

        PlaybackStatus status = command.status() == null
                ? roomService.getOpenRoom(roomId).getPlaybackStatus()
                : command.status();
        RoomEntity room = roomService.updatePlayback(user.id(), roomId, status, command.positionSeconds());
        broadcast(roomId, RealtimeEventType.SEEK, user.id(), PlaybackEventPayload.from(room));
    }

    @MessageMapping("/rooms/{roomId}/video")
    public void changeVideo(
            @DestinationVariable UUID roomId,
            @Valid @Payload ChangeVideoCommand command,
            Principal principal
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.CHANGE_VIDEO);

        RoomEntity room = roomService.changeVideo(user.id(), roomId, command.videoUrl());
        broadcast(roomId, RealtimeEventType.CHANGE_VIDEO, user.id(), PlaybackEventPayload.from(room));
    }

    @MessageMapping("/rooms/{roomId}/sync")
    public void syncState(@DestinationVariable UUID roomId, Principal principal) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.SYNC_STATE);
        sendSyncState(roomId, user);
    }

    @MessageExceptionHandler
    public void handleRealtimeException(Throwable exception, Principal principal) {
        if (principal == null) {
            return;
        }

        RealtimeErrorResponse error = RealtimeErrorResponse.of(
                exception.getClass().getSimpleName(),
                exception.getMessage() == null ? "حدث خطأ في WebSocket" : exception.getMessage()
        );
        messagingTemplate.convertAndSendToUser(principal.getName(), RealtimeDestinations.USER_ERRORS_QUEUE, error);
    }

    private <T> void broadcast(UUID roomId, RealtimeEventType type, UUID senderId, T payload) {
        messagingTemplate.convertAndSend(
                RealtimeDestinations.roomEvents(roomId),
                RealtimeEnvelope.of(type, roomId, senderId, payload)
        );
    }

    private void sendSyncState(UUID roomId, SupabaseUserPrincipal user) {
        SyncStatePayload state = syncStateService.buildSyncState(roomId, user.id());
        messagingTemplate.convertAndSendToUser(
                user.getName(),
                RealtimeDestinations.userRoomSync(roomId),
                RealtimeEnvelope.of(RealtimeEventType.SYNC_STATE, roomId, user.id(), state)
        );
    }

    private SupabaseUserPrincipal requireSupabasePrincipal(Principal principal) {
        if (principal instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
            return supabaseUserPrincipal;
        }
        throw new UnauthorizedException("WebSocket يتطلب Supabase JWT صالحًا");
    }
}
