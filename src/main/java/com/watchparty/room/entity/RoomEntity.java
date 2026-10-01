package com.watchparty.room.entity;

import com.watchparty.common.model.VideoType;
import com.watchparty.profile.entity.ProfileEntity;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "rooms", schema = "public")
public class RoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invite_code", unique = true, length = 32)
    private String inviteCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private ProfileEntity host;

    @Column(name = "is_public", nullable = false)
    private boolean publicRoom = true;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_mode", nullable = false, length = 32)
    private ControlMode controlMode = ControlMode.HOST_ONLY;

    @Column(name = "current_video_url", length = 2048)
    private String currentVideoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_video_type", length = 16)
    private VideoType currentVideoType;

    @Enumerated(EnumType.STRING)
    @Column(name = "playback_status", nullable = false, length = 16)
    private PlaybackStatus playbackStatus = PlaybackStatus.PAUSED;

    @Column(name = "playback_position_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal playbackPositionSeconds = BigDecimal.ZERO;

    @Column(name = "playback_updated_at", nullable = false)
    private Instant playbackUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected RoomEntity() {
        // مطلوب من JPA.
    }

    public RoomEntity(String name, ProfileEntity host) {
        this.name = name;
        this.host = host;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (playbackUpdatedAt == null) {
            playbackUpdatedAt = now;
        }
        if (playbackPositionSeconds == null) {
            playbackPositionSeconds = BigDecimal.ZERO;
        }
        if (playbackStatus == null) {
            playbackStatus = PlaybackStatus.PAUSED;
        }
        if (controlMode == null) {
            controlMode = ControlMode.HOST_ONLY;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void updatePlayback(PlaybackStatus status, BigDecimal positionSeconds) {
        this.playbackStatus = status == null ? PlaybackStatus.PAUSED : status;
        this.playbackPositionSeconds = positionSeconds == null ? BigDecimal.ZERO : positionSeconds;
        this.playbackUpdatedAt = Instant.now();
    }

    public void changeVideo(String videoUrl, VideoType videoType) {
        this.currentVideoUrl = videoUrl;
        this.currentVideoType = videoType;
        updatePlayback(PlaybackStatus.PAUSED, BigDecimal.ZERO);
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProfileEntity getHost() {
        return host;
    }

    public void setHost(ProfileEntity host) {
        this.host = host;
    }

    public boolean isPublicRoom() {
        return publicRoom;
    }

    public void setPublicRoom(boolean publicRoom) {
        this.publicRoom = publicRoom;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public ControlMode getControlMode() {
        return controlMode;
    }

    public void setControlMode(ControlMode controlMode) {
        this.controlMode = controlMode;
    }

    public String getCurrentVideoUrl() {
        return currentVideoUrl;
    }

    public void setCurrentVideoUrl(String currentVideoUrl) {
        this.currentVideoUrl = currentVideoUrl;
    }

    public VideoType getCurrentVideoType() {
        return currentVideoType;
    }

    public void setCurrentVideoType(VideoType currentVideoType) {
        this.currentVideoType = currentVideoType;
    }

    public PlaybackStatus getPlaybackStatus() {
        return playbackStatus;
    }

    public void setPlaybackStatus(PlaybackStatus playbackStatus) {
        this.playbackStatus = playbackStatus;
    }

    public BigDecimal getPlaybackPositionSeconds() {
        return playbackPositionSeconds;
    }

    public void setPlaybackPositionSeconds(BigDecimal playbackPositionSeconds) {
        this.playbackPositionSeconds = playbackPositionSeconds;
    }

    public Instant getPlaybackUpdatedAt() {
        return playbackUpdatedAt;
    }

    public void setPlaybackUpdatedAt(Instant playbackUpdatedAt) {
        this.playbackUpdatedAt = playbackUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoomEntity that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
