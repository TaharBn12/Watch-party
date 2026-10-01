package com.watchparty.realtime.service;

import com.watchparty.realtime.dto.RealtimeEventType;
import com.watchparty.room.service.RoomPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RealtimeAuthorizationService {

    private final RoomPermissionService roomPermissionService;

    public RealtimeAuthorizationService(RoomPermissionService roomPermissionService) {
        this.roomPermissionService = roomPermissionService;
    }

    /**
     * نقطة مركزية لصلاحيات WebSocket قبل تنفيذ أي حدث لحظي.
     *
     * التعليق بالعربية: كل Controller لحظي في المرحلة 7 يجب أن يستدعي هذه الدالة قبل بث الحدث،
     * حتى لا يمر PLAY أو KICK أو CHANGE_VIDEO من مستخدم لا يملك الصلاحية.
     */
    @Transactional(readOnly = true)
    public void authorize(UUID roomId, UUID userId, RealtimeEventType eventType) {
        switch (eventType) {
            case PLAY, PAUSE, SEEK, CHANGE_VIDEO, PLAY_NEXT ->
                    roomPermissionService.requireControlPermission(roomId, userId);

            case KICK_MEMBER, TRANSFER_HOST, SET_CONTROL_PERMISSION ->
                    roomPermissionService.requireHost(roomId, userId);

            case JOIN, LEAVE, CHAT, EMOJI, SYNC_STATE,
                    WEBRTC_JOIN_CALL, WEBRTC_LEAVE_CALL,
                    WEBRTC_OFFER, WEBRTC_ANSWER, WEBRTC_ICE_CANDIDATE ->
                    roomPermissionService.requireActiveMember(roomId, userId);
        }
    }

    @Transactional(readOnly = true)
    public boolean canBroadcast(UUID roomId, UUID userId, RealtimeEventType eventType) {
        try {
            authorize(roomId, userId, eventType);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
