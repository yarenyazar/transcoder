package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.LiveStreamRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LiveStreamRecordRepository extends JpaRepository<LiveStreamRecord, Long> {

    // Find all chunks belonging to a specific stream, ordered by time
    List<LiveStreamRecord> findByStreamKeyOrderByStartTimeAsc(String streamKey);

    // Find custom old chunks that surpass retention threshold, to delete them
    List<LiveStreamRecord> findByStreamKeyAndStartTimeBefore(String streamKey, LocalDateTime threshold);

    // Find all old chunks globally if needed by retention scheduler
    List<LiveStreamRecord> findByStartTimeBefore(LocalDateTime threshold);
}
