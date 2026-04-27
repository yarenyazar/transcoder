package com.yaren.transcoder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transcoderOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Yaren Transcoder API")
                        .description("Video Transcoding Service API Documentation")
                        .version("v1.0"));
    }
}
