package com.watchparty.room.controller;

import com.watchparty.room.dto.ChangeVideoRequest;
import com.watchparty.room.dto.CreateRoomRequest;
import com.watchparty.room.dto.PlaybackRequest;
import com.watchparty.room.dto.RoomPreviewResponse;
import com.watchparty.room.dto.RoomResponse;
import com.watchparty.room.dto.TransferHostRequest;
import com.watchparty.room.dto.UpdateRoomSettingsRequest;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.service.RoomPermissionService;
import com.watchparty.room.service.RoomService;
import com.watchparty.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class RoomController {

    private static final String INVITE_CODE_PATTERN = "^[A-Za-z0-9_-]{6,32}$";

    private final RoomService roomService;
    private final RoomPermissionService roomPermissionService;
    private final CurrentUserService currentUserService;

    public RoomController(
            RoomService roomService,
            RoomPermissionService roomPermissionService,
            CurrentUserService currentUserService
    ) {
        this.roomService = roomService;
        this.roomPermissionService = roomPermissionService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        RoomEntity room = roomService.createRoom(request.toCommand(actorId));
        return RoomResponse.from(room);
    }

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        roomPermissionService.requireActiveMember(roomId, actorId);
        return RoomResponse.from(roomService.getOpenRoom(roomId));
    }

    @GetMapping("/invite/{inviteCode}")
    public RoomPreviewResponse getRoomByInviteCode(
            @PathVariable
            @Pattern(regexp = INVITE_CODE_PATTERN, message = "كود الدعوة غير صالح")
            String inviteCode
    ) {
        // هذا endpoint يعرض بيانات غير حساسة فقط حتى يستطيع المدعو رؤية اسم الغرفة قبل الانضمام.
        return RoomPreviewResponse.from(roomService.getOpenRoomByInviteCode(inviteCode));
    }

    @GetMapping("/mine")
    public List<RoomResponse> myHostedRooms(HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return roomService.listHostedRooms(actorId).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @GetMapping("/public")
    public List<RoomResponse> publicRooms() {
        return roomService.listPublicRooms().stream()
                .map(RoomResponse::from)
                .toList();
    }

    @PatchMapping("/{roomId}/settings")
    public RoomResponse updateRoomSettings(
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomSettingsRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomResponse.from(roomService.updateRoomSettings(request.toCommand(actorId, roomId)));
    }

    @PostMapping("/{roomId}/playback")
    public RoomResponse updatePlayback(
            @PathVariable UUID roomId,
            @Valid @RequestBody PlaybackRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomResponse.from(roomService.updatePlayback(actorId, roomId, request.status(), request.positionSeconds()));
    }

    @PostMapping("/{roomId}/video")
    public RoomResponse changeVideo(
            @PathVariable UUID roomId,
            @Valid @RequestBody ChangeVideoRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomResponse.from(roomService.changeVideo(actorId, roomId, request.videoUrl()));
    }

    @PostMapping("/{roomId}/transfer-host")
    public RoomResponse transferHost(
            @PathVariable UUID roomId,
            @Valid @RequestBody TransferHostRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return RoomResponse.from(roomService.transferHost(actorId, roomId, request.newHostId()));
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeRoom(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        roomService.closeRoom(actorId, roomId);
    }
}
