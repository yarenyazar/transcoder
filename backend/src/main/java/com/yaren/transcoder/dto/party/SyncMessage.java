package com.yaren.transcoder.dto.party;

/**
 * WebSocket üzerinden giden/gelen senkronizasyon mesajı.
 * Aksiyonlar: PLAY | PAUSE | SEEK | SET_FILM | JOIN_ACK | LOCK_LIFTED
 */
public class SyncMessage {
    private String roomId;
    private String userId;
    private String username;
    private String action;
    private Double currentPosition;
    private Long serverTime;

    // Pause Lock bilgisi
    private String pausedByUserId;
    private String pausedByUsername;
    private Long unlockAt;        // epoch ms

    // SET_FILM için
    private String videoUrl;

    // ── Constructors ───────────────────────────────────────

    public SyncMessage() {}

    // ── Getters / Setters ──────────────────────────────────

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Double getCurrentPosition() { return currentPosition; }
    public void setCurrentPosition(Double currentPosition) { this.currentPosition = currentPosition; }

    public Long getServerTime() { return serverTime; }
    public void setServerTime(Long serverTime) { this.serverTime = serverTime; }

    public String getPausedByUserId() { return pausedByUserId; }
    public void setPausedByUserId(String pausedByUserId) { this.pausedByUserId = pausedByUserId; }

    // Eski alan uyumu
    public String getPausedBy() { return pausedByUserId; }
    public void setPausedBy(String v) { this.pausedByUserId = v; }

    public String getPausedByUsername() { return pausedByUsername; }
    public void setPausedByUsername(String pausedByUsername) { this.pausedByUsername = pausedByUsername; }

    public Long getUnlockAt() { return unlockAt; }
    public void setUnlockAt(Long unlockAt) { this.unlockAt = unlockAt; }

    // Eski alan uyumu
    public Long getLockExpiresAt() { return unlockAt; }
    public void setLockExpiresAt(Long v) { this.unlockAt = v; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    // Eski alan uyumları (gereksiz ama ViewerState / eski Controller erişebilir)
    public boolean isDurduruldu() { return false; }
    public void setDurduruldu(boolean v) {}
}
