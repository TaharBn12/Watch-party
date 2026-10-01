package com.watchparty.member.repository;

import com.watchparty.member.entity.MemberRole;
import com.watchparty.member.entity.RoomMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMemberRepository extends JpaRepository<RoomMemberEntity, UUID> {

    Optional<RoomMemberEntity> findByRoom_IdAndUser_Id(UUID roomId, UUID userId);

    Optional<RoomMemberEntity> findByRoom_IdAndUser_IdAndLeftAtIsNull(UUID roomId, UUID userId);

    Optional<RoomMemberEntity> findByRoom_IdAndRoleAndLeftAtIsNull(UUID roomId, MemberRole role);

    boolean existsByRoom_IdAndUser_IdAndLeftAtIsNull(UUID roomId, UUID userId);

    List<RoomMemberEntity> findByRoom_IdAndLeftAtIsNullOrderByJoinedAtAsc(UUID roomId);

    List<RoomMemberEntity> findByRoom_IdAndConnectedTrueAndLeftAtIsNullOrderByJoinedAtAsc(UUID roomId);
}
