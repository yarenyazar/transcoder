package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.CastMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CastMemberRepository extends JpaRepository<CastMember, Long> {
    List<CastMember> findByNameContainingIgnoreCase(String name);
}
