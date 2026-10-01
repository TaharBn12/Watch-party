package com.watchparty.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeVideoRequest(
        @NotBlank(message = "رابط الفيديو مطلوب")
        @Size(max = 2048, message = "رابط الفيديو طويل جدًا")
        String videoUrl
) {
}
