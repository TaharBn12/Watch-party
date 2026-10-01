package com.watchparty.member.controller;

import com.watchparty.member.dto.JoinRoomRequest;
import com.watchparty.member.dto.RoomMemberResponse;
import com.watchparty.member.dto.SetControlPermissionRequest;
import com.watchparty.member.service.RoomMemberService;
import com.watchparty.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@Validated
public class RoomMemberController {

    private static final String INVITE_CODE_PATTERN = "^[A-Za-z0-9_-]{6,32}$";

    private final RoomMemberService roomMemberService;
    private final CurrentUserService currentUserService;

    public RoomMemberController(RoomMemberService roomMemberService, CurrentUserService currentUserService) {
        this.roomMemberService = roomMemberService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{roomId}/members/join")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomMemberResponse joinRoom(
            @PathVariable UUID roomId,
            @Valid @RequestBody(required = false) JoinRoomRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        String password = request == null ? null : request.password();
        return RoomMemberResponse.from(roomMemberService.joinRoom(roomId, actorId, password));
    }

    @PostMapping("/invite/{inviteCode}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomMemberResponse joinRoomByInviteCode(
            @PathVariable
            @Pattern(regexp = INVITE_CODE_PATTERN, message = "كود الدعوة غير صالح")
            String inviteCode,
            @Valid @RequestBody(required = false) JoinRoomRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        String password = request == null ? null : request.password();
        return RoomMemberResponse.from(roomMemberService.joinRoomByInviteCode(inviteCode, actorId, password));
    }

    @GetMapping("/{roomId}/members")
    public List<RoomMemberResponse> listMembers(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return roomMemberService.listActiveMembers(roomId, actorId).stream()
                .map(RoomMemberResponse::from)
                .toList();
    }

    @GetMapping("/{roomId}/members/connected")
    public List<RoomMemberResponse> listConnectedMembers(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return roomMemberService.listConnectedMembers(roomId, actorId).stream()
                .map(RoomMemberResponse::from)
                .toList();
    }

    @PostMapping("/{roomId}/members/me/connect")
    public RoomMemberResponse markConnected(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomMemberResponse.from(roomMemberService.markConnected(roomId, actorId));
    }

    @PostMapping("/{roomId}/members/me/disconnect")
    public RoomMemberResponse markDisconnected(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomMemberResponse.from(roomMemberService.markDisconnected(roomId, actorId));
    }

    @PostMapping("/{roomId}/members/me/leave")
    public RoomMemberResponse leaveRoom(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomMemberResponse.from(roomMemberService.leaveRoom(roomId, actorId));
    }

    @PostMapping("/{roomId}/members/{targetUserId}/kick")
    public RoomMemberResponse kickMember(
            @PathVariable UUID roomId,
            @PathVariable UUID targetUserId,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomMemberResponse.from(roomMemberService.kickMember(actorId, roomId, targetUserId));
    }

    @PatchMapping("/{roomId}/members/{targetUserId}/control")
    public RoomMemberResponse setControlPermission(
            @PathVariable UUID roomId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody SetControlPermissionRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomMemberResponse.from(roomMemberService.setControlPermission(
                actorId,
                roomId,
                targetUserId,
                request.canControlValue()
        ));
    }
}
