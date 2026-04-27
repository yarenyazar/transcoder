package com.yaren.transcoder.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface VodTranscoderService {
    void uploadAndTranscodeVod(Long vodContentId, MultipartFile file, java.util.List<Long> presetIds)
            throws IOException;
}
