package com.watchparty.playlist.repository;

import com.watchparty.playlist.entity.PlaylistItemEntity;
import com.watchparty.playlist.entity.PlaylistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItemEntity, UUID> {

    List<PlaylistItemEntity> findByRoom_IdOrderByPositionAsc(UUID roomId);

    List<PlaylistItemEntity> findByRoom_IdAndStatus(UUID roomId, PlaylistStatus status);

    Optional<PlaylistItemEntity> findFirstByRoom_IdAndStatusOrderByPositionAsc(UUID roomId, PlaylistStatus status);

    @Query("""
            select coalesce(max(p.position), 0)
            from PlaylistItemEntity p
            where p.room.id = :roomId
            """)
    int findMaxPositionByRoomId(@Param("roomId") UUID roomId);
}
