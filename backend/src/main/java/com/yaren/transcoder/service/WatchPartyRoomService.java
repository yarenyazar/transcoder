package com.yaren.transcoder.service;

import com.yaren.transcoder.dto.party.RoomState;

public interface WatchPartyRoomService {

    /** Oda yoksa oluştur, varsa state döndür */
    RoomState getOrCreateRoom(String roomId);

    /** Mevcut durumu döndür (null = oda yok) */
    RoomState getRoomState(String roomId);

    /** Film URL'ini oda'ya ata, pozisyonu sıfırla */
    void updateRoomFilm(String roomId, String filmUrl);

    /** Kullanıcı katıldı → roomFull exception fırlatabilir */
    void userJoined(String roomId, String userId, String username);

    /** Kullanıcı ayrıldı → gerekirse kilidi kaldır */
    void userLeft(String roomId, String userId);

    /** PLAY: kilidi kaldır (eğer bu kullanıcı pause etmişse), isPlaying=true */
    boolean applyPlay(String roomId, String userId, double position);

    /** PAUSE: 2 dk kilit başlat, isPlaying=false */
    boolean applyPause(String roomId, String userId, double position);

    /** SEEK: pozisyon güncelle, kilit gerektirmez */
    void applySeek(String roomId, String userId, double position);

    /** Stale kullanıcıları temizle — @Scheduled ile çağrılır */
    void cleanupStaleUsers();

    /** Odayı ve tüm in-memory state'i sil */
    void deleteRoom(String roomId);
}
