package com.watchparty.room.dto;

import com.watchparty.room.entity.PlaybackStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaybackRequest(
        @NotNull(message = "حالة التشغيل مطلوبة")
        PlaybackStatus status,

        @NotNull(message = "موضع الفيديو مطلوب")
        @DecimalMin(value = "0.0", message = "موضع الفيديو لا يمكن أن يكون سالبًا")
        @Digits(integer = 9, fraction = 3, message = "موضع الفيديو يجب أن يحتوي حتى 3 أرقام عشرية")
        BigDecimal positionSeconds
) {
}
