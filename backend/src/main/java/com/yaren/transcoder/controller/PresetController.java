package com.yaren.transcoder.controller;

import com.yaren.transcoder.entity.Preset;
import com.yaren.transcoder.service.PresetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/presets")
@CrossOrigin(origins = "*")
public class PresetController {
    private final PresetService presetService;
    private static final Logger logger = LoggerFactory.getLogger(PresetController.class);

    // Manuel Constructor
    public PresetController(PresetService presetService) {
        this.presetService = presetService;
        logger.info("✅ PresetController BAŞARIYLA YÜKLENDİ (Service Enjected)!");
    }

    @GetMapping
    public List<Preset> getAll() {
        return presetService.getAllPresets();
    }

    @PostMapping
    public Preset create(@RequestBody Preset preset) {
        logger.info("Yeni preset oluşturuluyor: {}", preset.getName());
        return presetService.createPreset(preset);
    }

    @PutMapping("/{id}")
    public Preset update(@PathVariable Long id, @RequestBody Preset preset) {
        logger.info("Preset güncelleniyor. ID: {}, Name: {}", id, preset.getName());
        return presetService.updatePreset(id, preset);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        logger.info("Preset siliniyor. ID: {}", id);
        presetService.deletePreset(id);
    }
}