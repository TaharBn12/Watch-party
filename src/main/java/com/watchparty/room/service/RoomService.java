package com.watchparty.room.service;

import com.watchparty.common.exception.BadRequestException;
import com.watchparty.common.exception.ConflictException;
import com.watchparty.common.model.VideoType;
import com.watchparty.common.validation.InputSanitizer;
import com.watchparty.common.validation.VideoSourceService;
import com.watchparty.member.entity.MemberRole;
import com.watchparty.member.entity.RoomMemberEntity;
import com.watchparty.member.repository.RoomMemberRepository;
import com.watchparty.profile.entity.ProfileEntity;
import com.watchparty.profile.repository.ProfileRepository;
import com.watchparty.room.entity.ControlMode;
import com.watchparty.room.entity.PlaybackStatus;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.repository.RoomRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private static final String INVITE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RoomRepository roomRepository;
    private final ProfileRepository profileRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomPermissionService roomPermissionService;
    private final InputSanitizer inputSanitizer;
    private final VideoSourceService videoSourceService;
    private final PasswordEncoder passwordEncoder;

    public RoomService(
            RoomRepository roomRepository,
            ProfileRepository profileRepository,
            RoomMemberRepository roomMemberRepository,
            RoomPermissionService roomPermissionService,
            InputSanitizer inputSanitizer,
            VideoSourceService videoSourceService,
            PasswordEncoder passwordEncoder
    ) {
        this.roomRepository = roomRepository;
        this.profileRepository = profileRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.roomPermissionService = roomPermissionService;
        this.inputSanitizer = inputSanitizer;
        this.videoSourceService = videoSourceService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RoomEntity createRoom(CreateRoomCommand command) {
        ProfileEntity host = profileRepository.findById(command.hostId())
                .orElseThrow(() -> new BadRequestException("لا يمكن إنشاء غرفة دون ملف شخصي صالح للمضيف"));

        RoomEntity room = new RoomEntity(
                inputSanitizer.sanitizeRequiredText(command.name(), "اسم الغرفة", 3, 100),
                host
        );
        room.setInviteCode(generateUniqueInviteCode());
        room.setPublicRoom(command.publicRoom());
        room.setControlMode(command.controlMode() == null ? ControlMode.HOST_ONLY : command.controlMode());

        if (StringUtils.hasText(command.rawPassword())) {
            room.setPasswordHash(passwordEncoder.encode(command.rawPassword().trim()));
        }

        if (StringUtils.hasText(command.initialVideoUrl())) {
            applyVideo(room, command.initialVideoUrl());
        }

        // التعليق بالعربية: عضوية المضيف تُنشأ أيضًا بواسطة Trigger في schema.sql لضمان الاتساق حتى لو أنشئت الغرفة خارج التطبيق.
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public RoomEntity getOpenRoom(UUID roomId) {
        return roomPermissionService.requireOpenRoom(roomId);
    }

    @Transactional(readOnly = true)
    public RoomEntity getOpenRoomByInviteCode(String inviteCode) {
        String cleanedInviteCode = inputSanitizer.sanitizeRequiredText(inviteCode, "كود الدعوة", 6, 32);
        return roomPermissionService.requireOpenRoomByInviteCode(cleanedInviteCode);
    }

    @Transactional(readOnly = true)
    public List<RoomEntity> listHostedRooms(UUID hostId) {
        return roomRepository.findByHost_IdAndClosedAtIsNullOrderByCreatedAtDesc(hostId);
    }

    @Transactional(readOnly = true)
    public List<RoomEntity> listPublicRooms() {
        return roomRepository.findTop20ByPublicRoomTrueAndClosedAtIsNullOrderByCreatedAtDesc();
    }

    @Transactional
    public RoomEntity updateRoomSettings(UpdateRoomSettingsCommand command) {
        RoomEntity room = roomPermissionService.requireHost(command.roomId(), command.actorId());
        room.setName(inputSanitizer.sanitizeRequiredText(command.name(), "اسم الغرفة", 3, 100));
        room.setPublicRoom(command.publicRoom());
        room.setControlMode(command.controlMode() == null ? ControlMode.HOST_ONLY : command.controlMode());

        if (command.clearPassword()) {
            room.setPasswordHash(null);
        } else if (StringUtils.hasText(command.newRawPassword())) {
            room.setPasswordHash(passwordEncoder.encode(command.newRawPassword().trim()));
        }

        return roomRepository.save(room);
    }

    @Transactional
    public RoomEntity updatePlayback(UUID actorId, UUID roomId, PlaybackStatus status, BigDecimal positionSeconds) {
        RoomEntity room = roomPermissionService.requireControlPermission(roomId, actorId);
        room.updatePlayback(status, normalizePosition(positionSeconds));
        return roomRepository.save(room);
    }

    @Transactional
    public RoomEntity changeVideo(UUID actorId, UUID roomId, String rawVideoUrl) {
        RoomEntity room = roomPermissionService.requireControlPermission(roomId, actorId);
        applyVideo(room, rawVideoUrl);
        return roomRepository.save(room);
    }

    @Transactional
    public RoomEntity transferHost(UUID currentHostId, UUID roomId, UUID newHostId) {
        RoomEntity room = roomPermissionService.requireHost(roomId, currentHostId);
        RoomMemberEntity newHostMember = roomPermissionService.requireActiveMember(roomId, newHostId);

        RoomMemberEntity oldHostMember = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, currentHostId)
                .orElseThrow(() -> new ConflictException("المضيف الحالي غير موجود في قائمة الأعضاء"));

        ProfileEntity newHost = newHostMember.getUser();
        room.setHost(newHost);

        oldHostMember.setRole(MemberRole.MEMBER);
        oldHostMember.setCanControl(false);
        newHostMember.setRole(MemberRole.HOST);
        newHostMember.setCanControl(true);

        roomMemberRepository.save(oldHostMember);
        roomMemberRepository.save(newHostMember);
        return roomRepository.save(room);
    }

    @Transactional
    public RoomEntity closeRoom(UUID hostId, UUID roomId) {
        RoomEntity room = roomPermissionService.requireHost(roomId, hostId);
        room.setClosedAt(Instant.now());
        return roomRepository.save(room);
    }

    private void applyVideo(RoomEntity room, String rawVideoUrl) {
        String normalizedUrl = videoSourceService.normalizeUrl(rawVideoUrl);
        VideoType videoType = videoSourceService.detectVideoType(normalizedUrl);
        room.changeVideo(normalizedUrl, videoType);
    }

    private BigDecimal normalizePosition(BigDecimal positionSeconds) {
        if (positionSeconds == null) {
            return BigDecimal.ZERO;
        }
        if (positionSeconds.signum() < 0) {
            throw new BadRequestException("موضع الفيديو لا يمكن أن يكون سالبًا");
        }
        return positionSeconds;
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = generateInviteCode(10);
            if (!roomRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("تعذر توليد رابط دعوة فريد، حاول مرة أخرى");
    }

    private String generateInviteCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(INVITE_ALPHABET.length());
            builder.append(INVITE_ALPHABET.charAt(index));
        }
        return builder.toString();
    }

    public record CreateRoomCommand(
            UUID hostId,
            String name,
            boolean publicRoom,
            String rawPassword,
            ControlMode controlMode,
            String initialVideoUrl
    ) {
    }

    public record UpdateRoomSettingsCommand(
            UUID actorId,
            UUID roomId,
            String name,
            boolean publicRoom,
            ControlMode controlMode,
            boolean clearPassword,
            String newRawPassword
    ) {
    }
}
