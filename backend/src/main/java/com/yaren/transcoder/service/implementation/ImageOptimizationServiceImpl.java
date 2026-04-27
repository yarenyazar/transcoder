package com.yaren.transcoder.service.implementation;

import com.yaren.transcoder.service.ImageOptimizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class ImageOptimizationServiceImpl implements ImageOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizationServiceImpl.class);

    @Value("${file.upload-dir:./data/uploads}")
    private String uploadDir;

    @Value("${ffmpeg.path:/opt/homebrew/bin/ffmpeg}")
    private String ffmpegPath;

    @Override
    public String downloadAndConvertToWebp(String imageUrl, String imdbId) {
        if (imageUrl == null || imageUrl.isEmpty() || "N/A".equalsIgnoreCase(imageUrl)) {
            return null;
        }

        try {
            // Ensure the posters directory exists
            Path postersDirPath = Paths.get(uploadDir, "posters");
            if (!Files.exists(postersDirPath)) {
                Files.createDirectories(postersDirPath);
            }

            // 1. Download the original image to a temp file
            log.info("Downloading image from {}...", imageUrl);
            Path tempInputPath = postersDirPath.resolve(imdbId + "_raw.jpg");
            try (InputStream in = new URL(imageUrl).openStream()) {
                Files.copy(in, tempInputPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Image downloaded to {}", tempInputPath);

            // 2. Prepare WebP output path
            String webpFilename = imdbId + ".webp";
            Path webpOutputPath = postersDirPath.resolve(webpFilename);

            // 3. Convert via FFmpeg
            log.info("Converting {} to WebP...", tempInputPath);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-y", // overwrite output files
                    "-i", tempInputPath.toString(), // input file
                    "-c:v", "libwebp", // use libwebp codec
                    "-quality", "80", // quality scale (0-100)
                    webpOutputPath.toString());

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            // 4. Clean up original temp file
            Files.deleteIfExists(tempInputPath);

            if (exitCode == 0) {
                log.info("Successfully created WebP poster: {}", webpOutputPath);
                return webpFilename;
            } else {
                log.error("FFmpeg exited with error code {} while converting poster.", exitCode);
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to download or convert poster to WebP for IMDb ID: {}", imdbId, e);
            return null;
        }
    }
}
