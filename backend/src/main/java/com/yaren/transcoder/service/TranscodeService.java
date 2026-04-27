package com.yaren.transcoder.service;

import com.yaren.transcoder.entity.TranscodingJob;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

public interface TranscodeService {
    void startTranscoding(Long jobId);

    TranscodingJob createAndStartJob(MultipartFile file, Long presetId) throws IOException;

    TranscodingJob createAndStartJobFromExisting(String fileName, Long presetId) throws IOException;

    void deleteJob(Long id);

    List<String> listUploadedFiles();

    Map<String, Object> getJobDetails(Long id);

    Resource getDownloadResource(String fileName) throws MalformedURLException;
}