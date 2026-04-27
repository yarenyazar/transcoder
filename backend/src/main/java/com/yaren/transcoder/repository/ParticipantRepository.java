package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, String> {
    Optional<Participant> findByUserIdAndRoomId(String userId, String roomId);
}
