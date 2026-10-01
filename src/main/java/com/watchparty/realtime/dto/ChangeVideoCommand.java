package com.watchparty.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeVideoCommand(
        @NotBlank(message = "رابط الفيديو مطلوب")
        @Size(max = 2048, message = "رابط الفيديو طويل جدًا")
        String videoUrl
) {
}
