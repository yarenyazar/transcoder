package com.yaren.transcoder.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "live_stream_sessions")
public class LiveStreamSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String streamKey;

    @Column(name = "stream_name")
    private String streamName;

    private String mode; // "LISTEN" or "PULL"
    private String inputUrl; // Only for PULL mode

    private String status; // "ACTIVE", "STOPPED", "FAILED"

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Output URL path for HLS files (Nginx)
    private String outputPath;

    // Comma-separated list of preset IDs used for this session
    private String presetIds;

    @Column(name = "record_interval_minutes")
    private Integer recordIntervalMinutes = 0; // 0 means disabled

    @Column(name = "retention_period_days")
    private Integer retentionPeriodDays = 0; // 0 means infinite/disabled

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getInputUrl() {
        return inputUrl;
    }

    public void setInputUrl(String inputUrl) {
        this.inputUrl = inputUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getPresetIds() {
        return presetIds;
    }

    public void setPresetIds(String presetIds) {
        this.presetIds = presetIds;
    }

    public Integer getRecordIntervalMinutes() {
        return recordIntervalMinutes;
    }

    public void setRecordIntervalMinutes(Integer recordIntervalMinutes) {
        this.recordIntervalMinutes = recordIntervalMinutes;
    }

    public Integer getRetentionPeriodDays() {
        return retentionPeriodDays;
    }

    public void setRetentionPeriodDays(Integer retentionPeriodDays) {
        this.retentionPeriodDays = retentionPeriodDays;
    }
}
