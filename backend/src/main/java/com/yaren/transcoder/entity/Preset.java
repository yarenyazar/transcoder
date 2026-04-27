package com.yaren.transcoder.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "presets")
public class Preset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    
    // Video Parametreleri
    private int width;
    private int height;
    private String videoCodec;
    private int videoBitrate;
    private int fps;
    private String aspectRatio;
    
    // Ses Parametreleri
    private String audioCodec;
    private int audioBitrate;
    private int audioSampleRate;   // 44100, 48000 Hz
    private int audioChannels;     // 1=Mono, 2=Stereo, 6=5.1
    
    // Kodlama Kalitesi
    private int crf;
    private String presetSpeed;    // ultrafast, medium, veryslow
    private String container;      // mp4, mkv, avi, webm
    private String tune;           // film, animation, grain, zerolatency
    @Column(name = "codec_profile")
    private String profile;        // baseline, main, high
    private int keyframeInterval;  // GOP size

    // ===== GETTERS =====
    public Long getId() { return id; }
    public String getName() { return name; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getVideoCodec() { return videoCodec; }
    public int getVideoBitrate() { return videoBitrate; }
    public int getFps() { return fps; }
    public String getAspectRatio() { return aspectRatio; }
    public String getAudioCodec() { return audioCodec; }
    public int getAudioBitrate() { return audioBitrate; }
    public int getAudioSampleRate() { return audioSampleRate; }
    public int getAudioChannels() { return audioChannels; }
    public int getCrf() { return crf; }
    public String getPresetSpeed() { return presetSpeed; }
    public String getContainer() { return container; }
    public String getTune() { return tune; }
    public String getProfile() { return profile; }
    public int getKeyframeInterval() { return keyframeInterval; }

    // ===== SETTERS =====
    public void setName(String name) { this.name = name; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }
    public void setVideoBitrate(int videoBitrate) { this.videoBitrate = videoBitrate; }
    public void setFps(int fps) { this.fps = fps; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }
    public void setAudioBitrate(int audioBitrate) { this.audioBitrate = audioBitrate; }
    public void setAudioSampleRate(int audioSampleRate) { this.audioSampleRate = audioSampleRate; }
    public void setAudioChannels(int audioChannels) { this.audioChannels = audioChannels; }
    public void setCrf(int crf) { this.crf = crf; }
    public void setPresetSpeed(String presetSpeed) { this.presetSpeed = presetSpeed; }
    public void setContainer(String container) { this.container = container; }
    public void setTune(String tune) { this.tune = tune; }
    public void setProfile(String profile) { this.profile = profile; }
    public void setKeyframeInterval(int keyframeInterval) { this.keyframeInterval = keyframeInterval; }
}