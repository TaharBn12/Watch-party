package com.watchparty.message.dto;

import com.watchparty.message.entity.MessageEntity;
import com.watchparty.message.entity.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID roomId,
        UUID senderId,
        MessageType messageType,
        String content,
        Instant createdAt,
        Instant deletedAt
) {
    public static MessageResponse from(MessageEntity message) {
        return new MessageResponse(
                message.getId(),
                message.getRoom() == null ? null : message.getRoom().getId(),
                message.getSender() == null ? null : message.getSender().getId(),
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt(),
                message.getDeletedAt()
        );
    }
}
