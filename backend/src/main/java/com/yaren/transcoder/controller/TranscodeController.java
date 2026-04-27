package com.yaren.transcoder.controller;

import com.yaren.transcoder.entity.TranscodingJob;
import com.yaren.transcoder.repository.TranscodingJobRepository;
import com.yaren.transcoder.service.TranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/transcode")
@CrossOrigin(origins = "*")
public class TranscodeController {

    private final TranscodingJobRepository jobRepository;
    private final TranscodeService transcodeService;
    private static final Logger logger = LoggerFactory.getLogger(TranscodeController.class);

    public TranscodeController(TranscodingJobRepository jobRepository,
            TranscodeService transcodeService) {
        this.jobRepository = jobRepository;
        this.transcodeService = transcodeService;
        logger.info("✅ TranscodeController BAŞARIYLA YÜKLENDİ!");
    }

    @GetMapping("/jobs")
    public List<TranscodingJob> getAllJobs() {
        return jobRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJobById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(transcodeService.getJobDetails(id));
    }

    @PostMapping("/start")
    public ResponseEntity<TranscodingJob> startTranscode(
            @RequestParam("file") MultipartFile file,
            @RequestParam("presetId") Long presetId) throws IOException {
        logger.info("Yeni transcode isteği alındı. Dosya: {}, PresetID: {}", file.getOriginalFilename(), presetId);
        TranscodingJob savedJob = transcodeService.createAndStartJob(file, presetId);

        // Asenkron işlemi burada tetikliyoruz (Proxy üzerinden geçtiği için @Async
        // çalışır)
        transcodeService.startTranscoding(savedJob.getId());

        return ResponseEntity.ok(savedJob);
    }

    @DeleteMapping("/jobs/{id}")
    public void deleteJob(@PathVariable Long id) {
        logger.info("Job siliniyor. ID: {}", id);
        transcodeService.deleteJob(id);
    }

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) throws IOException {
        logger.info("Dosya indirme isteği: {}", fileName);
        Resource resource = transcodeService.getDownloadResource(fileName);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> listUploadedFiles() {
        return ResponseEntity.ok(transcodeService.listUploadedFiles());
    }

    @PostMapping("/start-existing")
    public ResponseEntity<TranscodingJob> startTranscodeExisting(
            @RequestParam("fileName") String fileName,
            @RequestParam("presetId") Long presetId) throws IOException {
        logger.info("Mevcut dosya ile transcode isteği. Dosya: {}, PresetID: {}", fileName, presetId);
        TranscodingJob savedJob = transcodeService.createAndStartJobFromExisting(fileName, presetId);

        // Asenkron işlemi burada tetikliyoruz
        transcodeService.startTranscoding(savedJob.getId());

        return ResponseEntity.ok(savedJob);
    }
}