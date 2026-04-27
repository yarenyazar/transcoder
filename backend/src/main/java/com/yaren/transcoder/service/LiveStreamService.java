package com.yaren.transcoder.service;

import com.yaren.transcoder.entity.LiveStreamRecord;
import com.yaren.transcoder.entity.LiveStreamSession;
import java.util.List;

public interface LiveStreamService {
    LiveStreamSession startStream(String streamKey, String streamName, String mode, String inputUrl,
            List<Long> presetIds,
            Integer recordIntervalMinutes, Integer retentionPeriodDays);

    void stopStream(Long sessionId);

    List<LiveStreamSession> getAllSessions();

    LiveStreamSession getSession(Long id);

    void deleteSession(Long sessionId);

    List<LiveStreamRecord> getRecordings(String streamKey);
}
