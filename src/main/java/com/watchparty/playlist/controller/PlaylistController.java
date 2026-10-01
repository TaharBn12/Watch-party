package com.watchparty.playlist.controller;

import com.watchparty.playlist.dto.AddPlaylistItemRequest;
import com.watchparty.playlist.dto.PlaylistItemResponse;
import com.watchparty.playlist.entity.PlaylistItemEntity;
import com.watchparty.playlist.service.PlaylistService;
import com.watchparty.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/playlist")
@Validated
public class PlaylistController {

    private final PlaylistService playlistService;
    private final CurrentUserService currentUserService;

    public PlaylistController(PlaylistService playlistService, CurrentUserService currentUserService) {
        this.playlistService = playlistService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<PlaylistItemResponse> listPlaylist(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return playlistService.listPlaylist(roomId, actorId).stream()
                .map(PlaylistItemResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistItemResponse addItem(
            @PathVariable UUID roomId,
            @Valid @RequestBody AddPlaylistItemRequest request,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return PlaylistItemResponse.from(playlistService.addItem(request.toCommand(actorId, roomId)));
    }

    @PostMapping("/{itemId}/play")
    public PlaylistItemResponse playItem(
            @PathVariable UUID roomId,
            @PathVariable UUID itemId,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return PlaylistItemResponse.from(playlistService.playItem(actorId, roomId, itemId));
    }

    @PostMapping("/next")
    public ResponseEntity<PlaylistItemResponse> playNext(@PathVariable UUID roomId, HttpServletRequest servletRequest) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        Optional<PlaylistItemEntity> nextItem = playlistService.playNext(actorId, roomId);
        if (nextItem.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(PlaylistItemResponse.from(nextItem.get()));
    }

    @PostMapping("/{itemId}/skip")
    public PlaylistItemResponse skipItem(
            @PathVariable UUID roomId,
            @PathVariable UUID itemId,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        return PlaylistItemResponse.from(playlistService.skipItem(actorId, roomId, itemId));
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable UUID roomId,
            @PathVariable UUID itemId,
            HttpServletRequest servletRequest
    ) {
        UUID actorId = currentUserService.requireCurrentUserId(servletRequest);
        playlistService.deleteItem(actorId, roomId, itemId);
    }
}
