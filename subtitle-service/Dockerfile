FROM python:3.10

WORKDIR /app

# System dependencies for faster-whisper (ffmpeg and dev headers for PyAV on ARM64)
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg pkg-config \
    libavformat-dev libavcodec-dev libavdevice-dev libavutil-dev \
    libswscale-dev libswresample-dev libavfilter-dev && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --upgrade pip && pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 5000

CMD ["python", "app.py"]
