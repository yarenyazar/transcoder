package com.yaren.transcoder.dto.party;

public class ViewerState {
    private String userId;
    private String username;
    private Double playbackPosition;
    private Double bufferedEnd;
    private Long lastHeartbeat;
    private Long latency; // Round-trip time or offset-based delay in ms
    private boolean bagliMi = true;
    private String socketId;

    public ViewerState() {
    }

    public ViewerState(String userId, String username) {
        this.userId = userId;
        this.username = username;
        this.playbackPosition = 0.0;
        this.bufferedEnd = 0.0;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Double getPlaybackPosition() {
        return playbackPosition;
    }

    public void setPlaybackPosition(Double playbackPosition) {
        this.playbackPosition = playbackPosition;
    }

    public Double getBufferedEnd() {
        return bufferedEnd;
    }

    public void setBufferedEnd(Double bufferedEnd) {
        this.bufferedEnd = bufferedEnd;
    }

    public Long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Long getLatency() {
        return latency;
    }

    public void setLatency(Long latency) {
        this.latency = latency;
    }
    public boolean isBagliMi() {
        return bagliMi;
    }

    public void setBagliMi(boolean bagliMi) {
        this.bagliMi = bagliMi;
    }

    public String getSocketId() {
        return socketId;
    }

    public void setSocketId(String socketId) {
        this.socketId = socketId;
    }
}
