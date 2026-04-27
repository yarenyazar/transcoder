package com.yaren.transcoder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Tüm endpointlere izin ver
                        .allowedOrigins("http://localhost:4200", "http://localhost", "http://127.0.0.1") // Angular dev
                                                                                                         // server ve
                                                                                                         // Docker
                                                                                                         // server'a
                                                                                                         // izin ver
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
}