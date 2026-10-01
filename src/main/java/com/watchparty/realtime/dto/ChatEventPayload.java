package com.watchparty.realtime.dto;

import com.watchparty.message.dto.MessageResponse;

public record ChatEventPayload(
        MessageResponse message
) {
}
