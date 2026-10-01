package com.watchparty.realtime.service;

import com.watchparty.member.dto.RoomMemberResponse;
import com.watchparty.member.entity.RoomMemberEntity;
import com.watchparty.member.service.RoomMemberService;
import com.watchparty.realtime.dto.MemberEventPayload;
import com.watchparty.realtime.dto.RealtimeDestinations;
import com.watchparty.realtime.dto.RealtimeEnvelope;
import com.watchparty.realtime.dto.RealtimeEventType;
import com.watchparty.security.SupabaseUserPrincipal;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

@Component
public class RealtimeDisconnectListener implements ApplicationListener<SessionDisconnectEvent> {

    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final RoomMemberService roomMemberService;
    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeDisconnectListener(
            RealtimeSessionRegistry realtimeSessionRegistry,
            RoomMemberService roomMemberService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.roomMemberService = roomMemberService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (!(principal instanceof SupabaseUserPrincipal user)) {
            return;
        }

        Set<UUID> roomIds = realtimeSessionRegistry.unregisterSession(event.getSessionId());
        for (UUID roomId : roomIds) {
            try {
                RoomMemberEntity member = roomMemberService.markDisconnected(roomId, user.id());
                messagingTemplate.convertAndSend(
                        RealtimeDestinations.roomEvents(roomId),
                        RealtimeEnvelope.of(
                                RealtimeEventType.LEAVE,
                                roomId,
                                user.id(),
                                new MemberEventPayload(RoomMemberResponse.from(member))
                        )
                );
            } catch (RuntimeException ignored) {
                // التعليق بالعربية: إذا كانت الغرفة أُغلقت أو العضو خرج مسبقًا، لا نكسر دورة فصل WebSocket.
            }
        }
    }
}
