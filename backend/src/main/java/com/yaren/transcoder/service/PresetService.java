package com.yaren.transcoder.service;

import com.yaren.transcoder.entity.Preset;
import java.util.List;

public interface PresetService {
    List<Preset> getAllPresets();
    Preset createPreset(Preset preset);
    Preset updatePreset(Long id, Preset preset);
    void deletePreset(Long id);
}
