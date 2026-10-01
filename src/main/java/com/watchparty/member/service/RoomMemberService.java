package com.watchparty.member.service;

import com.watchparty.common.exception.BadRequestException;
import com.watchparty.common.exception.ForbiddenException;
import com.watchparty.member.entity.MemberRole;
import com.watchparty.member.entity.RoomMemberEntity;
import com.watchparty.member.repository.RoomMemberRepository;
import com.watchparty.profile.entity.ProfileEntity;
import com.watchparty.profile.repository.ProfileRepository;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.repository.RoomRepository;
import com.watchparty.room.service.RoomPermissionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RoomMemberService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ProfileRepository profileRepository;
    private final RoomPermissionService roomPermissionService;
    private final PasswordEncoder passwordEncoder;

    public RoomMemberService(
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            ProfileRepository profileRepository,
            RoomPermissionService roomPermissionService,
            PasswordEncoder passwordEncoder
    ) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.profileRepository = profileRepository;
        this.roomPermissionService = roomPermissionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RoomMemberEntity joinRoom(UUID roomId, UUID userId, String rawPassword) {
        RoomEntity room = roomPermissionService.requireOpenRoom(roomId);
        validatePasswordIfNeeded(room, rawPassword);

        ProfileEntity user = profileRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("لا يمكن الانضمام دون ملف شخصي صالح"));

        RoomMemberEntity member = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseGet(() -> new RoomMemberEntity(room, user));

        member.reactivate();
        if (member.getRole() == null) {
            member.setRole(MemberRole.MEMBER);
        }
        return roomMemberRepository.save(member);
    }

    @Transactional
    public RoomMemberEntity joinRoomByInviteCode(String inviteCode, UUID userId, String rawPassword) {
        RoomEntity room = roomRepository.findByInviteCodeAndClosedAtIsNull(inviteCode)
                .orElseThrow(() -> new BadRequestException("رابط الدعوة غير صحيح أو الغرفة مغلقة"));
        return joinRoom(room.getId(), userId, rawPassword);
    }

    @Transactional(readOnly = true)
    public List<RoomMemberEntity> listActiveMembers(UUID roomId, UUID requesterId) {
        roomPermissionService.requireActiveMember(roomId, requesterId);
        return roomMemberRepository.findByRoom_IdAndLeftAtIsNullOrderByJoinedAtAsc(roomId);
    }

    @Transactional(readOnly = true)
    public List<RoomMemberEntity> listConnectedMembers(UUID roomId, UUID requesterId) {
        roomPermissionService.requireActiveMember(roomId, requesterId);
        return roomMemberRepository.findByRoom_IdAndConnectedTrueAndLeftAtIsNullOrderByJoinedAtAsc(roomId);
    }

    @Transactional
    public RoomMemberEntity markConnected(UUID roomId, UUID userId) {
        RoomMemberEntity member = roomPermissionService.requireActiveMember(roomId, userId);
        member.markConnected();
        return roomMemberRepository.save(member);
    }

    @Transactional
    public RoomMemberEntity markDisconnected(UUID roomId, UUID userId) {
        RoomMemberEntity member = roomPermissionService.requireActiveMember(roomId, userId);
        member.markDisconnected();
        return roomMemberRepository.save(member);
    }

    @Transactional
    public RoomMemberEntity leaveRoom(UUID roomId, UUID userId) {
        RoomEntity room = roomPermissionService.requireOpenRoom(roomId);
        if (room.getHost().getId().equals(userId)) {
            throw new BadRequestException("يجب نقل الإدارة قبل مغادرة المضيف للغرفة");
        }

        RoomMemberEntity member = roomPermissionService.requireActiveMember(roomId, userId);
        member.markLeft();
        return roomMemberRepository.save(member);
    }

    @Transactional
    public RoomMemberEntity kickMember(UUID hostId, UUID roomId, UUID targetUserId) {
        RoomEntity room = roomPermissionService.requireHost(roomId, hostId);
        if (room.getHost().getId().equals(targetUserId)) {
            throw new BadRequestException("لا يمكن طرد المضيف الحالي");
        }

        RoomMemberEntity target = roomPermissionService.requireActiveMember(roomId, targetUserId);
        target.markLeft();
        return roomMemberRepository.save(target);
    }

    @Transactional
    public RoomMemberEntity setControlPermission(UUID hostId, UUID roomId, UUID targetUserId, boolean canControl) {
        RoomEntity room = roomPermissionService.requireHost(roomId, hostId);
        RoomMemberEntity target = roomPermissionService.requireActiveMember(roomId, targetUserId);

        if (room.getHost().getId().equals(targetUserId) && !canControl) {
            throw new BadRequestException("لا يمكن سحب صلاحية التحكم من المضيف");
        }

        target.setCanControl(canControl);
        return roomMemberRepository.save(target);
    }

    @Transactional
    public RoomMemberEntity touch(UUID roomId, UUID userId) {
        RoomMemberEntity member = roomPermissionService.requireActiveMember(roomId, userId);
        member.setLastSeenAt(Instant.now());
        return roomMemberRepository.save(member);
    }

    private void validatePasswordIfNeeded(RoomEntity room, String rawPassword) {
        if (!room.hasPassword()) {
            return;
        }
        if (!StringUtils.hasText(rawPassword) || !passwordEncoder.matches(rawPassword, room.getPasswordHash())) {
            throw new ForbiddenException("كلمة سر الغرفة غير صحيحة");
        }
    }
}
