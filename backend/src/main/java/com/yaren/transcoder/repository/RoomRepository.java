package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    java.util.List<Room> findByContentIdOrderByCreatedAtDesc(String contentId);
}
