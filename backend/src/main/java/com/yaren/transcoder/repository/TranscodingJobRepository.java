package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.TranscodingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranscodingJobRepository extends JpaRepository<TranscodingJob, Long> {
    java.util.List<TranscodingJob> findBySelectedPresetId(Long presetId);
}