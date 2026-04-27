package com.yaren.transcoder.service.implementation;

import com.yaren.transcoder.entity.LiveStreamRecord;
import com.yaren.transcoder.entity.LiveStreamSession;
import com.yaren.transcoder.repository.LiveStreamRecordRepository;
import com.yaren.transcoder.repository.LiveStreamSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Service
public class RecordingScannerService {

    private final LiveStreamRecordRepository recordRepository;
    private final LiveStreamSessionRepository sessionRepository;
    private final String RECORDINGS_ROOT = "/data/outputs/recordings";

    public RecordingScannerService(LiveStreamRecordRepository recordRepository,
            LiveStreamSessionRepository sessionRepository) {
        this.recordRepository = recordRepository;
        this.sessionRepository = sessionRepository;
    }

    // Runs every 1 minute
    @Scheduled(fixedDelay = 60000)
    public void scanAndCleanupRecordings() {
        System.out.println("[DVR] Scanning for new segments and enforcing retention policies...");

        List<LiveStreamSession> allSessions = sessionRepository.findAll();

        for (LiveStreamSession session : allSessions) {
            String streamKey = session.getStreamKey();
            Path dirPath = Paths.get(RECORDINGS_ROOT, streamKey);

            if (!Files.exists(dirPath)) {
                continue;
            }

            // 1. Scan for new files that aren't in DB yet
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".mp4"))
                        .forEach(filePath -> processFoundFile(filePath, streamKey));
            } catch (Exception e) {
                System.err.println("[DVR] Error scanning directory: " + dirPath.toString());
            }

            // 2. Enforce Retention Policy for this stream
            Integer retentionDays = session.getRetentionPeriodDays();
            if (retentionDays != null && retentionDays > 0) {
                LocalDateTime thresholdTime = LocalDateTime.now().minusDays(retentionDays);

                // Find all chunks older than the threshold and belonging to this stream
                List<LiveStreamRecord> oldRecords = recordRepository.findByStreamKeyAndStartTimeBefore(streamKey,
                        thresholdTime);

                for (LiveStreamRecord oldRecord : oldRecords) {
                    File physicalFile = new File(oldRecord.getFilePath());
                    if (physicalFile.exists()) {
                        if (physicalFile.delete()) {
                            System.out.println("[DVR] Deleted expired segment: " + oldRecord.getFilePath());
                        }
                    }
                    // Remove from database regardless of physical presence
                    recordRepository.delete(oldRecord);
                }
            }
        }
    }

    private void processFoundFile(Path filePath, String streamKey) {
        String absolutePathStr = filePath.toString();

        // Skip if already in database (Assuming path is unique and reliable)
        // Optimization: In a huge system we would do a batch query, but this is fine
        // for now
        boolean exists = recordRepository.findByStreamKeyOrderByStartTimeAsc(streamKey)
                .stream().anyMatch(r -> r.getFilePath().equals(absolutePathStr));

        if (exists) {
            return;
        }

        // Parse time from file name (Format: %Y-%m-%d_%H-%M-%S.mp4)
        String fileName = filePath.getFileName().toString().replace(".mp4", "");
        LocalDateTime startTime;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            startTime = LocalDateTime.parse(fileName, formatter);
        } catch (Exception e) {
            System.err.println("[DVR] Could not parse timestamp from file: " + fileName + ". Using current time.");
            startTime = LocalDateTime.now();
        }

        LiveStreamRecord record = new LiveStreamRecord();
        record.setStreamKey(streamKey);
        record.setFilePath(absolutePathStr);
        record.setStartTime(startTime);

        try {
            record.setFileSize(Files.size(filePath));
        } catch (Exception e) {
            record.setFileSize(0L);
        }

        recordRepository.save(record);
        System.out.println("[DVR] Registered new segment: " + absolutePathStr);
    }
}
