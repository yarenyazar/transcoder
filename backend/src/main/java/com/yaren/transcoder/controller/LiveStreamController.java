package com.yaren.transcoder.controller;

import com.yaren.transcoder.entity.LiveStreamRecord;
import com.yaren.transcoder.entity.LiveStreamSession;
import com.yaren.transcoder.service.LiveStreamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/live")
@CrossOrigin(origins = "*")
public class LiveStreamController {

    private final LiveStreamService liveStreamService;

    public LiveStreamController(LiveStreamService liveStreamService) {
        this.liveStreamService = liveStreamService;
    }

    @PostMapping("/start")
    public ResponseEntity<LiveStreamSession> startStream(@RequestBody StartStreamRequest request) {
        LiveStreamSession session = liveStreamService.startStream(
                request.getStreamKey(),
                request.getStreamName(),
                request.getMode(),
                request.getInputUrl(),
                request.getPresetIds(),
                request.getRecordIntervalMinutes() != null ? request.getRecordIntervalMinutes() : 0,
                request.getRetentionPeriodDays() != null ? request.getRetentionPeriodDays() : 0);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/stop/{id}")
    public ResponseEntity<Void> stopStream(@PathVariable Long id) {
        liveStreamService.stopStream(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<LiveStreamSession>> getAllSessions() {
        return ResponseEntity.ok(liveStreamService.getAllSessions());
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<LiveStreamSession> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(liveStreamService.getSession(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        liveStreamService.deleteSession(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recordings/{streamKey}")
    public ResponseEntity<List<LiveStreamRecord>> getRecordings(@PathVariable String streamKey) {
        return ResponseEntity.ok(liveStreamService.getRecordings(streamKey));
    }

    // DTO for Start Request
    public static class StartStreamRequest {
        private String streamKey;
        private String streamName;
        private String mode; // "LISTEN" or "PULL"
        private String inputUrl;
        private List<Long> presetIds;
        private Integer recordIntervalMinutes;
        private Integer retentionPeriodDays;

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

        public List<Long> getPresetIds() {
            return presetIds;
        }

        public void setPresetIds(List<Long> presetIds) {
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
}
