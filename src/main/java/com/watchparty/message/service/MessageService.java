package com.watchparty.message.service;

import com.watchparty.common.exception.BadRequestException;
import com.watchparty.common.exception.ResourceNotFoundException;
import com.watchparty.common.ratelimit.RateLimiterService;
import com.watchparty.common.validation.InputSanitizer;
import com.watchparty.message.entity.MessageEntity;
import com.watchparty.message.entity.MessageType;
import com.watchparty.message.repository.MessageRepository;
import com.watchparty.profile.entity.ProfileEntity;
import com.watchparty.profile.repository.ProfileRepository;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.service.RoomPermissionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    public static final int MAX_RECENT_MESSAGES = 50;
    private static final int MAX_USER_MESSAGES_PER_MINUTE = 20;

    private final MessageRepository messageRepository;
    private final ProfileRepository profileRepository;
    private final RoomPermissionService roomPermissionService;
    private final InputSanitizer inputSanitizer;
    private final RateLimiterService rateLimiterService;

    public MessageService(
            MessageRepository messageRepository,
            ProfileRepository profileRepository,
            RoomPermissionService roomPermissionService,
            InputSanitizer inputSanitizer,
            RateLimiterService rateLimiterService
    ) {
        this.messageRepository = messageRepository;
        this.profileRepository = profileRepository;
        this.roomPermissionService = roomPermissionService;
        this.inputSanitizer = inputSanitizer;
        this.rateLimiterService = rateLimiterService;
    }

    @Transactional(readOnly = true)
    public List<MessageEntity> getRecentMessages(UUID roomId, UUID requesterId) {
        roomPermissionService.requireActiveMember(roomId, requesterId);
        Pageable firstFifty = PageRequest.of(0, MAX_RECENT_MESSAGES);
        List<MessageEntity> newestFirst = messageRepository.findRecentMessages(roomId, firstFifty);

        // التعليق بالعربية: قاعدة البيانات تجلب الأحدث أولًا للأداء، والواجهة تحتاج العرض من الأقدم إلى الأحدث.
        List<MessageEntity> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    @Transactional
    public MessageEntity sendChatMessage(UUID roomId, UUID senderId, String rawContent) {
        return createUserMessage(roomId, senderId, MessageType.CHAT, rawContent, 1, 1000);
    }

    @Transactional
    public MessageEntity sendEmojiMessage(UUID roomId, UUID senderId, String rawContent) {
        return createUserMessage(roomId, senderId, MessageType.EMOJI, rawContent, 1, 80);
    }

    @Transactional
    public MessageEntity sendSystemMessage(UUID roomId, UUID senderId, String rawContent) {
        RoomEntity room = roomPermissionService.requireOpenRoom(roomId);
        ProfileEntity sender = requireProfile(senderId);
        String content = inputSanitizer.sanitizeRequiredText(rawContent, "رسالة النظام", 1, 1000);
        return messageRepository.save(new MessageEntity(room, sender, MessageType.SYSTEM, content));
    }

    @Transactional
    public MessageEntity softDeleteMessage(UUID hostId, UUID roomId, UUID messageId) {
        roomPermissionService.requireHost(roomId, hostId);
        MessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("الرسالة غير موجودة"));

        if (!message.getRoom().getId().equals(roomId)) {
            throw new BadRequestException("الرسالة لا تنتمي لهذه الغرفة");
        }

        message.softDelete();
        return messageRepository.save(message);
    }

    private MessageEntity createUserMessage(
            UUID roomId,
            UUID senderId,
            MessageType type,
            String rawContent,
            int minLength,
            int maxLength
    ) {
        rateLimiterService.check(
                "message:" + roomId + ":" + senderId,
                MAX_USER_MESSAGES_PER_MINUTE,
                Duration.ofMinutes(1),
                "تم تجاوز حد الرسائل: 20 رسالة في الدقيقة"
        );
        RoomEntity room = roomPermissionService.requireOpenRoom(roomId);
        roomPermissionService.requireActiveMember(roomId, senderId);
        ProfileEntity sender = requireProfile(senderId);
        String content = inputSanitizer.sanitizeRequiredText(rawContent, "محتوى الرسالة", minLength, maxLength);
        return messageRepository.save(new MessageEntity(room, sender, type, content));
    }

    private ProfileEntity requireProfile(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("الملف الشخصي غير موجود"));
    }
}
