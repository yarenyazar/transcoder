package com.yaren.transcoder.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "vod_contents")
public class VodContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // "MOVIE", "SERIES" vs.
    private String type;

    private Integer releaseYear;

    // Total duration in seconds or minutes
    private Integer durationSeconds;

    // e.g. tt1234567
    private String imdbId;

    // Original poster URL or our local converted one
    private String posterUrl;

    // Converted WebP poster URL
    private String webpPosterUrl;

    // Main background image
    private String backdropUrl;

    // URL to the streaming playlist (.m3u8)
    private String videoUrl;

    // Ready, Encoding, Failed, etc.
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @ManyToMany
    @JoinTable(name = "vod_content_cast", joinColumns = @JoinColumn(name = "content_id"), inverseJoinColumns = @JoinColumn(name = "cast_member_id"))
    private Set<CastMember> castMembers = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "vod_content_subtitles", joinColumns = @JoinColumn(name = "vod_content_id"))
    @MapKeyColumn(name = "language")
    @Column(name = "subtitle_url")
    private java.util.Map<String, String> subtitles = new java.util.HashMap<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getWebpPosterUrl() {
        return webpPosterUrl;
    }

    public void setWebpPosterUrl(String webpPosterUrl) {
        this.webpPosterUrl = webpPosterUrl;
    }

    public String getBackdropUrl() {
        return backdropUrl;
    }

    public void setBackdropUrl(String backdropUrl) {
        this.backdropUrl = backdropUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<CastMember> getCastMembers() {
        return castMembers;
    }

    public void setCastMembers(Set<CastMember> castMembers) {
        this.castMembers = castMembers;
    }

    public java.util.Map<String, String> getSubtitles() {
        return subtitles;
    }

    public void setSubtitles(java.util.Map<String, String> subtitles) {
        this.subtitles = subtitles;
    }
}
