package com.watchparty.playlist.service;

import com.watchparty.common.exception.BadRequestException;
import com.watchparty.common.exception.ResourceNotFoundException;
import com.watchparty.common.model.VideoType;
import com.watchparty.common.validation.InputSanitizer;
import com.watchparty.common.validation.VideoSourceService;
import com.watchparty.playlist.entity.PlaylistItemEntity;
import com.watchparty.playlist.entity.PlaylistStatus;
import com.watchparty.playlist.repository.PlaylistItemRepository;
import com.watchparty.profile.entity.ProfileEntity;
import com.watchparty.profile.repository.ProfileRepository;
import com.watchparty.room.entity.RoomEntity;
import com.watchparty.room.repository.RoomRepository;
import com.watchparty.room.service.RoomPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlaylistService {

    private final PlaylistItemRepository playlistItemRepository;
    private final ProfileRepository profileRepository;
    private final RoomRepository roomRepository;
    private final RoomPermissionService roomPermissionService;
    private final InputSanitizer inputSanitizer;
    private final VideoSourceService videoSourceService;

    public PlaylistService(
            PlaylistItemRepository playlistItemRepository,
            ProfileRepository profileRepository,
            RoomRepository roomRepository,
            RoomPermissionService roomPermissionService,
            InputSanitizer inputSanitizer,
            VideoSourceService videoSourceService
    ) {
        this.playlistItemRepository = playlistItemRepository;
        this.profileRepository = profileRepository;
        this.roomRepository = roomRepository;
        this.roomPermissionService = roomPermissionService;
        this.inputSanitizer = inputSanitizer;
        this.videoSourceService = videoSourceService;
    }

    @Transactional(readOnly = true)
    public List<PlaylistItemEntity> listPlaylist(UUID roomId, UUID requesterId) {
        roomPermissionService.requireActiveMember(roomId, requesterId);
        return playlistItemRepository.findByRoom_IdOrderByPositionAsc(roomId);
    }

    @Transactional
    public PlaylistItemEntity addItem(AddPlaylistItemCommand command) {
        RoomEntity room = roomPermissionService.requireControlPermission(command.roomId(), command.actorId());
        ProfileEntity actor = profileRepository.findById(command.actorId())
                .orElseThrow(() -> new ResourceNotFoundException("الملف الشخصي غير موجود"));

        String normalizedUrl = videoSourceService.normalizeUrl(command.url());
        VideoType videoType = videoSourceService.detectVideoType(normalizedUrl);
        int nextPosition = playlistItemRepository.findMaxPositionByRoomId(command.roomId()) + 1;

        PlaylistItemEntity item = new PlaylistItemEntity(room, actor, normalizedUrl, videoType, nextPosition);
        if (StringUtils.hasText(command.title())) {
            item.setTitle(inputSanitizer.sanitizeOptionalText(command.title(), "عنوان الفيديو", 200));
        }

        return playlistItemRepository.save(item);
    }

    @Transactional
    public PlaylistItemEntity playItem(UUID actorId, UUID roomId, UUID itemId) {
        PlaylistItemEntity item = requireItemInRoom(itemId, roomId);
        RoomEntity room = roomPermissionService.requireControlPermission(roomId, actorId);

        markCurrentlyPlayingAsPlayed(room.getId());
        item.markPlaying();
        room.changeVideo(item.getUrl(), item.getVideoType());

        roomRepository.save(room);
        return playlistItemRepository.save(item);
    }

    @Transactional
    public Optional<PlaylistItemEntity> playNext(UUID actorId, UUID roomId) {
        RoomEntity room = roomPermissionService.requireControlPermission(roomId, actorId);
        markCurrentlyPlayingAsPlayed(roomId);

        Optional<PlaylistItemEntity> nextItem = playlistItemRepository
                .findFirstByRoom_IdAndStatusOrderByPositionAsc(roomId, PlaylistStatus.QUEUED);

        nextItem.ifPresent(item -> {
            item.markPlaying();
            room.changeVideo(item.getUrl(), item.getVideoType());
            roomRepository.save(room);
            playlistItemRepository.save(item);
        });

        return nextItem;
    }

    @Transactional
    public PlaylistItemEntity skipItem(UUID actorId, UUID roomId, UUID itemId) {
        PlaylistItemEntity item = requireItemInRoom(itemId, roomId);
        roomPermissionService.requireControlPermission(roomId, actorId);

        if (item.getStatus() == PlaylistStatus.PLAYED) {
            throw new BadRequestException("لا يمكن تخطي فيديو انتهى بالفعل");
        }

        item.markSkipped();
        return playlistItemRepository.save(item);
    }

    @Transactional
    public void deleteItem(UUID hostId, UUID roomId, UUID itemId) {
        PlaylistItemEntity item = requireItemInRoom(itemId, roomId);
        roomPermissionService.requireHost(roomId, hostId);
        playlistItemRepository.delete(item);
    }

    private void markCurrentlyPlayingAsPlayed(UUID roomId) {
        List<PlaylistItemEntity> playingItems = playlistItemRepository.findByRoom_IdAndStatus(roomId, PlaylistStatus.PLAYING);
        for (PlaylistItemEntity playingItem : playingItems) {
            playingItem.markPlayed();
        }
        playlistItemRepository.saveAll(playingItems);
    }

    private PlaylistItemEntity requireItem(UUID itemId) {
        return playlistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("عنصر قائمة الانتظار غير موجود"));
    }

    private PlaylistItemEntity requireItemInRoom(UUID itemId, UUID roomId) {
        PlaylistItemEntity item = requireItem(itemId);
        if (item.getRoom() == null || !item.getRoom().getId().equals(roomId)) {
            throw new BadRequestException("عنصر قائمة الانتظار لا ينتمي لهذه الغرفة");
        }
        return item;
    }

    public record AddPlaylistItemCommand(
            UUID actorId,
            UUID roomId,
            String url,
            String title
    ) {
    }
}
