package com.watchparty.room.dto;

import com.watchparty.room.entity.ControlMode;
import com.watchparty.room.service.RoomService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRoomRequest(
        @NotBlank(message = "اسم الغرفة مطلوب")
        @Size(min = 3, max = 100, message = "اسم الغرفة يجب أن يكون بين 3 و100 حرف")
        String name,

        Boolean publicRoom,

        @Size(max = 128, message = "كلمة سر الغرفة طويلة جدًا")
        String password,

        ControlMode controlMode,

        @Size(max = 2048, message = "رابط الفيديو طويل جدًا")
        String initialVideoUrl
) {
    public RoomService.CreateRoomCommand toCommand(UUID hostId) {
        return new RoomService.CreateRoomCommand(
                hostId,
                name,
                publicRoom == null || publicRoom,
                password,
                controlMode,
                initialVideoUrl
        );
    }
}
