package com.watchparty.message.repository;

import com.watchparty.message.entity.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    @Query("""
            select m
            from MessageEntity m
            where m.room.id = :roomId
              and m.deletedAt is null
            order by m.createdAt desc
            """)
    List<MessageEntity> findRecentMessages(@Param("roomId") UUID roomId, Pageable pageable);
}
