package com.watchparty.room.dto;

import com.watchparty.room.entity.ControlMode;
import com.watchparty.room.service.RoomService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateRoomSettingsRequest(
        @NotBlank(message = "اسم الغرفة مطلوب")
        @Size(min = 3, max = 100, message = "اسم الغرفة يجب أن يكون بين 3 و100 حرف")
        String name,

        Boolean publicRoom,
        ControlMode controlMode,
        Boolean clearPassword,

        @Size(max = 128, message = "كلمة سر الغرفة طويلة جدًا")
        String newPassword
) {
    public RoomService.UpdateRoomSettingsCommand toCommand(UUID actorId, UUID roomId) {
        return new RoomService.UpdateRoomSettingsCommand(
                actorId,
                roomId,
                name,
                publicRoom == null || publicRoom,
                controlMode,
                Boolean.TRUE.equals(clearPassword),
                newPassword
        );
    }
}
