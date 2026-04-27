package com.yaren.transcoder.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transcoding_jobs")
public class TranscodingJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fileName;
    private String inputPath;
    private String outputPath;
    
    // YENİ EKLENEN ALAN: Frontend'in videoyu bulması için şart
    private String outputFileName; 
    
    private String status;
    private Integer progress;
    private LocalDateTime timestamp;

    @ManyToOne
    private Preset selectedPreset;

    // Mevcut Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getInputPath() { return inputPath; }
    public void setInputPath(String inputPath) { this.inputPath = inputPath; }
    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }

    // YENİ GETTER VE SETTER: Controller'daki hatayı bu metodlar çözer
    public String getOutputFileName() { return outputFileName; }
    public void setOutputFileName(String outputFileName) { this.outputFileName = outputFileName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Preset getSelectedPreset() { return selectedPreset; }
    public void setSelectedPreset(Preset selectedPreset) { this.selectedPreset = selectedPreset; }
}