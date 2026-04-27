package com.yaren.transcoder.service.implementation;

import com.yaren.transcoder.dto.OmdbResponse;
import com.yaren.transcoder.dto.VodContentRequest;
import com.yaren.transcoder.entity.CastMember;
import com.yaren.transcoder.entity.VodContent;
import com.yaren.transcoder.repository.CastMemberRepository;
import com.yaren.transcoder.repository.VodContentRepository;
import com.yaren.transcoder.service.ImageOptimizationService;
import com.yaren.transcoder.service.OmdbService;
import com.yaren.transcoder.service.VodContentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.io.File;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;

@Service
public class VodContentServiceImpl implements VodContentService {

    private final VodContentRepository vodContentRepository;
    private final CastMemberRepository castMemberRepository;
    private final OmdbService omdbService;
    private final ImageOptimizationService imageOptimizationService;
    private static final Logger logger = LoggerFactory.getLogger(VodContentServiceImpl.class);

    @Autowired
    public VodContentServiceImpl(
            VodContentRepository vodContentRepository,
            CastMemberRepository castMemberRepository,
            OmdbService omdbService,
            ImageOptimizationService imageOptimizationService) {
        this.vodContentRepository = vodContentRepository;
        this.castMemberRepository = castMemberRepository;
        this.omdbService = omdbService;
        this.imageOptimizationService = imageOptimizationService;
    }

    @Override
    public OmdbService getOmdbService() {
        return this.omdbService;
    }

    @Override
    public VodContent createFromImdb(String imdbId) {
        logger.info("🎬 IMDB IMPORT BAŞLADI: {}", imdbId);
        // 1. Check for duplicates before hitting the external API
        if (imdbId != null && vodContentRepository.existsByImdbId(imdbId)) {
            logger.warn("⚠️ Bu içerik zaten mevcut: {}", imdbId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu içerik zaten ekli: " + imdbId);
        }

        OmdbResponse omdbResponse = omdbService.getDetailsByImdbId(imdbId);

        if (omdbResponse == null || "False".equalsIgnoreCase(omdbResponse.getResponse())) {
            String errorMsg = omdbResponse != null ? omdbResponse.getError() : "Unknown error or null response";
            logger.error("❌ OMDB API Hatası (IMDb #{}): {}", imdbId, errorMsg);
            throw new RuntimeException("Content not found in OMDb or invalid API Key. Error: " + errorMsg);
        }
        logger.info("✅ OMDB Verisi Alındı: {}", omdbResponse.getTitle());

        VodContent content = new VodContent();
        content.setTitle(omdbResponse.getTitle());
        content.setDescription(omdbResponse.getPlot());
        content.setType(omdbResponse.getType());

        try {
            if (omdbResponse.getYear() != null && !omdbResponse.getYear().isEmpty()) {
                // Year might come like "2015-" for series, just take first 4 digits
                content.setReleaseYear(Integer.parseInt(omdbResponse.getYear().substring(0, 4)));
            }
        } catch (Exception e) {
            content.setReleaseYear(null);
        }

        content.setImdbId(omdbResponse.getImdbId());

        // Setup original poster URL
        String originalPosterUrl = omdbResponse.getPoster();
        if ("N/A".equalsIgnoreCase(originalPosterUrl)) {
            originalPosterUrl = null;
        }
        content.setPosterUrl(originalPosterUrl);

        // Convert to WebP and save local file name
        String webpFilename = imageOptimizationService.downloadAndConvertToWebp(originalPosterUrl, content.getImdbId());
        content.setWebpPosterUrl(webpFilename != null ? "/api/vod/poster/" + webpFilename : null);

        content.setStatus("METADATA_ONLY");

        // Save content first to establish ID
        VodContent savedContent = vodContentRepository.save(content);

        // Process Cast (Actors and Directors)
        processAndAddCast(savedContent, omdbResponse.getActors(), "Actor");
        processAndAddCast(savedContent, omdbResponse.getDirector(), "Director");

        logger.info("💾 İçerik veri tabanına kaydedildi: ID #{}", savedContent.getId());
        return vodContentRepository.save(savedContent); // Update with cast references
    }

    private void processAndAddCast(VodContent content, String namesCommaSeparated, String role) {
        if (namesCommaSeparated == null || "N/A".equalsIgnoreCase(namesCommaSeparated)) {
            return;
        }

        String[] names = namesCommaSeparated.split(",");
        for (String name : names) {
            String trimmedName = name.trim();
            if (trimmedName.isEmpty())
                continue;

            // Check if cast member exists
            List<CastMember> existingMembers = castMemberRepository.findByNameContainingIgnoreCase(trimmedName);
            CastMember member;

            if (!existingMembers.isEmpty()) {
                member = existingMembers.get(0);
            } else {
                member = new CastMember();
                member.setName(trimmedName);
                member.setRole(role);
                member = castMemberRepository.save(member);
            }

            content.getCastMembers().add(member);
        }
    }

    @Override
    public VodContent createManual(VodContentRequest request) {
        VodContent content = new VodContent();
        content.setTitle(request.getTitle());
        content.setDescription(request.getDescription());
        content.setType(request.getType());
        content.setReleaseYear(request.getReleaseYear());
        content.setImdbId(request.getImdbId());
        content.setDurationSeconds(request.getDurationSeconds());
        content.setPosterUrl(request.getPosterUrl());
        content.setBackdropUrl(request.getBackdropUrl());
        content.setStatus("METADATA_ONLY");

        return vodContentRepository.save(content);
    }

    @Override
    public List<VodContent> getAllContents() {
        return vodContentRepository.findAll();
    }

    @Override
    public VodContent getContentById(Long id) {
        Optional<VodContent> content = vodContentRepository.findById(id);
        if (content.isEmpty()) {
            throw new RuntimeException("Vod Content not found with id: " + id);
        }
        return content.get();
    }

    @Override
    public VodContent updateContent(Long id, VodContentRequest request) {
        VodContent existingContent = getContentById(id);

        existingContent.setTitle(request.getTitle());
        existingContent.setDescription(request.getDescription());
        existingContent.setType(request.getType());
        existingContent.setReleaseYear(request.getReleaseYear());
        existingContent.setDurationSeconds(request.getDurationSeconds());
        existingContent.setPosterUrl(request.getPosterUrl());
        existingContent.setBackdropUrl(request.getBackdropUrl());

        // Status updates etc.
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            existingContent.setStatus(request.getStatus());
        }
        if (request.getVideoUrl() != null && !request.getVideoUrl().isEmpty()) {
            existingContent.setVideoUrl(request.getVideoUrl());
        }

        return vodContentRepository.save(existingContent);
    }

