package com.yaren.transcoder.service.implementation;

import com.github.kokorin.jaffree.ffmpeg.FFmpegResultFuture;
import com.yaren.transcoder.entity.LiveStreamRecord;
import com.yaren.transcoder.entity.LiveStreamSession;
import com.yaren.transcoder.entity.Preset;
import com.yaren.transcoder.repository.LiveStreamRecordRepository;
import com.yaren.transcoder.repository.LiveStreamSessionRepository;
import com.yaren.transcoder.repository.PresetRepository;
import com.yaren.transcoder.service.LiveStreamService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveStreamServiceImpl implements LiveStreamService {

    @Value("${app.live-hls-base-url:http://localhost:8085/hls}")
    private String hlsBaseUrl;

    // Nginx RTMP reads from its internal container network or localhost if running
    // natively
    // We assume backend is running alongside RTMP on localhost/docker net
    @Value("${app.rtmp-server-url:rtmp://transcoder-rtmp:1935/live}")
    private String rtmpServerUrl;

    private final LiveStreamSessionRepository sessionRepository;
    private final PresetRepository presetRepository;
    private final LiveStreamRecordRepository recordRepository;

    // Store running FFmpeg processes to stop them later
    private final Map<Long, FFmpegResultFuture> runningStreams = new ConcurrentHashMap<>();

    public LiveStreamServiceImpl(LiveStreamSessionRepository sessionRepository, PresetRepository presetRepository,
            LiveStreamRecordRepository recordRepository) {
        this.sessionRepository = sessionRepository;
        this.presetRepository = presetRepository;
        this.recordRepository = recordRepository;
    }

    @Override
    public LiveStreamSession startStream(String streamKey, String streamName, String mode, String inputUrl,
            List<Long> presetIds,
            Integer recordIntervalMinutes, Integer retentionPeriodDays) {
        // Find existing or create new
        LiveStreamSession session = sessionRepository.findAll().stream()
                .filter(s -> s.getStreamKey().equals(streamKey) && "ACTIVE".equals(s.getStatus()))
                .findFirst()
                .orElseGet(LiveStreamSession::new);

        if (session.getId() != null) {
            return session; // Already active
        }

        session.setStreamKey(streamKey);
        session.setStreamName(streamName != null && !streamName.trim().isEmpty() ? streamName : streamKey);
        session.setMode("LISTEN");
        session.setStartTime(LocalDateTime.now());
        session.setStatus("ACTIVE");

        session.setRecordIntervalMinutes(
                recordIntervalMinutes != null && recordIntervalMinutes > 0 ? recordIntervalMinutes : 0);
        session.setRetentionPeriodDays(
                retentionPeriodDays != null && retentionPeriodDays > 0 ? retentionPeriodDays : 0);

        if (presetIds == null || presetIds.isEmpty()) {
            // Save early to get ID
            session.setOutputPath(hlsBaseUrl + "/" + streamKey + ".m3u8");
            session = sessionRepository.save(session);

            // Still launch FFmpeg just to do DVR (stream copy) if requested
            if (session.getRecordIntervalMinutes() > 0) {
                try {
                    String sourceUrl = rtmpServerUrl + "/" + streamKey;
                    startDvrProcess(session, sourceUrl);
                } catch (Exception e) {
                    System.err.println("Failed to start DVR for raw stream: " + e.getMessage());
                }
            }
            return session;
        }

        // Ensure IDs are Longs and safe
        List<Long> safeIds = presetIds.stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(java.util.stream.Collectors.toList());

        // Store them as string for entity
        session.setPresetIds(safeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));

        session.setRecordIntervalMinutes(
                recordIntervalMinutes != null && recordIntervalMinutes > 0 ? recordIntervalMinutes : 0);
        session.setRetentionPeriodDays(
                retentionPeriodDays != null && retentionPeriodDays > 0 ? retentionPeriodDays : 0);

        List<Preset> selectedPresets = presetRepository.findAllById(safeIds);
        if (selectedPresets.isEmpty()) {
            System.err.println("No presets found for IDs: " + safeIds);
            session.setOutputPath(hlsBaseUrl + "/" + streamKey + ".m3u8");
            sessionRepository.save(session);
            return session;
        }

        // Save session early to get the generated ID for port allocation
        session = sessionRepository.save(session);

        // Preset Selected: Must Spawn FFmpeg to transcode the raw RTMP stream
        try {
            // Ensure HLS dir exists
            Path hlsDir = Paths.get("/tmp/hls/" + streamKey);
            if (!Files.exists(hlsDir)) {
                Files.createDirectories(hlsDir);
            }

            String sourceUrl = rtmpServerUrl + "/" + streamKey;

            System.out.println("Starting FFmpeg and Shaka Packager for Live Stream: " + sourceUrl);

            // Build FFmpeg command
            List<String> ffmpegCmd = new java.util.ArrayList<>();
            ffmpegCmd.add("ffmpeg");
            ffmpegCmd.add("-i");
            ffmpegCmd.add(sourceUrl);

            // Build Shaka Packager command
            List<String> shakaCmd = new java.util.ArrayList<>();
            shakaCmd.add("packager");

            // Assign unique base port based on session ID to avoid collisions
            long sessionId = session.getId();
            int basePort = 20000 + (int) (sessionId % 1000) * 10;

            // Build preset outputs for Shaka UDP ingest
            for (int i = 0; i < selectedPresets.size(); i++) {
                Preset preset = selectedPresets.get(i);
                int videoPort = basePort + (i * 2);
                int audioPort = basePort + (i * 2) + 1;
                String presetStreamName = preset.getHeight() + "p_" + preset.getId();

                // Sanitize Video Codec for Live HLS (Must be H264)
                String vCodec = "libx264";

                // FFmpeg output for this preset (Video)
                ffmpegCmd.add("-map");
                ffmpegCmd.add("0:v:0");
                ffmpegCmd.add("-c:v:0");
                ffmpegCmd.add(vCodec);
                ffmpegCmd.add("-b:v:0");
                ffmpegCmd.add(preset.getVideoBitrate() + "k");
                ffmpegCmd.add("-s:v:0");
                ffmpegCmd.add(preset.getWidth() + "x" + preset.getHeight());
                ffmpegCmd.add("-g");
                ffmpegCmd.add("60");
                ffmpegCmd.add("-keyint_min");
                ffmpegCmd.add("60");
                ffmpegCmd.add("-f");
                ffmpegCmd.add("mpegts");
                ffmpegCmd.add("udp://127.0.0.1:" + videoPort);

                // Sanitize Audio Codec for Live HLS (Must be AAC)
                String aCodec = "aac";

                // FFmpeg output for this preset (Audio)
                ffmpegCmd.add("-map");
                ffmpegCmd.add("0:a:0?"); // Use ? because input stream might not have audio
                ffmpegCmd.add("-c:a:0");
                ffmpegCmd.add(aCodec);
                ffmpegCmd.add("-b:a:0");
                ffmpegCmd.add(preset.getAudioBitrate() + "k");
                ffmpegCmd.add("-f");
                ffmpegCmd.add("mpegts");
                ffmpegCmd.add("udp://127.0.0.1:" + audioPort);

                // Shaka inputs mapping
                int totalVideoBandwidth = (preset.getVideoBitrate() + 100) * 1024; // Adding a small overhead

                shakaCmd.add(String.format(
                        "in=udp://127.0.0.1:%d,stream=video,init_segment=/tmp/hls/%s/video_%s_init.mp4,segment_template=/tmp/hls/%s/video_%s_$Number$.m4s,playlist_name=video_%s.m3u8,iframe_playlist_name=video_%s_iframe.m3u8,bandwidth=%d",
                        videoPort, streamKey, presetStreamName, streamKey, presetStreamName, presetStreamName,
                        presetStreamName,
                        totalVideoBandwidth));
                shakaCmd.add(String.format(
                        "in=udp://127.0.0.1:%d,stream=audio,init_segment=/tmp/hls/%s/audio_%s_init.mp4,segment_template=/tmp/hls/%s/audio_%s_$Number$.m4s,playlist_name=audio_%s.m3u8,bandwidth=%d",
                        audioPort, streamKey, presetStreamName, streamKey, presetStreamName, presetStreamName,
                        preset.getAudioBitrate() * 1024));
            }

            // DVR Recording Hook (Segment Muxer) appended AT THE END of FFmpeg commands
            if (recordIntervalMinutes != null && recordIntervalMinutes > 0) {
                try {
                    String recordDir = "/data/outputs/recordings/" + streamKey;
                    new java.io.File(recordDir).mkdirs();

                    int segmentTimeSeconds = recordIntervalMinutes * 60;

                    ffmpegCmd.add("-map");
                    ffmpegCmd.add("0:v:0");
                    ffmpegCmd.add("-map");
                    ffmpegCmd.add("0:a:0?");
                    ffmpegCmd.add("-c:v");
                    ffmpegCmd.add("copy"); // Copy raw stream to avoid CPU hit
                    ffmpegCmd.add("-c:a");
                    ffmpegCmd.add("copy");
                    ffmpegCmd.add("-f");
                    ffmpegCmd.add("segment");
                    ffmpegCmd.add("-segment_format");
                    ffmpegCmd.add("mp4");
                    // Make MP4 fragments immediately streamable and flush them out so we don't lose
                    // the last partial duration on kill
                    ffmpegCmd.add("-segment_format_options");
                    ffmpegCmd.add("movflags=+faststart+frag_keyframe+empty_moov");
                    ffmpegCmd.add("-segment_time");
                    ffmpegCmd.add(String.valueOf(segmentTimeSeconds));
                    ffmpegCmd.add("-break_non_keyframes");
                    ffmpegCmd.add("1");
                    ffmpegCmd.add("-reset_timestamps");
                    ffmpegCmd.add("1");
                    ffmpegCmd.add("-strftime");
                    ffmpegCmd.add("1");
                    ffmpegCmd.add(recordDir + "/%Y-%m-%d_%H-%M-%S.mp4");
                } catch (Exception e) {
                    System.err.println("Could not setup DVR directories: " + e.getMessage());
                }
            }

            // Shaka HLS/DASH outputs
            shakaCmd.add("--hls_master_playlist_output");
            shakaCmd.add("/tmp/hls/" + streamKey + "/master.m3u8");
            shakaCmd.add("--mpd_output");
            shakaCmd.add("/tmp/hls/" + streamKey + "/master.mpd");
            shakaCmd.add("--hls_playlist_type");
            shakaCmd.add("LIVE");

            // Segment settings
            shakaCmd.add("--segment_duration");
            shakaCmd.add("2");
            shakaCmd.add("--time_shift_buffer_depth");
            shakaCmd.add("120");
            shakaCmd.add("--preserved_segments_outside_live_window");
            shakaCmd.add("30");

            ProcessBuilder pbFFmpeg = new ProcessBuilder(ffmpegCmd);
            pbFFmpeg.redirectErrorStream(true);
            Process ffmpegProcess = pbFFmpeg.start();

            ProcessBuilder pbShaka = new ProcessBuilder(shakaCmd);
            pbShaka.redirectErrorStream(true);
            Process shakaProcess = pbShaka.start();

            session.setOutputPath(hlsBaseUrl + "/" + streamKey + "/master.m3u8");
            sessionRepository.save(session);

            // Store the process so we can stop it later. Wrap in a thread to consume output
            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(ffmpegProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[FFmpeg] " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(shakaProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Shaka] " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Store processes temporarily, we will refactor Map to hold custom object or
            // both processes
            // For now, storing a future that kills both
            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
            future.whenComplete((res, ex) -> {
                ffmpegProcess.destroy();
                shakaProcess.destroy();
            });
            // We can't put CompletableFuture in FFmpegResultFuture map, let's change map
            // type in next step
            // For now, save them to a local static map or just change map type
            activeProcesses.put(session.getId(), new Process[] { ffmpegProcess, shakaProcess });

        } catch (Exception e) {
            System.err.println("Error applying Preset to Live Stream: " + e.getMessage());
            e.printStackTrace();
            // Fallback to raw stream on error
            session.setOutputPath(hlsBaseUrl + "/" + streamKey + ".m3u8");
            sessionRepository.save(session);
        }

        return session;
    }

    // New map for Process array
    private final Map<Long, Process[]> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public void stopStream(Long sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus("STOPPED");
            session.setEndTime(LocalDateTime.now());
            sessionRepository.save(session);

            // Stop processes if they exist
            Process[] processes = activeProcesses.remove(sessionId);
            if (processes != null) {
                for (Process p : processes) {
                    if (p != null && p.isAlive()) {
                        p.destroy();
                    }
                }
            }

            // Also cleanup legacy Jaffree map if it exists
            FFmpegResultFuture future = runningStreams.remove(sessionId);
            if (future != null && !future.isCancelled() && !future.isDone()) {
                future.graceStop();
            }
        });
    }

    @Override
    public List<LiveStreamSession> getAllSessions() {
        return sessionRepository.findAll();
    }

    @Override
    public LiveStreamSession getSession(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> new RuntimeException("Session not found"));
    }

    @Override
    public void deleteSession(Long sessionId) {
        stopStream(sessionId);
        sessionRepository.deleteById(sessionId);
    }

    @Override
    public List<LiveStreamRecord> getRecordings(String streamKey) {
        return recordRepository.findByStreamKeyOrderByStartTimeAsc(streamKey);
    }

    private void startDvrProcess(LiveStreamSession session, String sourceUrl) {
        try {
            String streamKey = session.getStreamKey();
            int recordIntervalMinutes = session.getRecordIntervalMinutes();

            String recordDir = "/data/outputs/recordings/" + streamKey;
            new java.io.File(recordDir).mkdirs();

            int segmentTimeSeconds = recordIntervalMinutes * 60;

            List<String> ffmpegCmd = new java.util.ArrayList<>();
            ffmpegCmd.add("ffmpeg");
            ffmpegCmd.add("-i");
            ffmpegCmd.add(sourceUrl);
            ffmpegCmd.add("-c:v");
            ffmpegCmd.add("copy");
            ffmpegCmd.add("-c:a");
            ffmpegCmd.add("copy");
            ffmpegCmd.add("-f");
            ffmpegCmd.add("segment");
            ffmpegCmd.add("-segment_format");
            ffmpegCmd.add("mp4");
            ffmpegCmd.add("-segment_format_options");
            ffmpegCmd.add("movflags=+faststart+frag_keyframe+empty_moov");
            ffmpegCmd.add("-segment_time");
            ffmpegCmd.add(String.valueOf(segmentTimeSeconds));
            ffmpegCmd.add("-break_non_keyframes");
            ffmpegCmd.add("1");
            ffmpegCmd.add("-reset_timestamps");
            ffmpegCmd.add("1");
            ffmpegCmd.add("-strftime");
            ffmpegCmd.add("1");
            ffmpegCmd.add(recordDir + "/%Y-%m-%d_%H-%M-%S.mp4");

            ProcessBuilder pbFFmpeg = new ProcessBuilder(ffmpegCmd);
            pbFFmpeg.redirectErrorStream(true);
            Process ffmpegProcess = pbFFmpeg.start();

            // Store the process so we can stop it later
            activeProcesses.put(session.getId(), new Process[] { ffmpegProcess });

            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(ffmpegProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[DVR-Only FFmpeg] " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            System.out.println("Started DVR-only FFmpeg process for " + streamKey);
        } catch (Exception e) {
            System.err.println("Could not setup DVR process: " + e.getMessage());
        }
    }
}
