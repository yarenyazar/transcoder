package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.Preset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PresetRepository extends JpaRepository<Preset, Long> {
}