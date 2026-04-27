package com.yaren.transcoder.controller;

import com.yaren.transcoder.dto.party.*;
import com.yaren.transcoder.entity.Message;
import com.yaren.transcoder.repository.MessageRepository;
import com.yaren.transcoder.repository.RoomRepository;
import com.yaren.transcoder.repository.UserRepository;
import com.yaren.transcoder.security.UserDetailsImpl;
import com.yaren.transcoder.service.WatchPartyRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Watch Party WebSocket + REST Controller
 *
 * WebSocket kanalları:
 *   /app/party/{roomId}/sync  → PLAY | PAUSE | SEEK | SET_FILM
 *   /app/party/{roomId}/chat  → Sohbet mesajı
 *   /app/party/{roomId}/event → JOIN | LEAVE
 *
 * Yayın kanalları:
 *   /topic/party/{roomId}/sync
 *   /topic/party/{roomId}/chat
 *   /topic/party/{roomId}/event
 */
@RestController
@RequestMapping("/api/party")
public class WatchPartyController {

    private final WatchPartyRoomService roomService;
    private final SimpMessagingTemplate messaging;
    private final RoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public WatchPartyController(WatchPartyRoomService roomService,
                                SimpMessagingTemplate messaging,
                                RoomRepository roomRepository,
                                MessageRepository messageRepository,
                                UserRepository userRepository) {
        this.roomService = roomService;
        this.messaging = messaging;
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    // ─────────────────────────────────────────────────────────────
    // REST
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/{roomId}/state")
    public ResponseEntity<RoomState> getRoomState(@PathVariable String roomId) {
        RoomState state = roomService.getRoomState(roomId);
        if (state == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(state);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(@PathVariable String roomId) {
        List<ChatMessage> messages = roomRepository.findById(roomId)
                .map(room -> messageRepository.findByRoomOrderByTimestampDesc(room).stream()
                        .map(m -> {
                            ChatMessage dto = new ChatMessage();
                            dto.setRoomId(room.getId());
                            dto.setUserId(m.getSender().getId());
                            dto.setUsername(m.getSender().getUsername());
                            dto.setContent(m.getContent());
                            dto.setServerTime(m.getTimestamp().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                            return dto;
                        }).collect(Collectors.toList()))
                .orElse(List.of());
        return ResponseEntity.ok(messages);
    }

    // ─────────────────────────────────────────────────────────────
    // WebSocket — Sohbet
    // ─────────────────────────────────────────────────────────────

    @MessageMapping("/party/{roomId}/chat")
    @SendTo("/topic/party/{roomId}/chat")
    public ChatMessage handleChat(@DestinationVariable String roomId,
                                  @Payload ChatMessage message,
                                  Principal principal) {
        message.setServerTime(System.currentTimeMillis());
        enrichFromPrincipal(message, principal);

        // DB'ye kaydet
        roomRepository.findById(roomId).ifPresent(room ->
                userRepository.findByUsername(message.getUsername()).ifPresent(user -> {
                    Message dbMsg = new Message();
                    dbMsg.setRoom(room);
                    dbMsg.setSender(user);
                    dbMsg.setContent(message.getContent());
                    messageRepository.save(dbMsg);
                })
        );
        return message;
    }

    // ─────────────────────────────────────────────────────────────
    // WebSocket — Oda olayları (JOIN / LEAVE)
    // ─────────────────────────────────────────────────────────────

    @MessageMapping("/party/{roomId}/event")
    @SendTo("/topic/party/{roomId}/event")
    public RoomEvent handleEvent(@DestinationVariable String roomId,
                                 @Payload RoomEvent event) {
        event.setServerTime(System.currentTimeMillis());
        event.setRoomId(roomId);

        switch (event.getEventType()) {
            case "JOIN" -> {
                try {
                    roomService.userJoined(roomId, event.getUserId(), event.getUsername());
                } catch (RuntimeException e) {
                    if ("ROOM_FULL".equals(e.getMessage())) {
                        RoomEvent err = new RoomEvent();
                        err.setRoomId(roomId);
                        err.setEventType("ERROR");
                        err.setUserId(event.getUserId());
                        err.setUsername("System");
                        err.setServerTime(System.currentTimeMillis());
                        messaging.convertAndSend("/topic/party/" + roomId + "/event", err);
                        return null;
                    }
                }
            }
            case "LEAVE" -> roomService.userLeft(roomId, event.getUserId());
        }
        return event;
    }

    // ─────────────────────────────────────────────────────────────
    // WebSocket — Video senkronizasyonu
    // ─────────────────────────────────────────────────────────────

    @MessageMapping("/party/{roomId}/sync")
    @SendTo("/topic/party/{roomId}/sync")
    public SyncMessage handleSync(@DestinationVariable String roomId,
                                  @Payload SyncMessage message) {
        message.setServerTime(System.currentTimeMillis());
        message.setRoomId(roomId);

        double position = message.getCurrentPosition() != null ? message.getCurrentPosition() : 0.0;
        String userId = message.getUserId();

        switch (message.getAction()) {

            case "PLAY" -> {
                boolean allowed = roomService.applyPlay(roomId, userId, position);
                if (!allowed) return deniedMsg(roomId, userId, roomService.getRoomState(roomId));

                // Güncel state'i mesaja ekle
                RoomState state = roomService.getRoomState(roomId);
                if (state != null) clearLockFields(message);
                return message;
            }

            case "PAUSE" -> {
                boolean allowed = roomService.applyPause(roomId, userId, position);
                if (!allowed) return deniedMsg(roomId, userId, roomService.getRoomState(roomId));

                RoomState state = roomService.getRoomState(roomId);
                if (state != null) {
                    message.setPausedByUserId(state.getPausedByUserId());
                    message.setPausedByUsername(state.getPausedByUsername());
                    message.setUnlockAt(state.getUnlockAt());
                }
                return message;
            }

            case "SEEK" -> {
                roomService.applySeek(roomId, userId, position);
                RoomState state = roomService.getRoomState(roomId);
                if (state != null && state.getUnlockAt() != null) {
                    // Kilit sırasında: reddet
                    if (state.getUnlockAt() > System.currentTimeMillis()
                            && !userId.equals(state.getPausedByUserId())) {
                        return deniedMsg(roomId, userId, state);
                    }
                }
                return message;
            }

            case "SET_FILM" -> {
                String filmUrl = message.getVideoUrl();
                if (filmUrl == null || filmUrl.isBlank()) return null;
                roomService.getOrCreateRoom(roomId);  // Oda yoksa oluştur
                roomService.updateRoomFilm(roomId, filmUrl);
                return message;
            }

            default -> { return message; }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // WebSocket — WebRTC Sinyalizasyon (pass-through relay)
    // ─────────────────────────────────────────────────────────────

    /**
     * Tüm WebRTC sinyal mesajlarını (offer/answer/ice/user-joined/user-left)
     * alıp değiştirmeden odadaki tüm kullanıcılara iletir.
     * Frontend tarafı type alanına göre filtreleme yapar.
     */
    @MessageMapping("/party/{roomId}/webrtc")
    @SendTo("/topic/party/{roomId}/webrtc")
    public Map<String, Object> handleWebRtc(@DestinationVariable String roomId,
                                            @Payload Map<String, Object> signal) {
        return signal;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private SyncMessage deniedMsg(String roomId, String userId, RoomState state) {
        SyncMessage denied = new SyncMessage();
        denied.setRoomId(roomId);
        denied.setAction("DENIED");
        denied.setUserId(userId);
        denied.setServerTime(System.currentTimeMillis());
        if (state != null) {
            denied.setCurrentPosition(state.getCurrentPosition());
            denied.setPausedByUserId(state.getPausedByUserId());
            denied.setPausedByUsername(state.getPausedByUsername());
            denied.setUnlockAt(state.getUnlockAt());
        }
        return denied;
    }

    private void clearLockFields(SyncMessage msg) {
        msg.setPausedByUserId(null);
        msg.setPausedByUsername(null);
        msg.setUnlockAt(null);
    }

    private void enrichFromPrincipal(ChatMessage msg, Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UserDetailsImpl ud) {
            msg.setUserId(ud.getId());
            msg.setUsername(ud.getUsername());
        }
    }
}
