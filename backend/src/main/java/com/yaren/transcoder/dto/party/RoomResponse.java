package com.yaren.transcoder.dto.party;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomResponse {
    private String roomId;
    private LocalDateTime lockedUntil;
    private String hostId;
}
