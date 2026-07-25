package com.g93.be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("local")
public class S3Config {

    @Value("${app.aws.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        // Builds the S3 client using the specified region.
        // It uses the default credentials provider chain (e.g., environment variables, ~/.aws/credentials).
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
