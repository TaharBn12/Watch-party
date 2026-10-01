package com.watchparty.room.repository;

import com.watchparty.room.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    Optional<RoomEntity> findByIdAndClosedAtIsNull(UUID id);

    Optional<RoomEntity> findByInviteCodeAndClosedAtIsNull(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    List<RoomEntity> findByHost_IdAndClosedAtIsNullOrderByCreatedAtDesc(UUID hostId);

    List<RoomEntity> findTop20ByPublicRoomTrueAndClosedAtIsNullOrderByCreatedAtDesc();
}
