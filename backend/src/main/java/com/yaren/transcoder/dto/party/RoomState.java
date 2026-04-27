package com.yaren.transcoder.dto.party;

import java.util.List;

/**
 * Oda anlık durumu — sadece ihtiyaç duyulan alanlar.
 */
public class RoomState {

    private String filmUrl;           // HLS stream URL
    private Double currentPosition;   // Son bilinen konum (saniye)
    private boolean isPlaying;

    // Pause Lock
    private String pausedByUserId;
    private String pausedByUsername;
    private Long unlockAt;            // epoch ms — 0 veya null = kilit yok

    private Long serverTime;
    private String hostId;

    private List<ViewerState> viewers;

    // ── Constructors ──────────────────────────────────────

    public RoomState() {}

    // ── Getters / Setters ─────────────────────────────────

    public String getFilmUrl() { return filmUrl; }
    public void setFilmUrl(String filmUrl) { this.filmUrl = filmUrl; }

    /** @deprecated Eski API uyumu için — filmUrl kullan */
    public String getFilmId() { return filmUrl; }
    public void setFilmId(String filmId) { this.filmUrl = filmId; }

    public Double getCurrentPosition() { return currentPosition; }
    public void setCurrentPosition(Double currentPosition) { this.currentPosition = currentPosition; }

    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }

    public String getPausedByUserId() { return pausedByUserId; }
    public void setPausedByUserId(String pausedByUserId) { this.pausedByUserId = pausedByUserId; }

    public String getPausedByUsername() { return pausedByUsername; }
    public void setPausedByUsername(String pausedByUsername) { this.pausedByUsername = pausedByUsername; }

    /** @deprecated eski alan — pausedByUserId kullan */
    public String getPausedBy() { return pausedByUserId; }
    public void setPausedBy(String v) { this.pausedByUserId = v; }

    public Long getUnlockAt() { return unlockAt; }
    public void setUnlockAt(Long unlockAt) { this.unlockAt = unlockAt; }

    /** @deprecated eski alan — unlockAt kullan */
    public Long getLockExpiresAt() { return unlockAt; }
    public void setLockExpiresAt(Long v) { this.unlockAt = v; }

    public boolean isDurduruldu() { return !isPlaying; }
    public void setDurduruldu(boolean v) { this.isPlaying = !v; }

    public Long getServerTime() { return serverTime; }
    public void setServerTime(Long serverTime) { this.serverTime = serverTime; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public List<ViewerState> getViewers() { return viewers; }
    public void setViewers(List<ViewerState> viewers) { this.viewers = viewers; }
}
