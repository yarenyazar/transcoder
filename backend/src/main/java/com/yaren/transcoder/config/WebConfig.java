package com.yaren.transcoder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.output-dir:./outputs}")
    private String outputDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File folder = new File(outputDir);
        String absolutePath = folder.getAbsolutePath();
        
        if (!absolutePath.endsWith("/")) {
            absolutePath += "/";
        }

        String resourceLocation = "file://" + absolutePath;
        System.out.println("🚀 Video output path: " + resourceLocation);

        registry.addResourceHandler("/outputs/**")
                .addResourceLocations(resourceLocation);
    }
}