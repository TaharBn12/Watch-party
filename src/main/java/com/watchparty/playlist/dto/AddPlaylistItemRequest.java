package com.watchparty.playlist.dto;

import com.watchparty.playlist.service.PlaylistService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddPlaylistItemRequest(
        @NotBlank(message = "رابط الفيديو مطلوب")
        @Size(max = 2048, message = "رابط الفيديو طويل جدًا")
        String url,

        @Size(max = 200, message = "عنوان الفيديو طويل جدًا")
        String title
) {
    public PlaylistService.AddPlaylistItemCommand toCommand(UUID actorId, UUID roomId) {
        return new PlaylistService.AddPlaylistItemCommand(actorId, roomId, url, title);
    }
}
