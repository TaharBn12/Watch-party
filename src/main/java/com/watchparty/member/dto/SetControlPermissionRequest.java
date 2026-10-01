package com.watchparty.member.dto;

import jakarta.validation.constraints.NotNull;

public record SetControlPermissionRequest(
        @NotNull(message = "قيمة صلاحية التحكم مطلوبة")
        Boolean canControl
) {
    public boolean canControlValue() {
        return Boolean.TRUE.equals(canControl);
    }
}
