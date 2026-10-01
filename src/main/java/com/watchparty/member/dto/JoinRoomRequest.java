package com.watchparty.member.dto;

import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
        @Size(max = 128, message = "كلمة سر الغرفة طويلة جدًا")
        String password
) {
}