    @Override
    public void deleteContent(Long id) {
        vodContentRepository.deleteById(id);
    }

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir:./uploads}")
    private String baseUploadDirStr;

    @org.springframework.beans.factory.annotation.Value("${app.vod-hls-base-url:http://localhost:8080/api/vod/stream}")
    private String streamBaseUrl;

    @Override
    public VodContent uploadSubtitle(Long id, String language, org.springframework.web.multipart.MultipartFile file) {
        VodContent content = getContentById(id);
        try {
            java.nio.file.Path subtitleDir = java.nio.file.Paths.get(baseUploadDirStr, "vod", String.valueOf(id));
            java.nio.file.Files.createDirectories(subtitleDir);

            String filename = language.toLowerCase().replaceAll("[^a-z0-9]", "") + ".vtt";
            java.nio.file.Path filePath = subtitleDir.resolve(filename);

            java.nio.file.Files.copy(file.getInputStream(), filePath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String url = streamBaseUrl + "/" + id + "/" + filename;
            content.getSubtitles().put(language, url);

            return vodContentRepository.save(content);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to store subtitle file", e);
        }
    }

    @Override
    public VodContent generateSubtitles(Long id, String language) {
        String lang = language.toLowerCase();
        logger.info("🤖 AI ALTYAZI ÜRETİMİ BAŞLADI: Content #{} Dil: {}", id, lang);

        VodContent content = getContentById(id);

        // Find the newest raw video file
        File vodDir = new File(baseUploadDirStr + "/vod/" + content.getId());
        File[] files = vodDir.listFiles((d, name) -> name.startsWith("raw_") && name.endsWith(".mp4"));

        String videoFileName = "raw.mp4";
        if (files != null && files.length > 0) {
            // Sort by last modified descending
            java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            videoFileName = files[0].getName();
        } else {
            logger.error("❌ Ham video dosyası bulunamadı: /data/uploads/vod/{}/", content.getId());
            throw new IllegalStateException("Video dosyası henüz yüklenmemiş veya bulunamadı.");
        }

        String videoPath = "/data/uploads/vod/" + content.getId() + "/" + videoFileName;
        String outputDir = "/data/uploads/vod/" + content.getId();

        logger.info("📂 Kullanılacak video yolu: {}", videoPath);

        // Simple timeout configuration for RestTemplate (10 minutes)
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(600000); // 10 minutes

        RestTemplate restTemplate = new RestTemplate(factory);
        String aiServiceUrl = "http://ai-subtitles:5000/generate-subtitles";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = String.format("{\"video_path\":\"%s\", \"output_dir\":\"%s\", \"language\":\"%s\"}",
                videoPath, outputDir, lang);

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        try {
            logger.info("📡 AI servisine istek gönderiliyor: {}", aiServiceUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(aiServiceUrl, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                String subtitleUrl = streamBaseUrl + "/" + id + "/" + lang + ".vtt";
                content.getSubtitles().put(lang, subtitleUrl);
                logger.info("✅ AI ALTYAZI BAŞARILI: {}", subtitleUrl);
                return vodContentRepository.save(content);
            } else {
                logger.error("❌ AI Servis Hatası: {}", response.getBody());
                throw new RuntimeException("AI servis hatası: " + response.getBody());
            }
        } catch (Exception e) {
            logger.error("❌ AI Altyazı Üretiminde Beklenmedik Hata: ", e);
            throw new RuntimeException("AI altyazı üretimi başarısız oldu: " + e.getMessage());
        }
    }
}
