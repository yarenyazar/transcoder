package com.yaren.transcoder.service.implementation;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.yaren.transcoder.entity.VodContent;
import com.yaren.transcoder.repository.VodContentRepository;
import com.yaren.transcoder.service.VodTranscoderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class VodTranscoderServiceImpl implements VodTranscoderService {

    private static final Logger logger = LoggerFactory.getLogger(VodTranscoderServiceImpl.class);

    @Value("${file.upload-dir:./uploads}")
    private String baseUploadDirStr;

    @Value("${ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    @Value("${app.vod-hls-base-url:/api/vod/stream}")
    private String streamBaseUrl;

    private final VodContentRepository vodContentRepository;
    private final com.yaren.transcoder.repository.PresetRepository presetRepository;

    public VodTranscoderServiceImpl(VodContentRepository vodContentRepository,
            com.yaren.transcoder.repository.PresetRepository presetRepository) {
        this.vodContentRepository = vodContentRepository;
        this.presetRepository = presetRepository;
    }

    @Override
    public void uploadAndTranscodeVod(Long vodContentId, MultipartFile file, List<Long> presetIds)
            throws IOException {
        VodContent content = vodContentRepository.findById(vodContentId)
                .orElseThrow(() -> new RuntimeException("VOD Content not found: " + vodContentId));

        logger.info("🚀 YENİ VOD VİDEO YÜKLENİYOR: {}", file.getOriginalFilename());

        Path vodDir = Paths.get(baseUploadDirStr, "vod", String.valueOf(vodContentId));
        Files.createDirectories(vodDir);

        String rawName = file.getOriginalFilename();
        String originalFilename = (rawName != null && !rawName.isEmpty()) ? rawName : "video.mp4";
        String cleanName = System.currentTimeMillis() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9.\\-]", "_");

        Path inputPath = vodDir.resolve("raw_" + cleanName);
        Files.copy(file.getInputStream(), inputPath);

        content.setStatus("ENCODING");
        vodContentRepository.save(content);

        processVideoAsync(content.getId(), inputPath, presetIds);
    }

    @Async("transcodeTaskExecutor")
    public void processVideoAsync(Long vodContentId, Path inputPath, List<Long> presetIds) {
        VodContent content = vodContentRepository.findById(vodContentId).orElse(null);
        if (content == null)
            return;

        try {
            logger.info("🎬 VOD HLS TRANSCODE BAŞLIYOR: Content #{}", vodContentId);

            Path vodDir = Paths.get(baseUploadDirStr, "vod", String.valueOf(vodContentId));
            Files.createDirectories(vodDir);

            List<com.yaren.transcoder.entity.Preset> presets;
            if (presetIds != null && !presetIds.isEmpty()) {
                presets = presetRepository.findAllById(presetIds);
            } else {
                presets = presetRepository.findAll();
            }

            if (presets.isEmpty()) {
                com.yaren.transcoder.entity.Preset p = new com.yaren.transcoder.entity.Preset();
                p.setName("1080p");
                p.setHeight(1080);
                p.setVideoBitrate(5000);
                p.setAudioBitrate(128);
                p.setCrf(23);
                presets.add(p);
            }

            FFmpeg ffmpeg = FFmpeg.atPath(Paths.get(ffmpegPath).getParent())
                    .addInput(UrlInput.fromPath(inputPath));

            StringBuilder filterComplex = new StringBuilder();
            filterComplex.append("[0:v]split=").append(presets.size());
            for (int i = 0; i < presets.size(); i++)
                filterComplex.append("[v").append(i).append("]");
            filterComplex.append(";");

            for (int i = 0; i < presets.size(); i++) {
                int height = presets.get(i).getHeight();
                Integer widthOpt = presets.get(i).getWidth();
                String scale = (widthOpt != null && widthOpt > 0) ? widthOpt + ":" + height : "-2:" + height;
                filterComplex.append("[v").append(i).append("]scale=").append(scale).append("[v").append(i)
                        .append("out];");
            }

            if (filterComplex.length() > 0 && filterComplex.charAt(filterComplex.length() - 1) == ';') {
                filterComplex.setLength(filterComplex.length() - 1);
            }

            ffmpeg.addArguments("-filter_complex", filterComplex.toString());

            boolean hasAudio = false;
            try {
                ProcessBuilder pb = new ProcessBuilder(Paths.get(ffmpegPath).getParent().resolve("ffprobe").toString(), "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", inputPath.toString());
                Process p = pb.start();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().equalsIgnoreCase("audio")) {
                        hasAudio = true;
                        break;
                    }
                }
                p.waitFor();
            } catch (Exception e) {
                logger.warn("ffprobe check failed, assuming hasAudio=true", e);
                hasAudio = true;
            }

            StringBuilder varStreamMap = new StringBuilder();
            for (int i = 0; i < presets.size(); i++) {
                com.yaren.transcoder.entity.Preset p = presets.get(i);

                ffmpeg.addArguments("-map", "[v" + i + "out]");
                ffmpeg.addArguments("-c:v:" + i, "libx264");
                ffmpeg.addArguments("-preset", "fast");
                ffmpeg.addArguments("-b:v:" + i, p.getVideoBitrate() + "k");
                ffmpeg.addArguments("-maxrate:v:" + i, (int) (p.getVideoBitrate() * 1.1) + "k");
                ffmpeg.addArguments("-bufsize:v:" + i, (p.getVideoBitrate() * 2) + "k");
                ffmpeg.addArguments("-crf:v:" + i, String.valueOf(p.getCrf() > 0 ? p.getCrf() : 23));
                ffmpeg.addArguments("-g", "50");
                ffmpeg.addArguments("-keyint_min", "50");
                ffmpeg.addArguments("-sc_threshold", "0");

                if (hasAudio) {
                    ffmpeg.addArguments("-map", "a:0?");
                    ffmpeg.addArguments("-c:a:" + i, "aac");
                    ffmpeg.addArguments("-b:a:" + i, p.getAudioBitrate() + "k");
                    ffmpeg.addArguments("-ar:a:" + i, "44100");
                }

                String variantName = p.getName() != null ? p.getName().replaceAll("[^a-zA-Z0-9]", "")
                        : p.getHeight() + "p";
                if (hasAudio) {
                    varStreamMap.append("v:").append(i).append(",a:").append(i).append(",name:").append(variantName).append(" ");
                } else {
                    varStreamMap.append("v:").append(i).append(",name:").append(variantName).append(" ");
                }
            }

            // Flattened structure: Output v%v.m3u8 and v%v_%03d.ts in the SAME directory
            String segmentFilename = vodDir.resolve("v%v_%03d.ts").toString();
            UrlOutput output = UrlOutput.toPath(vodDir.resolve("v%v.m3u8"))
                    .setFormat("hls")
                    .addArguments("-hls_time", "6")
                    .addArguments("-hls_list_size", "0")
                    .addArguments("-hls_playlist_type", "vod")
                    .addArguments("-hls_segment_filename", segmentFilename)
                    .addArguments("-master_pl_name", "master.m3u8")
                    .addArguments("-hls_flags", "independent_segments")
                    .addArguments("-var_stream_map", varStreamMap.toString().trim());

            ffmpeg.addOutput(output).setOverwriteOutput(true);
            logger.info("🎬 FFmpeg ÇALIŞTIRILIYOR (Flattened ABR)...");
            ffmpeg.execute();

            logger.info("✅ VOD ABR İŞLEM BAŞARILI: Content #{}", vodContentId);

            content.setStatus("READY");
            content.setVideoUrl(streamBaseUrl + "/" + vodContentId + "/master.m3u8");
            vodContentRepository.save(content);

        } catch (Throwable e) {
            logger.error("❌ VOD TRANSCODE HATASI (Content #{}): {}", vodContentId, e.getMessage());
            content.setStatus("FAILED");
            vodContentRepository.save(content);
        }
    }
}
