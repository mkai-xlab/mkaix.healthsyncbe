package com.g93.be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class LocalFileWebConfig implements WebMvcConfigurer {

    private final Path avatarRoot;

    public LocalFileWebConfig(@Value("${app.avatar.storage-dir}") String avatarStorageDir) {
        this.avatarRoot = Path.of(avatarStorageDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/avatars/**")
                .addResourceLocations(avatarRoot.toUri().toString());
    }
}
