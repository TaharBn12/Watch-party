package com.watchparty.room.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferHostRequest(
        @NotNull(message = "معرف المضيف الجديد مطلوب")
        UUID newHostId
) {
}
