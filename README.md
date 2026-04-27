# Transcoder & OTT Platform

A comprehensive, microservices-oriented Over-The-Top (OTT) video streaming platform. This project handles everything from automated video transcoding and live streaming to real-time synchronized "Watch Parties" and AI-powered subtitle generation.

## Key Features

### Video-on-Demand (VOD) Transcoding
- **Multi-Resolution Support:** Automatically converts uploaded videos into HLS (HTTP Live Streaming) format with various resolutions (360p, 480p, 720p, 1080p).
- **FFmpeg Integration:** Utilizes high-performance FFmpeg via the Jaffree wrapper for efficient video processing.
- **Queue Management:** Intelligent task execution to handle multiple transcoding jobs concurrently.

### Live Streaming (RTMP to HLS)
- **Live Ingest:** Supports RTMP input streams via Nginx-RTMP.
- **Dynamic Conversion:** Real-time conversion from RTMP to HLS for browser-compatible playback.
- **DVR Capabilities:** Automatic segmenting and recording of live sessions.

### Watch Party & Real-time Interaction
- **Synchronized Playback:** Real-time synchronization of video state (Play/Pause/Seek) across all participants using WebSockets (STOMP).
- **WebRTC Integration:** Built-in support for peer-to-peer audio/video communication during watch parties.
- **Live Chat:** Real-time messaging system for room participants.

### AI-Powered Subtitles
- **Automated Transcription:** Dedicated Python microservice using **Faster-Whisper** models to generate subtitles automatically from video audio.
- **Multi-Language Potential:** Leveraging AI for high-accuracy speech-to-text conversion.

### Security & Management
- **JWT Authentication:** Secure user authentication and authorization.
- **CMS Dashboard:** Administrative interface for managing movies, monitoring transcoding jobs, and configuring presets.

---

## Tech Stack

- **Backend:** Java 17, Spring Boot 3, Spring Security, Spring Data JPA, WebSockets (STOMP).
- **Frontend:** Angular, RxJS, HLS.js, SockJS, WebRTC.
- **AI Service:** Python, Flask, Faster-Whisper.
- **Infrastructure:** Docker & Docker Compose, Nginx (with RTMP module), PostgreSQL.
- **Tools:** FFmpeg, Maven.

---

## Architecture

The project follows a modular architecture orchestrated by Docker:

- **`backend/`**: Core logic, API, and transcoding orchestration.
- **`frontend/`**: Modern Angular-based UI for users and admins.
- **`subtitle-service/`**: Python-based AI service for audio processing.
- **`nginx/`**: High-performance reverse proxy and media server.
- **`data/`**: Shared volume for media storage (uploads, HLS outputs).

---

## Getting Started

### Prerequisites
- Docker & Docker Compose
- Git

### Installation

1. **Clone the repository:**
   ```bash
   git clone git@github.com:yarenyazar/transcoder.git
   cd yaren-transcoder
