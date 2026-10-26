package com.aws.alphabetuniverse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;

@Configuration
public class PollyConfig {

    @Bean
    public PollyClient pollyClient(
            @Value("${aws.region:us-east-1}") String region
    ) {
        return PollyClient.builder()
                .region(Region.of(region))
                .build();
    }
}