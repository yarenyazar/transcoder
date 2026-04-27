package com.yaren.transcoder.service;

public interface ImageOptimizationService {
    /**
     * Downloads an image from the given URL, converts it to WebP using FFmpeg,
     * and saves it to a designated output folder.
     * 
     * @param imageUrl The URL of the image to download (e.g., from IMDb)
     * @param imdbId   The ID to use for generating the final filename (e.g.
     *                 tt1234567.webp)
     * @return The local filename or relative path of the generated WebP file (e.g.
     *         "tt1234567.webp"),
     *         or null if operation failed.
     */
    String downloadAndConvertToWebp(String imageUrl, String imdbId);
}
