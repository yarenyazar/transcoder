package com.yaren.transcoder.controller;

import com.yaren.transcoder.dto.party.RoomRequest;
import com.yaren.transcoder.dto.party.RoomResponse;
import com.yaren.transcoder.entity.Room;
import com.yaren.transcoder.entity.User;
import com.yaren.transcoder.repository.RoomRepository;
import com.yaren.transcoder.repository.UserRepository;
import com.yaren.transcoder.security.UserDetailsImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/party/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.yaren.transcoder.service.WatchPartyRoomService watchPartyRoomService;

    public RoomController(RoomRepository roomRepository, UserRepository userRepository, PasswordEncoder passwordEncoder, com.yaren.transcoder.service.WatchPartyRoomService watchPartyRoomService) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.watchPartyRoomService = watchPartyRoomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@RequestBody RoomRequest request) {
        UserDetailsImpl currentUser = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User host = userRepository.findById(currentUser.getId()).orElseThrow();

        Room room = new Room();
        room.setContentId(request.getContentId());
        room.setHost(host);
        room.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        
        // Locked until 120 seconds after creation
        room.setLockedUntil(LocalDateTime.now().plusSeconds(120));
        
        roomRepository.save(room);

        return ResponseEntity.ok(new RoomResponse(room.getId(), room.getLockedUntil(), host.getId()));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @RequestBody RoomRequest request) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found");
        }

        UserDetailsImpl currentUser = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean isAdmin = currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin && !currentUser.getId().equals(room.getHost().getId())) {
            if (!passwordEncoder.matches(request.getPassword(), room.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid room password");
            }
        }

        com.yaren.transcoder.dto.party.RoomState state = watchPartyRoomService.getRoomState(roomId);
        if (state != null && state.getViewers() != null && state.getViewers().size() >= 2) {
            // Allow if user is reconnecting
            boolean isReconnecting = state.getViewers().stream().anyMatch(v -> v.getUserId().equals(currentUser.getId()));
            if (!isReconnecting) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Room is full");
            }
        }

        return ResponseEntity.ok(new RoomResponse(room.getId(), room.getLockedUntil(), room.getHost().getId()));
    }

    @GetMapping("/active/{contentId}")
    public ResponseEntity<java.util.List<RoomResponse>> getActiveRooms(@PathVariable String contentId) {
        java.util.List<Room> rooms = roomRepository.findByContentIdOrderByCreatedAtDesc(contentId);
        java.util.List<RoomResponse> responses = rooms.stream()
                .map(room -> new RoomResponse(room.getId(), room.getLockedUntil(), room.getHost().getId()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Room not found");
        }

        UserDetailsImpl currentUser = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean isAdmin = currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin || currentUser.getId().equals(room.getHost().getId())) {
            watchPartyRoomService.deleteRoom(roomId);
            roomRepository.delete(room);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only host or admin can delete the room");
        }
    }
}
