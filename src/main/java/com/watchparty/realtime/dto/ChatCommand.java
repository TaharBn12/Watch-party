package com.watchparty.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatCommand(
        @NotBlank(message = "محتوى الرسالة مطلوب")
        @Size(max = 1000, message = "الرسالة طويلة جدًا")
        String content
) {
}
