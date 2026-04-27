package com.yaren.transcoder.service.implementation;

import com.yaren.transcoder.dto.party.RoomState;
import com.yaren.transcoder.dto.party.SyncMessage;
import com.yaren.transcoder.dto.party.ViewerState;
import com.yaren.transcoder.entity.Room;
import com.yaren.transcoder.repository.RoomRepository;
import com.yaren.transcoder.service.WatchPartyRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class WatchPartyRoomServiceImpl implements WatchPartyRoomService {

    private static final Logger log = LoggerFactory.getLogger(WatchPartyRoomServiceImpl.class);
    private static final long LOCK_DURATION_MS = 120_000L; // 2 dakika
    private static final int MAX_VIEWERS = 2;
    private static final long STALE_THRESHOLD_MS = 60_000L;      // 60s heartbeat yok → stale
    private static final long RECONNECT_GRACE_MS = 10_000L;      // ayrılınca 10s beklenir

    // roomId → RoomState (in-memory oda durumu)
    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();

    // roomId → (userId → ViewerState)
    private final Map<String, Map<String, ViewerState>> roomViewers = new ConcurrentHashMap<>();

    // roomId → scheduled pause-auto-lift task
    private final Map<String, ScheduledFuture<?>> pauseLocks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RoomRepository roomRepository;
    private final SimpMessagingTemplate messaging;

    public WatchPartyRoomServiceImpl(RoomRepository roomRepository, SimpMessagingTemplate messaging) {
        this.roomRepository = roomRepository;
        this.messaging = messaging;
    }

    // ──────────────────────────────────────────────────────────────────
    // Oda lifecycle
    // ──────────────────────────────────────────────────────────────────

    @Override
    public RoomState getOrCreateRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, id -> {
            RoomState s = new RoomState();
            s.setCurrentPosition(0.0);
            s.setPlaying(false);
            return s;
        });
    }

    @Override
    public RoomState getRoomState(String roomId) {
        RoomState state = rooms.get(roomId);
        if (state == null) return null;
        state.setServerTime(System.currentTimeMillis());
        state.setViewers(activeViewers(roomId));
        return state;
    }

    @Override
    public void updateRoomFilm(String roomId, String filmUrl) {
        RoomState s = getOrCreateRoom(roomId);
        s.setFilmUrl(filmUrl);
        s.setCurrentPosition(0.0);
        s.setPlaying(false);
        s.setPausedByUserId(null);
        s.setPausedByUsername(null);
        s.setUnlockAt(null);
        cancelPauseLock(roomId);
        log.info("Room {} → film set: {}", roomId, filmUrl);
    }

    @Override
    public void deleteRoom(String roomId) {
        rooms.remove(roomId);
        roomViewers.remove(roomId);
        cancelPauseLock(roomId);
        log.info("Room {} deleted.", roomId);
    }

    // ──────────────────────────────────────────────────────────────────
    // Kullanıcı katılım / ayrılma
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void userJoined(String roomId, String userId, String username) {
        RoomState state = getOrCreateRoom(roomId);
        Map<String, ViewerState> viewers = roomViewers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

        // Yeniden bağlantı
        if (viewers.containsKey(userId)) {
            ViewerState existing = viewers.get(userId);
            existing.setBagliMi(true);
            existing.setLastHeartbeat(System.currentTimeMillis());
            log.info("User {} ({}) RECONNECTED to room {}", userId, username, roomId);
            return;
        }

        // Oda dolu mu?
        if (activeViewers(roomId).size() >= MAX_VIEWERS) {
            log.warn("Room {} is full. Rejected {}", roomId, userId);
            throw new RuntimeException("ROOM_FULL");
        }

        // Host seçimi (DB'den)
        if (state.getHostId() == null) {
            Room dbRoom = roomRepository.findById(roomId).orElse(null);
            if (dbRoom != null) {
                state.setHostId(dbRoom.getHost().getId());
            } else {
                state.setHostId(userId);
            }
            log.info("Room {} host set to {}", roomId, state.getHostId());
        }

        ViewerState viewer = new ViewerState(userId, username);
        viewer.setBagliMi(true);
        viewer.setLastHeartbeat(System.currentTimeMillis());
        viewers.put(userId, viewer);
        log.info("User {} ({}) joined room {}", userId, username, roomId);
    }

    @Override
    public void userLeft(String roomId, String userId) {
        Map<String, ViewerState> viewers = roomViewers.get(roomId);
        if (viewers == null) return;

        ViewerState viewer = viewers.get(userId);
        if (viewer == null) return;

        viewer.setBagliMi(false);
        log.info("User {} left room {}. Grace period {}ms", userId, roomId, RECONNECT_GRACE_MS);

        scheduler.schedule(() -> {
            ViewerState current = viewers.get(userId);
            if (current != null && !current.isBagliMi()) {
                viewers.remove(userId);
                log.info("User {} permanently removed from room {}", userId, roomId);

                // Bu kullanıcı pause yapmıştı → kilidi kaldır
                RoomState state = rooms.get(roomId);
                if (state != null && userId.equals(state.getPausedByUserId())) {
                    liftPauseLock(roomId, state, state.getCurrentPosition() != null ? state.getCurrentPosition() : 0.0);
                }
            }
        }, RECONNECT_GRACE_MS, TimeUnit.MILLISECONDS);
    }

    // ──────────────────────────────────────────────────────────────────
    // Video kontrolleri
    // ──────────────────────────────────────────────────────────────────

    @Override
    public boolean applyPlay(String roomId, String userId, double position) {
        RoomState state = getOrCreateRoom(roomId);

        // Kilit aktif ve bu kullanıcı pause etmemişse → reddedildi
        if (isLocked(state) && !userId.equals(state.getPausedByUserId())) {
            log.debug("PLAY rejected for {} — room {} is locked by {}", userId, roomId, state.getPausedByUserId());
            return false;
        }

        state.setPlaying(true);
        state.setCurrentPosition(position);
        cancelPauseLock(roomId);
        state.setPausedByUserId(null);
        state.setPausedByUsername(null);
        state.setUnlockAt(null);

        log.info("Room {} → PLAY at {}s by {}", roomId, position, userId);
        return true;
    }

    @Override
    public boolean applyPause(String roomId, String userId, double position) {
        RoomState state = getOrCreateRoom(roomId);

        // Kilit aktif ve başkası pause yaparsa → reddedildi
        if (isLocked(state) && !userId.equals(state.getPausedByUserId())) {
            log.debug("PAUSE rejected for {} — room {} already locked", userId, roomId);
            return false;
        }

        state.setPlaying(false);
        state.setCurrentPosition(position);
        state.setPausedByUserId(userId);
        state.setPausedByUsername(viewerUsername(roomId, userId));
        long unlockAt = System.currentTimeMillis() + LOCK_DURATION_MS;
        state.setUnlockAt(unlockAt);

        // Önceki kilidi iptal et
        cancelPauseLock(roomId);

        // 2 dakika sonra otomatik kilidi kaldır
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            RoomState current = rooms.get(roomId);
            if (current != null && userId.equals(current.getPausedByUserId()) && !current.isPlaying()) {
                liftPauseLock(roomId, current, current.getCurrentPosition() != null ? current.getCurrentPosition() : 0.0);
                // Otomatik kaldırma bildirimi gönder
                broadcastLockLifted(roomId, current.getCurrentPosition() != null ? current.getCurrentPosition() : 0.0);
            }
        }, LOCK_DURATION_MS, TimeUnit.MILLISECONDS);

        pauseLocks.put(roomId, task);
        log.info("Room {} → PAUSE at {}s by {}. Lock until {}", roomId, position, userId, unlockAt);
        return true;
    }

    @Override
    public void applySeek(String roomId, String userId, double position) {
        RoomState state = getOrCreateRoom(roomId);

        // Kilit aktifken seek → sadece pause eden kullanıcı seek yapabilir
        if (isLocked(state) && !userId.equals(state.getPausedByUserId())) {
            log.debug("SEEK rejected for {} — room {} is locked", userId, roomId);
            return;
        }

        state.setCurrentPosition(position);
        log.debug("Room {} → SEEK to {}s by {}", roomId, position, userId);
    }

    // ──────────────────────────────────────────────────────────────────
    // Yardımcı — kilit kaldırma
    // ──────────────────────────────────────────────────────────────────

    private void liftPauseLock(String roomId, RoomState state, double position) {
        state.setPlaying(false); // Kilit kalktıktan sonra herkes play'e basabilir, otomatik oynatma yok
        state.setPausedByUserId(null);
        state.setPausedByUsername(null);
        state.setUnlockAt(null);
        cancelPauseLock(roomId);
        log.info("Room {} → pause lock lifted at position {}s", roomId, position);
    }

    private void broadcastLockLifted(String roomId, double position) {
        SyncMessage msg = new SyncMessage();
        msg.setRoomId(roomId);
        msg.setAction("LOCK_LIFTED");
        msg.setUserId("SYSTEM");
        msg.setUsername("System");
        msg.setCurrentPosition(position);
        msg.setServerTime(System.currentTimeMillis());
        messaging.convertAndSend("/topic/party/" + roomId + "/sync", msg);
        log.info("Room {} → LOCK_LIFTED broadcast at {}s", roomId, position);
    }

    private void cancelPauseLock(String roomId) {
        ScheduledFuture<?> existing = pauseLocks.remove(roomId);
        if (existing != null) existing.cancel(false);
    }

    private boolean isLocked(RoomState state) {
        return state.getUnlockAt() != null && state.getUnlockAt() > System.currentTimeMillis();
    }

    // ──────────────────────────────────────────────────────────────────
    // Viewer yardımcıları
    // ──────────────────────────────────────────────────────────────────

    private List<ViewerState> activeViewers(String roomId) {
        long threshold = System.currentTimeMillis() - STALE_THRESHOLD_MS;
        return roomViewers.getOrDefault(roomId, new ConcurrentHashMap<>())
                .values().stream()
                .filter(v -> v.isBagliMi() && v.getLastHeartbeat() > threshold)
                .collect(Collectors.toList());
    }

    private String viewerUsername(String roomId, String userId) {
        return Optional.ofNullable(roomViewers.get(roomId))
                .map(v -> v.get(userId))
                .map(ViewerState::getUsername)
                .orElse("Kullanıcı");
    }

    // ──────────────────────────────────────────────────────────────────
    // Zamanlanmış temizlik
    // ──────────────────────────────────────────────────────────────────

    @Override
    @Scheduled(fixedDelay = 30_000)
    public void cleanupStaleUsers() {
        long pruneThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MS;
        roomViewers.forEach((roomId, viewers) -> {
            int before = viewers.size();
            viewers.entrySet().removeIf(e -> e.getValue().getLastHeartbeat() < pruneThreshold);
            int removed = before - viewers.size();
            if (removed > 0) log.info("Cleaned {} stale users from room {}", removed, roomId);
        });
    }
}
