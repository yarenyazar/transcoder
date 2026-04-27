package com.yaren.transcoder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "transcodeTaskExecutor")
    public Executor transcodeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Aynı anda sadece 1 video işlensin
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // Kuyruk kapasitesi (bekleyen işler için)
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Transcoder-");
        executor.initialize();
        System.out.println("✅ Transcode Task Executor Hazır: Core=1, Max=1, Queue=500");
        return executor;
    }
}
