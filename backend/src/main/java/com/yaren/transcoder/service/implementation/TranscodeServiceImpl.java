package com.yaren.transcoder.service.implementation;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.*;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.yaren.transcoder.entity.Preset;
import com.yaren.transcoder.entity.TranscodingJob;
import com.yaren.transcoder.repository.TranscodingJobRepository;
import com.yaren.transcoder.service.TranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TranscodeServiceImpl implements TranscodeService {

    private static final Logger logger = LoggerFactory.getLogger(TranscodeServiceImpl.class);

    @Value("${ffmpeg.path:/opt/homebrew/bin/ffmpeg}")
    private String ffmpegPath;

    @Value("${ffprobe.path:/opt/homebrew/bin/ffprobe}")
    private String ffprobePath;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDirStr;

    @Value("${file.output-dir:./outputs}")
    private String outputDirStr;

    private final TranscodingJobRepository jobRepository;
    private final com.yaren.transcoder.repository.PresetRepository presetRepository;

    public TranscodeServiceImpl(TranscodingJobRepository jobRepository,
            com.yaren.transcoder.repository.PresetRepository presetRepository) {
        this.jobRepository = jobRepository;
        this.presetRepository = presetRepository;
    }

    @Async("transcodeTaskExecutor")
    @Override
    public void startTranscoding(Long jobId) {
        TranscodingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            logger.error("❌ Job bulunamadı: {}", jobId);
            return;
        }

        try {
            // Durum güncelle
            logger.info("🔄 Durum güncelleniyor: IN_PROGRESS for Job #{}", jobId);
            job.setStatus("IN_PROGRESS");
            job.setProgress(0);
            jobRepository.save(job);

            Path inputPath = Paths.get(job.getInputPath()).toAbsolutePath();
            Path outputPath = Paths.get(job.getOutputPath()).toAbsolutePath();

            logger.info("🎬 TRANSCODE BAŞLIYOR: Job #{}", jobId);
            logger.info("   📂 Input : {}", inputPath);
            logger.info("   📂 Output: {}", outputPath);

            // Çıktı dizini kontrolü
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            // Giriş dosyası kontrolü
            if (!Files.exists(inputPath)) {
                throw new RuntimeException("Giriş dosyası bulunamadı: " + inputPath);
            }

            // Medya Analizi (FFprobe)
            FFprobeResult probeResult = FFprobe.atPath(Paths.get(ffprobePath).getParent())
                    .setInput(inputPath)
                    .setShowStreams(true)
                    .setShowFormat(true)
                    .execute();

            Float durationSecondsFloat = probeResult.getFormat().getDuration();
            Double durationSeconds = durationSecondsFloat != null ? durationSecondsFloat.doubleValue() : 0.0;
            long durationMillis = (long) (durationSeconds * 1000);
            logger.info("   ⏱ Dosya süresi: {} saniye ({} ms)", String.format("%.1f", durationSeconds), durationMillis);

            // Preset kontrolü
            Preset preset = job.getSelectedPreset();
            if (preset == null) {
                throw new RuntimeException("Job #" + jobId + " için preset atanmamış!");
            }

            logger.info("   ⚙ Preset: {} [Codec: {}, Size: {}x{}, CRF: {}, Speed: {}]",
                    preset.getName(), preset.getVideoCodec(), preset.getWidth(), preset.getHeight(),
                    preset.getCrf(), preset.getPresetSpeed());

            // --- JAFFREE FFMPEG START ---

            // Atomic referans, progress listener içinde son güncellenen yüzdeyi tutmak için
            AtomicLong lastPercentage = new AtomicLong(-1);

            FFmpeg ffmpeg = FFmpeg.atPath(Paths.get(ffmpegPath).getParent())
                    .addInput(UrlInput.fromPath(inputPath))
                    .addOutput(buildOutputFromPreset(outputPath, preset))
                    .setOverwriteOutput(true)
                    .setProgressListener(progress -> {
                        // Progress Listener Implementation
                        long timeMillis = progress.getTimeMillis();
                        if (durationMillis > 0) {
                            int percentage = (int) ((timeMillis * 100) / durationMillis);

                            // 0-100 arası sınırla
                            percentage = Math.max(0, Math.min(100, percentage));

                            // Veritabanını çok sık güncellememek için (her %5 değişimde)
                            long last = lastPercentage.get();
                            if (percentage > last + 4 && percentage <= 99) {
                                lastPercentage.set(percentage);
                                logger.info("   ⏳ İlerleme: %{}", percentage);
                                job.setProgress(percentage);
                                jobRepository.save(job); // Transactional olmadığı için flush eder
                            }
                        }
                    });

            // Komutu çalıştır
            FFmpegResult result = ffmpeg.execute();

            // --- JAFFREE FFMPEG END ---

            logger.info("✅ İŞLEM BAŞARILI: Job #{}", jobId);

            // Çıktı boyutu
            if (Files.exists(outputPath)) {
                logger.info("   📦 Çıktı boyutu: {} KB", Files.size(outputPath) / 1024);
            }

            job.setStatus("COMPLETED");
            job.setProgress(100);
            jobRepository.save(job);

        } catch (Throwable e) {
            logger.error("❌ TRANSCODE HATASI (Job #{}): {} - {}", jobId, e.getClass().getName(), e.getMessage());
            e.printStackTrace();

            job.setStatus("FAILED");
            job.setProgress(0);
            jobRepository.save(job);
        }
    }

    private UrlOutput buildOutputFromPreset(Path outputPath, Preset preset) {
        UrlOutput output = UrlOutput.toPath(outputPath);

        // Codecs
        if (preset.getVideoCodec() != null && !preset.getVideoCodec().isEmpty()) {
            output.setCodec(StreamType.VIDEO, preset.getVideoCodec());
        }

        // Audio Codec Logic
        String audioCodec = preset.getAudioCodec();
        // Rule 0: General Audio Codec Correction
        if ("opus".equalsIgnoreCase(audioCodec)) {
            logger.warn("⚠️ UYARI: 'opus' encoder deneysel. 'libopus' olarak güncelleniyor.");
            audioCodec = "libopus";
        }

        // Rule 1: WebM Container Compatibility
        String container = preset.getContainer() != null ? preset.getContainer().toLowerCase() : "mp4";
        if ("webm".equals(container)) {
            // WebM Check logic could be here, but usually container implies codec or vice
            // versa.
            // Jaffree is more flexible. We adhere to the user's explicit codec choice
            // mostly.
            if (audioCodec == null || (!audioCodec.equals("libvorbis") && !audioCodec.equals("libopus"))) {
                logger.warn("⚠️ UYARI: WebM formatı için audio codec 'libopus' olarak ayarlanıyor.");
                audioCodec = "libopus";
            }
        }

        if (audioCodec != null && !audioCodec.isEmpty()) {
            output.setCodec(StreamType.AUDIO, audioCodec);
        }

        // Resolution
        // Resolution needs strict even numbers usually
        int width = preset.getWidth();
        int height = preset.getHeight();
        if (width > 0 && width % 2 != 0)
            width--;
        if (height > 0 && height % 2 != 0)
            height--;

        if (width > 0 && height > 0) {
            // Jaffree uses filters for scaling usually, but simple -s is also supported via
            // arguments
            // Using generic argument for safety and simplicity compatible with previous
            // approach
            output.addArguments("-s", width + "x" + height);
        }

        // FPS
        if (preset.getFps() > 0) {
            output.setFrameRate(preset.getFps());
        }

        // Bitrate / CRF
        if (preset.getCrf() > 0) {
            // CRF is codec specific, usually via -crf arg
            output.addArguments("-crf", String.valueOf(preset.getCrf()));
        } else if (preset.getVideoBitrate() > 0) {
            output.addArguments("-b:v", preset.getVideoBitrate() + "k");
        }

        // Preset Speed (e.g., fast, slow)
        if (preset.getPresetSpeed() != null && !preset.getPresetSpeed().isEmpty()) {
            output.addArguments("-preset", preset.getPresetSpeed());
        }

        // Audio Params
        if (preset.getAudioBitrate() > 0) {
            output.addArguments("-b:a", preset.getAudioBitrate() + "k");
        }
        if (preset.getAudioSampleRate() > 0) {
            output.addArguments("-ar", String.valueOf(preset.getAudioSampleRate()));
        }
        if (preset.getAudioChannels() > 0) {
            output.addArguments("-ac", String.valueOf(preset.getAudioChannels()));
        }

        // Tune
        if (preset.getTune() != null && !preset.getTune().isEmpty()) {
            output.addArguments("-tune", preset.getTune());
        }

        // Profile
        if (preset.getProfile() != null && !preset.getProfile().isEmpty()) {
            output.addArguments("-profile:v", preset.getProfile());
            // For compatibility
            output.setPixelFormat("yuv420p");
        }

        // GOP
        if (preset.getKeyframeInterval() > 0) {
            output.addArguments("-g", String.valueOf(preset.getKeyframeInterval()));
        }

        // Aspect
        if (preset.getAspectRatio() != null && !preset.getAspectRatio().isEmpty()) {
            output.addArguments("-aspect", preset.getAspectRatio());
        }

        return output;
    }

    // --- Service Implementation ---

    @Override
    public TranscodingJob createAndStartJob(org.springframework.web.multipart.MultipartFile file, Long presetId)
            throws IOException {
        logger.info("🚀 YENİ VİDEO YÜKLENİYOR: {}", file.getOriginalFilename());

        Preset preset = presetRepository.findById(presetId)
                .orElseThrow(() -> new RuntimeException("Preset bulunamadı! ID: " + presetId));

        Path uploadDir = Paths.get(uploadDirStr);
        Files.createDirectories(uploadDir);

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path inputPath = uploadDir.resolve(fileName);
        Files.copy(file.getInputStream(), inputPath);

        return createJobEntityAndStart(fileName, inputPath, preset, file.getOriginalFilename());
    }

    @Override
    public TranscodingJob createAndStartJobFromExisting(String fileName, Long presetId) throws IOException {
        Preset preset = presetRepository.findById(presetId)
                .orElseThrow(() -> new RuntimeException("Preset bulunamadı! ID: " + presetId));

        Path uploadDir = Paths.get(uploadDirStr);
        Path inputPath = uploadDir.resolve(fileName).normalize();

        if (!Files.exists(inputPath)) {
            throw new RuntimeException("Giriş dosyası bulunamadı: " + fileName);
        }

        return createJobEntityAndStart(fileName, inputPath, preset, fileName);
    }

    private TranscodingJob createJobEntityAndStart(String fileName, Path inputPath, Preset preset,
            String originalFilename) throws IOException {
        Path outputDir = Paths.get(outputDirStr);
        Files.createDirectories(outputDir);

        TranscodingJob job = new TranscodingJob();
        job.setFileName(fileName);
        job.setInputPath(inputPath.toString());

        String cleanName = "video";
        if (originalFilename != null && originalFilename.contains(".")) {
            cleanName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        }

        String extension = (preset.getContainer() != null && !preset.getContainer().isEmpty())
                ? preset.getContainer()
                : "mp4";

        // WebM to MKV correction logic can be applied here for filename if needed,
        // but for now keeping it simple as per previous logic.
        if ("webm".equalsIgnoreCase(extension) &&
                (preset.getVideoCodec().contains("h264") || preset.getVideoCodec().contains("h265"))) {
            extension = "mkv";
        }

        String outputName = "out_" + System.currentTimeMillis() + "_" + cleanName + "." + extension;

        job.setOutputPath(outputDir.resolve(outputName).toString());
        job.setOutputFileName(outputName);

        job.setSelectedPreset(preset);
        job.setStatus("QUEUED");
        job.setProgress(0);
        job.setTimestamp(java.time.LocalDateTime.now());

        return jobRepository.save(job);
    }

    @Override
    public void deleteJob(Long id) {
        TranscodingJob job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getOutputPath() != null) {
            try {
                Files.deleteIfExists(Paths.get(job.getOutputPath()));
                logger.info("🗑️ Dosya silindi: {}", job.getOutputPath());
            } catch (IOException e) {
                logger.warn("⚠️ Dosya silinemedi: {}", e.getMessage());
            }
        }
        jobRepository.delete(job);
    }

    @Override
    public List<String> listUploadedFiles() {
        try {
            Path uploadDir = Paths.get(uploadDirStr);
            if (!Files.exists(uploadDir)) {
                return List.of();
            }

            try (Stream<Path> stream = Files.list(uploadDir)) {
                return stream
                        .filter(file -> !Files.isDirectory(file))
                        .filter(file -> !file.getFileName().toString().startsWith("."))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getJobDetails(Long id) {
        TranscodingJob job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job bulunamadı: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", job.getId());
        response.put("fileName", job.getFileName());
        response.put("inputPath", job.getInputPath());
        response.put("outputPath", job.getOutputPath());
        response.put("outputFileName", job.getOutputFileName());
        response.put("status", job.getStatus());
        response.put("progress", job.getProgress());
        response.put("timestamp", job.getTimestamp());

        if (job.getSelectedPreset() != null) {
            Preset p = job.getSelectedPreset();
            Map<String, Object> presetMap = new LinkedHashMap<>();
            presetMap.put("id", p.getId());
            presetMap.put("name", p.getName());
            presetMap.put("width", p.getWidth());
            presetMap.put("height", p.getHeight());
            presetMap.put("videoCodec", p.getVideoCodec());
            presetMap.put("container", p.getContainer());
            response.put("selectedPreset", presetMap);
        }

        return response;
    }

    @Override
    public org.springframework.core.io.Resource getDownloadResource(String fileName)
            throws MalformedURLException {
        Path filePath = Paths.get(outputDirStr).resolve(fileName).normalize();
        org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Dosya bulunamadı veya okunamıyor: " + fileName);
        }
    }
}