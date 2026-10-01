package com.watchparty.webrtc.controller;

import com.watchparty.common.exception.BadRequestException;
import com.watchparty.common.exception.UnauthorizedException;
import com.watchparty.common.ratelimit.RateLimiterService;
import com.watchparty.realtime.dto.RealtimeDestinations;
import com.watchparty.realtime.dto.RealtimeEnvelope;
import com.watchparty.realtime.dto.RealtimeEventType;
import com.watchparty.realtime.service.RealtimeAuthorizationService;
import com.watchparty.room.service.RoomPermissionService;
import com.watchparty.security.SupabaseUserPrincipal;
import com.watchparty.webrtc.dto.WebRtcCallParticipantPayload;
import com.watchparty.webrtc.dto.WebRtcCallStateCommand;
import com.watchparty.webrtc.dto.WebRtcSignalCommand;
import com.watchparty.webrtc.dto.WebRtcSignalPayload;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

@Controller
@Validated
public class WebRtcSignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RealtimeAuthorizationService realtimeAuthorizationService;
    private final RoomPermissionService roomPermissionService;
    private final RateLimiterService rateLimiterService;

    public WebRtcSignalingController(
            SimpMessagingTemplate messagingTemplate,
            RealtimeAuthorizationService realtimeAuthorizationService,
            RoomPermissionService roomPermissionService,
            RateLimiterService rateLimiterService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.realtimeAuthorizationService = realtimeAuthorizationService;
        this.roomPermissionService = roomPermissionService;
        this.rateLimiterService = rateLimiterService;
    }

    @MessageMapping("/rooms/{roomId}/webrtc/join")
    public void joinCall(
            @DestinationVariable UUID roomId,
            @Valid @Payload WebRtcCallStateCommand command,
            Principal principal
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.WEBRTC_JOIN_CALL);

        messagingTemplate.convertAndSend(
                RealtimeDestinations.roomEvents(roomId),
                RealtimeEnvelope.of(
                        RealtimeEventType.WEBRTC_JOIN_CALL,
                        roomId,
                        user.id(),
                        new WebRtcCallParticipantPayload(
                                user.id(),
                                command == null || command.audioEnabledValue(),
                                command == null || command.videoEnabledValue()
                        )
                )
        );
    }

    @MessageMapping("/rooms/{roomId}/webrtc/leave")
    public void leaveCall(
            @DestinationVariable UUID roomId,
            @Valid @Payload WebRtcCallStateCommand command,
            Principal principal
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), RealtimeEventType.WEBRTC_LEAVE_CALL);

        messagingTemplate.convertAndSend(
                RealtimeDestinations.roomEvents(roomId),
                RealtimeEnvelope.of(
                        RealtimeEventType.WEBRTC_LEAVE_CALL,
                        roomId,
                        user.id(),
                        new WebRtcCallParticipantPayload(user.id(), false, false)
                )
        );
    }

    @MessageMapping("/rooms/{roomId}/webrtc/offer")
    public void offer(
            @DestinationVariable UUID roomId,
            @Valid @Payload WebRtcSignalCommand command,
            Principal principal
    ) {
        forwardSignal(roomId, command, principal, RealtimeEventType.WEBRTC_OFFER);
    }

    @MessageMapping("/rooms/{roomId}/webrtc/answer")
    public void answer(
            @DestinationVariable UUID roomId,
            @Valid @Payload WebRtcSignalCommand command,
            Principal principal
    ) {
        forwardSignal(roomId, command, principal, RealtimeEventType.WEBRTC_ANSWER);
    }

    @MessageMapping("/rooms/{roomId}/webrtc/ice-candidate")
    public void iceCandidate(
            @DestinationVariable UUID roomId,
            @Valid @Payload WebRtcSignalCommand command,
            Principal principal
    ) {
        forwardSignal(roomId, command, principal, RealtimeEventType.WEBRTC_ICE_CANDIDATE);
    }

    private void forwardSignal(
            UUID roomId,
            WebRtcSignalCommand command,
            Principal principal,
            RealtimeEventType eventType
    ) {
        SupabaseUserPrincipal user = requireSupabasePrincipal(principal);
        realtimeAuthorizationService.authorize(roomId, user.id(), eventType);
        rateLimiterService.check(
                "webrtc:" + roomId + ":" + user.id(),
                240,
                Duration.ofMinutes(1),
                "تم تجاوز حد رسائل WebRTC signaling"
        );

        validateSignalPayload(command, eventType);

        if (command.targetUserId().equals(user.id())) {
            throw new BadRequestException("لا يمكن إرسال WebRTC signaling إلى نفسك");
        }

        // التعليق بالعربية: نتحقق أن الطرف الهدف عضو في نفس الغرفة حتى لا تستخدم قناة signaling لإزعاج مستخدمين خارج الغرفة.
        roomPermissionService.requireActiveMember(roomId, command.targetUserId());

        messagingTemplate.convertAndSendToUser(
                command.targetUserId().toString(),
                RealtimeDestinations.userRoomWebRtc(roomId),
                RealtimeEnvelope.of(
                        eventType,
                        roomId,
                        user.id(),
                        new WebRtcSignalPayload(
                                user.id(),
                                command.targetUserId(),
                                command.sdpType(),
                                command.sdp(),
                                command.candidate()
                        )
                )
        );
    }

    private void validateSignalPayload(WebRtcSignalCommand command, RealtimeEventType eventType) {
        if (command == null) {
            throw new BadRequestException("Payload الخاص بـ WebRTC signaling مطلوب");
        }
        if ((eventType == RealtimeEventType.WEBRTC_OFFER || eventType == RealtimeEventType.WEBRTC_ANSWER)
                && (command.sdp() == null || command.sdp().isBlank())) {
            throw new BadRequestException("SDP مطلوب في offer/answer");
        }
        if (eventType == RealtimeEventType.WEBRTC_ICE_CANDIDATE && command.candidate() == null) {
            throw new BadRequestException("ICE candidate مطلوب");
        }
    }

    private SupabaseUserPrincipal requireSupabasePrincipal(Principal principal) {
        if (principal instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
            return supabaseUserPrincipal;
        }
        throw new UnauthorizedException("WebRTC signaling يتطلب Supabase JWT صالحًا");
    }
}
