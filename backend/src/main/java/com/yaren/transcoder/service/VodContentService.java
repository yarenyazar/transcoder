package com.yaren.transcoder.service;

import com.yaren.transcoder.entity.VodContent;
import com.yaren.transcoder.dto.VodContentRequest;

import java.util.List;

public interface VodContentService {
    VodContent createFromImdb(String imdbId);

    VodContent createManual(VodContentRequest request);

    List<VodContent> getAllContents();

    VodContent getContentById(Long id);

    VodContent updateContent(Long id, VodContentRequest request);

    void deleteContent(Long id);

    VodContent uploadSubtitle(Long id, String language, org.springframework.web.multipart.MultipartFile file);

    VodContent generateSubtitles(Long id, String language);

    OmdbService getOmdbService();
}
