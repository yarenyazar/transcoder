package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.VodContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VodContentRepository extends JpaRepository<VodContent, Long> {
    boolean existsByImdbId(String imdbId);
}
