package com.yaren.transcoder.controller;

import com.yaren.transcoder.dto.VodContentRequest;
import com.yaren.transcoder.entity.VodContent;
import com.yaren.transcoder.service.VodContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/vod")
public class VodContentController {
    private static final Logger logger = LoggerFactory.getLogger(VodContentController.class);

    private final VodContentService vodContentService;

    @Autowired
    public VodContentController(VodContentService vodContentService) {
        this.vodContentService = vodContentService;
    }

    @PostMapping("/imdb/{imdbId}")
    public ResponseEntity<VodContent> createFromImdb(@PathVariable String imdbId) {
        return ResponseEntity.ok(vodContentService.createFromImdb(imdbId));
    }

    @GetMapping("/search/omdb")
    public ResponseEntity<com.yaren.transcoder.dto.OmdbSearchResponse> searchOmdb(@RequestParam("title") String title) {
        return ResponseEntity
                .ok(((com.yaren.transcoder.service.implementation.OmdbServiceImpl) vodContentService.getOmdbService())
                        .searchByTitle(title));
    }

    @PostMapping
    public ResponseEntity<VodContent> createManual(@RequestBody VodContentRequest request) {
        return ResponseEntity.ok(vodContentService.createManual(request));
    }

    @GetMapping
    public ResponseEntity<List<VodContent>> getAllContents() {
        return ResponseEntity.ok(vodContentService.getAllContents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VodContent> getContentById(@PathVariable Long id) {
        return ResponseEntity.ok(vodContentService.getContentById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VodContent> updateContent(@PathVariable Long id, @RequestBody VodContentRequest request) {
        return ResponseEntity.ok(vodContentService.updateContent(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContent(@PathVariable Long id) {
        vodContentService.deleteContent(id);
        return ResponseEntity.noContent().build();
    }

    @Value("${file.upload-dir:./data/uploads}")
    private String uploadDir;

    @Autowired
    private com.yaren.transcoder.service.VodTranscoderService vodTranscoderService;

    private static final java.util.Map<String, String> CONTENT_TYPES = java.util.Map.of(
            "m3u8", "application/vnd.apple.mpegurl",
            "ts", "video/MP2T",
            "vtt", "text/vtt"
    );

    @GetMapping("/poster/{filename:.+}")
    public ResponseEntity<Resource> servePoster(@PathVariable String filename) throws java.io.IOException {
        Path file = Paths.get(uploadDir, "posters").resolve(filename).normalize();
        Resource resource = new UrlResource(file.toUri());

        return Optional.of(resource)
                .filter(r -> r.exists() || r.isReadable())
                .map(r -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("image/webp"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.getFilename() + "\"")
                        .body(r))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/upload-video")
    public ResponseEntity<String> uploadVideo(@PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "presetIds", required = false) List<Long> presetIds) throws Exception {
        vodTranscoderService.uploadAndTranscodeVod(id, file, presetIds);
        return ResponseEntity.ok("Video upload started and transcoding queued.");
    }

    @PostMapping("/{id}/upload-subtitle")
    public ResponseEntity<VodContent> uploadSubtitle(
            @PathVariable Long id,
            @RequestParam("language") String language,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        return ResponseEntity.ok(vodContentService.uploadSubtitle(id, language, file));
    }

    @PostMapping("/{id}/generate-subtitles")
    public ResponseEntity<VodContent> generateSubtitles(
            @PathVariable Long id,
            @RequestParam("language") String language) {
        return ResponseEntity.ok(vodContentService.generateSubtitles(id, language));
    }

    @GetMapping(value = "/stream/{id}/{filename:.+}", produces = { "application/vnd.apple.mpegurl", "video/MP2T",
            "text/vtt" })
    public ResponseEntity<Resource> serveVodStream(
            @PathVariable Long id,
            @PathVariable String filename) throws java.io.IOException {
        Path file = Paths.get(uploadDir, "vod", String.valueOf(id)).resolve(filename).normalize();
        Resource resource = new UrlResource(file.toUri());

        return Optional.of(resource)
                .filter(r -> r.exists() || r.isReadable())
                .map(r -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(getContentType(filename)))
                        .body(r))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String getContentType(String filename) {
        return Optional.of(filename.lastIndexOf('.'))
                .filter(i -> i > 0)
                .map(i -> filename.substring(i + 1).toLowerCase())
                .map(CONTENT_TYPES::get)
                .orElse("application/octet-stream");
    }

    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<Void> handleIOException(java.io.IOException e) {
        logger.error("IO Error: {}", e.getMessage());
        return ResponseEntity.internalServerError().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception e) {
        logger.error("Error in VodContentController: {}", e.getMessage());
        if (e instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.internalServerError().body(e.getMessage());
    }

}
