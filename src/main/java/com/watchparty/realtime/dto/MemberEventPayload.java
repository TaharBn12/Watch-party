package com.watchparty.realtime.dto;

import com.watchparty.member.dto.RoomMemberResponse;

public record MemberEventPayload(
        RoomMemberResponse member
) {
}
