package com.yaren.transcoder.repository;

import com.yaren.transcoder.entity.Message;
import com.yaren.transcoder.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByRoomOrderByTimestampDesc(Room room);
}
