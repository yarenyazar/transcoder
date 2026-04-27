package com.yaren.transcoder.service.implementation;

import com.yaren.transcoder.entity.Preset;
import com.yaren.transcoder.entity.TranscodingJob;
import com.yaren.transcoder.repository.PresetRepository;
import com.yaren.transcoder.repository.TranscodingJobRepository;
import com.yaren.transcoder.service.PresetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PresetServiceImpl implements PresetService {

    private final PresetRepository presetRepository;
    private final TranscodingJobRepository jobRepository;

    public PresetServiceImpl(PresetRepository presetRepository, TranscodingJobRepository jobRepository) {
        this.presetRepository = presetRepository;
        this.jobRepository = jobRepository;
    }

    @Override
    public List<Preset> getAllPresets() {
        return presetRepository.findAll();
    }

    @Override
    public Preset createPreset(Preset preset) {
        return presetRepository.save(preset);
    }

    @Override
    public Preset updatePreset(Long id, Preset preset) {
        Preset existing = presetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Preset not found with id: " + id));
        
        existing.setName(preset.getName());
        existing.setWidth(preset.getWidth());
        existing.setHeight(preset.getHeight());
        existing.setVideoCodec(preset.getVideoCodec());
        existing.setVideoBitrate(preset.getVideoBitrate());
        existing.setFps(preset.getFps());
        existing.setAspectRatio(preset.getAspectRatio());
        existing.setAudioCodec(preset.getAudioCodec());
        existing.setAudioBitrate(preset.getAudioBitrate());
        existing.setAudioSampleRate(preset.getAudioSampleRate());
        existing.setAudioChannels(preset.getAudioChannels());
        existing.setCrf(preset.getCrf());
        existing.setPresetSpeed(preset.getPresetSpeed());
        existing.setContainer(preset.getContainer());
        existing.setTune(preset.getTune());
        existing.setProfile(preset.getProfile());
        existing.setKeyframeInterval(preset.getKeyframeInterval());

        return presetRepository.save(existing);
    }

    @Transactional
    @Override
    public void deletePreset(Long id) {
        // Önce bu preseti kullanan işleri bul ve bağı kopar
        List<TranscodingJob> jobs = jobRepository.findBySelectedPresetId(id);
        for (TranscodingJob job : jobs) {
            job.setSelectedPreset(null);
            jobRepository.save(job);
        }
        // Şimdi güvenle sil
        presetRepository.deleteById(id);
    }
}
