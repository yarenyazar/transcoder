package com.yaren.transcoder.config;

import com.yaren.transcoder.service.WatchPartyRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketEventListener {
    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final WatchPartyRoomService roomService;
    // Map sessionId to userId and roomId
    private final Map<String, UserSessionInfo> sessions = new ConcurrentHashMap<>();

    public WebSocketEventListener(WatchPartyRoomService roomService) {
        this.roomService = roomService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("Received a new web socket connection. SessionId: {}", headerAccessor.getSessionId());
    }

    /**
     * Links a userId and roomId to a sessionId when a JOIN event is processed.
     * This is needed because SessionDisconnectEvent doesn't contain the custom payload.
     */
    public void linkSession(String sessionId, String userId, String roomId) {
        sessions.put(sessionId, new UserSessionInfo(userId, roomId));
        log.info("Linked sessionId {} to user {} in room {}", sessionId, userId, roomId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        UserSessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            log.info("User {} disconnected from session {}. Notifying room {}", info.userId, sessionId, info.roomId);
            roomService.userLeft(info.roomId, info.userId);
        }
    }

    private static class UserSessionInfo {
        String userId;
        String roomId;

        UserSessionInfo(String userId, String roomId) {
            this.userId = userId;
            this.roomId = roomId;
        }
    }
}
