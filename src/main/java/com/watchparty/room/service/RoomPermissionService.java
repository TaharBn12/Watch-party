package com.watchparty.room.service;

import com.watchparty.common.exception.ForbiddenException;
import com.watchparty.common.exception.ResourceNotFoundException;
import com.watchparty.member.entity.RoomMemberEntity;
import com.watchparty.member.repository.RoomMemberRepository;
import com.watchparty.room.entity.ControlMode;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RoomPermissionService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    public RoomPermissionService(RoomRepository roomRepository, RoomMemberRepository roomMemberRepository) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
    }

    @Transactional(readOnly = true)
    public RoomEntity requireOpenRoom(UUID roomId) {
        return roomRepository.findByIdAndClosedAtIsNull(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("الغرفة غير موجودة أو مغلقة"));
    }

    @Transactional(readOnly = true)
    public RoomEntity requireOpenRoomByInviteCode(String inviteCode) {
        return roomRepository.findByInviteCodeAndClosedAtIsNull(inviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("رابط الدعوة غير صحيح أو الغرفة مغلقة"));
    }

    @Transactional(readOnly = true)
    public RoomMemberEntity requireActiveMember(UUID roomId, UUID userId) {
        return roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new ForbiddenException("يجب أن تكون عضوًا في الغرفة لتنفيذ هذه العملية"));
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID roomId, UUID userId) {
        return roomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isHost(RoomEntity room, UUID userId) {
        return room.getHost() != null && room.getHost().getId().equals(userId);
    }

    @Transactional(readOnly = true)
    public RoomEntity requireHost(UUID roomId, UUID userId) {
        RoomEntity room = requireOpenRoom(roomId);
        if (!isHost(room, userId)) {
            throw new ForbiddenException("هذه العملية مخصصة للمضيف فقط");
        }
        return room;
    }

    @Transactional(readOnly = true)
    public RoomEntity requireControlPermission(UUID roomId, UUID userId) {
        RoomEntity room = requireOpenRoom(roomId);
        if (!canControl(room, userId)) {
            throw new ForbiddenException("ليس لديك صلاحية التحكم بالتشغيل في هذه الغرفة");
        }
        return room;
    }

    @Transactional(readOnly = true)
    public boolean canControl(RoomEntity room, UUID userId) {
        if (isHost(room, userId)) {
            return true;
        }

        RoomMemberEntity member = requireActiveMember(room.getId(), userId);
        ControlMode controlMode = room.getControlMode();

        if (controlMode == ControlMode.EVERYONE) {
            return true;
        }
        return controlMode == ControlMode.MEMBERS_WITH_PERMISSION && member.isCanControl();
    }
}
