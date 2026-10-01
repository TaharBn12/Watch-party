package com.watchparty.member.entity;

import com.watchparty.profile.entity.ProfileEntity;
import com.watchparty.room.entity.RoomEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "room_members", schema = "public")
public class RoomMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ProfileEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "can_control", nullable = false)
    private boolean canControl = false;

    @Column(name = "is_connected", nullable = false)
    private boolean connected = false;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected RoomMemberEntity() {
        // مطلوب من JPA.
    }

    public RoomMemberEntity(RoomEntity room, ProfileEntity user) {
        this.room = room;
        this.user = user;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (joinedAt == null) {
            joinedAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
        if (role == null) {
            role = MemberRole.MEMBER;
        }
    }

    public void markConnected() {
        this.connected = true;
        this.leftAt = null;
        this.lastSeenAt = Instant.now();
    }

    public void markDisconnected() {
        this.connected = false;
        this.lastSeenAt = Instant.now();
    }

    public void markLeft() {
        this.connected = false;
        this.canControl = false;
        this.leftAt = Instant.now();
        this.lastSeenAt = this.leftAt;
    }

    public void reactivate() {
        this.leftAt = null;
        this.lastSeenAt = Instant.now();
    }

    public boolean isActiveMember() {
        return leftAt == null;
    }

    public boolean isHost() {
        return role == MemberRole.HOST;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RoomEntity getRoom() {
        return room;
    }

    public void setRoom(RoomEntity room) {
        this.room = room;
    }

    public ProfileEntity getUser() {
        return user;
    }

    public void setUser(ProfileEntity user) {
        this.user = user;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public boolean isCanControl() {
        return canControl;
    }

    public void setCanControl(boolean canControl) {
        this.canControl = canControl;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(Instant leftAt) {
        this.leftAt = leftAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoomMemberEntity that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
