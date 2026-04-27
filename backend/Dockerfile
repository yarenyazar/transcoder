# ===== Build Stage =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ===== Runtime Stage =====
FROM eclipse-temurin:17-jre

# FFmpeg ve Shaka Packager kurulumu
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Mimariye göre doğru Shaka Packager sürümünü indir
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "aarch64" ]; then \
    curl -L https://github.com/shaka-project/shaka-packager/releases/download/v3.4.0/packager-linux-arm64 -o /usr/local/bin/packager; \
    else \
    curl -L https://github.com/shaka-project/shaka-packager/releases/download/v3.4.0/packager-linux-x64 -o /usr/local/bin/packager; \
    fi && \
    chmod +x /usr/local/bin/packager

WORKDIR /app

# Data dizinlerini oluştur
RUN mkdir -p /data/uploads /data/outputs

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", "-jar", "app.jar"]
